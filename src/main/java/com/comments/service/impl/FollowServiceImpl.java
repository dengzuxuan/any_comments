package com.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.Follow;
import com.comments.entity.User;
import com.comments.mapper.FollowMapper;
import com.comments.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Autowired
    UserServiceImpl userService;

    @Override
    public Result followUser(Long followid, Boolean isFollow) {
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return Result.fail("请先登录");
        }
        String followKey = "follow:"+user.getId();
        if(isFollow){
            //新增关注
            Follow newfollow = new Follow();
            newfollow.setFollowUserId(followid);
            newfollow.setUserId(user.getId());
            boolean isSuccess = save(newfollow);
            if(isSuccess){
                //写入redis的sort中 用于后续共同关注
                stringRedisTemplate.opsForSet().add(followKey, String.valueOf(followid));
            }
        }else{
            //取消关注
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", user.getId()).eq("follow_user_id", followid));
            if(isSuccess){
                stringRedisTemplate.opsForSet().remove(followKey, String.valueOf(followid));
            }
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

    @Override
    public Result commonFollowInfo(Long userid) {
        UserDTO user = UserHolder.getUser();
        if(user==null){
            return Result.ok();
        }
        String loginUserKey = "follow:"+user.getId();
        String visitUserKey = "follow:"+userid;

        Set<String> commonFollowSets = stringRedisTemplate.opsForSet().intersect(loginUserKey, visitUserKey);
        if(commonFollowSets == null || commonFollowSets.isEmpty()){
            return Result.ok(Collections.emptyList());
        } 
        List<Long> commonFollowInts = commonFollowSets.stream().map(id -> Long.valueOf(id)).collect(Collectors.toList());
        List<UserDTO> commonFollowUsers = userService.listByIds(commonFollowInts).stream().map(User -> BeanUtil.copyProperties(User, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(commonFollowUsers);
    }
}
