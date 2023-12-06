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
import com.comments.utils.SimpleRedisLock;
import com.comments.utils.UserHolder;
import lombok.Synchronized;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true);

    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    RedisIdWorker redisIdWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    VoucherOrderServiceImpl voucherOrderService;
    //生成秒杀订单
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询秒杀券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if(voucher == null){
            return Result.fail("优惠券信息不存在");
        }
        //秒杀券时间是否有效
        //秒杀券开始时间比当前时间靠后 未开始
        if(voucher.getBeginTime().isAfter(LocalDateTime.now())){
            return Result.fail("秒杀活动未开始");
        }
        //秒杀券结束时间比开始时间靠前 已结束
        if(voucher.getEndTime().isBefore(LocalDateTime.now())){
            return Result.fail("秒杀活动已结束");
        }
        //秒杀券是否有
        if(voucher.getStock() <= 0){
            return Result.fail("很抱歉，优惠券已被抢光~");
        }

        Long userId = UserHolder.getUser().getId();
        /*synchronized中获取互斥锁失败会一直等待锁的释放
        synchronized (userId.toString().intern()){
            return voucherOrderService.getOrder(voucherId);
        }*/

        //采用redis分布式锁 以解决不同进程间上锁问题
        SimpleRedisLock redisLock = new SimpleRedisLock(stringRedisTemplate,"order:" + userId);
        //Boolean successFlag = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + "order", ID_PREFIX+"_"+ Thread.currentThread().getId(), 5, TimeUnit.SECONDS);
        boolean successFlag = redisLock.setLock(5);
        if(!successFlag){
            //未成功获取锁
            return Result.fail("一人仅可购买一个");
        }
        try {
            return voucherOrderService.getOrder(voucherId);
        }finally {
            stringRedisTemplate.delete(LOCK_PREFIX + "order");
        }
    }

    @Override
    @Transactional
    public Result getOrder(Long voucherId) {
        //判定一人一单
        int userCount = query().
                eq("user_id", UserHolder.getUser().getId()).
                eq("voucher_id",voucherId).count();
        if(userCount!=0){
            return Result.fail("每个用户只能购买一个");
        }

        boolean successFlag = seckillVoucherService.update().
                setSql("stock = stock - 1").
                eq("voucher_id", voucherId).
                gt("stock",0). //添加乐观锁，确保stock是大于0的，防止超卖问题
                        update();
        if(!successFlag){
            return Result.fail("很抱歉，优惠券已被抢光~");
        }

        //新建订单表
        VoucherOrder voucherOrder = new VoucherOrder();
        //生成唯一订单id
        long orderId = redisIdWorker.getNextId("order");
        voucherOrder.setId(orderId);

        long userId = UserHolder.getUser().getId();
        voucherOrder.setUserId(userId);

        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
