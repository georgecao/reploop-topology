package org.reploop.topology.aliyun;

import org.reploop.topology.core.HostPort;
import org.reploop.topology.core.Node;
import org.reploop.topology.core.State;
import org.reploop.topology.model.Proc;
import org.reploop.topology.parser.Conn;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.reploop.topology.repository.ProcRepository;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LineAdapter implements Collector {
    private static final int PPID = 1;
    private static final String USER = "nobody";
    private final Map<String, AtomicInteger> hostPid = new ConcurrentHashMap<>();
    @Resource
    private ProcRepository procRepository;

    @Override
    public void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        for (Line line : lines) {
            String host = line.getPrivateIp();
            int pid = pid(host);
            HostPort local = new HostPort(host, line.getPort());
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
            String anotherIp;
            if (null != (anotherIp = line.getPublicIp())
                    && !anotherIp.isEmpty()
                    && !host.equals(anotherIp)) {
                HostPort local2 = new HostPort(anotherIp, line.getPort());
                nb.conn(new Conn(local2));
                records.add(nb.build());
            }
            RawProcess process = RawProcess.builder()
                    .host(host)
                    .command(line.getName())
                    .pid(pid)
                    .ppid(PPID)
                    .user(USER)
                    .build();
            processes.add(process);
        }
    }

    private int pid(String host) {
        AtomicInteger pid = hostPid.computeIfAbsent(host, h -> new AtomicInteger(0));
        if (0 != pid.get()) {
            return pid.incrementAndGet();
        }
        Proc proc = null;
        if (null != procRepository) {
            proc = procRepository.findFirstByHostOrderByPidDesc(host);
        }
        int candidate = ThreadLocalRandom.current().nextInt(1000, 2000);
        int val = (null == proc) ? candidate : (proc.getPid() + candidate);

        pid.compareAndSet(0, val);
        return pid.incrementAndGet();
    }
}
