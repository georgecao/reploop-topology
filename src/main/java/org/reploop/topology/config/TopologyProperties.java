package org.reploop.topology.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "reploop.topology")
public class TopologyProperties {
    private Dot dot = new Dot();
    private Lsof lsof = new Lsof();
    private Service service = new Service();

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Dot getDot() {
        return dot;
    }

    public void setDot(Dot dot) {
        this.dot = dot;
    }

    public Lsof getLsof() {
        return lsof;
    }

    public void setLsof(Lsof lsof) {
        this.lsof = lsof;
    }

    @Override
    public String toString() {
        return "TopologyConf{" +
                "dot=" + dot +
                ", lsof=" + lsof +
                ", service=" + service +
                '}';
    }

    public static class Dot {
        private String path;
        private String output;
        private String type = "svg";
        private Boolean details = false;
        private Integer limit = 100;
        private Boolean mergeUnknown = true;

        public Integer getLimit() {
            return limit;
        }

        public void setLimit(Integer limit) {
            this.limit = limit;
        }

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Boolean getDetails() {
            return details;
        }

        public void setDetails(Boolean details) {
            this.details = details;
        }

        public Boolean getMergeUnknown() {
            return mergeUnknown;
        }

        public void setMergeUnknown(Boolean mergeUnknown) {
            this.mergeUnknown = mergeUnknown;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        @Override
        public String toString() {
            return "Dot{" +
                    "path='" + path + '\'' +
                    ", output='" + output + '\'' +
                    ", limit='" + limit + '\'' +
                    ", type='" + type + '\'' +
                    ", details=" + details +
                    ", mergeUnknown=" + mergeUnknown +
                    '}';
        }
    }

    public static class Lsof {
        private String filename;
        private String directory;

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public String getDirectory() {
            return directory;
        }

        public void setDirectory(String directory) {
            this.directory = directory;
        }

        @Override
        public String toString() {
            return "Lsof{" +
                    "filename='" + filename + '\'' +
                    ", directory='" + directory + '\'' +
                    '}';
        }
    }

    public static class Service {
        boolean mergeUnknown = true;
        String knownServices;
        String output = "known_services.csv";

        public String getKnownServices() {
            return knownServices;
        }

        public void setKnownServices(String knownServices) {
            this.knownServices = knownServices;
        }

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public boolean isMergeUnknown() {
            return mergeUnknown;
        }

        public void setMergeUnknown(boolean mergeUnknown) {
            this.mergeUnknown = mergeUnknown;
        }

        @Override
        public String toString() {
            return "Service{" +
                    "output=" + output +
                    "knownServices=" + knownServices +
                    "mergeUnknown=" + mergeUnknown +
                    '}';
        }
    }
}
