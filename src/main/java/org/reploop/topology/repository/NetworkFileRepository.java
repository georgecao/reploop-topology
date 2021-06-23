package org.reploop.topology.repository;

import org.reploop.topology.core.State;
import org.reploop.topology.model.NetworkFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NetworkFileRepository extends JpaRepository<NetworkFile, Long> {
    List<NetworkFile> findByHostAndPid(String host, Integer pid);

    List<NetworkFile> findByHost(String host);

    List<NetworkFile> findByHostAndPidAndPpid(String host, Integer pid, Integer ppid);

    List<NetworkFile> findByHostAndPidOrPpid(String host, Integer pid, Integer ppid);

    List<NetworkFile> findByHostAndPpid(String host, Integer ppid);

    List<NetworkFile> findByHostAndPidIn(String host, Collection<Integer> pids);

    List<NetworkFile> findByState(State state);

    List<NetworkFile> findByHostAndState(String host, State state);

    List<NetworkFile> findByLocalHostAndLocalPort(String localHost, Integer localPort);

    List<NetworkFile> findByLocalHost(String localHost);

    List<NetworkFile> findByLocalHostAndPidAndState(String localHost, Integer pid, State state);

    List<NetworkFile> findByLocalHostAndRemoteHostAndRemotePort(String localHost, String remoteHost, Integer localPort);

    List<NetworkFile> findByLocalHostAndLocalPortIn(String localHost, Collection<Integer> ports);
}
