package com.cclx.topology.parser;

import com.cclx.topology.config.TopologyProperties;
import com.cclx.topology.core.Accessible;
import com.cclx.topology.core.HostPort;
import com.cclx.topology.function.SystemServicePredicate;
import com.cclx.topology.model.*;
import com.cclx.topology.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.cclx.topology.Constants.*;
import static java.util.stream.Collectors.toMap;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@Slf4j
public class LsofDriver implements InitializingBean {
    private static final Pattern IP_PATTERN = Pattern.compile("([0-9.]+) \\| ");
    @Resource
    private RecordParser recordParser;
    @Resource
    private ProcessParser processParser;
    @Resource
    private ProcRepository processRepository;
    @Resource
    private NetworkFileRepository networkFileRepository;
    @Resource
    private ServiceRepository serviceRepository;
    @Resource
    private HostRepository hostRepository;
    @Resource
    private ServerPortRepository serverPortRepository;
    @Resource
    private ServerRepository serverRepository;
    @Resource
    private SystemServicePredicate ssp;
    @Resource
    private TopologyProperties properties;

    @Override
    public void afterPropertiesSet() throws Exception {
        TopologyProperties.Lsof lsof = properties.getLsof();
        parse(lsof.getDirectory(), lsof.getFilename());
    }

    private void parse(String directory, String filename) throws IOException {
        Path path = Paths.get(directory).resolve(filename);
        List<String> lines = Files.readAllLines(path);
        parse(lines);
    }

    private void createIfAbsent(String h) {
        createHostsIfAbsent(Collections.singleton(h), Accessible.UNKNOWN);
    }

    public void parse(List<String> lines) {
        // Parse to entity
        List<RawRecord> records = new ArrayList<>();
        List<RawProcess> processes = new ArrayList<>();
        parse(lines, records, processes);

        // First handle connection
        CompletableFuture<List<NetworkFile>> cfr = CompletableFuture.supplyAsync(() -> saveAllRecords(records));

        // Then handle process
        CompletableFuture<List<Proc>> cfp = CompletableFuture.supplyAsync(() -> saveAllProcesses(processes));

        // #3 Save all host ip
        CompletableFuture<List<Host>> cfi = cfr.thenApplyAsync(this::saveAllHosts);

        // #4 After save all host ip, group different ips into server.
        CompletableFuture<List<Server>> cfs = cfi.thenCombineAsync(cfr, (ips, files) -> saveAllServers(files));

        // #5 Create local (server,port) pairs
        CompletableFuture<List<ServerPort>> csp = cfs.thenCombineAsync(cfr, (servers, files) -> saveAllServerPort(files));

        // #6 Save process cmd
        CompletableFuture<Void> cfc = cfp.thenAcceptBoth(cfr, (ps, nf) -> saveAllProcessCmd(ps));

        // #7 handle process
        CompletableFuture<Void> cfv = cfc.thenAcceptBothAsync(cfp, (unused, ps) -> handleServices(ps));

        // Finally, wait to complete
        CompletableFuture.allOf(csp, cfv).join();
    }

    private void parse(List<String> lines, List<RawRecord> records, List<RawProcess> processes) {
        String host = null;
        Context context = null;
        for (String line : lines) {
            try {
                Matcher matcher = IP_PATTERN.matcher(line);
                if (matcher.find()) {
                    // clear
                    context = Context.INIT;
                    host = matcher.group(1);
                    createIfAbsent(host);
                    continue;
                }
                if (line.contains(COMMAND) && line.contains(PID)) {
                    context = Context.INSTANCE;
                    continue;
                }
                if (line.contains(UID) && line.contains(PID)) {
                    context = Context.PROCESS;
                    continue;
                }
                if (context == Context.INSTANCE) {
                    records.add(recordParser.parse(host, line));
                }
                if (context == Context.PROCESS) {
                    processes.add(processParser.parse(host, line));
                }
            } catch (UdpBroadcastException e) {
                log.warn("Host {} line {}", host, line, e);
            }
        }
    }

