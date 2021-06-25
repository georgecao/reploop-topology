package org.reploop.topology.aliyun;

import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.Constants;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.core.HostPort;
import org.reploop.topology.core.Node;
import org.reploop.topology.core.State;
import org.reploop.topology.model.Proc;
import org.reploop.topology.parser.Conn;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.reploop.topology.repository.ProcRepository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;

@Service
@Slf4j
public class RedisService implements CsvFile<Line> {
    @Resource
    private TopologyProperties properties;

    private static final int PPID = 1;
    private static final String USER = "nobody";

    void redis() throws IOException {
        var sc = properties.getService();
        String paths = sc.getKnownServices();
        List<RawRecord> records = new ArrayList<>();
        List<RawProcess> processes = new ArrayList<>();
        String[] files = paths.split(Constants.COMMA);
        for (String file : files) {
            loadCsvFile(file, records, processes);
        }
    }

    @Resource
    private ProcRepository procRepository;

    private int pid(String host) {
        Proc proc = null;
        if (null != procRepository) {
            proc = procRepository.findFirstByHostOrderByPidDesc(host);
        }
        int candidate = ThreadLocalRandom.current().nextInt(1000, 2000);
        return null == proc ? candidate : proc.getPid() + candidate;
    }

    public void loadCsvFile(String file, List<RawRecord> records, List<RawProcess> processes) throws IOException {
        List<Line> lines = loadCsvFile(file);
        for (Line line : lines) {
            String host = line.privateIp;
            int pid = pid(host);
            HostPort local = new HostPort(host, line.port);
            RawRecord.RawRecordBuilder nb = RawRecord.builder()
                    .host(host)
                    .pid(pid)
                    .ppid(PPID)
                    .state(State.LISTEN)
                    .conn(new Conn(local))
                    .user(USER)
                    .fd("fd")
                    .type("type")
                    .device("device")
                    .sizeOrOff("size/off")
                    .node(Node.TCP)
                    .cmd(line.getCmd());
            records.add(nb.build());
            if (null != line.publicIp && line.publicIp.isEmpty()) {
                HostPort local2 = new HostPort(line.publicIp, line.port);
                nb.conn(new Conn(local2));
                records.add(nb.build());
            }
            RawProcess process = RawProcess.builder()
                    .host(host)
                    .command(line.name)
                    .pid(pid)
                    .ppid(PPID)
                    .user(USER)
                    .build();
            processes.add(process);
        }
    }

    @Override
    public Line newInstance() {
        return new Line();
    }

    @Override
    public void collect(Integer i, String val, Map<Integer, BiConsumer<Line, String>> handlers) {
        switch (val) {
            case "实例名称" -> handlers.put(i, (l0, v0) -> l0.name = v0);
            case "端口" -> handlers.put(i, (l1, v1) -> l1.port = Integer.parseInt(v1));
            case "版本" -> handlers.put(i, (l2, v2) -> l2.version = v2);
            case "IP地址" -> handlers.put(i, (l3, v3) -> l3.publicIp = v3);
            case "私网IP" -> handlers.put(i, (l4, v4) -> l4.privateIp = v4);
            default -> log.warn("Ignore column {}", val);
        }
    }
}
