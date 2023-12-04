package com.comments.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.comments.dto.Result;
import com.comments.entity.Shop;
import com.comments.mapper.ShopMapper;
import com.comments.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

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
        String shopKey = CACHE_SHOP_KEY + id;
        String shopInfoJson = stringRedisTemplate.opsForValue().get(shopKey);
        if(StrUtil.isNotBlank(shopInfoJson)){
            Shop shop = JSONUtil.toBean(shopInfoJson, Shop.class);
            return Result.ok(shop);
        }

        if("".equals(shopInfoJson)){
            return Result.fail("该商店不存在");
        }

        Shop shop = getById(id);
        if(shop == null){
            //防止缓存穿透 使用【缓存空对象解决】
            stringRedisTemplate.opsForValue().set(shopKey,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return Result.fail("该商店不存在");
        }
        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shop);
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
}
