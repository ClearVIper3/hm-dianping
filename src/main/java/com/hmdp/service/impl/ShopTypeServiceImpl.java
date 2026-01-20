package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryList() {
        String key = CACHE_SHOP_KEY + "shopType";
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);

        if(shopTypeJson != null){
            List<ShopType> list = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(list);
        }

        List<ShopType> list = query().orderByAsc("sort").list();

        if(list == null || list.isEmpty()){
            return Result.fail("查询不到商铺类型");
        }

        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(list));

        return Result.ok(list);
    }
}
