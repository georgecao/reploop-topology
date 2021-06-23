package org.reploop.topology.core;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
public class HostPort {
    public String ip;
    public Integer port;

    public HostPort(HostPort hp) {
        this(hp.ip, hp.port);
    }

    public HostPort(String ip, Integer port) {
        this.ip = ip;
        this.port = port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HostPort hostPort = (HostPort) o;
        return Objects.equals(ip, hostPort.ip) && Objects.equals(port, hostPort.port);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
}
