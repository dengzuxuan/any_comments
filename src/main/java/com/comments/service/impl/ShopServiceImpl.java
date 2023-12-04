package com.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.comments.dto.Result;
import com.comments.entity.Shop;
import com.comments.mapper.ShopMapper;
import com.comments.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.comments.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getShopInfo(Long id) {
        if(id < 0){
            return Result.fail("输入有效商店id");
        }
        //使用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //使用逻辑过期解决缓存击穿
        Shop shop = queryWithLogistical(id);
        if(shop==null){
            return Result.fail("该商店不存在");
        }
        return Result.ok(shop);
    }

    private Shop queryWithPassThought(Long id){
        String shopKey = CACHE_SHOP_KEY + id;
        String shopInfoJson = stringRedisTemplate.opsForValue().get(shopKey);
        if(StrUtil.isNotBlank(shopInfoJson)){
            return JSONUtil.toBean(shopInfoJson, Shop.class);
        }

        if("".equals(shopInfoJson)){
            return null;
        }

        Shop shop = getById(id);
        if(shop == null){
            //防止缓存穿透 使用【缓存空对象解决】
            stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }
    public static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private Shop queryWithLogistical(Long id){
        String shopKey = CACHE_SHOP_KEY + id;
        String shopInfoJson = stringRedisTemplate.opsForValue().get(shopKey);
        if(StrUtil.isBlank(shopInfoJson)) {
            return null;
        }

        //数据不为空 为redisdata 其中有过期时间，需要判断是否过期，若过期则返回过期数据
        // 然后试图获取锁->新开线程重建缓存
        RedisData redisData = JSONUtil.toBean(shopInfoJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        if(LocalDateTime.now().isBefore(redisData.getExpireTime())){
            //未过期
            return shop;
        }
        //已过期
        String ShopLockKey = LOCK_SHOP_KEY+id;
        boolean isLock = setLock(ShopLockKey);
        if(isLock){
            //成功获取互斥锁 开启线程进行重建缓存
            //获取成功后需要做dobule check重新检测过期时间，避免其他线程已经重建完成了
            String shopInfoJsonCheck = stringRedisTemplate.opsForValue().get(shopKey);
            RedisData redisDataCheck = JSONUtil.toBean(shopInfoJsonCheck, RedisData.class);
            if(!LocalDateTime.now().isBefore(redisDataCheck.getExpireTime())){
                CACHE_REBUILD_EXECUTOR.submit(()->{
                    try {
                        setShopRedisData(id,50L);
                    }catch (Exception e){
                        throw new RuntimeException(e);
                    }finally {
                        delLock(ShopLockKey);
                    }
                });
            }
        }

        return shop;
    }
    private Shop queryWithMutex(Long id){
        String shopKey = CACHE_SHOP_KEY + id;
        String shopInfoJson = stringRedisTemplate.opsForValue().get(shopKey);
        if(StrUtil.isNotBlank(shopInfoJson)){
            return JSONUtil.toBean(shopInfoJson, Shop.class);
        }
        if("".equals(shopInfoJson)){
            return null;
        }
        Shop shop = null;
        String lockKey = LOCK_SHOP_KEY+id;
        try {
            //防止缓存击穿 使用【互斥锁进行缓存重建】
            if(!setLock(lockKey)){
                //如果没有获取到互斥锁，需要睡眠等待
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            shop = getById(id);
            if(shop == null){
                //防止缓存穿透 使用【缓存空对象解决】
                stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }finally {
            //释放互斥锁
            delLock(lockKey);
        }
        return shop;
    }
    @Override
    public Result updateShopInfo(Shop shop) {
        if(shop.getId() == null){
            return Result.fail("请输入有效商店");
        }
        //先更新
        updateById(shop);

        //后删除缓存
        String shopKey = CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(shopKey);
        return null;
    }

    private boolean setLock(String lockKey){
        Boolean b = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(b);
    }

    private void delLock(String lockKey){
        stringRedisTemplate.delete(lockKey);
    }

    //设置逻辑过期数据，防止缓存击穿
    public void setShopRedisData(long id,long deltaExpireTime){
        String shopKey = CACHE_SHOP_KEY + id;

        RedisData redisData = new RedisData();
        Shop shop = getById(id);
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(deltaExpireTime));

        //System.out.println("更新缓存:"+redisData.getExpireTime());
        //过期了，过期时间:2023-12-04T20:51:06.274
        //不设置过期时间
        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(redisData));
    }
}
