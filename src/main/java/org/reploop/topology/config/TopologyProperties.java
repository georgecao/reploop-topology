package org.reploop.topology.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "reploop.topology")
public class TopologyProperties {
    private String directory;
    private Dot dot = new Dot();
    private Lsof lsof = new Lsof();
    private Service service = new Service();
    private Aliyun aliyun = new Aliyun();

    @Data
    public static class Aliyun {
        String accessKey;
        String accessSecret;
    }

    @Data
    public static class Dot {
        private String path;
        private String output;
        private String type = "svg";
        private Boolean details = false;
        private Integer limit = 100;
        private Boolean mergeUnknown = true;
    }

    @Data
    public static class Lsof {
        private String filename;
    }

    @Data
    public static class Service {
        boolean mergeUnknown = true;
        List<String> knownServices;
        String output = "known_services.csv";
    }
}
