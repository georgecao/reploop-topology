package org.reploop.topology.service;

import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.Constants;
import org.reploop.topology.Link;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.core.HostPort;
import org.reploop.topology.core.State;
import org.reploop.topology.function.WellKnownPortPredicate;
import org.reploop.topology.function.WellKnownServicePredicate;
import org.reploop.topology.model.*;
import org.reploop.topology.repository.HostRepository;
import org.reploop.topology.repository.NetworkFileRepository;
import org.reploop.topology.repository.ServiceRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;
import static org.reploop.topology.Constants.*;
import static org.reploop.topology.core.Accessible.YES;

@Component
@Slf4j
@Order
public class TopologyService implements InitializingBean {
    @Autowired
    private NetworkFileRepository networkFileRepository;
    @Resource
    private ServiceRepository serviceRepository;
    private volatile AtomicLong seed;
    @Resource
    private WellKnownPortPredicate wpp;
    @Resource
    private HostRepository hostRepository;
    @Resource
    private WellKnownServicePredicate wsp;
    @Resource
    private TopologyProperties properties;

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

    private Long nextVirtualServiceId() {
        if (null == seed) {
            seed = new AtomicLong(System.currentTimeMillis());
        }
        long serviceId = seed.get();
        if (!properties.getService().isMergeUnknown()) {
            serviceId = seed.incrementAndGet();
        }
        return -(serviceId);
    }

    public void analyze() {
        // group by service
        List<NetworkFile> all = networkFileRepository.findAll(Sort.by(Sort.Direction.ASC, "host", "localHost", "localPort"));
        Set<HostPort> servicePorts = all.stream()
                .filter(r -> r.getState() == State.LISTEN)
                .map(r -> new HostPort(r.getLocalHost(), r.getLocalPort()))
                .collect(Collectors.toSet());
        // All service
        Map<HostPort, Service> serviceMap = getServices();
        // known service
        outputKnownServices(serviceMap, servicePorts);

        Set<Link> links = new HashSet<>();
        for (NetworkFile file : all) {
            if (file.getState() == State.LISTEN || wpp.test(file.getLocalPort()) || wpp.test(file.getRemotePort())) {
                continue;
            }
            HostPort local = new HostPort(file.getLocalHost(), file.getLocalPort());
            Service localService = serviceMap.get(local);
            Set<HostPort> localPorts = getHostPorts(servicePorts, serviceMap, localService);

            HostPort remote = new HostPort(file.getRemoteHost(), file.getRemotePort());
            Service remoteService = serviceMap.get(remote);

            if (filter(local, localService) || filter(remote, localService)) {
                continue;
            }

            Set<HostPort> remotePorts;
            if (null != remoteService) {
                remotePorts = getHostPorts(servicePorts, serviceMap, remoteService);
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
        Optional<Path> op = dot(links, serviceMap).map(this::convert);
        op.ifPresent(path -> log.info("Output svg {}", path));

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

    private Optional<Path> dot(Set<Link> links, Map<HostPort, Service> serviceMap) {
        Set<Long> added = new HashSet<>();
        TopologyProperties.Dot dot = properties.getDot();
        Path path = Paths.get(properties.getDirectory()).resolve(dot.getOutput());
        try (BufferedWriter bw = Files.newBufferedWriter(path, UTF_8, CREATE, WRITE, TRUNCATE_EXISTING)) {
            LineWriter writer = new LineWriter(bw);
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
            writer.writeLine("}");
            return Optional.of(path);
        } catch (IOException e) {
            log.error("Cannot output dot {} ", path, e);
        }
        return Optional.empty();
    }

    private String nameWithoutExt(Path name) {
        String filename = name.toString();
        int idx = filename.lastIndexOf(Constants.DOT);
        if (idx > 0) {
            return filename.substring(0, idx);
        }
        return filename;
    }

    private String nameAppendExt(String name, String ext) {
        return name + Constants.DOT + ext;
    }

    private Path convert(Path path) {
        // dot -Tsvg -o topology.svg topology.gv
        TopologyProperties.Dot dot = properties.getDot();
        String filename = nameAppendExt(nameWithoutExt(path.getFileName()), dot.getType());
        Path outPath = path.getParent().resolve(filename);
        String[] cmds = new String[]{
                dot.getPath(),
                "-T" + dot.getType(),
                "-o",
                outPath.toString(),
                path.toString()
        };
        try {
            Process p = Runtime.getRuntime().exec(cmds);
            String error = StreamUtils.copyToString(p.getErrorStream(), UTF_8);
            String output = StreamUtils.copyToString(p.getInputStream(), UTF_8);
            log.info("Process file {}, output {}, error {}", path, output, error);
            return outPath;
        } catch (IOException e) {
            log.warn("Cannot dot {}", path, e);
        }
        return null;
    }

    private void edge(Link link, LineWriter writer) throws IOException {
        Service client = link.getClient();
        Service server = link.getServer();
        writer.writeLine(fullNodeId(client.getId()) + " -> " + fullNodeId(server.getId()) + ";");
    }

    private Optional<String> node(Service service, Set<HostPort> ports, Set<Long> added) {
        Long serviceId = service.getId();
        if (added.contains(serviceId)) {
            return Optional.empty();
        }
        added.add(serviceId);
        StringBuilder label = new StringBuilder();
        label.append(service.getName()).append(AT).append(service.getCmd());
        var dot = properties.getDot();
        if (dot.getDetails()) {
            Integer limit = dot.getLimit();
            int c = 0;
            List<HostPort> sorted = ports.stream()
                    .sorted(Comparator.comparing(HostPort::getIp).thenComparing(HostPort::getPort))
                    .collect(Collectors.toList());
            for (HostPort hp : sorted) {
                if (c++ >= limit) {
                    label.append(VER_SEP).append("...").append(COLON).append("...");
                    break;
                }
                label.append(VER_SEP).append(hp.getIp()).append(COLON).append(hp.getPort());
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
            return wsp.test(name, port.port);
        }
        return false;
    }

    private void outputKnownServices(Map<HostPort, Service> serviceMap, Set<HostPort> knownServicePorts) {
        String[] headers = new String[]{"服务名称", "服务命令", "服务器IP", "服务端口"};
        var sc = properties.getService();
        Path path = Paths.get(properties.getDirectory()).resolve(sc.getOutput());
        try (BufferedWriter writer = Files.newBufferedWriter(path, UTF_8, CREATE, TRUNCATE_EXISTING)) {
            writer.write(String.join(Constants.COMMA, headers));
            writer.newLine();
            serviceMap.forEach((hostPort, service) -> {
                if (knownServicePorts.contains(hostPort)) {
                    output(writer, service.getName(), service.getCmd(), hostPort.getIp(), hostPort.getPort());
                }
            });
        } catch (IOException e) {
            log.error("Cannot handle known services {} ", path, e);
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
                    List<Proc> processes = service.getProcesses();
                    if (null != processes) {
                        processes.forEach(proc -> {
                            List<ServerPort> serverPorts = proc.getServerPorts();
                            if (null != serverPorts) {
                                serverPorts.forEach(serverPort -> {
                                    Server server = serverPort.getServer();
                                    List<Host> hosts = server.getHosts();
                                    if (null != hosts) {
                                        hosts.forEach(host -> serviceMap.put(new HostPort(host.getHost(), serverPort.getPort()), service));
                                    }
                                });
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
}
