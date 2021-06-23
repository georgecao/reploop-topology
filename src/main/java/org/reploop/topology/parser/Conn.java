package org.reploop.topology.parser;

import org.reploop.topology.core.HostPort;

public class Conn {
    HostPort local;
    HostPort remote;

    public Conn() {
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
