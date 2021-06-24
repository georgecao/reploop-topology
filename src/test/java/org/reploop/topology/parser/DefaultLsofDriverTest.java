package org.reploop.topology.parser;

import org.junit.jupiter.api.Test;
import org.reploop.topology.BaseTest;
import org.reploop.topology.Constants;
import org.reploop.topology.core.Accessible;
import org.reploop.topology.model.*;
import org.reploop.topology.repository.*;

import javax.annotation.Resource;
import java.util.*;

import static java.util.stream.Collectors.*;
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
        // service and temporary port
        List<ServerPort> serverPorts = serverPortRepository.findAll();
        // Accessible servers
        List<Server> servers = serverRepository.findAll();
        // all hosts
        List<Host> hosts = hostRepository.findAll();
        // processes on all accessible servers
        List<Proc> processes = procRepository.findAll();
        // services grouped by processes
        List<Service> services = serviceRepository.findAll();

        // #1 process's servers equals all servers
        Set<Long> serverIds = processes.stream()
                .map(Proc::getServerPorts)
                .flatMap(Collection::stream)
                .map(ServerPort::getServer)
                .map(Server::getId)
                .collect(toSet());
        Set<Long> allServerIds = servers.stream().map(Server::getId).collect(toSet());
        isSame(allServerIds, serverIds);

        // Process has server
        List<Proc> yes = processes.stream().filter(proc -> !proc.getServerPorts().isEmpty()).collect(toList());
        assertThat(yes).isNotEmpty();
        for (Proc proc : yes) {
            List<NetworkFile> files = networkFileRepository.findByHostAndMid(proc.getHost(), proc.getMid());
            assertThat(files).isNotEmpty();
        }

        List<NetworkFile> files = networkFileRepository.findAll();
        for (NetworkFile nf : files) {
            Proc proc = procRepository.findByHostAndPid(nf.getHost(), nf.getMid());
            assertThat(proc).isNotNull();
        }
    }

    @Resource
    private ProcessableTree processableTree;
    @Resource
    private ServerPortRepository serverPortRepository;

    @Test
    void saveAllProcessCmd() {
        List<DefaultProcessableTree.Simple> entities = new ArrayList<>();
        String host = "host";
        String host1 = "host1";
        String host2 = "host2";
        entities.add(new DefaultProcessableTree.Simple(host, 1, 0));
        entities.add(new DefaultProcessableTree.Simple(host, 29, 1));
        entities.add(new DefaultProcessableTree.Simple(host, 299, 29));
        entities.add(new DefaultProcessableTree.Simple(host, 2999, 29));
        entities.add(new DefaultProcessableTree.Simple(host, 2998, 29));
        entities.add(new DefaultProcessableTree.Simple(host, 2997, 29));
        entities.add(new DefaultProcessableTree.Simple(host, 2987, 2997));
        entities.add(new DefaultProcessableTree.Simple(host, 2986, 2997));
        entities.add(new DefaultProcessableTree.Simple(host, 2985, 2997));
        entities.add(new DefaultProcessableTree.Simple(host, 29, 1));
        entities.add(new DefaultProcessableTree.Simple(host2, 23, 1));
        entities.add(new DefaultProcessableTree.Simple(host1, 233, 23));

        List<DefaultProcessableTree.Simple> entities2 = new ArrayList<>();
        entities2.add(new DefaultProcessableTree.Simple(host, 2987, 2997));
        entities2.add(new DefaultProcessableTree.Simple(host, 2986, 2997));
        var pidMap = processableTree.reduce(entities, entities2);
        var ppidMap = pidMap.get(host);
        assertThat(ppidMap).hasSize(9);
        assertThat(ppidMap.get(2987)).isEqualTo(29);
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
        Map<Integer, Proc> pidMap = processes.stream().collect(toMap(Proc::getPid, proc -> proc));

        for (NetworkFile nf : files) {
            Proc proc = procRepository.findByHostAndPid(nf.getHost(), nf.getMid());
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