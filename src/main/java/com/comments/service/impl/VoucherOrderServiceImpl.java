package com.comments.service.impl;

import cn.hutool.core.lang.UUID;
import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.SeckillVoucher;
import com.comments.entity.VoucherOrder;
import com.comments.mapper.VoucherOrderMapper;
import com.comments.service.ISeckillVoucherService;
import com.comments.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.utils.RedisIdWorker;
import com.comments.utils.RedissonConfig;
import com.comments.utils.SimpleRedisLock;
import com.comments.utils.UserHolder;
import lombok.Synchronized;
import lombok.extern.log4j.Log4j;
import lombok.extern.log4j.Log4j2;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Service
@Log4j2
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);
    private final static DefaultRedisScript<Long> ORDER_SCRIPT;//Long是返回值
    static {
        //动态加载unlock.lua内容
        ORDER_SCRIPT = new DefaultRedisScript<>();
        ORDER_SCRIPT.setLocation(new ClassPathResource("order.lua"));
        ORDER_SCRIPT.setResultType(Long.class);
    }
    //阻塞队列的特点是：当有线程从队列中获取元素时，如果队列中没有元素的话会阻塞，直到队列里有
    private BlockingQueue<VoucherOrder> orders = new ArrayBlockingQueue<>(1024*1024);
    //异步获取的线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    //在进入该类时初始化该线程池
    @PostConstruct
    private void init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    //异步获取阻塞队列然后保存到mysql的线程
    private class VoucherOrderHandler implements Runnable{
        @Override
        public void run() {
            //执行保存操作
            while(true){
                //不断轮询获取阻塞队列的待保存订单
                try {
                    VoucherOrder voucherOrder = orders.take();
                    saveVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("保存订单失败:"+e);
                }
            }
        }
    }

    private void saveVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
        Long userId = voucherOrder.getUserId();
        //采用redissonLock中集成的分布式锁 可以解决重入 重释等问题
        RLock redissonClientLock = redissonClient.getLock("lock:order:" + userId);
        boolean successFlag = redissonClientLock.tryLock(1,TimeUnit.MINUTES);
        if(!successFlag){
            //未成功获取锁
            log.error("一人仅可购买一个");
            return;
        }
        try {
            voucherOrderService.createOrder(voucherOrder);
        }finally {
            //redisLock.unLock();
            redissonClientLock.unlock();
        }
    }

    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    VoucherOrderServiceImpl voucherOrderService;
    @Autowired
    RedissonClient redissonClient;
    //生成秒杀订单
    @Override
    public Result seckillVoucher(Long voucherId)  {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //生成唯一订单id
        long orderId = redisIdWorker.getNextId("order");

        Long res = stringRedisTemplate.execute(
                ORDER_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        int resCode = res.intValue();
        if(resCode != 0){
            if(resCode == 1){
                return Result.fail("很抱歉，优惠券已被抢光~");
            }else if(resCode ==2){
                return Result.fail("每个用户只能购买一个");
            }
        }
        //抢单成功后，将订单信息保存入阻塞队列中，然后新开线程去处理阻塞队列的订单数据，将订单数据写入mysql里
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orders.add(voucherOrder);
        return Result.ok(orderId);
    }

    //下单
    @Override
    @Transactional
    public void createOrder(VoucherOrder voucherOrder) {
        //判定一人一单
        int userCount = query().
                eq("user_id", voucherOrder.getUserId()).
                eq("voucher_id",voucherOrder.getVoucherId()).count();
        if(userCount!=0){
            log.error("每个用户只能购买一个");
        }

        boolean successFlag = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherOrder.getVoucherId()).
                gt("stock",0). //添加乐观锁，确保stock是大于0的，防止超卖问题
                update();
        if(!successFlag){
           log.error("很抱歉，优惠券已被抢光~");
        }
        save(voucherOrder);
    }
//数据库IO的速度太慢 影响并发 所以拆分为抢单和下单
//    @Override
//    @Transactional
//    public Result getOrder(Long voucherId) {
//        //判定一人一单
//        int userCount = query().
//                eq("user_id", UserHolder.getUser().getId()).
//                eq("voucher_id",voucherId).count();
//        if(userCount!=0){
//            return Result.fail("每个用户只能购买一个");
//        }
//
//        boolean successFlag = seckillVoucherService.update().
//                setSql("stock = stock - 1").
//                eq("voucher_id", voucherId).
//                gt("stock",0). //添加乐观锁，确保stock是大于0的，防止超卖问题
//                        update();
//        if(!successFlag){
//            return Result.fail("很抱歉，优惠券已被抢光~");
//        }
//
//        //新建订单表
//        VoucherOrder voucherOrder = new VoucherOrder();
//        //生成唯一订单id
//        long orderId = redisIdWorker.getNextId("order");
//        voucherOrder.setId(orderId);
//
//        long userId = UserHolder.getUser().getId();
//        voucherOrder.setUserId(userId);
//
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        return Result.ok(orderId);
//    }
//@Override
//public Result seckillVoucher(Long voucherId) throws InterruptedException {
//    //查询秒杀券信息
//    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//    if(voucher == null){
//        return Result.fail("优惠券信息不存在");
//    }
//    //秒杀券时间是否有效
//    //秒杀券开始时间比当前时间靠后 未开始
//    if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
//        return Result.fail("秒杀活动未开始");
//    }
//    //秒杀券结束时间比开始时间靠前 已结束
//    if(voucher.getEndTime().isBefore(LocalDateTime.now())){
//        return Result.fail("秒杀活动已结束");
//    }
//    //秒杀券是否有
//    if(voucher.getStock() <= 0){
//        return Result.fail("很抱歉，优惠券已被抢光~");
//    }
//
//    Long userId = UserHolder.getUser().getId();
//        /*synchronized中获取互斥锁失败会一直等待锁的释放
//        synchronized (userId.toString().intern()){
//            return voucherOrderService.getOrder(voucherId);
//        }*/
//
//    //采用redis分布式锁 以解决不同进程间上锁问题
//    //SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate,"order:" + userId);
//    //boolean successFlag = redisLock.setLock(5);
//
//    //采用redissonLock中集成的分布式锁 可以解决重入 重释等问题
//    RLock redissonClientLock = redissonClient.getLock("order:" + userId);
//    boolean successFlag = redissonClientLock.tryLock(1,TimeUnit.MINUTES);
//    if(!successFlag){
//        //未成功获取锁
//        return Result.fail("一人仅可购买一个");
//    }
//    try {
//        return voucherOrderService.createOrder(voucherId);
//    }finally {
//        //redisLock.unLock();
//        redissonClientLock.unlock();
//    }
//}
}
