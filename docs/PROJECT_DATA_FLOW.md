# 项目数据流（当前实现）

本文按浏览器发起的业务操作描述数据的读写与返回路径。运行组件及依赖关系见 [PROJECT_ARCHITECTURE.md](PROJECT_ARCHITECTURE.md)。下图省略仓库外的宝塔 Nginx 和 Higress；仓库提供其路由配置步骤，所有后端 HTTP 路径都带 `/api` 前缀。

## 1. 注册、登录与创建应用

```mermaid
sequenceDiagram
    autonumber
    participant B as 浏览器 / 前端
    participant U as User 服务
    participant A as App 服务
    participant D as MySQL
    participant R as Redis
    participant L as 模型服务

    B->>U: POST /api/user/register
    U->>D: 检查账号并写入 user
    D-->>U: 用户 ID
    U-->>B: 用户 ID
    B->>U: POST /api/user/login
    U->>D: 按账号查询 user
    D-->>U: User
    U->>U: 校验密码
    U->>R: Spring Session 保存登录态
    U-->>B: 登录用户 VO + Session Cookie

    B->>A: POST /api/app/add + Cookie + initPrompt
    A->>R: 从共享 Session 读取登录态
    A->>A: 敏感词检查与用户限流
    A->>L: 根据初始提示词选择类型和名称
    L-->>A: HTML / MULTI_FILE / VUE_PROJECT
    A->>R: 普通用户配额锁
    A->>D: 检查配额并写入 app
    A-->>B: appId
```

User 与 App 使用同一个 Redis Spring Session；App 通过 `InnerUserService.getLoginUser(request)` 读取当前 Session 中的 User，此步不是 Dubbo 调用。普通用户的应用创建配额按代码类型计算，使用 Redisson 锁；管理员不经过该配额分支。依据：[UserServiceImpl](../lin-ai-code-user/src/main/java/com/lin/linaicodemother/service/impl/UserServiceImpl.java)、[InnerUserService](../lin-ai-code-client/src/main/java/com/lin/linaicodemother/innerservice/InnerUserService.java)、[AppServiceImpl](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/service/impl/AppServiceImpl.java)、[AppCreationQuotaService](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/service/AppCreationQuotaService.java)。

## 2. AI 生成与 SSE

```mermaid
sequenceDiagram
    autonumber
    participant B as 浏览器 / 前端
    participant A as App 服务
    participant D as MySQL
    participant R as Redis
    participant L as 模型服务
    participant F as 本机 code_output
    participant N as Vue 构建容器（生产）/ 本机 npm（开发）

    B->>A: POST /api/app/chat/gen/code?appId=... + Cookie + message
    A->>R: 读取 Session；Redisson 限流
    A->>D: 读取 app，校验创建者
    A->>D: 写入 USER 类型 chat_history
    opt AI 服务实例首次建立或缓存失效
        A->>D: 加载最近聊天历史
        A->>R: 初始化会话记忆
    end
    A->>L: 发起流式生成请求
    loop 模型分片或工具事件
        L-->>A: 文本分片 / 工具事件
        opt Vue 工具调用
            A->>F: 在 vue_project_{appId} 读写、修改或删除文件
        end
        A-->>B: SSE data（JSON 字段 d）
    end
    alt HTML 或 MULTI_FILE
        A->>A: 汇总并解析完整响应
        A->>F: 写入 index.html；多文件另写 style.css、script.js
    else VUE_PROJECT
        A->>N: 发送项目源码并执行 npm install + npm run build
        N-->>A: 返回 dist 构建产物
        A->>F: 写入 dist
    end
    A->>D: 完成后写入 AI 类型 chat_history
    A-->>B: SSE done（仅正常完成）
```

代码生成类型在创建应用时确定，具体值为 `html`、`multi_file`、`vue_project`。App 使用 LangChain4j 调用外部模型；Redis 存会话记忆，MySQL `chat_history` 存可查询的用户与 AI 消息。HTML 和多文件模式在流结束后解析并保存；Vue 模式由 AI 工具直接操作项目文件，模型完成回调中构建 `dist`。SSE 的普通数据被包为 `{"d":"..."}`，正常完成才追加 `done` 事件。参考 [CodeGenTypeEnum](../lin-ai-code-model/src/main/java/com/lin/linaicodemother/model/enums/CodeGenTypeEnum.java)、[AppController](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/controller/AppController.java)、[AiCodeGeneratorFacade](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/core/AiCodeGeneratorFacade.java)、[AiCodeGeneratorServiceFactory](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/ai/AiCodeGeneratorServiceFactory.java)。

