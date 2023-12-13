package com.comments.controller;


import com.comments.dto.Result;
import com.comments.service.IFollowService;
import com.comments.service.impl.FollowServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author Colin
 * @since 2023-12-01
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    IFollowService followService;
    @PutMapping("/{id}/{isFollow}")
    public Result followUser(@PathVariable("id") Long followid,@PathVariable("isFollow") Boolean isFollow) {
        return followService.followUser(followid,isFollow);
    }
    @GetMapping("/or/not/{id}")
    public Result checkFollowInfo(@PathVariable("id") Long followid){
        return followService.checkFollowId(followid);
    }

    @GetMapping("/common/{id}")
    public Result commonFollowInfo(@PathVariable("id") Long userid){
        return followService.commonFollowInfo(userid);
    }
}
