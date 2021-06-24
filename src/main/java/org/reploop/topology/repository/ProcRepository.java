package org.reploop.topology.repository;

import org.reploop.topology.model.Proc;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ProcRepository extends JpaRepository<Proc, Long> {
    Proc findByHostAndPid(String host, Integer pid);

    List<Proc> findByHostAndPidIn(String host, Collection<Integer> pids);

    List<Proc> findByHost(String host);

    Proc findFirstByHostInOrderByPidDesc(Collection<String> hosts);
}