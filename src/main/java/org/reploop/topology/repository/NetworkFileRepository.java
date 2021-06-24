package org.reploop.topology.repository;

import org.reploop.topology.model.NetworkFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface NetworkFileRepository extends JpaRepository<NetworkFile, Long> {
    List<NetworkFile> findByHost(String host);

    List<NetworkFile> findByHostAndMid(String host, Integer mid);

    List<NetworkFile> findByHostAndPidIn(String host, Collection<Integer> pids);
}
