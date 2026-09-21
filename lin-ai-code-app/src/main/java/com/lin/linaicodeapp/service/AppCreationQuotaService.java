package com.lin.linaicodeapp.service;

import com.lin.linaicodeapp.mapper.AppMapper;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.exception.ThrowUtils;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import com.lin.linaicodemother.model.enums.UserRoleEnum;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * 应用创建额度服务。
 */
@Service
@RequiredArgsConstructor
public class AppCreationQuotaService {

    private static final int MAX_APP_COUNT_PER_TYPE = 1;

    private static final String APP_CREATION_LOCK_KEY = "app:create:quota:%s";

    private final AppMapper appMapper;

    private final RedissonClient redissonClient;

    /**
     * 校验普通用户的创建额度并保存应用。
     *
     * @param app       待保存应用
     * @param loginUser 当前用户
     * @return 是否保存成功
     */
    public boolean saveApp(App app, User loginUser) {
        if (!UserRoleEnum.USER.getValue().equals(loginUser.getUserRole())) {
            return appMapper.insertSelective(app) == 1;
        }
        RLock lock = redissonClient.getLock(APP_CREATION_LOCK_KEY.formatted(loginUser.getId()));
        ThrowUtils.throwIf(!lock.tryLock(), ErrorCode.TOO_MANY_REQUEST, "项目正在创建中，请勿重复提交");
        try {
            checkQuota(app);
            return appMapper.insertSelective(app) == 1;
        } finally {
            lock.unlock();
        }
    }

    private void checkQuota(App app) {
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("user_id", app.getUserId())
                .eq("code_gen_type", app.getCodeGenType());
        long appCount = appMapper.selectCountByQuery(queryWrapper);
        ThrowUtils.throwIf(appCount >= MAX_APP_COUNT_PER_TYPE, ErrorCode.FORBIDDEN_ERROR,
                "普通用户最多只能创建 1 个 %s 项目".formatted(codeGenType.getText()));
    }
}
