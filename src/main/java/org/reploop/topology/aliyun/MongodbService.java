package org.reploop.topology.aliyun;

import com.aliyun.dds20151201.Client;
import com.aliyun.dds20151201.models.DescribeDBInstancesRequest;
import com.aliyun.dds20151201.models.DescribeReplicaSetRoleRequest;
import com.aliyun.teaopenapi.models.Config;
import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MongodbService implements Collector, Loader {
    @Resource
    private TopologyProperties properties;
    @Resource
    private LineAdapter lineAdapter;

    public List<Line> list(String accessKeyId, String accessKeySecret) throws Exception {
        List<Line> lines = new ArrayList<>();
        Client client = getClient(accessKeyId, accessKeySecret);
        DescribeDBInstancesRequest request = new DescribeDBInstancesRequest();
        var response = client.describeDBInstances(request);
        var instances = response.getBody().getDBInstances().getDBInstance();
        for (var instance : instances) {
            DescribeReplicaSetRoleRequest rsr = new DescribeReplicaSetRoleRequest();
            rsr.setDBInstanceId(instance.getDBInstanceId());
            var ns = client.describeReplicaSetRole(rsr);
            var list = ns.getBody().getReplicaSets().getReplicaSet();
            Line line = new Line();
            line.setCmd(instance.getEngine());
            line.setVersion(instance.getEngineVersion());
            line.setName(instance.getDBInstanceId());
            for (var info : list) {
                InetAddress address = InetAddress.getByName(info.getConnectionDomain());
                String ip = address.getHostAddress();
                String ipType = address.isSiteLocalAddress() ? "Private" : "Public";
                line.setPort(Integer.valueOf(info.getConnectionPort()));
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
        return lines;
    }

    private Client getClient(String accessKeyId, String accessKeySecret) throws Exception {
        Config config = new Config()
                // 您的AccessKey ID
                .setAccessKeyId(accessKeyId)
                // 您的AccessKey Secret
                .setAccessKeySecret(accessKeySecret);
        // 访问的域名
        config.endpoint = "mongodb.aliyuncs.com";
        return new Client(config);
    }

    public List<Line> loadDds() throws Exception {
        TopologyProperties.Aliyun aliyun = properties.getAliyun();
        return list(aliyun.getAccessKey(), aliyun.getAccessSecret());
    }

    @Override
    public void load(List<RawRecord> records, List<RawProcess> processes) {
        try {
            convert(loadDds(), records, processes);
        } catch (Exception e) {
            log.error("Cannot load rds", e);
        }
    }

    @Override
    public void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        lineAdapter.convert(lines, records, processes);
    }
}
