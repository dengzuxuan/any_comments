package com.comments.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.Blog;
import com.comments.entity.User;
import com.comments.service.IBlogService;
import com.comments.service.IUserService;
import com.comments.utils.SystemConstants;
import com.comments.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {

        // 返回id
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.updateBlogLike(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikeLists(@PathVariable("id") Long id) {
        return blogService.queryBlogLikeLists(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlogs(current);
    }

    @GetMapping("/{id}")
    public Result querySingleBlog(@PathVariable("id") Long id) {
        // 根据用户查询
        return blogService.querySingleBlog(id);
    }

    // BlogController
    @GetMapping("/of/user")
    public Result queryBlogByUserId(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam("id") Long id) {
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", id).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }
    //给两个参数 一个是max【上一次查询的最大的时间戳】,一个是offset【上一次查询和最大时间戳相同的blog的数量】
    @GetMapping("/of/follow")
    public Result queryFollowBlogInfo(@RequestParam("lastId") Long lastId,@RequestParam(value = "offset",defaultValue = "0") Integer offset){
        return blogService.queryFollowBlogInfo(lastId,offset);
    }
}
