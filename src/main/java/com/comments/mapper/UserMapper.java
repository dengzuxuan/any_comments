package com.comments.mapper;

import com.comments.entity.User;
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
public interface UserMapper extends BaseMapper<User> {

}
