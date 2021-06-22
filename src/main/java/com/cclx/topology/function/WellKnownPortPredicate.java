package com.cclx.topology.function;

import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
public class WellKnownPortPredicate implements Predicate<Integer> {
    @Override
    public boolean test(Integer port) {
        return filter(port);
    }

    private boolean filter(Integer p) {
        return p == 22          // sshd
                || p == 123     // ntpd
                || p == 873     // rpcbind
                || p == 10050   // zabbix
                || p == 60020   // zabbix
                || p == 25      // mail
                || p == 53      // dns
                || p == 111     // rpc
                || p == 65533;  // sshd
    }
}