生成过程有两个值得区分的失败边界：Vue 构建失败会让流报错；HTML/多文件保存异常目前只写日志，可能已向前端发送 `done`。生产 Compose 为 App 配置独立 Vue 构建容器，本地开发未配置 `VUE_BUILDER_URL` 时才调用本机 npm。聊天历史读取接口为 `GET /api/chatHistory/app/{appId}`，由 App 服务校验应用所有者或管理员。

## 3. 预览令牌与静态预览

```mermaid
sequenceDiagram
    autonumber
    participant B as 浏览器 / 前端
    participant A as App 服务
    participant D as MySQL
    participant R as Redis
    participant F as 本机 code_output

    B->>A: GET /api/app/preview-token/{appId} + Cookie
    A->>R: 读取 Session
    A->>D: 查询 app 并校验所有者或管理员
    A->>R: 保存 app:preview:{token}，有效期 600 秒
    A-->>B: token
    B->>A: GET /api/static/{type}_{appId}/preview/{token}/...
    A->>D: 确认 app 与 projectKey 匹配
    A->>R: 验证 token 与 appId/ownerId
    A->>F: 只读取允许的静态产物
    F-->>A: HTML / CSS / JS / Vue dist 文件
    A-->>B: 静态资源
```

静态资源也可以由应用所有者的 Session 授权，此时无需预览令牌。HTML/多文件模式只允许固定产物；Vue 模式只读取 `dist`。预览路径、令牌发放、路径规范化与符号链接检查见 [AppController](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/controller/AppController.java) 和 [StaticResourceController](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/controller/StaticResourceController.java)。

## 4. 部署、异步截图与封面

```mermaid
sequenceDiagram
    autonumber
    participant B as 浏览器 / 前端
    participant A as App 服务
    participant F as 本机 code_output
    participant N as Vue 构建容器（生产）/ 本机 npm（开发）
    participant P as 本机 code_deploy
    participant D as MySQL
    participant S as Screenshot 服务
    participant C as Chrome / ChromeDriver
    participant O as 腾讯云 COS

    B->>A: POST /api/app/deploy + Cookie + appId
    A->>D: 查 app 并校验创建者
    A->>F: 读取 {type}_{appId} 项目
    opt Vue 项目
        A->>N: 再次发送源码并执行 npm install + npm run build
        N-->>A: 返回 dist 构建产物
        A->>F: 更新 dist
    end
    A->>P: 复制 HTML / 多文件产物或 Vue dist 到 {deployKey}
    A->>D: 更新 deployKey、部署时间
    A->>A: 启动截图虚拟线程
    A-->>B: {app.deploy-host}/{deployKey}/
    opt 截图任务在后台运行，可能与 HTTP 响应重叠
        A->>S: Dubbo generateAndUploadScreenshot(URL)
        S->>C: 打开部署 URL 并截图
        C-->>S: PNG 截图
        S->>S: 压缩为 JPEG
        S->>O: 上传 /screenshots/日期/文件名
        O-->>S: 对象 URL
        S-->>A: 封面 URL
        A->>D: 更新 app.cover
    end
```

部署 HTTP 响应不等待截图完成；封面随后由 App 的虚拟线程经 Dubbo 调用 Screenshot、上传 COS 后更新数据库。返回值由 `app.deploy-host` 配置；生产示例为 `https://aiapp.linwh.top/dist/{deployKey}/`。仓库提供 `/dist/` Nginx 映射片段，须由外部宝塔站点加载，截图服务也须能访问该地址。依据：[AppServiceImpl](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/service/impl/AppServiceImpl.java)、[VueProjectBuilder](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/core/builder/VueProjectBuilder.java)、[ScreenshotServiceImpl](../lin-ai-code-screenshot/src/main/java/com/lin/linaicodemother/service/impl/ScreenshotServiceImpl.java)、[WebScreenshotUtils](../lin-ai-code-screenshot/src/main/java/com/lin/linaicodemother/utils/WebScreenshotUtils.java) 和 [部署说明](../deploy/README.md)。

