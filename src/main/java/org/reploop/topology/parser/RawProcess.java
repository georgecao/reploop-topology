package org.reploop.topology.parser;

import org.reploop.topology.model.Processable;

import java.util.Objects;

public class RawProcess implements Processable {
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

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public Integer getPid() {
        return pid;
    }

    @Override
    public Integer getPpid() {
        return ppid;
    }
}
