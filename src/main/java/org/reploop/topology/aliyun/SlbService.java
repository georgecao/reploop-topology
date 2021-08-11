package org.reploop.topology.aliyun;

import com.aliyun.slb20140515.Client;
import com.aliyun.slb20140515.models.DescribeLoadBalancerListenersRequest;
import com.aliyun.slb20140515.models.DescribeLoadBalancersRequest;
import com.aliyun.slb20140515.models.DescribeRegionsRequest;
import com.aliyun.slb20140515.models.DescribeRegionsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.aliyun.slb20140515.models.DescribeLoadBalancersResponseBody.DescribeLoadBalancersResponseBodyLoadBalancersLoadBalancer;

@Service
@Slf4j
public class SlbService implements Collector, Loader {
    @Resource
    private TopologyProperties properties;
    @Resource
    private LineAdapter lineAdapter;

    private Set<String> regions(Client client) throws Exception {
        DescribeRegionsRequest request = new DescribeRegionsRequest();
        var response = client.describeRegions(request);
        return response.getBody()
                .getRegions()
                .getRegion()
                .stream()
                .map(DescribeRegionsResponseBody.DescribeRegionsResponseBodyRegionsRegion::getRegionId)
                .collect(Collectors.toSet());
    }

    private Map<String, Set<Integer>> ports(Client client, String region, List<String> lbIds) throws Exception {
        if (lbIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Set<Integer>> listeners = new HashMap<>();
        List<List<String>> parts = Lists.partition(lbIds, 10);
        for (List<String> part : parts) {
            DescribeLoadBalancerListenersRequest lr = new DescribeLoadBalancerListenersRequest();
            lr.setRegionId(region);
            lr.setLoadBalancerId(part);
            while (true) {
                var ls = client.describeLoadBalancerListeners(lr);
                var body = ls.getBody();
                var list = body.getListeners();
                if (null == list) {
                    break;
                }
                for (var l : list) {
                    Set<Integer> ports = listeners.computeIfAbsent(l.getLoadBalancerId(), id -> new HashSet<>());
                    ports.add(l.getListenerPort());
                }
                String nextToken;
                if (null == (nextToken = body.getNextToken())) {
                    break;
                }
                lr.setNextToken(nextToken);
            }
        }
        return listeners;
    }

    private List<Line> load() throws Exception {
        List<Line> lines = new ArrayList<>();
        var ac = properties.getAliyun();
        Client client = getClient(ac.getAccessKey(), ac.getAccessSecret());
        Set<String> regions = regions(client);
        for (String region : regions) {
            DescribeLoadBalancersRequest lbr = new DescribeLoadBalancersRequest();
            lbr.setRegionId(region);
            var lbs = client.describeLoadBalancers(lbr);
            var instances = lbs.getBody().getLoadBalancers().getLoadBalancer();
            List<String> lbIds = instances.stream()
                    .map(DescribeLoadBalancersResponseBodyLoadBalancersLoadBalancer::getLoadBalancerId)
                    .sorted()
                    .distinct()
                    .collect(Collectors.toList());
            Map<String, Set<Integer>> ports = ports(client, region, lbIds);
            for (var instance : instances) {
                Line line = new Line();
                line.setCmd("slb");
                line.setVersion("v1.0");
                line.setName(instance.getLoadBalancerName());
                String addressType = instance.getAddressType();
                String ip = instance.getAddress();
                // One slb one ip
                line.setPrivateIp(ip);
                if (!"intranet".equals(addressType)) {
                    line.setPublicIp(ip);
                }
                var list = ports.get(instance.getLoadBalancerId());
                for (var l : list) {
                    line.setPort(l);
                    lines.add(line.clone());
                }
            }
        }
        return lines;
    }

    private Client getClient(String accessKeyId, String accessKeySecret) throws Exception {
        Config config = new Config()
                // 您的AccessKey ID
                .setAccessKeyId(accessKeyId)
                // 您的AccessKey Secret
                .setAccessKeySecret(accessKeySecret);
        // 访问的域名
        config.endpoint = "slb.aliyuncs.com";
        return new Client(config);
    }

    @Override
    public void load(List<RawRecord> records, List<RawProcess> processes) {
        try {
            List<Line> lines = load();
            convert(lines, records, processes);
        } catch (Exception e) {
            log.error("Cannot load slb", e);
        }
    }

    @Override
    public void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        lineAdapter.convert(lines, records, processes);
    }
}
