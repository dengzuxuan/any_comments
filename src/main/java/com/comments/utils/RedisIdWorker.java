package com.comments.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static com.comments.utils.RedisConstants.INCR_KEY;

/**
 * <p>
 *  基于redis的全局id
 *  64位整型
 *  第1位是符号
 *  前31位时间戳
 *  后32位是序列号， 是当前日期（yyyy:mm:dd）位key的redis自增
 * </p>
 *
 * @author Colin
 * @since 2023/12/5
 */
@Component
public class RedisIdWorker {
    //开始时间戳
    private final static long BEGIN_TIMESTAMP = 1672531200L;

    //时间戳左移位数
    private final static int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long getNextId(String keyPrefix){
        //获取时间戳
        long deltaTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC)-BEGIN_TIMESTAMP;

        //获取当日自增长
        String todayKey = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long increment = stringRedisTemplate.opsForValue().increment(INCR_KEY + keyPrefix + todayKey);

        //时间戳左移31位，这样的话在末尾会多出32位0，然后和increment做或运算
        // 或运算是有1为1，全0为0，这样就是把increment覆盖在后32位上了
        return deltaTimestamp<<COUNT_BITS | increment;
    }


//    public static void main(String[] args) {
//        LocalDateTime time = LocalDateTime.of(2023,1,1,0,0,0);
//        long epochSecond = time.toEpochSecond(ZoneOffset.UTC);
//        System.out.println("epochSecond = " + epochSecond);
//    }
}