## 5. 源码下载

```mermaid
sequenceDiagram
    autonumber
    participant B as 浏览器 / 前端
    participant A as App 服务
    participant D as MySQL
    participant F as 本机 code_output

    B->>A: GET /api/app/download/{appId} + Cookie
    A->>D: 查 app 并校验创建者
    A->>F: 读取 {codeGenType}_{appId}
    A->>A: 过滤 node_modules、dist、.env 等并压缩
    A-->>B: application/zip 下载流
```

下载的是生成目录源码，不是 `code_deploy` 的发布目录；当前接口仅允许创建者下载。文件过滤策略见 [AppController](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/controller/AppController.java) 和 [ProjectDownloadServiceImpl](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/service/impl/ProjectDownloadServiceImpl.java)。

## 6. 管理端数据流

```mermaid
sequenceDiagram
    autonumber
    participant B as 管理员浏览器
    participant U as User 服务
    participant A as App 服务
    participant R as Redis Session
    participant D as MySQL

    B->>U: 用户列表 / 新增 / 更新 / 删除请求 + Cookie
    U->>R: 读取登录态
    U->>U: AuthCheck 校验管理员角色
    U->>D: 查询或变更 user
    U-->>B: 用户 VO 或操作结果
    B->>A: 应用管理请求 /api/app/admin/* + Cookie
    A->>R: 读取共享登录态
    A->>A: AuthCheck 校验管理员角色
    A->>D: 查询或变更 app
    opt 列表或详情需要用户 VO
        A->>U: Dubbo 查询用户信息
        U-->>A: 用户 VO
    end
    A-->>B: 应用 VO 或操作结果
    B->>A: POST /api/chatHistory/admin/list/page/vo
    A->>R: 读取登录态并校验管理员角色
    A->>D: 分页查询 chat_history
    A-->>B: 对话记录
```

前端管理页有用户、应用、对话三个入口。后端目前只提供管理员**查询**对话记录的接口，没有管理员删除对话记录接口；前端对话管理页的“删除”操作目前只展示成功提示，未形成数据库写入。前端路由守卫只改善界面体验，最终权限由后端 `@AuthCheck` 控制。相关入口见 [UserController](../lin-ai-code-user/src/main/java/com/lin/linaicodemother/controller/UserController.java)、[AppController](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/controller/AppController.java) 与 [ChatHistoryController](../lin-ai-code-app/src/main/java/com/lin/linaicodeapp/controller/ChatHistoryController.java)。

## 数据生命周期与失败边界

| 数据或阶段 | 当前落点与生命周期 | 需要关注的失败状态 |
| --- | --- | --- |
| 登录态 | Redis Spring Session；服务配置为 30 天超时 | User 与 App 的 Redis、Session 命名空间或 Cookie 域/路径不一致时，跨服务登录失效 |
| 预览令牌 | Redis `app:preview:{token}`，600 秒有效 | 过期或 Redis 不可用时，预览请求失败；发布站点不使用此令牌 |
| 用户、应用、对话 | MySQL `user`、`app`、`chat_history` | 当前无仓库内数据库迁移脚本，结构与版本需另行管理 |
| 生成源码 | App 工作目录下 `tmp/code_output` | 多实例不共享；HTML/多文件保存异常可能只记录日志，前端仍收到 `done` |
| Vue 构建 | 生产 Compose 的独立容器执行 npm，App 将返回的 `dist` 写入生成目录；本地开发可调用本机 npm | 构建失败会中断生成或部署；构建容器有资源和网络边界，仍须关注不可信依赖 |
| 部署文件 | App 工作目录下 `tmp/code_deploy/{deployKey}`，生产环境映射到宝塔 Nginx `/dist/` | 复制文件与 MySQL 更新不是原子事务；Nginx 规则需在外部站点加载，仓库无完整回滚流程 |
| 截图封面 | Screenshot 临时文件 -> COS；URL 写入 `app.cover` | 截图在返回部署 URL 后异步运行，失败时部署仍成功但封面可能缺失 |

生产环境还需要定义生成物、发布物、COS 对象和数据库记录的清理/保留策略，并为 SSE、构建、Dubbo 截图及各存储依赖建立 traceId、日志、指标和告警；这些不是当前已部署能力。
