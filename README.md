### 点评类项目
##### 核心：Redis
核心要点：
1. 登陆模块
- 集群登陆问题-session解决
![1653319474181](/pic/1653319474181.png)
- 拦截器优化-双层拦截器
   ![1653320764547](/pic/1653320764547.png)
2. 商品模块
- 数据缓存
  ![1653322097736](/pic/1653322097736.png)
- 内存更新策略  
cache aside，先操作db 再操作cache
  ![1653323595206](/pic/1653323595206.png)
- 缓存穿透  
采用缓存空对象的方式解决缓存穿透，也可以使用布隆过滤器
  ![1653327124561](/pic/1653327124561.png)
- 缓存雪崩  
降级限流/ttl添加随机值/多级缓存/redis集群
- 缓存击穿  
互斥锁解决缓存重建【低性能 高可用】  
  ![1653328288627](/pic/1653328288627.png)

逻辑过期开线程解决【低可用 高性能】
![1653328663897](/pic/1653328663897.png)
3. 秒杀模块
 - 生成唯一id  
第一位是符号位，2~32是时间戳，32~64是以当天作key的redis自增value
   ![1653363172079](/pic/1653363172079.png)
 - 基本逻辑  
   ![1653366238564](/pic/1653366238564.png)
 - 超卖问题  
多线程下会产生安全问题，多线程访问共享资源，悲观锁是确保线程串行，客观锁可以分为CAS法以及版本号法，核心是对比。悲观锁性能差，乐观锁成功率低。
   ![1653368562591](/pic/1653368562591.png)
 - 一个一单问题  
多线程下一个人可能会同时下多个订单，这时候需要对user_id添加悲观锁synchronized 来解决，尽可能的缩小锁的粒度。然后需要添加上Transactional用来实现事务，在事务中需要使用动态代理来生效，需要获得原始的事务对象。  
eg:aaa()上@Transactional，bbb()上没有，bbb()中调用aaa()，此时会发生：由于Spring的事务是通过AOP来实现的，只有通过代理对象调用@Transactional注解的对象方法时，事务才会生效，也就是直接调用aaa()方法事务才会生效，调用bbb()方法后间接调用aaa()方法事务是不会生效的。因为这次调用并不是通过代理对象来实现的。解决方法是将aaa()方法重新写为一个类，然后在bbb中@Autowired注入aaa，然后在bbb中调用aaa就可以。
   ![1653371854389](/pic/1653371854389.png)
 - 集群下的一人一单问题  
   synchronized锁只能对同一JVM进程下的**多线程**进行上锁从而实现串行化，但是无法对**不同JVM进程**之间进行上锁，因为每个JVM进程都会有一个自己的锁监视器。因此需要一个独立的锁监视器线程。也就是分布式锁。  
分布式锁是满足分布式系统或集群模式下多进程可见并且互斥的锁。
![1653374296906](/pic/1653374296906.png)
 - 分布式锁误删问题  
当线程1获取到redis锁后产生了业务阻塞，当其获取到的分布式锁超时释放后线程2获取到了锁，然而当线程1阻塞的业务结束后会释放锁，会将线程2的锁释放掉，进而其他线程又可以获取到这个锁了，因此需要存入线程标识，确保锁的唯一。使用UUID来标识，因为UUID可以区分不同的JVM进程标识。
   ![1653385920025](/pic/1653385920025.png)
 - 分布式锁原子性问题  
由于判断锁标识与释放锁之间有可能产生阻塞，进而导致其他线程获取到锁，因此需要使用Lua脚本确保这两个动作的原子性。
   ![1653387764938](/pic/1653387764938.png)
 - 分布式锁会有不可重入，不可重试，超时释放以及主从一致性问题  
