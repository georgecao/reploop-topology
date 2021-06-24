package org.reploop.topology.parser;

import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.core.Accessible;
import org.reploop.topology.core.HostPort;
import org.reploop.topology.function.SystemServicePredicate;
import org.reploop.topology.model.*;
import org.reploop.topology.repository.*;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.*;
import static org.reploop.topology.Constants.*;

@Component("defaultLsofDriver")
@Slf4j
public class DefaultLsofDriver implements LsofDriver {
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
    private ProcessableTree processableTree;

    private Host createIfAbsent(String h) {
        List<Host> hosts = createHostsIfAbsent(Collections.singleton(h), Accessible.UNKNOWN);
        return hosts.iterator().next();
    }

    public void parse(List<RawRecord> records, List<RawProcess> rawProcesses) {
        List<NetworkFile> files = saveAllRecords(records);
        List<Proc> processes = saveAllProcesses(rawProcesses, files);
        List<Host> hosts = saveAllHosts(files);
        List<Server> servers = saveAllServers(files);
        List<ServerPort> serverPorts = saveAllServerPort(files);
        saveAllProcessCmd(processes);
        handleServices(processes);
    }

    private void wellKnowService(String name, String cmd, List<HostPort> servicePorts) {
        // Create service
        Service service = serviceRepository.findByNameAndCmd(name, cmd);
        if (null == service) {
            Service newService = Service.builder()
                    .cmd(cmd)
                    .name(name)
                    .build();
            service = serviceRepository.save(newService);
        }
        for (HostPort hostPort : servicePorts) {
            // Create server and hosts
            String addr = hostPort.getIp();
            Set<String> ips = Set.of(addr.split(COMMA));
            List<Host> hosts = createHostsIfAbsent(ips, Accessible.NO);
            Server server = hosts.stream()
                    .map(Host::getServer)
                    .filter(Objects::nonNull)
                    .findAny()
                    .orElseGet(() -> serverRepository.save(Server.builder().name(addr).build()));
            hosts.forEach(h -> h.setServer(server));
            hostRepository.saveAll(hosts);

            // Server port
            ServerPort serverPort = serverPortRepository.findByServerAndPort(server, hostPort.getPort());
            if (null == serverPort) {
                // Any pid will do
                int pid = pid(hosts);
                Proc newProc = Proc.builder()
                        .service(service)
                        .host(ips.iterator().next())
                        .command(name)
                        .cmd(cmd)
                        .pid(pid)
                        .ppid(1)
                        .mid(pid)
                        .build();
                Proc proc = processRepository.save(newProc);
                ServerPort nsp = ServerPort.builder()
                        .port(hostPort.getPort())
                        .server(server)
                        .process(proc)
                        .build();
                serverPort = serverPortRepository.save(nsp);
            }
        }
    }

    private Integer pid(List<Host> hosts) {
        Proc proc = processRepository.findFirstByHostInOrderByPidDesc(hosts.stream().map(Host::getHost).collect(toSet()));
        return null == proc ? 999 : proc.getPid() + 1;
    }

