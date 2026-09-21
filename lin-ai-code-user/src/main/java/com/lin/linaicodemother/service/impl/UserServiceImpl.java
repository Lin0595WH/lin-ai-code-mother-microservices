package com.lin.linaicodemother.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.exception.ThrowUtils;
import com.lin.linaicodemother.mapper.UserMapper;
import com.lin.linaicodemother.mapstruct.UserModuleMapper;
import com.lin.linaicodemother.model.dto.user.UserQueryRequest;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.enums.UserRoleEnum;
import com.lin.linaicodemother.model.vo.LoginUserVO;
import com.lin.linaicodemother.model.vo.UserVO;
import com.lin.linaicodemother.service.UserService;
import com.mybatisflex.core.logicdelete.LogicDeleteManager;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.lin.linaicodemother.constant.UserConstant.USER_LOGIN_STATE;

/**
 * 用户 服务层实现。
 *
 * @author Lin
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserModuleMapper userModuleMapper;

    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验参数
        if (CharSequenceUtil.hasBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        verifyUserAccountAndPassword(userAccount, userPassword);
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 查询用户是否已存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("user_account", userAccount);
        /*
        被删除的用户的也不可再被使用，直接用selectCountByQuery会默认带上 is_delete = 0 条件，所以这里要忽视掉逻辑删除字段
        long count = this.mapper.selectCountByQuery(queryWrapper);
        */
        long count = LogicDeleteManager.execWithoutLogicDelete(() ->
                this.mapper.selectCountByQuery(queryWrapper)
        );
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复，请重新输入");
        }
        // 3. 加密密码
        String encryptPassword = getEncryptPassword(userPassword);
        // 4. 创建用户，插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserName(userAccount);
        user.setUserRole(UserRoleEnum.USER.getValue());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        LoginUserVO loginUserVO = userModuleMapper.userToLoginUserVO(user);
        loginUserVO.setLoginTime(LocalDateTime.now());
        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验参数
        ThrowUtils.throwIf(CharSequenceUtil.hasBlank(userAccount, userPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        verifyUserAccountAndPassword(userAccount, userPassword);
        // 2. 加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询用户是否存在
        QueryWrapper queryWrapper = new QueryWrapper();
        queryWrapper.eq("user_account", userAccount);
        queryWrapper.eq("user_password", encryptPassword);
        User user = this.mapper.selectOneByQuery(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 4. 如果用户存在，记录用户的登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        // 5. 返回脱敏的用户信息
        return this.getLoginUserVO(user);
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询当前用户信息
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    @Override
    public UserVO getUserVO(User user) {
        return userModuleMapper.userToUserVO(user);
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        return userModuleMapper.userListToUserVOList(userList);
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断用户是否登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "用户未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }

    @Override
    public QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper queryWrapper = QueryWrapper.create().where(User::getId).eq(id, id != null)
                .and(User::getUserRole).eq(userRole, StrUtil::isNotBlank)
                .and(User::getUserAccount).like(userAccount, StrUtil::isNotBlank)
                .and(User::getUserName).like(userName, StrUtil::isNotBlank)
                .and(User::getUserProfile).like(userProfile, StrUtil::isNotBlank);
        if (CharSequenceUtil.isNotBlank(sortField)) {
            queryWrapper = queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        return queryWrapper;

    }

    /**
     * 校验账号和密码
     *
     * @param userAccount  用户账号
     * @param userPassword 用户密码
     */
    private static void verifyUserAccountAndPassword(String userAccount, String userPassword) {
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过短");
        }
        if (userAccount.length() > 16) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度过长");
        }
        if (userPassword.length() < 8 || userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过短");
        }
        if (userPassword.length() > 16 || userPassword.length() > 16) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度过长");
        }
    }

    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "lin-ai-code-mother";
        return DigestUtil.md5Hex(userPassword + SALT);
    }
}
