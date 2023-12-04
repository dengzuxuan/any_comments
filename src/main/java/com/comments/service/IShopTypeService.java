package com.comments.service;

import com.comments.dto.Result;
import com.comments.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
public interface IShopTypeService extends IService<ShopType> {

    Result getTypeInfo();
}