    public void parseAsync(List<RawRecord> records, List<RawProcess> processes) {
        // First handle connection
        CompletableFuture<List<NetworkFile>> cfr = CompletableFuture.supplyAsync(() -> saveAllRecords(records));

        // Then handle process
        CompletableFuture<List<Proc>> cfp = cfr.thenApplyAsync(files -> saveAllProcesses(processes, files));

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

    @Override
    public void parse(List<String> lines) {
        // Parse to entity
        List<RawRecord> records = new ArrayList<>();
        List<RawProcess> processes = new ArrayList<>();
        parse(lines, records, processes);
        parse(records, processes);
    }

    @Override
    public void parse(List<String> lines, List<RawRecord> records, List<RawProcess> processes) {
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

    @Override
    public void handleServices(List<Proc> processes) {
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

    @Override
    public List<Host> saveAllHosts(List<NetworkFile> records) {
        Set<String> hosts = records.stream()
                .flatMap(nf -> Stream.of(nf.getHost(), nf.getLocalHost()))
                .collect(Collectors.toSet());
        return createHostsIfAbsent(hosts, Accessible.YES);
    }

    /**
     * Accessible servers
     */
    @Override
    public List<Server> saveAllServers(List<NetworkFile> records) {
        List<Server> servers = new ArrayList<>();
        // Record map by host
        Map<String, List<NetworkFile>> recordGroup = records.stream()
                .collect(Collectors.groupingBy(NetworkFile::getHost, Collectors.toList()));
        recordGroup.forEach((host, list) -> {
            Set<String> hosts = list.stream()
                    .flatMap(nf -> Stream.of(nf.getHost(), nf.getLocalHost()))
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
    @Override
    public List<ServerPort> saveAllServerPort(List<NetworkFile> files) {
        List<ServerPort> ports = new ArrayList<>();
        // Host map, include all hosts
        List<Host> hosts = hostRepository.findAll();
        Map<String, Host> hostMap = hosts.stream().collect(toMap(Host::getHost, h -> h));
        // ServerPort and process
        Map<String, List<NetworkFile>> groups = files.stream().collect(groupingBy(NetworkFile::getHost, toList()));
        groups.forEach((hostIp, filesPerHost) -> {
            Set<Integer> pids = filesPerHost.stream().map(NetworkFile::getMid).collect(toSet());
            List<Proc> masterProcesses = processRepository.findByHostAndPidIn(hostIp, pids);
            Map<Integer, Proc> pidProcessMap = masterProcesses.stream().collect(toMap(Proc::getPid, proc -> proc));
            for (NetworkFile record : filesPerHost) {
                Host host = hostMap.get(record.getLocalHost());
                Server server = host.getServer();
                // Include service port and temporary port
                ServerPort serverPort = serverPortRepository.findByServerAndPort(server, record.getLocalPort());
                if (null == serverPort) {
                    Proc process = pidProcessMap.get(record.getMid());
                    ServerPort sp = ServerPort.builder()
                            .server(server)
                            .port(record.getLocalPort())
                            .process(process)
                            .build();
                    ports.add(serverPortRepository.save(sp));
                }
            }
        });
        return ports;
    }

    @Override
    public void saveAllProcessCmd(List<Proc> processes) {
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

    @Override
    public List<Host> createHostsIfAbsent(Set<String> hosts, Accessible accessible) {
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
            return refreshAccessible(accessible, all);
        }
        return refreshAccessible(accessible, knownHosts);
    }

    @Override
    public List<Host> refreshAccessible(Accessible accessible, List<Host> knownHosts) {
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

    public void updateAllMasterPerHost(List<NetworkFile> files, Map<Integer, Integer> pidMap) {
        files.forEach(nf -> {
            Integer pid = nf.getPid();
            nf.setMid(pidMap.get(pid));
        });
        networkFileRepository.saveAll(files);
    }

    @Override
    public List<Proc> saveAllProcesses(List<RawProcess> processes, Map<Integer, Integer> pidMap) {
        List<Proc> list = new ArrayList<>(processes.size());
        Integer kid = processes.stream()
                .filter(p -> ssp.test(p.command))
                .map(p -> p.pid)
                .findAny()
                .orElse(0);
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
            list.add(p.build());
        }
        return processRepository.saveAll(list);
    }

    /**
     * Some sub process may miss from `ps -ef` 's resust.
     */
    private Map<String, Map<Integer, Integer>> findMasterProcess(List<RawProcess> processes, List<NetworkFile> files) {
        return processableTree.reduce(processes, files);
    }

    @Override
    public List<Proc> saveAllProcesses(List<RawProcess> processes, List<NetworkFile> files) {
        List<Proc> list = new ArrayList<>(processes.size());

        Map<String, Map<Integer, Integer>> master = findMasterProcess(processes, files);
        // Save processes
        Map<String, List<RawProcess>> groups = processes.stream().collect(groupingBy(RawProcess::getHost, toList()));
        groups.forEach((host, processesPerHost) -> list.addAll(saveAllProcesses(processesPerHost, master.getOrDefault(host, emptyMap()))));

        // Update NetworkFile's master process id
        Map<String, List<NetworkFile>> fileGroups = files.stream().collect(groupingBy(NetworkFile::getHost, toList()));
        fileGroups.forEach((host, filesPerHost) -> updateAllMasterPerHost(filesPerHost, master.getOrDefault(host, emptyMap())));
        return list;
    }

    @Override
    public List<NetworkFile> saveAllRecords(List<RawRecord> records) {
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
