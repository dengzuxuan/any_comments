package com.comments;

import com.comments.entity.Shop;
import com.comments.service.IUserService;
import com.comments.service.impl.ShopServiceImpl;
import com.comments.utils.CacheClient;
import com.comments.utils.RedisIdWorker;
import com.comments.utils.SimpleRedisLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.comments.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class CommentsApplicationTests {
    @Resource
    private IUserService userService;
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Resource
    ShopServiceImpl service;
    @Resource
    CacheClient cacheClient;
    @Resource
    ShopServiceImpl shopService;
    @Resource
    RedisIdWorker redisIdWorker;
    private ExecutorService executorService = Executors.newFixedThreadPool(300);
    @Test
    void testSetNX(){
      SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate,"order:1");
       boolean successFlag = redisLock.setLock(5);
        //stringRedisTemplate.opsForValue().setIfAbsent("test","value1",5,TimeUnit.MINUTES);
    }
    @Test
    void testSaveShop() {
        Shop shop = shopService.getById(10);
        cacheClient.setWithLogicalExprie(CACHE_SHOP_KEY+10,shop,10L, TimeUnit.SECONDS);
    }
    @Test
    void testId() throws InterruptedException {
        // CountDownLatch用于阻塞主线程&计数
        // 调用CountDownLatch的await是可以阻塞主线程
        // 当一个线程结束后调用CountDownLatch的countDowncount可以减一
        // 当减到0后可以结束阻塞

        // ExecutorService executorService = Executors.newFixedThreadPool(300)用于创建线程池 有300个线程
        // executorService.submit(task) 用于从线程池中提取线程并提交任务
        // task为Runnable类型，可以用函数式编程解决

        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()->{
            for (int i = 0; i < 100; i++) {
                long nextId = redisIdWorker.getNextId("order");
                System.out.println("nextId = " + nextId);
            }
            latch.countDown();
        };

        long start = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            executorService.submit(task);
        }
        latch.await();
        long delta = System.currentTimeMillis() - start;
        System.out.println("delta = " + delta);
    }
}
