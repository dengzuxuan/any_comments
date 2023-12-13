package com.comments.service;

import com.comments.dto.Result;
import com.comments.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
public interface IBlogService extends IService<Blog> {

    Result querySingleBlog(Long id);

    Result queryHotBlogs(int current);

    Result updateBlogLike(Long id);

    Result queryBlogLikeLists(Long id);

    Result saveBlog(Blog blog);
}
