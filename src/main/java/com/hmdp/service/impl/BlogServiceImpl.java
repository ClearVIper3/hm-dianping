package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            //查询该笔记的作者
            queryBlogUser(blog);
            //判断是否点赞过该笔记
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);

        if(blog == null){
            return Result.fail("笔记不存在!");
        }

        //查询该笔记的作者
        queryBlogUser(blog);
        //判断是否点赞过该笔记
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    //这仅仅只是做到防止用户刷赞
    public Result likeBlog(Long id) {
        //获得当前用户id
        Long userId = UserHolder.getUser().getId();
        //判断用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        if(BooleanUtil.isFalse(isLiked)){
            //如果未点赞，可以点赞
            //数据库点赞数+1
            boolean isSuccess = update()
                    .setSql("liked = liked + 1")
                    .eq("id",id)
                    .update();
            if(isSuccess){
                //把用户添加到Redis的set集合中
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        }else {
            //如果已点赞，不可以点赞
            //数据库点赞数-1
            boolean isSuccess = update()
                    .setSql("liked = liked - 1")
                    .eq("id",id)
                    .update();
            if(isSuccess){
                //把用户从Redis的set集合中移除
                stringRedisTemplate.opsForSet().remove(key,userId.toString());
            }
        }
        return Result.ok();
    }

    private void isBlogLiked(Blog blog){
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Boolean isLiked = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        //保存是否已点赞到blog里，给前端使用
        blog.setIsLike(BooleanUtil.isTrue(isLiked));
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
