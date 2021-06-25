package org.reploop.topology.aliyun;

import org.junit.jupiter.api.Test;
import org.reploop.topology.parser.RawProcess;
import org.reploop.topology.parser.RawRecord;

import java.util.ArrayList;
import java.util.List;

class RedisServiceTest {

    @Test
    void name() throws Exception {
        RedisService redisService = new RedisService();
        List<RawRecord> records = new ArrayList<>();
        List<RawProcess> processes = new ArrayList<>();
        redisService.loadCsvFile("/Users/george/Downloads/redis_instance_list_2021-06-24_15_11_09_S.csv", records, processes);
        System.out.println(records);
        System.out.println(processes);

    }
}