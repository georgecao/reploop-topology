package org.reploop.topology.aliyun;

import lombok.Data;

@Data
public class Line implements Cloneable {
    String name;
    String cmd = "redis";
    Integer port;
    String privateIp;
    String publicIp;
    String version;

    @Override
    public Line clone() {
        try {
            Line clone = (Line) super.clone();
            copyTo(clone);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    private void copyTo(Line clone) {
        clone.setName(name);
        clone.setCmd(cmd);
        clone.setPort(port);
        clone.setPrivateIp(privateIp);
        clone.setPublicIp(publicIp);
        clone.setVersion(version);
    }
}
