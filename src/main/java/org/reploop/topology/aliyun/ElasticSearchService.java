package org.reploop.topology.aliyun;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.elasticsearch.model.v20170613.DescribeInstanceRequest;
import com.aliyuncs.elasticsearch.model.v20170613.DescribeRegionsRequest;
import com.aliyuncs.elasticsearch.model.v20170613.DescribeRegionsResponse.RegionInfo;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.config.TopologyProperties;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ElasticSearchService implements Loader, Collector {
    @Resource
    private TopologyProperties properties;
    @Resource
    private LineAdapter lineAdapter;

    @Override
    public void convert(List<Line> lines, List<RawRecord> records, List<RawProcess> processes) {
        lineAdapter.convert(lines, records, processes);
    }

    @Override
    public void load(List<RawRecord> records, List<RawProcess> processes) {
        try {
            convert(load(), records, processes);
        } catch (ClientException e) {
            log.error("Cannot load elasticsearch", e);
        }
    }

    private List<Line> load() throws ClientException {
        List<Line> lines = new ArrayList<>();
        IAcsClient client = getClient();
        var regions = getRegions(client);
        for (var region : regions) {
            IAcsClient regionClient = getClient(region.getRegionId());
            DescribeInstanceRequest request = new DescribeInstanceRequest();
            request.setInstanceId("9s");
            var response = regionClient.getAcsResponse(request);
            var list = response.getResult();
        }
        return lines;
    }

    private static final String DEFAULT_REGION = "cn-hangzhou";

    private IAcsClient getClient() {
        return getClient(DEFAULT_REGION);
    }

    public List<RegionInfo> getRegions(IAcsClient client) throws ClientException {
        DescribeRegionsRequest request = new DescribeRegionsRequest();
        var response = client.getAcsResponse(request);
        return response.getResult();
    }

    private IAcsClient getClient(String regionId) {
        var as = properties.getAliyun();
        DefaultProfile profile = DefaultProfile.getProfile(regionId, as.getAccessKey(), as.getAccessSecret());
        return new DefaultAcsClient(profile);
    }
}
