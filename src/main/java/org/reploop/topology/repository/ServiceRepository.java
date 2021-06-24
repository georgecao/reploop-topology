package org.reploop.topology.repository;

import org.reploop.topology.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRepository extends JpaRepository<Service, Long> {
    Service findByNameAndCmd(String name, String cmd);
}
