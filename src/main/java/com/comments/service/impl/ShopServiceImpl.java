package com.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comments.dto.Result;
import com.comments.entity.Blog;
import com.comments.entity.Shop;
import com.comments.mapper.ShopMapper;
import com.comments.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.utils.CacheClient;
import com.comments.utils.RedisData;
import com.comments.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.beans.Transient;
import java.time.LocalDateTime;
import java.util.*;
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
    @Resource
    CacheClient cacheClient;

    @Override
    public Result getShopInfo(Long id) {
        if(id < 0){
            return Result.fail("输入有效商店id");
        }

        //使用缓存空对象缓存穿透
        Shop shop = cacheClient.queryWithPassThroght(CACHE_SHOP_KEY, id, Shop.class, iddb -> getById(iddb), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //使用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //使用逻辑过期解决缓存击穿
        //Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,iddb->getById(iddb),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        if(shop==null){
            return Result.fail("该商店不存在");
        }
        return Result.ok(shop);
    }

    private Shop queryWithPassThought(Long id){
        String shopKey = CACHE_SHOP_KEY + id;
        String shopInfoJson = stringRedisTemplate.opsForValue().get(shopKey);
        //isNotBlank在null、""、"\t\n"的情况时返回false
        if(StrUtil.isNotBlank(shopInfoJson)){
            //存在缓存直接返回
            return JSONUtil.toBean(shopInfoJson, Shop.class);
        }

        //json数据不为null 那么只能是 "" 是命中了空值 这时候限制缓存穿透直接返回
        if(shopInfoJson!=null){
            return null;
        }

        //排除下来此时json只能为null，即没有缓存，可以加载缓存了
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
    //加上事务 保证cache aside更新db+删除缓存的原子性
    @Transactional
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

    @Override
    public Result queryShopType(Integer typeId, Integer current, Double x, Double y) {
        // 根据类型分页查询
        //没有传入精度，代表不需要geo判断
        if(x == null || y == null){
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }

        //获取分页码
        int start = (current-1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //从redisgeo中取出当前类型，当前 x,y 附近5km的商家
        String geoKey = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> geoResults = stringRedisTemplate.opsForGeo().search(
                geoKey,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().limit(end)
        );
        if(geoResults == null){
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoResultsContent = geoResults.getContent();
        if(geoResultsContent.size() <= start){
            return Result.ok(Collections.emptyList());
        }

        //截取 start ~ end部分
        List<Long> shopIds = new ArrayList<>();
        Map<String,Distance> idToDistance = new HashMap<>();
        geoResultsContent.stream().skip(start).forEach(result->{
            String shopId = result.getContent().getName();
            shopIds.add(Long.valueOf(shopId));
            idToDistance.put(shopId,result.getDistance());
        });

        String blogSql = StrUtil.join(",",shopIds);
        //根据id解析出来商家信息
        List<Shop> shopList = query().in("id", shopIds).
                last("ORDER BY FIELD(id," + blogSql + ")").list();
        //加上对应的geo信息
        for (Shop shop : shopList) {
            shop.setDistance(idToDistance.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shopList);
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
