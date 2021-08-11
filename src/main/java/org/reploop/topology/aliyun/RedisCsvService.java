package org.reploop.topology.aliyun;

import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Service
@Slf4j
public class RedisCsvService implements CsvFile<Line>, Collector, Loader {
    @Resource
    private TopologyProperties properties;
    @Resource
    private LineAdapter lineAdapter;

    public void loadCsvFile(List<RawRecord> records, List<RawProcess> processes) {
        var sc = properties.getService();
        List<String> paths = sc.getKnownServices();
        for (String file : paths) {
            try {
                Path path = Paths.get(properties.getDirectory()).resolve(file).normalize();
                loadCsvFile(path.toString(), records, processes);
            } catch (IOException e) {
                log.warn("File {}", file, e);
            }
        }
    }

    public void loadCsvFile(String file, List<RawRecord> records, List<RawProcess> processes) throws IOException {
        List<Line> lines = loadCsvFile(file);
        loadCsvFile(lines, records, processes);
    }

    public void loadCsvFile(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        lineAdapter.convert(lines, records, processes);
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

    @Override
    public void load(List<RawRecord> records, List<RawProcess> processes) {
        loadCsvFile(records, processes);
    }

    @Override
    public void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        loadCsvFile(lines, records, processes);
    }
}
