package com.comments.service.impl;

import com.comments.dto.Result;
import com.comments.dto.UserDTO;
import com.comments.entity.SeckillVoucher;
import com.comments.entity.VoucherOrder;
import com.comments.mapper.VoucherOrderMapper;
import com.comments.service.ISeckillVoucherService;
import com.comments.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.comments.utils.RedisIdWorker;
import com.comments.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@Service
@Transactional
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;
    @Resource
    RedisIdWorker redisIdWorker;
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

        boolean successFlag = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
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
