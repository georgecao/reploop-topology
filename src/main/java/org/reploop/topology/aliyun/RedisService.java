package org.reploop.topology.aliyun;

import com.aliyun.r_kvstore20150101.Client;
import com.aliyun.r_kvstore20150101.models.DescribeDBInstanceNetInfoRequest;
import com.aliyun.r_kvstore20150101.models.DescribeInstancesRequest;
import com.aliyun.r_kvstore20150101.models.DescribeRegionsRequest;
import com.aliyun.r_kvstore20150101.models.DescribeRegionsResponseBody;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RedisService implements Collector, Loader {
    @Resource
    private TopologyProperties properties;
    @Resource
    private LineAdapter lineAdapter;

    private Set<String> regions(Client client) throws Exception {
        DescribeRegionsRequest request = new DescribeRegionsRequest();
        var response = client.describeRegions(request);
        return response.getBody()
                .getRegionIds()
                .getKVStoreRegion()
                .stream()
                .map(DescribeRegionsResponseBody.DescribeRegionsResponseBodyRegionIdsKVStoreRegion::getRegionId)
                .collect(Collectors.toSet());
    }

    private List<Line> load() throws Exception {
        List<Line> lines = new ArrayList<>();
        var ac = properties.getAliyun();
        Client client = getClient(ac.getAccessKey(), ac.getAccessSecret());
        Set<String> regions = regions(client);
        for (String region : regions) {
            DescribeInstancesRequest ir = new DescribeInstancesRequest();
            ir.setRegionId(region);
            var is = client.describeInstances(ir);
            var instances = is.getBody().getInstances().getKVStoreInstance();
            for (var instance : instances) {
                Line line = new Line();
                line.setCmd(instance.getInstanceType());
                line.setVersion(instance.getEngineVersion());
                line.setName(instance.getInstanceName());

                DescribeDBInstanceNetInfoRequest nr = new DescribeDBInstanceNetInfoRequest();
                nr.setInstanceId(instance.getInstanceId());
                var ns = client.describeDBInstanceNetInfo(nr);
                var list = ns.getBody().getNetInfoItems().getInstanceNetInfo();
                for (var l : list) {
                    line.setPort(Integer.valueOf(l.getPort()));
                    String ipType = l.getIPType();
                    String ip = l.getIPAddress();
                    if ("Private".equals(ipType)) {
                        line.setPrivateIp(ip);
                    } else {
                        line.setPublicIp(ip);
                    }
                }
                if (null != line.getPort()) {
                    lines.add(line);
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
        config.endpoint = "r-kvstore.aliyuncs.com";
        return new Client(config);
    }

    @Override
    public void load(List<RawRecord> records, List<RawProcess> processes) {
        try {
            List<Line> lines = load();
            convert(lines, records, processes);
        } catch (Exception e) {
            log.error("Cannot load redis", e);
        }
    }

    @Override
    public void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        lineAdapter.convert(lines, records, processes);
    }
}
