package com.comments.service.impl;

import cn.hutool.json.JSONUtil;
import com.comments.dto.Result;
import com.comments.entity.Shop;
import com.comments.mapper.ShopMapper;
import com.comments.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.comments.utils.RedisConstants.CACHE_SHOP_KEY;

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
        if(id <= 0){
            return Result.fail("输入有效商店id");
        }
        String shopKey = CACHE_SHOP_KEY + id;
        String shopInfoJson = stringRedisTemplate.opsForValue().get(shopKey);
        if(shopInfoJson!=null){
            Shop shop = JSONUtil.toBean(shopInfoJson, Shop.class);
            return Result.ok(shop);
        }

        Shop shop = getById(id);
        if(shop == null){
            return Result.fail("该商店不存在");
        }
        stringRedisTemplate.opsForValue().set(shopKey,JSONUtil.toJsonStr(shop));
        return Result.ok(shop);
    }
}
