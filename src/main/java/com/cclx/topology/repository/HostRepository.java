package com.cclx.topology.repository;

import com.cclx.topology.model.Host;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HostRepository extends JpaRepository<Host, Long> {
    Host findByHost(String host);

    List<Host> findByHostIn(Collection<String> hosts);
}
