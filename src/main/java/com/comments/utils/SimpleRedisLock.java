package com.comments.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

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
    public static final String LOCK_PREFIX = "lock:";
    private StringRedisTemplate stringRedisTemplate;
    private String lockname;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String lockname) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.lockname = lockname;
    }


    @Override
    public boolean setLock(long timeoutminute) {
        //获取线程id
        long threadId = Thread.currentThread().getId();
        String key = LOCK_PREFIX + lockname;
        Boolean successFlag = stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(threadId), timeoutminute, TimeUnit.SECONDS);
        //避免自动拆箱的空指针问题
        return Boolean.TRUE.equals(successFlag);
    }

    @Override
    public void unLock() {
        String key = LOCK_PREFIX + lockname;
        stringRedisTemplate.delete(key);
    }
}
