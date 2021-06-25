package org.reploop.topology.parser;

import lombok.Data;
import org.reploop.topology.core.HostPort;

@Data
public class Conn {
    HostPort local;
    HostPort remote;

    public Conn() {
    }

    public Conn(HostPort local) {
        this.local = local;
    }

    public Conn(HostPort local, HostPort remote) {
        this.local = local;
        this.remote = remote;
    }

    public Conn(Conn conn) {
        this.local = new HostPort(conn.local);
        if (null != conn.remote) {
            this.remote = new HostPort(conn.remote);
        }
    }


    @Override
    public String toString() {
        return "Connection{" +
                "local=" + local +
                ", remote=" + remote +
                '}';
    }
}
