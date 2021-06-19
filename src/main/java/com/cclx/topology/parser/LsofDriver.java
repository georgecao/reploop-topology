package com.cclx.topology.parser;

import com.cclx.topology.core.Accessible;
import com.cclx.topology.core.HostPort;
import com.cclx.topology.model.Process;
import com.cclx.topology.model.*;
import com.cclx.topology.repository.*;
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
public class LsofDriver implements InitializingBean {
    @Resource
    private RecordParser recordParser;
    @Resource
    private ProcessParser processParser;
    @Resource
    private ProcessRepository processRepository;
    @Resource
    private NetworkFileRepository networkFileRepository;
    @Resource
    private ServiceRepository serviceRepository;
    @Resource
    private HostRepository hostRepository;
    @Resource
    private ServerPortRepository serverPortRepository;

    @Override
    public void afterPropertiesSet() throws Exception {
        //Path path = Paths.get("/Users/george/Downloads/172.17.1.2.lsof");
        //Path path = Paths.get("/Users/george/Downloads/lsof.all1");
        //Path path = Paths.get("/Users/george/Downloads/lsof.all2");
        //Path path = Paths.get("/Users/george/Downloads/merge.lsof");
        // parse("lsof.all2");
        parse("lsof.all13");
        //parse("172.16.5.2.lsof");
    }

    private void parse(String filename) throws IOException {
        Path path = Paths.get("/Users/george/Downloads").resolve(filename);
        List<String> lines = Files.readAllLines(path);
        parse(lines);
    }

    private static final Pattern IP_PATTERN = Pattern.compile("([0-9.]+) \\| ");

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
        CompletableFuture<List<Process>> cfp = CompletableFuture.supplyAsync(() -> saveAllProcesses(processes));

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

        // Finally wait to complete
        CompletableFuture.allOf(csp, cfv).join();
    }

    private void parse(List<String> lines, List<RawRecord> records, List<RawProcess> processes) {
        String host = null;
        Context context = null;
        for (String line : lines) {
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
        }
    }

    private String parseServiceName(String command) {
        if (command.length() > 255) {
            return command.substring(0, 255);
        }
        return command;
    }

    private void handleServices(List<Process> processes) {
        for (Process process : processes) {
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

    @Resource
    private ServerRepository serverRepository;

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
                Process process = findMasterProcess(record.getHost(), record.getPid());
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

    private static class HostPid {
        String host;
        Integer pid;

        public HostPid(String host, Integer pid) {
            this.host = host;
            this.pid = pid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HostPid hostPid = (HostPid) o;
            return Objects.equals(host, hostPid.host) && Objects.equals(pid, hostPid.pid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(host, pid);
        }
    }

    private Optional<NetworkFile> findAny(List<NetworkFile> files) {
        return files.stream()
                .filter(nf -> null != nf.getCmd())
                .sorted(Comparator.comparing(NetworkFile::getPid))
                .findAny();
    }

    private void saveAllProcessCmd(List<Process> processes) {
        // Group by host
        Map<String, List<Process>> processGroup = processes.stream()
                .collect(Collectors.groupingBy(Process::getHost, Collectors.toList()));
        processGroup.forEach((host, processesOnHost) -> {
            // All process ids, including process's parent ids
            Set<Integer> processIds = processesOnHost.stream()
                    .map(Process::getPid)
                    .collect(Collectors.toSet());
            List<NetworkFile> files = networkFileRepository.findByHostAndPidIn(host, processIds);
            Map<Integer, List<NetworkFile>> pidMap = files.stream().collect(Collectors.groupingBy(NetworkFile::getPid, Collectors.toList()));
            List<Process> updates = new ArrayList<>();
            for (Process process : processesOnHost) {
                Integer processId = process.getPid();
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

    private CompletableFuture<List<Process>> saveAllProcessesAsync(List<RawProcess> processes) {
        return CompletableFuture.supplyAsync(() -> saveAllProcesses(processes));
    }

    private List<Process> saveAllProcesses(List<RawProcess> processes) {
        List<Process> list = new ArrayList<>(processes.size());
        Map<String, Map<Integer, Integer>> master = processParser.reduce(processes);
        for (RawProcess process : processes) {
            Process.ProcessBuilder p = Process.builder()
                    .host(process.host)
                    .command(process.command)
                    .pid(process.pid)
                    .user(process.user)
                    .ppid(process.ppid);
            Map<Integer, Integer> pidMap = master.getOrDefault(process.host, Collections.emptyMap());
            p.mid(pidMap.get(process.pid));
            list.add(processRepository.save(p.build()));
        }
        return list;
    }

    private Process findMasterProcess(String host, Integer pid) {
        Process process = processRepository.findByHostAndPid(host, pid);
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
