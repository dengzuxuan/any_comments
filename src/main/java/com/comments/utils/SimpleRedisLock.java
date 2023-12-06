package com.comments.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

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

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String lockname) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockname = lockname;
    }


    @Override
    public boolean setLock(long timeoutminute) {
        //获取线程id
        String idInfo = ID_PREFIX+"_"+ Thread.currentThread().getId();;
        String key = LOCK_PREFIX + lockname;
        Boolean successFlag = stringRedisTemplate.opsForValue().setIfAbsent(key, idInfo, timeoutminute, TimeUnit.SECONDS);
        System.out.println("successFlag = " + successFlag);
        System.out.println("key = " + key);
        //避免自动拆箱的空指针问题
        return Boolean.TRUE.equals(successFlag);
    }

    @Override
    public void unLock() {
        String key = LOCK_PREFIX + lockname;
        long threadId = Thread.currentThread().getId();
        String idInfoValue = ID_PREFIX+"_"+threadId;

        if(idInfoValue.equals(stringRedisTemplate.opsForValue().get(key))){
            stringRedisTemplate.delete(key);
        }
    }
}