因此企业下会直接采用Redission来实现分布式锁。不可重入是指同一个线程无法多次获取同一把锁，简单的说就是一个线程中连续两(多)次获取锁就叫做重入，如果判定为统一线程那么都可以获取这个锁。不可重试是指获取锁只尝试了一次就返回false，没有重试机制。超时释放是有可能业务还没有结束redis key的ttl就到了，主从一致性是在redis集群下主从同步存在延迟，若主宕机了，主上的锁可能还没有同步到从上。
   ![1653546070602](/pic/1653546070602.png)    
   1. Redission中解决不可重入问题：  
   本质上是使用Hash结构来解决的，为了解决同一线程获取同一把锁，采用的是rua脚本来判断线程标识是否一致，如果一致那么在hash的value中+1，该value标识的是重入次数。  
   在解锁时同样是rua脚本来进行的，首先判断标识是否一致【即锁是否是自己的】， 说的话就使用hincrby将value锁计数-1，即减少重入次数，当重入次数为0的时候代表释放锁。
   2. 解决不可重试问题：  
   采用信号量+PubSub功能实现等待、唤醒、获取锁失败的重试机制。在获取锁等待其间订阅信号量，当锁释放后会发送信号量，被接受到后唤醒该锁，不会过多占用cpu
   3. 解决超时释放问题： 
   利用watchDog机制，每间隔一段时间重置锁的超时时间，实现自动续约，直到业务逻辑结束，释放锁。
   4. 解决主从一致性问题  
   在分布式锁下，当加上锁的主机宕机后可能没有及时同步给从机，解决方案是向集群中每一个机器都分配锁，获取锁是否失效时需要检测每一个机器是否失效。【当我们去设置了多个锁时，redission会将多个锁添加到一个集合中，然后用while循环去不停去尝试拿锁，但是会有一个总共的加锁时间，这个时间是用需要加锁的个数 * 1500ms ，假设有3个锁，那么时间就是4500ms，假设在这4500ms内，所有的锁都加锁成功， 那么此时才算是加锁成功，如果在4500ms有线程加锁失败，则会再次去进行重试.】
4. 优化秒杀模块  
将购买阶段拆分为抢单阶段和下单阶段
  - 异步秒杀  
将检测购买资格放入redis中使用lua脚本判断，将购买操作【db操作 较耗时】拆分一个线程异步执行，将所有的订单信息放入队列中。拆分之前1000次用户压测购买优惠券的平均值为450ms，拆分后平均值为176ms
    ![1653562234886](/pic/1653562234886.png)
  - 采用redis消费者组实现下单队列  
由于阻塞对列占用java内存，数据不安全等问题，需要使用redis，redis实现消息队列有以下几种方式：  
基于List结构，使用LPUSH RPOP，优点是利用Redis，不受限于JVM内存上限，Redis可以持久化存储，数据安全性有保证并且可以满足消息有序性。问题是无法避免消息丢失，只能支持单消费者  
基于PubSub结构，使用SUBSCRIBE channel 订阅， PUBLISH channel msg发送消息。支持多生产多消费，缺点是不支持数据持久化，无法避免数据丢失，消息堆积存在上限   
基于Stream的消息队列，使用XADD key extry[field value]发送，使用XREAD[COUNT] 是否阻塞 key 起始id读取。但是有消息漏读的风险，在处理一条消息的过程中有超过1条消息到达队列，下次获取消息时只能获得最新的一条，会出现漏读消息的问题。  
基于**消费者组**的Stream消息队列
    ![1653577801668](/pic/1653577801668.png)  
    ![img](/pic/img_1.png)
发送消息
    ![1653577301737](/pic/1653577301737.png)  
读取消息
    ![img](/pic/img.png)
  - 总结优化秒杀：  
由于秒杀中设计对数据库的IO操作。需要减少库存，增加订单等等操作，会影响并发的吞吐量以及反应时间，因此将秒杀操作拆分为“秒”和“杀”，就是分成抢单和下单操作。 **使用jmter 1000个不同用户同时请求，平均耗时由497s->106ms.吞吐量为1006次/秒->1577/秒**  
抢单时采用lua脚本判定1.是否有库存 2.是否已经买过了 达成redis的原子性，并且在redis中进行库存的扣减，然后将订单信息写入stream消息队列中，然后直接返回订单id号  
下单时采用stream消息队列，输入端在抢单的lua脚本中，输出端采用多线程方式，轮询该消息队列是否有新订单产生，如果有则获取，进入保存阶段，成功则发送ack，失败则进入padiing list阶段。  
在保存订单阶段先采用redissonClient的分布式锁，防止集群下多jvm的多人的抢占问题可以有效解决解决重入 重释等问题，然后采用乐观锁防止一人多单以及超卖问题，来进行兜底。
5. Feed流模块  
动态feed流推送方式分为三种，推模式，拉取模式以及推拉结合模式
   ![img](/pic/img_2.png)
采用推模式，用户每发表一篇笔记就将该笔记的id放入该用户粉丝的【收件箱】中，基于sorted set实现的，score为时间戳。此外，在获取关注动态时，采用滚动分页方法，就是每次都要传入max，offset，根据这两个值进行分页查询。从sorted set中使用rangexxxg查找比max值小的过offset的blog信息。
6. 签到模块  
使用bitmap实现签到功能，redis中bitmap是利用string类型数据结构实现的，把每一个bit位对应为当月的每一天，形成了映射关系，用0和1表示业务状态，用极小的空间实现了大量数据的表示  
签到统计时采用右移<<<1进行统计，统计迄今为止最长签到天数。
   ![1653834455899](/pic/1653834455899.png)