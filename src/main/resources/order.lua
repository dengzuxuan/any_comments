-- 这个lua目的是判断该用户能否抢到优惠券 可以 0
-- 1.根据优惠券id查看redis中有没有库存 没有库存 1
-- 2.根据优惠券id查看reids中该订单的下单用户里set有没有该用户 已下单 2
-- 3.有库存，没下单的话 先扣减库存，然后将该用户放到本订单的下单用户set中
-- 4.然后将用户id 优惠券id 订单id放入消息队列里
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order' .. voucherId

--判断库存是否充足
if(tonumber(redis.call('get',stockKey)) <= 0) then
    return 1
end

--判断是否下单 redis的set使用 sismember 来判断是否存在set中
if(redis.call('sismember',orderKey,userId) == 1) then
    return 2
end

--扣减库存 使用incrby加减数据
redis.call('incrby',stockKey,-1)

--下单 保存用户到下单用户set中 set中使用sadd增加
redis.call('sadd',orderKey,userId)
--发送到消息队列
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
return 0