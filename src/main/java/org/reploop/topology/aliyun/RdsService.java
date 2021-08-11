package org.reploop.topology.aliyun;

import com.aliyun.rds20140815.Client;
import com.aliyun.rds20140815.models.DescribeDBInstanceNetInfoRequest;
import com.aliyun.rds20140815.models.DescribeDBInstancesRequest;
import com.aliyun.rds20140815.models.DescribeRegionsRequest;
import com.aliyun.rds20140815.models.DescribeRegionsResponseBody;
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
public class RdsService implements Collector, Loader {
    @Resource
    private TopologyProperties properties;
    @Resource
    private LineAdapter lineAdapter;

    public Set<String> regions(Client client) throws Exception {
        DescribeRegionsRequest request = new DescribeRegionsRequest();
        var response = client.describeRegions(request);
        var list = response.getBody().getRegions().getRDSRegion();
        return list.stream().map(DescribeRegionsResponseBody.DescribeRegionsResponseBodyRegionsRDSRegion::getRegionId).collect(Collectors.toSet());
    }

    public Set<String> listRegions(Client client) throws Exception {
        return regions(client);
    }

    public List<Line> list(String accessKeyId, String accessKeySecret) throws Exception {
        List<Line> lines = new ArrayList<>();
        Client client = getClient(accessKeyId, accessKeySecret);
        var regions = listRegions(client);
        for (var region : regions) {
            DescribeDBInstancesRequest request = new DescribeDBInstancesRequest();
            request.setRegionId(region);
            var response = client.describeDBInstances(request);
            var instances = response.getBody().getItems().getDBInstance();
            for (var instance : instances) {
                DescribeDBInstanceNetInfoRequest nr = new DescribeDBInstanceNetInfoRequest();
                nr.setDBInstanceId(instance.getDBInstanceId());
                var ns = client.describeDBInstanceNetInfo(nr);
                var list = ns.getBody().getDBInstanceNetInfos().getDBInstanceNetInfo();
                Line line = new Line();
                line.setCmd(instance.getEngine());
                line.setVersion(instance.getEngineVersion());
                line.setName(instance.getDBInstanceId());
                for (var info : list) {
                    String ipType = info.getIPType();
                    String ip = info.getIPAddress();
                    line.setPort(Integer.valueOf(info.getPort()));
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
        config.endpoint = "rds.aliyuncs.com";
        return new Client(config);
    }

    public List<Line> loadRds() throws Exception {
        TopologyProperties.Aliyun aliyun = properties.getAliyun();
        return list(aliyun.getAccessKey(), aliyun.getAccessSecret());
    }

    @Override
    public void load(List<RawRecord> records, List<RawProcess> processes) {
        try {
            convert(loadRds(), records, processes);
        } catch (Exception e) {
            log.error("Cannot load rds", e);
        }
    }

    @Override
    public void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        lineAdapter.convert(lines, records, processes);
    }
}
