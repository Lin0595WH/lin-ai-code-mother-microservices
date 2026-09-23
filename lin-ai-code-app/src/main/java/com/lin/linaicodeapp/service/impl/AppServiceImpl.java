package com.lin.linaicodeapp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.github.houbb.sensitive.word.core.SensitiveWordHelper;
import com.lin.linaicodemother.ai.AiCodeGenTypeRoutingService;
import com.lin.linaicodeapp.ai.AiCodeGenTypeRoutingServiceFactory;
import com.lin.linaicodemother.ai.model.RoutingResult;
import com.lin.linaicodemother.constant.AppConstant;
import com.lin.linaicodeapp.core.AiCodeGeneratorFacade;
import com.lin.linaicodeapp.core.builder.VueProjectBuilder;
import com.lin.linaicodeapp.core.handler.StreamHandlerExecutor;
import com.lin.linaicodemother.exception.BusinessException;
import com.lin.linaicodemother.exception.ErrorCode;
import com.lin.linaicodemother.exception.ThrowUtils;
import com.lin.linaicodeapp.mapper.AppMapper;
import com.lin.linaicodemother.innerservice.InnerScreenshotService;
import com.lin.linaicodemother.innerservice.InnerUserService;
import com.lin.linaicodemother.mapstruct.AppModuleMapper;
import com.lin.linaicodemother.model.dto.app.AppAddRequest;
import com.lin.linaicodemother.model.dto.app.AppQueryRequest;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.enums.ChatHistoryMessageTypeEnum;
import com.lin.linaicodemother.model.enums.CodeGenTypeEnum;
import com.lin.linaicodemother.model.vo.AppVO;
import com.lin.linaicodemother.model.vo.UserVO;
import com.lin.linaicodeapp.service.AppCreationQuotaService;
import com.lin.linaicodeapp.service.AppService;
import com.lin.linaicodeapp.service.ChatHistoryService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author Lin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @DubboReference
    private  InnerUserService userService;
    @DubboReference
    private  InnerScreenshotService screenshotService;

    private final AppModuleMapper appModuleMapper;

    private final AiCodeGeneratorFacade aiCodeGeneratorFacade;

    private final ChatHistoryService chatHistoryService;

    private final StreamHandlerExecutor streamHandlerExecutor;

    private final VueProjectBuilder vueProjectBuilder;

    private final AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;

    private final AppCreationQuotaService appCreationQuotaService;

    /**
     * 通过对话生成应用代码
     *
     * @param appId     应用 ID
     * @param message   提示词
     * @param loginUser 登录用户
     * @return
     */
    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(message), ErrorCode.PARAMS_ERROR, "提示词不能为空");
        List<String> sensitive = checkSensitive(message);
        ThrowUtils.throwIf(CollUtil.isNotEmpty(sensitive), ErrorCode.PARAMS_ERROR, "提示词中包含敏感词汇:" + sensitive);
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 权限校验，仅本人可以和自己的应用对话
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限访问该应用");
        }
        // 4. 获取应用的代码生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用代码生成类型错误");
        }
        // 5. 调用 AI 生成代码 先保存用户消息到数据库中
        chatHistoryService.addChatMessage(appId, message, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());
        // 6. 调用 AI 生成代码（流式）
        Flux<String> contentFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        // 7. 收集 AI 响应的内容，并且在完成后保存记录到对话历史
        return streamHandlerExecutor.doExecute(contentFlux, chatHistoryService, appId, loginUser, codeGenTypeEnum);
    }

    /**
     * 部署应用
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 可访问的部署url地址
     */
    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1.校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 错误");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2.校验应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3.权限校验:仅本人可以部署应用
        ThrowUtils.throwIf(!app.getUserId().equals(loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无操作权限");
        // 4.检查是否已有部署key
        // 没有就生成部署key(6位，字母+数字)
        String deployKey = app.getDeployKey();
        if (CharSequenceUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5.获取代码生成类型，获取文件生成路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        // 6.判断路径是否真实存在
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        if (!FileUtil.isDirectory(sourceDirPath)) {
            log.error("应用部署失败，应用代码路径{}不存在，请先生成应用:", sourceDirPath);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败，应用代码路径不存在，请先生成应用");
        }
        // 7. Vue 项目特殊处理：执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            // Vue 项目需要构建
            boolean buildSuccess = vueProjectBuilder.buildProject(sourceDirPath);
            ThrowUtils.throwIf(!buildSuccess, ErrorCode.SYSTEM_ERROR, "Vue 项目构建失败，请重试");
            // 检查 dist 目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists(), ErrorCode.SYSTEM_ERROR, "Vue 项目构建完成但未生成 dist 目录");
            // 构建完成后，需要将构建后的文件复制到部署目录
            sourceDirPath = distDir.getAbsolutePath();
        }
        // 8.复制文件到部署路径
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(new File(sourceDirPath), new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用部署失败：" + e.getMessage());
        }
        // 9.更新该应用的deployKey,deployedTime,editTime
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        LocalDateTime now = LocalDateTime.now();
        updateApp.setDeployedTime(now);
        updateApp.setEditTime(now);
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 10.生成可访问的url地址
        String appDeployUrl = CharSequenceUtil.format("{}/{}/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        // 11.异步生成应用截图（上传到腾讯云COS,并更新数据库封面字段)
        generateAppScreenshotAsync(appId, appDeployUrl);
        return appDeployUrl;
    }

    /**
     * 创建应用
     *
     * @param appAddRequest 应用生成请求
     * @param loginUser     登录用户
     * @return
     */
    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        // 参数校验
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(CharSequenceUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "初始化 prompt 不能为空");
        List<String> strings = checkSensitive(initPrompt);
        ThrowUtils.throwIf(!strings.isEmpty(), ErrorCode.PARAMS_ERROR, "用户输入中包含敏感词：" + strings);
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        // 使用 AI 智能选择代码生成类型（多例模式） 以及应用名称
        AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.createAiCodeGenTypeRoutingService();
        RoutingResult routingResult = aiCodeGenTypeRoutingService.routing(initPrompt);
        CodeGenTypeEnum selectedCodeGenType = routingResult.getCodeGenTypeEnum();
        // 生成类型
        String codeGenType = selectedCodeGenType == null
                ? CodeGenTypeEnum.HTML.getValue() : selectedCodeGenType.getValue();
        app.setCodeGenType(codeGenType);
        // 应用名称
        String appName = CharSequenceUtil.isBlank(routingResult.getAppName())
                ? initPrompt.substring(0, Math.min(initPrompt.length(), 12))
                : routingResult.getAppName();
        app.setAppName(appName);
        // 插入数据库
        boolean result = appCreationQuotaService.saveApp(app, loginUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        log.info("应用创建成功，ID: {}, 类型: {}", app.getId(), codeGenType);
        return app.getId();
    }

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    public void generateAppScreenshotAsync(Long appId, String appUrl) {
        Thread.startVirtualThread(() -> {
            log.info("开始生成应用截图，应用ID: {}", appId);
            // 1.生成应用截图，并上传到腾讯云COS,得到一个可访问的截图URL
            String screenshotUrl = screenshotService.generateAndUploadScreenshot(appUrl);
            if (CharSequenceUtil.isNotBlank(screenshotUrl)) {
                App updateApp = new App();
                updateApp.setId(appId);
                updateApp.setCover(screenshotUrl);
                boolean updateResult = this.updateById(updateApp);
                if (!updateResult) {
                    log.error("更新应用封面失败：{}", appId);
                }
            } else {
                log.error("应用截图生成失败：{}", appId);
            }
        });
    }

    /**
     * 判断传入内容是否含有敏感词
     *
     * @param content 文本内容
     * @return List<String> 敏感词列表
     */
    @Override
    public List<String> checkSensitive(String content) {
        // TODO: 有空改造下，现在这个太敏感了
        if (CharSequenceUtil.isBlank(content)) {
            return List.of();
        }
        return SensitiveWordHelper.findAll(content);
    }

    /**
     * 获取应用封装类
     *
     * @param app
     * @return
     */
    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        // 使用MapStruct转换基础字段
        AppVO appVO = appModuleMapper.appToAppVO(app);

        // 设置用户信息
        if (app.getUserId() != null) {
            User user = userService.getById(app.getUserId());
            if (user != null) {
                appVO.setUser(userService.getUserVO(user));
            }
        }
        return appVO;
    }

    /**
     * 获取应用封装类列表
     *
     * @param appList
     * @return
     */
    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (appList == null || appList.isEmpty()) {
            return List.of();
        }
        // 批量转换App基础字段
        List<AppVO> appVOList = appModuleMapper.appListToAppVOList(appList);
        // 批量获取用户ID
        List<Long> userIds = appList.stream()
                .map(App::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (!userIds.isEmpty()) {
            // 批量查询用户信息
            List<User> users = userService.listByIds(userIds);
            Map<Long, UserVO> userVOMap = users.stream()
                    .collect(Collectors.toMap(User::getId, userService::getUserVO));
            // 设置用户信息到AppVO
            for (AppVO appVO : appVOList) {
                if (appVO.getUserId() != null) {
                    appVO.setUser(userVOMap.get(appVO.getUserId()));
                }
            }
        }
        return appVOList;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        boolean isAscend = "ascend".equals(sortOrder);
        String orderField = switch (sortField == null ? "" : sortField) {
            case "id" -> "id";
            case "appName" -> "app_name";
            case "priority" -> "priority";
            case "userId" -> "user_id";
            case "createTime" -> "create_time";
            case "updateTime" -> "update_time";
            default -> "create_time";
        };
        return QueryWrapper.create()
                .eq("id", id, id != null)
                .like("app_name", appName, StrUtil::isNotBlank)
                .like("cover", cover, StrUtil::isNotBlank)
                .like("init_prompt", initPrompt, StrUtil::isNotBlank)
                .eq("code_gen_type", codeGenType, StrUtil::isNotBlank)
                .eq("deploy_key", deployKey, StrUtil::isNotBlank)
                .eq("priority", priority, priority != null)
                .eq("user_id", userId, userId != null)
                .orderBy(orderField, isAscend);
    }

    /**
     * 删除应用时，关联删除对话历史
     *
     * @param id
     * @return
     */
    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }
        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        // 先删除关联的对话历史
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("删除应用关联的对话历史失败：{}", e.getMessage());
        }
        // 删除应用
        return super.removeById(id);
    }
}
