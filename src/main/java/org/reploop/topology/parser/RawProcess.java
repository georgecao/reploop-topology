package org.reploop.topology.parser;

import java.util.Objects;

public class RawProcess {
    public String host;
    public String user;
    public Integer pid;
    public Integer ppid;
    public String command;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RawProcess process = (RawProcess) o;
        return Objects.equals(host, process.host) && Objects.equals(pid, process.pid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, pid);
    }

    @Override
    public String toString() {
        return "Process{" +
                "host='" + host + '\'' +
                ", user='" + user + '\'' +
                ", pid=" + pid +
                ", ppid=" + ppid +
                ", command='" + command + '\'' +
                '}';
    }
}
