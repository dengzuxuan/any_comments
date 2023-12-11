package com.comments.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comments.dto.Result;
import com.comments.entity.Blog;
import com.comments.entity.User;
import com.comments.mapper.BlogMapper;
import com.comments.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.service.IUserService;
import com.comments.utils.SystemConstants;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

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

    private void setBlogUserInfo(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
