package com.lin.linaicodemother.service.impl;


import com.lin.linaicodemother.innerservice.InnerUserService;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.vo.UserVO;
import com.lin.linaicodemother.service.UserService;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * @Author Lin
 * @Date 2026/9/14 21:02
 * @Descriptions 内部服务实现类
 */
@DubboService
@RequiredArgsConstructor
public class InnerUserServiceImpl implements InnerUserService {

    private final UserService userService;

    @Override
    public List<User> listByIds(Collection<? extends Serializable> ids) {
        return userService.listByIds(ids);
    }

    @Override
    public User getById(Serializable id) {
        return userService.getById(id);
    }

    @Override
    public UserVO getUserVO(User user) {
        return userService.getUserVO(user);
    }
}