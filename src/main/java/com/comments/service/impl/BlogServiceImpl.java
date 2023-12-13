package com.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.Blog;
import com.comments.entity.Follow;
import com.comments.entity.User;
import com.comments.mapper.BlogMapper;
import com.comments.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.service.IFollowService;
import com.comments.service.IUserService;
import com.comments.utils.SystemConstants;
import com.comments.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.comments.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.comments.utils.RedisConstants.FEED_KEY;

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
    @Resource
    private IFollowService followService;
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
        if(user == null){
            return Result.fail("需要先登录~");
        }
        String blogKey = BLOG_LIKED_KEY+id;
        //首先在redis中判断用户是否点赞
        Double score = stringRedisTemplate.opsForZSet().score(blogKey, String.valueOf(user.getId()));
        if(score == null){
            //没点赞则点赞
            boolean isAddSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if(isAddSuccess){
                stringRedisTemplate.opsForZSet().add(blogKey, String.valueOf(user.getId()),System.currentTimeMillis());
            }
        }else{
            //点赞则取消
            boolean isAddSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if(isAddSuccess){
                stringRedisTemplate.opsForZSet().remove(blogKey, String.valueOf(user.getId()));
            }
        }
        return Result.ok();
    }

    //获取有序点赞列表
    @Override
    public Result queryBlogLikeLists(Long id) { 
        String blogKey = BLOG_LIKED_KEY+id;
        Set<String> top5IdSet = stringRedisTemplate.opsForZSet().range(blogKey, 0, 5);
        if(top5IdSet == null || top5IdSet.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        
        //将string类型的id转为int类型
        List<Long> top5IdInt = top5IdSet.stream().map(idSet -> Long.valueOf(idSet)).collect(Collectors.toList());

        //从user表中获取到id对应的user信息 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        //由于mybatisplus不支持ORDER BY FIELD(id, 5, 1)写法，所以要自己写sql拼接
        String orderSql = StrUtil.join(",",top5IdSet);
        List<UserDTO> top5User = userService.query().
                in("id", top5IdInt).
                last("ORDER BY FIELD(id,"+orderSql+")").list().
                stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).
                collect(Collectors.toList());
        return Result.ok(top5User);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if(isSuccess){
            //保存成功后，写入个该用户粉丝的收件箱中【redis】
            List<Follow> followUserInfo = followService.query().eq("follow_user_id", user.getId()).list();
            for(Follow follow:followUserInfo){
                String followKey = FEED_KEY + follow.getUserId();
                stringRedisTemplate.opsForZSet().add(followKey, String.valueOf(blog.getId()),System.currentTimeMillis());
            }
        }
        return Result.ok(blog.getId());
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
            Double score = stringRedisTemplate.opsForZSet().score(blogKey, String.valueOf(userlogin.getId()));
            if(score!=null){
                blog.setIsLike(true);
            } 
        }
    }
}
