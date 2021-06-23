package org.reploop.topology;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reploop.topology.core.HostPort;
import org.reploop.topology.model.Service;

import java.util.Objects;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Link {
    Service client;
    Set<HostPort> clientPorts;
    Service server;
    Set<HostPort> serverPorts;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Link link = (Link) o;
        return Objects.equals(client.getId(), link.client.getId()) && Objects.equals(server.getId(), link.server.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(client.getId(), server.getId());
    }

    @Override
    public String toString() {
        return "Link{}";
    }
}
