package org.reploop.topology.parser;

import org.reploop.topology.core.Accessible;
import org.reploop.topology.model.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public interface LsofDriver {
    Pattern IP_PATTERN = Pattern.compile("([0-9.]+) \\| ");

    void parse(List<String> lines);

    List<Host> saveAllHosts(List<NetworkFile> records);

    void parse(List<String> lines, List<RawRecord> records, List<RawProcess> processes);

    void handleServices(List<Proc> processes);

    List<Proc> saveAllProcesses(List<RawProcess> processes, List<NetworkFile> files);

    List<Server> saveAllServers(List<NetworkFile> records);

    /**
     * save server port, depends server and process
     */
    List<ServerPort> saveAllServerPort(List<NetworkFile> records);

    void saveAllProcessCmd(List<Proc> processes);

    List<Host> createHostsIfAbsent(Set<String> hosts, Accessible accessible);

    List<Host> refreshAccessible(Accessible accessible, List<Host> knownHosts);

    List<Proc> saveAllProcesses(List<RawProcess> processes, Map<Integer, Integer> pidMap);

    List<NetworkFile> saveAllRecords(List<RawRecord> records);

}
