package org.reploop.topology.parser;

import org.junit.jupiter.api.Test;
import org.reploop.topology.BaseTest;
import org.reploop.topology.Constants;
import org.reploop.topology.core.Accessible;
import org.reploop.topology.model.Host;
import org.reploop.topology.model.NetworkFile;
import org.reploop.topology.model.Proc;
import org.reploop.topology.model.Server;
import org.reploop.topology.repository.*;

import javax.annotation.Resource;
import java.util.*;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultLsofDriverTest extends BaseTest {
    @Resource
    private LsofDriver defaultLsofDriver;

    @Resource
    private HostRepository hostRepository;
    @Resource
    private ServerRepository serverRepository;
    @Resource
    private ProcRepository procRepository;
    @Resource
    private NetworkFileRepository networkFileRepository;
    @Resource
    private ServiceRepository serviceRepository;

    @Test
    void parse() {
        List<Host> hosts = hostRepository.findAll();
        List<Server> servers = serverRepository.findAll();
        assertThat(servers).hasSizeLessThan(hosts.size());
        Set<Long> hostIds = servers.stream()
                .flatMap(s -> s.getHosts().stream())
                .map(Host::getId)
                .collect(toSet());
        Set<Long> knownIds = hosts.stream()
                .filter(s0 -> s0.getAccessible() == Accessible.YES)
                .map(Host::getId)
                .collect(toSet());
        isSame(knownIds, hostIds);

        Set<Long> serverIds = servers.stream()
                .map(Server::getId)
                .collect(toSet());
        assertThat(servers).hasSameSizeAs(serverIds);

        Set<Long> knownServerIds = hosts.stream()
                .map(Host::getServer)
                .filter(Objects::nonNull)
                .map(Server::getId)
                .collect(toSet());
        isSame(serverIds, knownServerIds);
    }

    @Test
    void testParse() {
    }

    @Test
    void handleServices() {
    }

    @Test
    void saveAllHosts() {
    }

    @Test
    void saveAllServers() {
    }

    @Test
    void saveAllServerPort() {
    }

    @Test
    void saveAllProcessCmd() {
    }

    @Test
    void createHostsIfAbsent() {
    }

    @Test
    void refreshAccessible() {
    }

    @Test
    void saveAllProcesses() {
    }

    @Test
    void testSaveAllProcesses() {
    }

    @Test
    void findMasterProcess() {
        var processes = procRepository.findAll();
        var files = networkFileRepository.findAll();

        Set<String> hosts = processes.stream().map(Proc::getHost).collect(toSet());
        Set<String> knownHosts = files.stream().map(NetworkFile::getHost).collect(toSet());
        isSame(knownHosts, hosts);
        for (String host : hosts) {
            testPerHost(host);
        }
    }

    private void testPerHost(String host) {
        var processes = procRepository.findByHost(host);
        var files = networkFileRepository.findByHost(host);
        for (NetworkFile nf : files) {
            String lh = nf.getLocalHost();
            assertThat(lh)
                    .doesNotContain(Constants.LO_V6)
                    .doesNotContain(Constants.ZERO)
                    .doesNotContain(Constants.STAR)
                    .doesNotContain(Constants.LO);
        }
        Set<Integer> pids = files.stream().map(NetworkFile::getMid).collect(toSet());

        Map<Integer, Proc> pidMap = processes.stream().collect(toMap(Proc::getPid, proc -> proc));

        for (Integer pid : pids) {
            Proc proc = defaultLsofDriver.findMasterProcess(host, pid);
            assertThat(proc).isNotNull();
            assertThat(pidMap).containsKey(proc.getPid());
        }
    }

    private <T> void isSame(Set<T> s0, Set<T> s1) {
        Set<T> merge = new HashSet<>();
        merge.addAll(s0);
        merge.addAll(s1);
        assertThat(merge).hasSameSizeAs(s0).hasSameSizeAs(s1);
    }

    @Test
    void saveAllRecords() {
    }
}