    private String parseServiceName(String command) {
        if (command.length() > 255) {
            return command.substring(0, 255);
        }
        return command;
    }

    private void handleServices(List<Proc> processes) {
        for (Proc process : processes) {
            String cmd = process.getCmd();
            String name = parseServiceName(process.getCommand());
            Service service = serviceRepository.findByNameAndCmd(name, cmd);
            if (null == service) {
                Service newService = Service.builder()
                        .cmd(cmd)
                        .name(name)
                        .build();
                service = serviceRepository.save(newService);
            }
            process.setService(service);
        }
        processRepository.saveAll(processes);
    }

    private List<Host> saveAllHosts(List<NetworkFile> records) {
        Set<String> hosts = records.stream()
                .map(NetworkFile::getLocalHost)
                .collect(Collectors.toSet());
        return createHostsIfAbsent(hosts, Accessible.YES);
    }

    private List<Server> saveAllServers(List<NetworkFile> records) {
        List<Server> servers = new ArrayList<>();
        // Record map by host
        Map<String, List<NetworkFile>> recordGroup = records.stream()
                .collect(Collectors.groupingBy(NetworkFile::getHost, Collectors.toList()));
        recordGroup.forEach((host, list) -> {
            Set<String> hosts = list.stream()
                    .map(NetworkFile::getLocalHost)
                    .collect(Collectors.toSet());

            // These IPs belong to the same server
            List<Host> knownHosts = hostRepository.findByHostIn(hosts);
            Server server = knownHosts.stream()
                    .map(Host::getServer)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> {
                        // Create new server.
                        Server newServer = new Server(hosts.stream().sorted().collect(Collectors.joining(HYPHEN)));
                        return serverRepository.save(newServer);
                    });
            // Update server if absent
            List<Host> updates = knownHosts.stream()
                    .filter(ip -> null == ip.getServer())
                    .peek(ip -> ip.setServer(server))
                    .collect(Collectors.toList());
            if (!updates.isEmpty()) {
                hostRepository.saveAll(updates);
            }
            // Save the server
            servers.add(server);
        });
        return servers;
    }

    /**
     * save server port, depends server and process
     */
    private List<ServerPort> saveAllServerPort(List<NetworkFile> records) {
        List<ServerPort> ports = new ArrayList<>();
        // Host map
        List<Host> hosts = hostRepository.findAll();
        Map<String, Host> hostMap = hosts.stream().collect(toMap(Host::getHost, h -> h));
        // ServerPort and process
        for (NetworkFile record : records) {
            Host host = hostMap.get(record.getLocalHost());
            Server server = host.getServer();
            ServerPort serverPort = serverPortRepository.findByServerAndPort(server, record.getLocalPort());
            if (null == serverPort) {
                Proc process = findMasterProcess(record.getHost(), record.getPid());
                ServerPort sp = ServerPort.builder()
                        .server(server)
                        .port(record.getLocalPort())
                        .process(process)
                        .build();
                ports.add(serverPortRepository.save(sp));
            }
        }
        return ports;
    }

    private void saveAllProcessCmd(List<Proc> processes) {
        // Group by host
        Map<String, List<Proc>> processGroup = processes.stream()
                .collect(Collectors.groupingBy(Proc::getHost, Collectors.toList()));
        processGroup.forEach((host, processesOnHost) -> {
            // All process ids, including process's parent ids
            Set<Integer> processIds = processesOnHost.stream()
                    .map(Proc::getPid)
                    .collect(Collectors.toSet());
            List<NetworkFile> files = networkFileRepository.findByHostAndPidIn(host, processIds);
            Map<Integer, List<NetworkFile>> pidMap = files.stream().collect(Collectors.groupingBy(NetworkFile::getPid, Collectors.toList()));
            List<Proc> updates = new ArrayList<>();
            for (Proc process : processesOnHost) {
                var list = pidMap.get(process.getPid());
                if (null == list) {
                    list = pidMap.get(process.getMid());
                }
                if (null == list) {
                    list = pidMap.get(process.getPpid());
                }
                if (null != list) {
                    list.stream()
                            .filter(f -> null != f.getCmd())
                            .min(Comparator.comparing(NetworkFile::getPid))
                            .ifPresent(file -> {
                                process.setCmd(file.getCmd());
                                updates.add(process);
                            });
                }
            }
            if (!updates.isEmpty()) {
                processRepository.saveAll(updates);
            }
        });
    }

    private List<Host> createHostsIfAbsent(Set<String> hosts, Accessible accessible) {
        List<Host> knownHosts = hostRepository.findByHostIn(hosts);
        // Some hosts not exists
        if (hosts.size() != knownHosts.size()) {
            List<Host> all = new ArrayList<>(knownHosts);
            Set<String> exists = knownHosts.stream().map(Host::getHost).collect(Collectors.toSet());
            for (String h : hosts) {
                if (!exists.contains(h)) {
                    all.add(hostRepository.save(new Host(h, accessible)));
                }
            }
            return all;
        }
        if (!knownHosts.isEmpty()) {
            List<Host> updates = new ArrayList<>();
            for (Host host : knownHosts) {
                Accessible actual;
                if (null == (actual = host.getAccessible()) || accessible != actual) {
                    host.setAccessible(accessible);
                    updates.add(host);
                }
            }
            if (!updates.isEmpty()) {
                hostRepository.saveAll(updates);
            }
        }
        return knownHosts;
    }

    private List<Proc> saveAllProcesses(List<RawProcess> processes, Map<Integer, Integer> pidMap) {
        List<Proc> list = new ArrayList<>(processes.size());
        Integer kid = processes.stream().filter(p -> ssp.test(p.command)).map(p -> p.pid).findAny().orElse(0);
        for (RawProcess process : processes) {
            Integer pid = process.pid;
            // Filter system process out safely
            Integer mid = pidMap.get(pid);
            if (pid.equals(kid) || kid.equals(mid)) {
                continue;
            }
            Proc.ProcBuilder p = Proc.builder()
                    .host(process.host)
                    .command(process.command)
                    .pid(pid)
                    .mid(mid)
                    .user(process.user)
                    .ppid(process.ppid);
            list.add(processRepository.save(p.build()));
        }
        return list;
    }

    private List<Proc> saveAllProcesses(List<RawProcess> processes) {
        List<Proc> list = new ArrayList<>(processes.size());
        Map<String, Map<Integer, Integer>> master = processParser.reduce(processes);
        Map<String, List<RawProcess>> groups = processes.stream().collect(Collectors.groupingBy(p0 -> p0.host, Collectors.toList()));
        groups.forEach((host, rawProcesses) -> list.addAll(saveAllProcesses(rawProcesses, master.getOrDefault(host, Collections.emptyMap()))));
        return list;
    }

    private Proc findMasterProcess(String host, Integer pid) {
        Proc process = processRepository.findByHostAndPid(host, pid);
        Objects.requireNonNull(process, String.format("%s-%d", host, pid));
        Integer mid = process.getMid();
        if (pid.equals(mid)) {
            return process;
        }
        return processRepository.findByHostAndPid(host, mid);
    }

    private List<NetworkFile> saveAllRecords(List<RawRecord> records) {
        recordParser.expand(records);
        List<NetworkFile> list = new ArrayList<>(records.size());
        for (RawRecord rr : records) {
            Conn conn = rr.conn;
            NetworkFile.NetworkFileBuilder b = NetworkFile.builder()
                    .cmd(rr.cmd)
                    .host(rr.host)
                    .pid(rr.pid)
                    .ppid(rr.ppid)
                    .localHost(conn.local.ip)
                    .localPort(conn.local.port)
                    .state(rr.state)
                    .user(rr.user);
            HostPort remote = conn.remote;
            if (null != remote) {
                b.remoteHost(remote.ip).remotePort(remote.port);
            }
            list.add(b.build());
        }
        return networkFileRepository.saveAll(list);
    }

    private enum Context {
        INIT, INSTANCE, PROCESS
    }
}
