package org.reploop.topology.aliyun;

import lombok.extern.slf4j.Slf4j;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
@Slf4j
public class AliyunResourceLoader implements Loader {
    @Resource
    private MongodbService mongodbService;
    @Resource
    private RdsService rdsService;
    @Resource
    private RedisService redisService;
    @Resource
    private SlbService slbService;
    @Resource
    private ElasticSearchService elasticSearchService;

    @Override
    public void load(List<RawRecord> records, List<RawProcess> processes) {
        redisService.load(records, processes);
        rdsService.load(records, processes);
        mongodbService.load(records, processes);
        slbService.load(records, processes);
        elasticSearchService.load(records, processes);
    }
}
