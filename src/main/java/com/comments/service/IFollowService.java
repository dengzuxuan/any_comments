package com.comments.service;

import com.comments.dto.Result;
import com.comments.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
public interface IFollowService extends IService<Follow> {

    Result followUser(Long followid, Boolean isFollow);

    Result checkFollowId(Long followid);

    Result commonFollowInfo(Long userid);
}
