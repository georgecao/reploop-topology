package org.reploop.topology.repository;

import org.reploop.topology.model.Host;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface HostRepository extends JpaRepository<Host, Long> {
    List<Host> findByHostIn(Collection<String> hosts);

    Host findByHost(String host);
}
