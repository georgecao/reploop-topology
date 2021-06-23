package org.reploop.topology.parser;

import org.reploop.topology.core.HostPort;
import org.reploop.topology.core.Node;
import org.reploop.topology.core.State;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.reploop.topology.Constants.*;

@Component
public class RecordParser {
    private HostPort parseHostPort(String val) {
        int idx = val.lastIndexOf(COLON);
        // Handle IPv6 Address Here
        String host = val.substring(0, idx);
        String port = val.substring(idx + COLON.length());
        if (STAR.equals(port)) {
            throw new UdpBroadcastException();
        }
        return new HostPort(host, Integer.valueOf(port));
    }

    /**
     * <p>
     * rpcbind     989     1    rpc    6u  IPv4     9205      0t0  UDP *:111
     * rapportd    410     1 george    8u  IPv4 0xbd94963bc61702f3      0t0  UDP *:*
     * rpcbind     989     1    rpc    7u  IPv4     9207      0t0  UDP *:736
     * rpcbind     989     1    rpc    8u  IPv4     9208      0t0  TCP *:111 (LISTEN)
     * openresty  9489  6123    web   11u  IPv4 1023797525      0t0  UDP 172.17.1.2:34823->100.100.2.136:53
     * </p>
     */
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
                case 8 -> ins.node = Node.valueOf(elements[i]);
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
                    Conn c = new Conn();
                    c.local = local;
                    c.remote = remote;
                    ins.conn = c;
                }
                case 10 -> {
                    String val = elements[i];
                    ins.state = State.valueOf(val.substring(1, val.length() - 1));
                }
            }
        }
        // UDP connection do not have state,
        if (ins.node == Node.UDP) {
            if (ins.conn.remote == null) {
                // Consider local port as listen state
                ins.state = State.LISTEN;
            } else {
                // Others keep unknown state
                ins.state = State.UNKNOWN;
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
                    .filter(s -> !(LO.equals(s) || LO_V6.equals(s)))
                    .collect(Collectors.toSet());
            if (hosts.isEmpty()) {
                throw new IllegalStateException(box);
            }
            hosts.add(box);

            // Replace LO with IP
            rawRecords.forEach(rr -> {
                Conn conn = rr.conn;
                HostPort local = conn.local;
                if (LO.equals(local.ip) || LO_V6.equals(local.ip)) {
                    local.ip = box;
                }
                HostPort remote = conn.remote;
                if (null != remote && (LO.equals(remote.ip) || LO_V6.equals(remote.ip))) {
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
