package com.hiddengemstore.lua;

import com.hiddengemstore.redis.api.RedisCache;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * 滑动窗口限流
 * @author : ZhaoJH
 */
@Slf4j
@RequiredArgsConstructor// 自动注入final字段
public class SlidingRateLimitOperate {

    private final RedisCache redisCache;


    private DefaultRedisScript<Integer> redisScript;

    @PostConstruct//依赖注入完成之后执行
    public void init(){
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/rateLimitSliding.lua")));
            redisScript.setResultType(Integer.class);
        } catch (Exception e) {
            log.error("SlidingRateLimitOperate init lua error", e);
        }
    }

    public Integer execute(List<String> keys, String[] args){
        return redisCache.getInstance().execute(redisScript, keys, (Object) args);
    }
}