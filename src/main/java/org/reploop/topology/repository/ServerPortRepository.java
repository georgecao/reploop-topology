package org.reploop.topology.repository;

import org.reploop.topology.model.Server;
import org.reploop.topology.model.ServerPort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerPortRepository extends JpaRepository<ServerPort, Long> {
    ServerPort findByServerAndPort(Server server, Integer port);
}
