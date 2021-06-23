package org.reploop.topology.parser;

import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.core.Accessible;
import org.reploop.topology.model.*;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component("lsofDriverDelegate")
@Order(Ordered.LOWEST_PRECEDENCE - 100)
@Slf4j
public class LsofDriverDelegate implements InitializingBean, LsofDriver {
    @Resource
    private LsofDriver defaultLsofDriver;
    @Resource
    private TopologyProperties properties;

    @Override
    public void parse(List<String> lines) {
        defaultLsofDriver.parse(lines);
    }

    @Override
    public void parse(List<String> lines, List<RawRecord> records, List<RawProcess> processes) {
        defaultLsofDriver.parse(lines, records, processes);
    }

    @Override
    public void handleServices(List<Proc> processes) {
        defaultLsofDriver.handleServices(processes);
    }

    @Override
    public List<Server> saveAllServers(List<NetworkFile> records) {
        return defaultLsofDriver.saveAllServers(records);
    }

    @Override
    public List<ServerPort> saveAllServerPort(List<NetworkFile> records) {
        return defaultLsofDriver.saveAllServerPort(records);
    }

    @Override
    public void saveAllProcessCmd(List<Proc> processes) {
        defaultLsofDriver.saveAllProcessCmd(processes);
    }

    @Override
    public List<Host> createHostsIfAbsent(Set<String> hosts, Accessible accessible) {
        return defaultLsofDriver.createHostsIfAbsent(hosts, accessible);
    }

    @Override
    public List<Host> refreshAccessible(Accessible accessible, List<Host> knownHosts) {
        return defaultLsofDriver.refreshAccessible(accessible, knownHosts);
    }

    @Override
    public List<Proc> saveAllProcesses(List<RawProcess> processes, Map<Integer, Integer> pidMap) {
        return defaultLsofDriver.saveAllProcesses(processes, pidMap);
    }

    @Override
    public List<Proc> saveAllProcesses(List<RawProcess> processes) {
        return defaultLsofDriver.saveAllProcesses(processes);
    }

    @Override
    public Proc findMasterProcess(String host, Integer pid) {
        return defaultLsofDriver.findMasterProcess(host, pid);
    }

    @Override
    public List<NetworkFile> saveAllRecords(List<RawRecord> records) {
        return defaultLsofDriver.saveAllRecords(records);
    }

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
}
