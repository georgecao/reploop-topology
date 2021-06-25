package org.reploop.topology.aliyun;

import lombok.Builder;
import lombok.Data;

@Data
public class Line {
    String name;
    @Builder.Default
    String cmd = "redis";
    Integer port;
    String privateIp;
    String publicIp;
    String version;
}
