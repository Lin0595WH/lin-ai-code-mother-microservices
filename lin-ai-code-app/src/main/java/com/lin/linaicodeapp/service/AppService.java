package com.lin.linaicodeapp.service;

import com.lin.linaicodemother.model.dto.app.AppAddRequest;
import com.lin.linaicodemother.model.dto.app.AppQueryRequest;
import com.lin.linaicodemother.model.entity.App;
import com.lin.linaicodemother.model.entity.User;
import com.lin.linaicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author Lin
 */
public interface AppService extends IService<App> {

    /**
     * 通过对话生成应用代码
     *
     * @param appId     应用 ID
     * @param message   提示词
     * @param loginUser 登录用户
     * @return 流式响应
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 应用部署
     *
     * @param appId     应用 ID
     * @param loginUser 登录用户
     * @return 可访问的部署url地址
     */
    String deployApp(Long appId, User loginUser);

    /**
     * 创建应用
     *
     * @param appAddRequest 应用生成请求
     * @param loginUser 登录用户
     * @return
     */
    Long createApp(AppAddRequest appAddRequest, User loginUser);

    /**
     * 异步生成应用截图并更新封面
     *
     * @param appId  应用ID
     * @param appUrl 应用访问URL
     */
    void generateAppScreenshotAsync(Long appId, String appUrl);

    /**
     * 判断传入内容是否含有敏感词
     *
     * @param content 文本内容
     * @return 敏感词列表
     */
    List<String> checkSensitive(String content);

    /**
     * 获取应用封装类
     *
     * @param app 原始app对象
     * @return appVO app封装类
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表
     *
     * @param appList 原始app对象列表
     * @return appVOList app封装类列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 构造应用查询条件
     *
     * @param appQueryRequest 查询条件
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

}
