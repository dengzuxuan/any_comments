package com.comments.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  TODO
 * </p>
 *
 * @author Colin
 * @since 2023/12/6
 */
public class SimpleRedisLock implements ILock {
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    private final StringRedisTemplate stringRedisTemplate;
    private final String lockname;
    private final static DefaultRedisScript<Long> UNLOCK_SCRIPT;//Long是返回值
    static {
        //动态加载unlock.lua内容
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String lockname) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockname = lockname;
    }


    @Override
    public boolean setLock(long timeoutminute) {
        //获取线程id
        String idInfo = ID_PREFIX+"_"+ Thread.currentThread().getId();
        String key = LOCK_PREFIX + lockname;
        Boolean successFlag = stringRedisTemplate.opsForValue().setIfAbsent(key, idInfo, timeoutminute, TimeUnit.MINUTES);
        //避免自动拆箱的空指针问题
        return Boolean.TRUE.equals(successFlag);
    }

    @Override
    public void unLock() {
        //execute执行脚本
        //传参的key为redis中锁的key
        //传入的args为当前的线程信息
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(LOCK_PREFIX + lockname),
                ID_PREFIX+"_"+Thread.currentThread().getId()
                );
    }
}
