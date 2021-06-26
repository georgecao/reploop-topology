package org.reploop.topology.aliyun;

import org.junit.jupiter.api.Test;
import org.reploop.topology.BaseTest;

import javax.annotation.Resource;

class RdsServiceTest extends BaseTest {

    @Resource
    private RdsService rdsService;

    @Test
    void main() throws Exception {
        var lines = rdsService.loadRds();
        System.out.println(lines);
    }
}