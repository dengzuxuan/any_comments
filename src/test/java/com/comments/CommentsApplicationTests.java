package com.comments;

import com.comments.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class CommentsApplicationTests {
    @Resource
    ShopServiceImpl service;

    @Test
    void testSaveShop() {
        service.setShopRedisData(10L,10L);
    }

}
