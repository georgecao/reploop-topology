package com.cclx.topology.repository;

import com.cclx.topology.model.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProcessRepository extends JpaRepository<Process, Long> {
    Process findByHostAndPid(String host, Integer pid);

    Process findByHostAndPidAndPpid(String host, Integer pid, Integer ppid);

    @Query(value = "select *from process where pid in (SELECT mid from process where host = ? and pid = ? and ppid = ?)", nativeQuery = true)
    List<Process> findMasterProcess(String host, Integer pid, Integer ppid);
}