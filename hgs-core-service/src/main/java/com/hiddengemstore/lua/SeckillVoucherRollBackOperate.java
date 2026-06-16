package com.hiddengemstore.lua;

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
 * 秒杀卷回滚
 * @author : ZhaoJH
 */
@Slf4j
@Component
public class SeckillVoucherRollBackOperate {
    @Resource
    private RedisCache redisCache;

    private DefaultRedisScript<Long> redisScript;


    /**
     * 初始化redisScript
     */
    @PostConstruct
    public void init(){
        try {
            redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seckillVoucherRollBack.lua")));
            redisScript.setResultType(Long.class);
        } catch (Exception e) {
            log.error("redisScript init lua error",e);
        }
    }

    /**
     * 执行Lua脚本
     * @param keys KEY列表
     * @param args 参数列表
     * @return 脚本返回码
     */
    public Integer execute(List<String> keys, String[] args){
        Long result = redisCache.getInstance().execute(redisScript, keys, (Object) args);
        return result.intValue();
    }
}
