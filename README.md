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
