package org.reploop.topology.parser;

import org.reploop.topology.core.Accessible;
import org.reploop.topology.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface LsofDriver {
    Pattern IP_PATTERN = Pattern.compile("([0-9.]+) \\| ");

    private void parse(String directory, String filename) throws IOException {
        Path path = Paths.get(directory).resolve(filename);
        List<String> lines = Files.readAllLines(path);
        parse(lines);
    }

    private void createIfAbsent(String h) {
        createHostsIfAbsent(Collections.singleton(h), Accessible.UNKNOWN);
    }

    void parse(List<String> lines);

    void parse(List<String> lines, List<RawRecord> records, List<RawProcess> processes);

    void handleServices(List<Proc> processes);

    private List<Host> saveAllHosts(List<NetworkFile> records) {
        Set<String> hosts = records.stream()
                .flatMap(nf -> Stream.of(nf.getHost(), nf.getLocalHost()))
                .collect(Collectors.toSet());
        return createHostsIfAbsent(hosts, Accessible.YES);
    }

    List<Server> saveAllServers(List<NetworkFile> records);

    /**
     * save server port, depends server and process
     */
    List<ServerPort> saveAllServerPort(List<NetworkFile> records);

    void saveAllProcessCmd(List<Proc> processes);

    List<Host> createHostsIfAbsent(Set<String> hosts, Accessible accessible);

    List<Host> refreshAccessible(Accessible accessible, List<Host> knownHosts);

    List<Proc> saveAllProcesses(List<RawProcess> processes, Map<Integer, Integer> pidMap);

    List<Proc> saveAllProcesses(List<RawProcess> processes);

    Proc findMasterProcess(String host, Integer pid);

    List<NetworkFile> saveAllRecords(List<RawRecord> records);

}
