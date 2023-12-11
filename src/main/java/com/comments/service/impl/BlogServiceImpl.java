package com.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.Blog;
import com.comments.entity.User;
import com.comments.mapper.BlogMapper;
import com.comments.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.service.IUserService;
import com.comments.utils.SystemConstants;
import com.comments.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.comments.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;
    @Override
    public Result querySingleBlog(Long id) {
        Blog blog = getById(id);
        setBlogUserInfo(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlogs(int current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(this::setBlogUserInfo);
        return Result.ok(records);
    }

    @Override
    public Result updateBlogLike(Long id) {
        //获取用户
        UserDTO user = UserHolder.getUser();
        String blogKey = BLOG_LIKED_KEY+id;
        //首先在redis中判断用户是否点赞
        Boolean isLike = stringRedisTemplate.opsForSet().isMember(blogKey, String.valueOf(user.getId()));
        if(BooleanUtil.isFalse(isLike)){
            //没点赞则点赞
            boolean isAddSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isAddSuccess){
                stringRedisTemplate.opsForSet().add(blogKey, String.valueOf(user.getId()));
            }
        }else{
            //点赞则取消
            boolean isAddSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isAddSuccess){
                stringRedisTemplate.opsForSet().remove(blogKey, String.valueOf(user.getId()));
            }
        }
        return Result.ok();
    }

    private void setBlogUserInfo(Blog blog){
        //设置用户信息
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());

        //设置点赞信息
        UserDTO userlogin = UserHolder.getUser();
        if(userlogin!=null){
            String blogKey = BLOG_LIKED_KEY+blog.getId();
            Boolean isMember = stringRedisTemplate.opsForSet().isMember(blogKey, String.valueOf(userlogin.getId()));
            blog.setIsLike(BooleanUtil.isTrue(isMember));
        }
    }
}
