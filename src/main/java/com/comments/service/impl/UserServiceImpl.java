package com.comments.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.dto.LoginFormDTO;
import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.User;
import com.comments.mapper.UserMapper;
import com.comments.service.IUserService;
import com.comments.utils.RegexUtils;
import com.comments.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.comments.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result setcode(String phone, HttpSession session) {
        //1.判断手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("请输入正确手机号");
        }
        //2.生成验证码
        String checkcode = RandomUtil.randomNumbers(6);
        log.debug("验证码:"+checkcode);

        //3.验证码存入session
        session.setAttribute("checkcode",checkcode);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("请输入有效手机号");
        }

        Object cachecode = session.getAttribute("checkcode");
        if(cachecode == null ||
                !cachecode.equals(loginForm.getCode())){
            return Result.fail("请输入正确验证码");
        }

        User findUser = query().eq("phone",loginForm.getPhone()).one();
        if(findUser == null){
            //新建
            findUser = createUserWithPhone(loginForm.getPhone());
        }
        session.setAttribute("user",findUser);
        return Result.ok();
    }

    @Override
    public Result getmyInfo() {
        User user = UserHolder.getUser();
        return Result.ok(user);
    }

    private User createUserWithPhone(String phone) {
        User newUser= new User();
        newUser.setPhone(phone);
        newUser.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(6));
        save(newUser);
        return newUser;
    }
}
