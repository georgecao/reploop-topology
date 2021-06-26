package org.reploop.topology.aliyun;

import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;

import java.util.List;

public interface Collector {

    void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes);
}
