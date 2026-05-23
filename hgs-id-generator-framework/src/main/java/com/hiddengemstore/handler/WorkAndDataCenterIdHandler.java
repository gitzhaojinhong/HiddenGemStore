package com.hiddengemstore.handler;


import com.alibaba.fastjson.JSON;
import com.hiddengemstore.core.IdGeneratorConstant;
import com.hiddengemstore.model.WorkDataCenterId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.Arrays;
import java.util.List;


/**
 * 工作节点ID和数据中心ID的分布式分配处理器,负责通过Redis Lua脚本.
 * 原子性地分配workerId（工作节点ID）和dataCenterId（数据中心ID）。
 * @author : Zhao
 */
@Slf4j
public class WorkAndDataCenterIdHandler {
    // Redis key
    private final String SNOWFLAKE_WORK_ID_KEY = "snowflake_work_id";
    private final String SNOWFLAKE_DATA_CENTER_ID_key = "snowflake_data_center_id";

    private final List<String> keys = Arrays.asList(SNOWFLAKE_WORK_ID_KEY, SNOWFLAKE_DATA_CENTER_ID_key);

    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<String> redisScript;

    // 构造函数初始化 stringRedisTemplate 和 redisScript
    public WorkAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/workAndDataCenterId.lua")));
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            log.error("初始化Redis Lua脚本失败", e);
        }
    }
    // 获取工作节点ID和数据中心ID
    public WorkDataCenterId getWorkAndDataCenterId() {
        WorkDataCenterId workDataCenterId = new WorkDataCenterId();
        try {
            // 参数传入: 最大工作节点ID, 最大数据中心ID
            Object[] data = new String[2];
            data[0] = String.valueOf(IdGeneratorConstant.MAX_WORKER_ID);
            data[1] = String.valueOf(IdGeneratorConstant.MAX_DATA_CENTER_ID);
            // 执行Lua脚本 参数传入: keys, data
            String result = stringRedisTemplate.execute(redisScript, keys, data);
            // 解析结果
            workDataCenterId = JSON.parseObject(result, WorkDataCenterId.class);
        } catch (Exception e) {
            log.error("获取工作节点ID和数据中心ID失败", e);
        }
        return workDataCenterId;
    }
}
