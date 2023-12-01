package com.comments.mapper;

import com.comments.entity.BlogComments;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Mapper
public interface BlogCommentsMapper extends BaseMapper<BlogComments> {

}
