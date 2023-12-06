package com.comments.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.comments.dto.Result;
import com.comments.entity.ShopType;
import com.comments.mapper.ShopTypeMapper;
import com.comments.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static com.comments.utils.RedisConstants.CACHE_SHOP_LIST_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result getTypeInfo() {
        // 1. 从redis中查询商铺类型列表
        String jsonArray = stringRedisTemplate.opsForValue().get(CACHE_SHOP_LIST_KEY);
        // json转list
        List<ShopType> jsonList = JSONUtil.toList(jsonArray,ShopType.class);
        // 2. 命中，返回redis中商铺类型信息
        if (!CollectionUtils.isEmpty(jsonList)) {
            return Result.ok(jsonList);
        }
        // 3. 未命中，从数据库中查询商铺类型,并根据sort排序
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 4. 将商铺类型存入到redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_LIST_KEY,JSONUtil.toJsonStr(typeList));
        // 5. 返回数据库中商铺类型信息
        return Result.ok(typeList);
    }
}
