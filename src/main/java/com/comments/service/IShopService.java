package com.comments.service;

import com.comments.dto.Result;
import com.comments.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
public interface IShopService extends IService<Shop> {

    Result getShopInfo(Long id);

    Result updateShopInfo(Shop shop);

    Result queryShopType(Integer typeId, Integer current, Double x, Double y);
}
