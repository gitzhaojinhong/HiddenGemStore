package com.hiddengemstore.lua;

import com.alibaba.fastjson.JSON;
import com.hiddengemstore.redis.api.RedisCache;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 秒杀券扣减
 * @author : ZhaoJH
 */
@Slf4j
@Component
public class SeckillVoucherOperate {
    @Resource
    private RedisCache redisCache;

    private DefaultRedisScript<String> redisScript;
    // bean生命周期初始化：Spring 完成依赖注入之后再调用
    @PostConstruct
    public void init() {
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckillVoucher.lua")));
            redisScript.setResultType(String.class);
        } catch (Exception e) {
            log.error("redisScript init lua error",e);
        }
    }
    public SeckillVoucherDomain execute(List<String> keys, String[] args){
        String object = redisCache.getInstance().execute(redisScript, keys, (Object) args);
        return JSON.parseObject(object, SeckillVoucherDomain.class);
    }
}
