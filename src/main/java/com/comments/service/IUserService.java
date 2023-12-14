package com.comments.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.comments.dto.LoginFormDTO;
import com.comments.dto.Result;
import com.comments.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
public interface IUserService extends IService<User> {

    Result setcode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result getmyInfo();

    Result sign();
}
