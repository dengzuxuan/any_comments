package com.comments.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.comments.utils.RedisConstants.*;
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
    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result setcode(String phone, HttpSession session) {
        //1.判断手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("请输入正确手机号");
        }
        //2.生成验证码
        String checkcode = RandomUtil.randomNumbers(6);
        log.debug("验证码:"+checkcode);

        //3.验证码存入redis中
        String key = LOGIN_CODE_KEY + phone;
        stringRedisTemplate.opsForValue().set(key,checkcode,LOGIN_CODE_TTL, TimeUnit.MINUTES);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("请输入有效手机号");
        }

        String codeKey = LOGIN_CODE_KEY + loginForm.getPhone();
        String cachecode = stringRedisTemplate.opsForValue().get(codeKey);
        if(cachecode == null ||
                !cachecode.equals(loginForm.getCode())){
            return Result.fail("请输入正确验证码");
        }

        User findUser = query().eq("phone",loginForm.getPhone()).one();
        if(findUser == null){
            //新建
            findUser = createUserWithPhone(loginForm.getPhone());
        }

        //转换 减少内存&保护隐私
        UserDTO userDTO = BeanUtil.copyProperties(findUser, UserDTO.class);
        //放入redis的hashset中 key为token【即uuid】
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY+token;

        //将user反射为map 需要注意的是long类型id无法存入redis的set中，因此需要映射为string类型
        Map<String, Object> mapUser = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fileName, fileValue) -> fileValue.toString()));
        stringRedisTemplate.opsForHash().putAll(tokenKey,mapUser);
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL,TimeUnit.MINUTES);

        //从前端返回
        return Result.ok(token);
    }

    @Override
    public Result getmyInfo() {
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    //使用bitmap进行签到
    @Override
    public Result sign() {
        UserDTO user = UserHolder.getUser();
        LocalDateTime now = LocalDateTime.now();
        //key为 sign:userid:当前年:当前月
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String signKey = USER_SIGN_KEY+user.getId()+keySuffix;

        int signDay = now.getDayOfMonth() - 1;
        stringRedisTemplate.opsForValue().setBit(signKey,signDay,true);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User newUser= new User();
        newUser.setPhone(phone);
        newUser.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(6));
        save(newUser);
        return newUser;
    }
}
