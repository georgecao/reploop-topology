package com.cclx.topology.parser;

import com.cclx.topology.core.HostPort;

public class Conn {
    HostPort local;
    HostPort remote;

    public Conn() {
    }

    public Conn(Conn conn) {
        this.local = new HostPort(conn.local);
        this.remote = new HostPort(conn.remote);
    }


    @Override
    public String toString() {
        return "Connection{" +
                "local=" + local +
                ", remote=" + remote +
                '}';
    }
}
