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
 * 令牌桶限流
 * @author : ZhaoJH
 */
@Slf4j
@RequiredArgsConstructor// 自动注入final字段
public class TokenBucketRateLimitOperate {
    private final RedisCache redisCache;

    private DefaultRedisScript<Integer> redisScript;
    @PostConstruct//依赖注入完成之后执行
    public void init() {
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/tokenBucket.lua")));
            redisScript.setResultType(Integer.class);
        } catch (Exception e) {
            log.error("TokenBucketRateLimitOperate init lua error", e);
        }
    }
    public Integer execute(List<String> keys, String[] args){
        return redisCache.getInstance().execute(redisScript, keys, (Object) args);
    }


}
