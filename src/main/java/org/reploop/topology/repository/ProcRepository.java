package org.reploop.topology.repository;

import org.reploop.topology.model.Proc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProcRepository extends JpaRepository<Proc, Long> {
    Proc findByHostAndPid(String host, Integer pid);

    Proc findByHostAndPidAndPpid(String host, Integer pid, Integer ppid);

    @Query(value = "select * from proc where pid in (SELECT mid from proc where host = ? and pid = ? and ppid = ?)", nativeQuery = true)
    List<Proc> findMasterProcess(String host, Integer pid, Integer ppid);
}