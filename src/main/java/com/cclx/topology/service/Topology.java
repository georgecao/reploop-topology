package com.cclx.topology.service;

import com.cclx.topology.Constants;
import com.cclx.topology.Link;
import com.cclx.topology.core.HostPort;
import com.cclx.topology.core.State;
import com.cclx.topology.model.*;
import com.cclx.topology.repository.HostRepository;
import com.cclx.topology.repository.NetworkFileRepository;
import com.cclx.topology.repository.ServerPortRepository;
import com.cclx.topology.repository.ServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.Process;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.cclx.topology.core.Accessible.YES;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

@Component
@Slf4j
@Order
public class Topology implements InitializingBean {
    @Autowired
    private NetworkFileRepository networkFileRepository;
    @Autowired
    private ServerPortRepository serverPortRepository;
    @Resource
    private ServiceRepository serviceRepository;

    private String nullToEmpty(String val) {
        return null == val ? "" : val;
    }

    private Set<HostPort> allOfService(Service service, Map<HostPort, Service> serviceMap) {
        Set<HostPort> ports = new HashSet<>();
        serviceMap.forEach((hp, s) -> {
            if (service.getId().equals(s.getId())) {
                ports.add(hp);
            }
        });
        return ports;
    }

    private volatile AtomicLong seed;

    private boolean mergeUnknown = true;

    private Long nextVirtualServiceId() {
        if (null == seed) {
            seed = new AtomicLong(System.currentTimeMillis());
        }
        long serviceId = seed.get();
        if (!mergeUnknown) {
            serviceId = seed.incrementAndGet();
        }
        return -(serviceId);
    }

    public void analyze() {
        // group by service
        List<NetworkFile> all = networkFileRepository.findAll(Sort.by(Sort.Direction.ASC, "host", "localHost", "localPort"));
        Set<HostPort> ipPorts = all.stream()
                .filter(r -> r.getState() == State.LISTEN)
                .map(r -> new HostPort(r.getLocalHost(), r.getLocalPort()))
                .collect(Collectors.toSet());
        Map<HostPort, Service> serviceMap = getServices();
        outputKnownServices(serviceMap, ipPorts);

        Set<Link> links = new HashSet<>();
        for (NetworkFile file : all) {
            if (file.getState() == State.LISTEN || filter(file.getLocalPort()) || filter(file.getRemotePort())) {
                continue;
            }
            HostPort local = new HostPort(file.getLocalHost(), file.getLocalPort());
            Service localService = serviceMap.get(local);
            Set<HostPort> localPorts = getHostPorts(ipPorts, serviceMap, localService);

            HostPort remote = new HostPort(file.getRemoteHost(), file.getRemotePort());
            Service remoteService = serviceMap.get(remote);

            if (filter(local, localService) || filter(remote, localService)) {
                continue;
            }

            Set<HostPort> remotePorts;
            if (null != remoteService) {
                remotePorts = getHostPorts(ipPorts, serviceMap, remoteService);
            } else {
                remoteService = Service.builder()
                        .id(nextVirtualServiceId())
                        .name("unknown")
                        .cmd("unknown")
                        .build();
                remotePorts = new HashSet<>();
                remotePorts.add(remote);

                serviceMap.put(remote, remoteService);
            }
            Link.LinkBuilder lb = Link.builder();
            if (localPorts.contains(local)) {
                // inbound connection, remote -> local
                lb.client(remoteService);
                lb.clientPorts(remotePorts);
                lb.server(localService);
                lb.serverPorts(localPorts);
            } else {
                // outbound connection, local -> remote
                lb.client(localService);
                lb.clientPorts(localPorts);
                lb.server(remoteService);
                lb.serverPorts(remotePorts);
            }
            Link link = lb.build();
            if (!links.contains(link)) {
                links.add(lb.build());
            }
        }
        dot(links, serviceMap);

        Map<String, Set<Long>> hostServices = new HashMap<>();
        serviceMap.forEach((hostPort, service) -> {
            if (!filter(hostPort, service)) {
                String sh = hostPort.getIp();
                hostServices.computeIfAbsent(sh, k -> new HashSet<>()).add(service.getId());
            }
        });

        List<Host> hosts = hostRepository.findAll();
        for (Host host : hosts) {
            if (host.getAccessible() == YES) {
                String hs = host.getHost();
                hostServices.computeIfAbsent(hs, k -> new HashSet<>());
            }
        }
        hostServices.entrySet().stream()
                .filter(entry -> {
                    String host = entry.getKey();
                    return host.startsWith("172.") || host.startsWith("10.") || host.startsWith("192.168");
                })
                .sorted(Comparator.comparingInt(o -> o.getValue().size()))
                .forEach(entry -> System.out.printf("%s\t%d%n", entry.getKey(), entry.getValue().size()));
    }

