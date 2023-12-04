package com.comments;

import com.comments.entity.Shop;
import com.comments.service.impl.ShopServiceImpl;
import com.comments.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.comments.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class CommentsApplicationTests {
    @Resource
    ShopServiceImpl service;
    @Resource
    CacheClient cacheClient;
    @Resource
    ShopServiceImpl shopService;
    @Test
    void testSaveShop() {
        Shop shop = shopService.getById(10);
        cacheClient.setWithLogicalExprie(CACHE_SHOP_KEY+10,shop,10L, TimeUnit.SECONDS);
    }

}
