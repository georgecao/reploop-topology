package org.reploop.topology.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.reploop.topology.core.Node;
import org.reploop.topology.core.State;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawRecord {
    String host;
    String cmd;
    Integer pid;
    Integer ppid;
    String user;
    String fd;
    String type;
    String device;
    String sizeOrOff;
    Node node;
    Conn conn;
    State state;

    public RawRecord(RawRecord instance) {
        this.host = instance.host;
        this.cmd = instance.cmd;
        this.pid = instance.pid;
        this.ppid = instance.ppid;
        this.user = instance.user;
        this.fd = instance.fd;
        this.type = instance.type;
        this.device = instance.device;
        this.sizeOrOff = instance.sizeOrOff;
        this.node = instance.node;
        this.conn = new Conn(instance.conn);
        this.state = instance.state;
    }

    @Override
    public String toString() {
        return "Instance{" +
                "host='" + host + '\'' +
                ", cmd='" + cmd + '\'' +
                ", pid=" + pid +
                ", ppid=" + ppid +
                ", user='" + user + '\'' +
                ", fd='" + fd + '\'' +
                ", type='" + type + '\'' +
                ", device='" + device + '\'' +
                ", sizeOrOff='" + sizeOrOff + '\'' +
                ", node='" + node + '\'' +
                ", conn='" + conn + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}
