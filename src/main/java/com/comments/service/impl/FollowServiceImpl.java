package com.comments.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.Follow;
import com.comments.mapper.FollowMapper;
import com.comments.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.utils.UserHolder;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Override
    public Result followUser(Long followid, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return Result.fail("请先登录");
        }
        if(isFollow){
            //新增关注
            Follow newfollow = new Follow();
            newfollow.setFollowUserId(followid);
            newfollow.setUserId(user.getId());
            save(newfollow);
        }else{
            //取消关注
            remove(new QueryWrapper<Follow>().eq("user_id",user.getId()).eq("follow_user_id",followid));
        }
        return Result.ok();
    }

    @Override
    public Result checkFollowId(Long followid) {
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return Result.ok();
        }
        Integer count = query().eq("user_id", user.getId()).eq("follow_user_id", followid).count();
        return Result.ok(count>0);
    }
}
