package com.comments.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.comments.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.comments.utils.RedisConstants.*;

/**
 * <p>
 *  封装工具类
 * * 方法1：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
 * * 方法2：将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
 * * 方法3：根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
 * * 方法4：根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
 * * 方法5：根据指定的key查询缓存，并反序列化为指定类型，需要利用互斥锁解决缓存击穿问题
 * </p>
 *
 * @author Colin
 * @since 2023/12/4
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);
    }

    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExprie(String key,Object value,Long time,TimeUnit timeUnit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public <R,ID> R queryWithPassThroght(String prefix, ID id,
                                         Class<R> type, Function<ID,R> dbFallback,
                                         Long time,TimeUnit timeUnit){
        String queryKey = prefix + id;
        String resultJson = stringRedisTemplate.opsForValue().get(queryKey);
        //isNotBlank在null、""、"\t\n"的情况时返回false
        if(StrUtil.isNotBlank(resultJson)){
            //存在缓存直接返回
            return JSONUtil.toBean(resultJson, type);
        }

        //json数据不为null 那么只能是 "" 是命中了空值 这时候限制缓存穿透直接返回
        if(resultJson!=null){
            return null;
        }

        //排除下来此时json只能为null，即没有缓存，可以加载缓存了
        R dbResult = dbFallback.apply(id);
        if(dbResult == null){
            //防止缓存穿透 使用【缓存空对象解决】
            stringRedisTemplate.opsForValue().set(queryKey,"",time,timeUnit);
            return null;
        }
        this.set(queryKey,dbResult,time,timeUnit);
        return dbResult;
    }

    //根据指定的key查询缓存，并反序列化为指定类型，需要利用互斥锁解决缓存击穿问题
    public <R,ID> R queryWithMutex(String prefix, ID id,
                                   Class<R> type, Function<ID,R> dbFallback,
                                   Long time,TimeUnit timeUnit){
        String queryKey = prefix + id;
        String resultJson = stringRedisTemplate.opsForValue().get(queryKey);

        //isNotBlank在null、""、"\t\n"的情况时返回false
        if(StrUtil.isNotBlank(resultJson)){
            return JSONUtil.toBean(resultJson, type);
        }

        //json数据不为null 那么只能是 "" 是命中了空值 这时候限制缓存穿透直接返回
        if(resultJson != null){
            return null;
        }
        R result = null;
        String lockKey = LOCK_PREFIX_KEY+queryKey;
        try {
            //防止缓存击穿 使用【互斥锁进行缓存重建】
            if(!setLock(lockKey)){
                //如果没有获取到互斥锁，需要睡眠等待
                Thread.sleep(50);
                return queryWithMutex(prefix,id,type,dbFallback,time,timeUnit);
            }
            result = dbFallback.apply(id);
            if(result == null){
                //防止缓存穿透 使用【缓存空对象解决】
                stringRedisTemplate.opsForValue().set(queryKey,"",time,timeUnit);
                return null;
            }
            this.set(queryKey,result,time,timeUnit);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            delLock(lockKey);
        }
        return result;
    }

    //根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R,ID> R queryWithLogicalExpire(String prefix, ID id,
                                         Class<R> type, Function<ID,R> dbFallback,
                                         Long time,TimeUnit timeUnit){
        String queryKey = prefix + id;
        String resultJson = stringRedisTemplate.opsForValue().get(queryKey);
        if(StrUtil.isBlank(resultJson)) {
            return null;
        }

        //数据不为空 为redisdata 其中有过期时间，需要判断是否过期，若过期则返回过期数据
        // 然后试图获取锁->新开线程重建缓存
        RedisData redisData = JSONUtil.toBean(resultJson, RedisData.class);
        R oldResult = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        if(LocalDateTime.now().isBefore(redisData.getExpireTime())){
            //未过期
            return oldResult;
        }
        //已过期
        String lockKey = LOCK_PREFIX_KEY+queryKey;
        boolean isLock = setLock(lockKey);
        if(isLock){
            //成功获取互斥锁 开启线程进行重建缓存
            //获取成功后需要做dobule check重新检测过期时间，避免其他线程已经重建完成了
            String resultJsonCheck = stringRedisTemplate.opsForValue().get(queryKey);
            RedisData redisDataCheck = JSONUtil.toBean(resultJsonCheck, RedisData.class);
            if(!LocalDateTime.now().isBefore(redisDataCheck.getExpireTime())){
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try {
                        R newResult = dbFallback.apply(id);
                        setWithLogicalExprie(queryKey,newResult,time,timeUnit);
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }finally {
                        delLock(lockKey);
                    }
                });
            }
        }

        return oldResult;
    }

    private boolean setLock(String lockKey){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void delLock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }

}