    private Set<HostPort> getHostPorts(Set<HostPort> servicePorts, Map<HostPort, Service> serviceMap, Service service) {
        Set<HostPort> hostPorts = allOfService(service, serviceMap);
        if (service.getId() > 0) {
            hostPorts.removeIf(port -> !servicePorts.contains(port));
        }
        return hostPorts;
    }

    @Resource
    private HostRepository hostRepository;

    public static class LineWriter {
        BufferedWriter writer;

        public LineWriter(BufferedWriter writer) {
            this.writer = writer;
        }

        public LineWriter writeLine(String line) throws IOException {
            writer.write(line);
            writer.newLine();
            return this;
        }
    }

    private void dot(Set<Link> links, Map<HostPort, Service> serviceMap) {
        Set<Long> added = new HashSet<>();
        Path path = Paths.get("/Users/george/Downloads/").resolve("topology.gv");
        try (BufferedWriter sb = Files.newBufferedWriter(path, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
            LineWriter writer = new LineWriter(sb);
            writer.writeLine("digraph structs {")
                    .writeLine("rankdir=LR;")
                    .writeLine("node [shape=record];");
            for (Link link : links) {
                Optional<String> clientNode = node(link.getClient(), link.getClientPorts(), added);
                if (clientNode.isPresent()) {
                    writer.writeLine(clientNode.get());
                }
                Optional<String> serverNode = node(link.getServer(), link.getServerPorts(), added);
                if (serverNode.isPresent()) {
                    writer.writeLine(serverNode.get());
                }
            }
            // dead service
            for (Map.Entry<HostPort, Service> entry : serviceMap.entrySet()) {
                var service = entry.getValue();
                var hostPort = entry.getKey();
                Optional<String> np = node(service, Set.of(hostPort), added);
                if (np.isPresent() && !filter(hostPort, service)) {
                    writer.writeLine(np.get());
                }
            }
            for (Link link : links) {
                edge(link, writer);
            }
            sb.append("}");

            // dot -Tsvg -o topology.svg topology.gv
            String[] cmds = new String[]{
                    "dot",
                    "-Tsvg",
                    "-o",
                    path.getParent().resolve("topology.svg").toString(),
                    path.toString()
            };
            Process p = Runtime.getRuntime().exec(cmds);
            String error = StreamUtils.copyToString(p.getErrorStream(), UTF_8);
            String output = StreamUtils.copyToString(p.getInputStream(), UTF_8);
            System.out.println(output);
        } catch (IOException e) {
            log.error("Cannot output dot {} ", path, e);
        }
    }

    private void edge(Link link, LineWriter writer) throws IOException {
        Service client = link.getClient();
        Service server = link.getServer();
        writer.writeLine(fullNodeId(client.getId()) + " -> " + fullNodeId(server.getId()) + ";");
    }

    private boolean details = false;

    private Optional<String> node(Service service, Set<HostPort> ports, Set<Long> added) {
        Long serviceId = service.getId();
        if (added.contains(serviceId)) {
            return Optional.empty();
        }
        added.add(serviceId);
        StringBuilder label = new StringBuilder();
        label.append(service.getName()).append("@").append(service.getCmd());
        if (details) {
            for (HostPort hp : ports) {
                label.append(" | ").append(hp.getIp()).append(":").append(hp.getPort());
            }
        }
        String node = String.format("%s [label=\"%s\"];", fullNodeId(serviceId), label);
        return Optional.of(node);
    }

    private String fullNodeId(Long serviceId) {
        return "service_" + nodeId(serviceId);
    }

    private String nodeId(Long serviceId) {
        if (serviceId < 0) {
            return "_" + -serviceId;
        }
        return String.valueOf(serviceId);
    }

    private boolean filter(HostPort port, Service service) {
        if (null != service) {
            String name = service.getName();
            if (name.startsWith("[")
                    || name.equals("sudo")
                    || name.endsWith("xinetd")
                    || name.contains("cocod")
                    || name.contains("rpc.rquotad")
                    || name.contains("rpc.mountd")
                    || name.contains("rpcbind")
                    || name.contains("rpc.statd")
                    || name.contains("gse_agent")
                    || name.contains("salt-")
                    || name.contains("cm-agent")
                    || name.contains("kubelet")
                    || name.contains("aliyun-service")
                    || name.contains("aliyun_assist_update")
                    || name.contains("dockerd")
                    || name.contains("AliYunDun")
                    || name.contains("aliyun-assist")
                    || name.contains("gunicorn")) {
                return true;
            }
        }
        Integer p = port.getPort();
        return filter(p);
    }

    private boolean filter(Integer p) {
        return p == 22
                || p == 873
                || p == 10050
                || p == 60020
                || p == 25
                || p == 111
                || p == 65533;
    }

    private void outputKnownServices(Map<HostPort, Service> serviceMap, Set<HostPort> knownServicePorts) {
        String[] headers = new String[]{"服务名称", "服务命令", "服务器IP", "服务端口"};
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get("/Users/george/Downloads/known_services.csv"), UTF_8, CREATE, TRUNCATE_EXISTING)) {
            writer.write(String.join(Constants.COMMA, headers));
            writer.newLine();
            for (Map.Entry<HostPort, Service> entry : serviceMap.entrySet()) {
                Service service = entry.getValue();
                HostPort hostPort = entry.getKey();
                if (knownServicePorts.contains(hostPort)) {
                    output(writer, service.getName(), service.getCmd(), hostPort.getIp(), hostPort.getPort());
                }
            }
        } catch (IOException e) {
            log.error("Cannot handle known services", e);
        }
    }

    private Map<HostPort, Service> getServices() {
        Map<HostPort, Service> serviceMap = new HashMap<>();
        // Known Service
        List<Service> list = serviceRepository.findAll();
        Comparator<Service> cmdCmp = Comparator.comparing(service -> nullToEmpty(service.getCmd()));
        Comparator<Service> nameCmp = Comparator.comparing(service -> nullToEmpty(service.getName()));
        list.stream()
                .sorted(nameCmp.thenComparing(cmdCmp))
                .forEach(service -> {
                    List<ServerPort> serverPortList = serverPortRepository.findByProcessIn(service.getProcesses());
                    if (!serverPortList.isEmpty()) {
                        serverPortList.forEach(serverPort -> {
                            Server server = serverPort.getServer();
                            List<Host> hostList = server.getHosts();
                            if (!hostList.isEmpty()) {
                                hostList.forEach(host -> serviceMap.put(new HostPort(host.getHost(), serverPort.getPort()), service));
                            }
                        });
                    }
                });
        return serviceMap;
    }

    private void output(BufferedWriter writer, Object... values) {
        Object val = null;
        try {
            for (Object value : values) {
                val = value;
                if (null == value) {
                    value = "";
                }
                writer.write(value.toString());
                writer.write(Constants.COMMA);
            }
            writer.newLine();
        } catch (IOException e) {
            log.error("Cannot write value {}", val);
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        analyze();
    }
}
