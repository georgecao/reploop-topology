package org.reploop.topology.aliyun;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.rds.model.v20140815.DescribeDatabasesRequest;
import com.aliyuncs.rds.model.v20140815.DescribeDatabasesResponse;
import com.aliyuncs.rds.model.v20140815.DescribeRegionsRequest;
import com.aliyuncs.rds.model.v20140815.DescribeRegionsResponse;

public class RdsService {
    public void main() throws ClientException {
        // 初始化请求参数
        DefaultProfile profile = DefaultProfile.getProfile();
        IAcsClient client = new DefaultAcsClient(profile);
        DescribeRegionsRequest rr = new DescribeRegionsRequest();
        DescribeRegionsResponse rs = client.getAcsResponse(rr);
        var regions = rs.getRegions();

        DescribeDatabasesRequest ddr = new DescribeDatabasesRequest();
        DescribeDatabasesResponse dds = client.getAcsResponse(ddr);
        var dbs = dds.getDatabases();
    }
}
