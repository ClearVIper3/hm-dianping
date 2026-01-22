package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private  String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //1.设置key
        String key = KEY_PREFIX + name;
        //2.存入Redis,返回
        //获取当前线程id
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isLock);
    }

    @Override
    public void unlock() {
        //1.设置key
        String key = KEY_PREFIX + name;
        //2.获取标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //3.获取Redis中的标识
        String id = stringRedisTemplate.opsForValue().get(key);
        if(threadId.equals(id)){
            //标识相同,释放锁
            //4.删除
            stringRedisTemplate.delete(key);
        }
    }
}