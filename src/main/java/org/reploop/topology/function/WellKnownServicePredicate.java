package org.reploop.topology.function;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.function.BiPredicate;

@Component
public class WellKnownServicePredicate implements BiPredicate<String, Integer> {
    @Resource
    private SystemServicePredicate ssp;
    @Resource
    private WellKnownPortPredicate wpp;

    @Override
    public boolean test(String cmd, Integer port) {
        return ssp.test(cmd) || wpp.test(port) || filter(cmd);
    }

    private boolean filter(String name) {
        return name.equals("sudo")
                || name.endsWith("xinetd")
                || name.contains("cocod")
                || name.contains("rpc.rquotad")
                || name.contains("rpc.mountd")
                || name.contains("rpcbind")
                || name.contains("rpc.statd")
                || name.contains("gse_agent")
                || name.contains("CmsGoAgent")
                || name.contains("salt-")
                || name.contains("cm-agent")
                || name.endsWith("sshd")
                || name.contains("kubelet")
                || name.contains("aliyun-service")
                || name.contains("aliyun_assist_update")
                || name.contains("dockerd")
                || name.contains("AliYunDun")
                || name.contains("aliyun-assist")
                || name.contains("gunicorn");
    }
}
