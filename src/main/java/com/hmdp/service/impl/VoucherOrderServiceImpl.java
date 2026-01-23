package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.SimpleRedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }


    @Override
    public Result seckill(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");
        // 1.执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId)
        );
        int r = result.intValue();
        // 2.判断结果是否为0
        if (r != 0) {
            // 2.1.不为0 ，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }
        //TODO 保存阻塞队列
        // 3.返回订单id
        return Result.ok(orderId);
    }

    /*
    @Override
    public Result seckill(Long seckillId) {

        //获取秒杀券的信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(seckillId);

        //判断秒杀是否开始
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            //尚未开始
            return Result.fail("秒杀尚未开始");
        }
        //判断秒杀是否结束
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            //已经结束了
            return Result.fail("秒杀已经结束");
        }

        //判断库存是否充足
        if(seckillVoucher.getStock() < 1){
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        //redis+lua脚本实现的分布式锁
        //SimpleRedisLock simpleRedisLock = new SimpleRedisLock("order:" + userId,stringRedisTemplate);

        //Redisson实现的分布式锁
        RLock lock = redissonClient.getLock("order:" + userId);
        boolean isLock = lock.tryLock();
        if(!isLock){
            return Result.fail("不允许重复下单");
        }
        try{
            //获取代理对象（事务）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(seckillId);
        } finally{
            lock.unlock();
        }
    }
    */

    @Transactional
    public Result createVoucherOrder(Long seckillId){

        //一人一单
        Long userId = UserHolder.getUser().getId();
        //查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", seckillId).count();
        //判断订单是否存在
        if(count > 0){
            //用户已经购买过了
            return Result.fail("当前用户已经购买过一次!");
        }

        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id",seckillId)
                .gt("stock",0)  //乐观锁的CAS方案解决超卖问题 where stock > 0
                .update();

        if(!success){
            //扣减失败
            return Result.fail("库存扣减失败");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(seckillId);
        voucherOrder.setId(redisIdWorker.nextId("order"));
        save(voucherOrder);

        return Result.ok(voucherOrder.getId());
    }
}
