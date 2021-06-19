package com.cclx.topology.parser;

import com.cclx.topology.core.HostPort;
import com.cclx.topology.core.State;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.cclx.topology.Constants.*;

@Component
public class RecordParser {
    private HostPort parseHostPort(String val) {
        int i = val.indexOf(COLON);
        String host = val.substring(0, i);
        Integer port = Integer.valueOf(val.substring(i + COLON.length()));
        return new HostPort(host, port);
    }

    public RawRecord parse(String host, String line) {
        String[] elements = line.split(WS);
        RawRecord ins = new RawRecord();
        for (int i = 0; i < elements.length; i++) {
            switch (i) {
                case 0 -> ins.cmd = elements[i];
                case 1 -> ins.pid = Integer.valueOf(elements[i]);
                case 2 -> ins.ppid = Integer.valueOf(elements[i]);
                case 3 -> ins.user = elements[i];
                case 4 -> ins.fd = elements[i];
                case 5 -> ins.type = elements[i];
                case 6 -> ins.device = elements[i];
                case 7 -> ins.sizeOrOff = elements[i];
                case 8 -> ins.node = elements[i];
                case 9 -> {
                    String conn = elements[i];
                    int index = conn.indexOf(SEP);
                    HostPort local;
                    HostPort remote = null;
                    if (index > 0) {
                        local = parseHostPort(conn.substring(0, index));
                        remote = parseHostPort(conn.substring(index + SEP.length()));
                    } else {
                        local = parseHostPort(conn);
                    }
                    Conn connection = new Conn();
                    connection.local = local;
                    connection.remote = remote;
                    ins.conn = connection;
                }
                case 10 -> {
                    String val = elements[i];
                    ins.state = State.valueOf(val.substring(1, val.length() - 1));
                }
            }
        }
        if (null != host) {
            ins.host = host;
        }
        return ins;
    }

    /**
     * Handle multi NIC, one host has more than one IP address.
     */
    public void expand(List<RawRecord> records) {
        Map<String, List<RawRecord>> groups = records.stream()
                .collect(Collectors.groupingBy(rr -> rr.host, Collectors.toList()));
        groups.forEach((box, rawRecords) -> {
            // all local hosts
            Set<String> hosts = rawRecords.stream()
                    .filter(instance -> instance.state != State.LISTEN)
                    .map(in -> in.conn.local.ip)
                    .filter(s -> !LO.equals(s))
                    .collect(Collectors.toSet());
            if (hosts.isEmpty()) {
                throw new IllegalStateException(box);
            }
            hosts.add(box);

            // Replace LO with IP
            rawRecords.forEach(rr -> {
                Conn conn = rr.conn;
                HostPort local = conn.local;
                if (LO.equals(local.ip)) {
                    local.ip = box;
                }
                HostPort remote = conn.remote;
                if (null != remote && LO.equals(remote.ip)) {
                    remote.ip = box;
                }
            });

            // Expand * or 0.0.0.0
            int maxPid = rawRecords.stream().mapToInt(rr -> rr.pid).max().orElse(0);
            for (RawRecord record : rawRecords) {
                if (State.LISTEN != record.state) {
                    continue;
                }
                HostPort local = record.conn.local;
                String address = local.ip;
                if (ZERO.equals(address) || STAR.equals(address)) {
                    for (String localHost : hosts) {
                        if (box.equals(localHost)) {
                            local.ip = localHost;
                        } else {
                            RawRecord rawRecord = new RawRecord(record);
                            // keep pid uniq
                            rawRecord.pid = ++maxPid;
                            rawRecord.conn.local.ip = localHost;
                            records.add(rawRecord);
                        }
                    }
                }
            }
        });
    }
}
