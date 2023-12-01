package com.comments.utils;


import cn.hutool.core.bean.BeanUtil;
import com.comments.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.comments.utils.RedisConstants.LOGIN_USER_KEY;
import static com.comments.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * <p>
 *  TODO
 * </p>
 *
 * @author Colin
 * @since 2023/12/1
 */
public class RefreshInterceptor implements HandlerInterceptor {

    // 由于该类型不是spring托管，因此无法注入redisTemplate 只能手动构造
    StringRedisTemplate stringRedisTemplate;
    public RefreshInterceptor(StringRedisTemplate upredisTemplate) {
        stringRedisTemplate = upredisTemplate;
    }

    //请求前
    //1.更新redis中token的过期时间
    //2.获取user
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("authorization");
        if(token==null){
            return true;
        }

        String tokenKey = LOGIN_USER_KEY+token;
        Map<Object, Object> loginUserMap = stringRedisTemplate.opsForHash().entries(tokenKey);
        if(loginUserMap.isEmpty()){
            return true;
        }
        UserDTO loginUser = BeanUtil.fillBeanWithMap(loginUserMap, new UserDTO(), false);
        UserHolder.saveUser(loginUser);

        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
