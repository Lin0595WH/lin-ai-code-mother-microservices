# Lin AI Code Mother 后端

这是 AI 应用生成平台的 Java 21 多模块后端。用户登录后可创建应用，通过流式对话生成 HTML、多文件或 Vue 项目，预览、部署、下载源码，并为部署结果生成截图封面；对话页还可在预览中进入编辑模式，选中元素后继续描述修改。Vue 前端位于独立仓库 `lin-ai-code-mother-frontend`。

> 仓库提供三个 Spring Boot 可执行 JAR、生产 Docker Compose、隔离的 Vue 构建容器和 Nginx 配置片段；MySQL、Redis、Nacos、宝塔 Nginx 与 Higress 仍由外部提供。数据库迁移和截图浏览器隔离等事项仍需管理，部署方法见 [deploy/README.md](deploy/README.md)。

## 模块与服务

| 模块 | 类型 | 职责 |
| --- | --- | --- |
| `lin-ai-code-common` | 依赖库 | 通用响应、异常、常量与工具 |
| `lin-ai-code-model` | 依赖库 | 实体、DTO、VO、枚举与转换 |
| `lin-ai-code-client` | 依赖库 | Dubbo 内部服务契约 |
| `lin-ai-code-ai` | 依赖库 | LangChain4j 服务、提示词、护轨与文件工具；**不是独立进程** |
| `lin-ai-code-user` | 运行服务 | 注册、登录、Session、用户管理 |
| `lin-ai-code-app` | 运行服务 | 应用、聊天、AI 编排、生成、预览、部署、下载 |
| `lin-ai-code-screenshot` | 运行服务 | Dubbo 截图、Chrome 渲染、COS 上传 |

三个服务的 HTTP 上下文路径都是 `/api`。Screenshot 的 HTTP 端口目前没有面向前端的业务 Controller，主要入口是 Dubbo。

| 服务 | HTTP | Dubbo Triple | 对外路由 |
| --- | ---: | ---: | --- |
| User | 8124 | 50051 | `/api/user/**` |
| App | 8125 | 50053 | `/api/app/**`、`/api/chatHistory/**`、`/api/static/**`、`/api/health/**` |
| Screenshot | 8127 | 50052 | 不应直接暴露公网 |

前端开发代理默认指向 `localhost:8080`，但**本仓库没有内置 8080 网关**。本地联调需自行配置 Nginx/Higress 等路由，或调整前端 Vite 代理；生产路由由外部宝塔 Nginx 与 Higress 承担，配置步骤见 [deploy/README.md](deploy/README.md)。

## 运行依赖

| 组件 | 当前用途 |
| --- | --- |
| MySQL | `user`、`app`、`chat_history` 数据；默认库名 `lin_ai_code_mother` |
| Redis | 跨服务 Spring Session、AI 对话记忆、缓存、限流、配额锁、预览令牌 |
| Nacos | Dubbo 服务注册与发现；不是本仓库的配置中心 |
| OpenAI 兼容模型 API | 类型路由与代码生成 |
| Node.js/npm | 生产环境由独立 Vue 构建容器执行；本地开发可由 App 调用本机 npm |
| Chrome/Chromium | Screenshot 服务渲染部署页面 |
| 腾讯云 COS | 存储截图封面 |
| 本地磁盘 | `tmp/code_output` 生成源码、`tmp/code_deploy` 部署静态文件 |

项目采用 Spring Boot 3.5.3、Dubbo 3.3.0、LangChain4j、MyBatis-Flex、Spring Session、Redisson 和 Selenium。**仓库尚无数据库建表或版本化迁移脚本**，必须先由实际数据库结构准备 `user`、`app`、`chat_history` 表，不能仅凭 README 自动建库。

## 本地启动

准备 JDK 21、Maven 3.9+、MySQL、Redis、Nacos，以及可用的模型 API；要验证 Vue 生成和截图，还需 Node.js/npm、Chrome/Chromium 与 COS。先启动外部依赖，再启动 User、Screenshot、App。

`application.yml` 中的数据库、Redis、Nacos 配置可由环境变量覆盖。以下仅为变量名示例，实际值从本机安全配置注入，勿提交真实凭据：

```bash
export DB_USERNAME='your_db_user'
export DB_PASSWORD='your_db_password'
export REDIS_PASSWORD='your_redis_password'
export NACOS_SERVER_ADDR='127.0.0.1:8848'
export NACOS_USERNAME='your_nacos_user'
export NACOS_PASSWORD='your_nacos_password'
export SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3306/lin_ai_code_mother'
export SPRING_DATA_REDIS_HOST='127.0.0.1'
export SESSION_COOKIE_SECURE=false # 仅本地 HTTP 调试；HTTPS 环境保持 true
```

模型配置位于 App 的 `langchain4j.open-ai.*` 属性，截图存储配置位于 Screenshot 的 `cos.client.*` 属性。开发时可在各服务 `src/main/resources/application-local.yml` 放置本地覆盖配置。该文件被 Git 忽略，且根 POM 将它排除在 JAR 外；运行 JAR 时须用 `spring.config.additional-location` 显式加载，仅激活 `local` profile 不会加载它。Nacos 凭据还需通过 `NACOS_USERNAME`、`NACOS_PASSWORD` 环境变量或本地文件中的 `dubbo.registry.username`、`dubbo.registry.password` 提供。生产配置写入服务器上受权限保护、未跟踪的 `deploy/config/*/application-prod.yml`，模型请求/响应日志不得记录敏感正文。

```bash
# 在仓库根目录执行完整构建与测试
mvn clean verify

# 仅供本地快速打包；发布仍以完整测试为准
mvn package -DskipTests

# 在仓库根目录、三个独立终端中运行；先确认本机配置完整
java -jar lin-ai-code-user/target/lin-ai-code-user-1.0-SNAPSHOT.jar --spring.profiles.active=local "--spring.config.additional-location=file:$PWD/lin-ai-code-user/src/main/resources/application-local.yml"
java -jar lin-ai-code-screenshot/target/lin-ai-code-screenshot-1.0-SNAPSHOT.jar --spring.profiles.active=local "--spring.config.additional-location=file:$PWD/lin-ai-code-screenshot/src/main/resources/application-local.yml"
java -jar lin-ai-code-app/target/lin-ai-code-app-1.0-SNAPSHOT.jar --spring.profiles.active=local "--spring.config.additional-location=file:$PWD/lin-ai-code-app/src/main/resources/application-local.yml"
```

上述命令分别在独立终端或进程中运行。若用生产环境变量覆盖全部配置，启动时不应激活 `local`。根 POM 中的 Spring Boot Maven Plugin 已由三个运行模块引用并执行 `repackage`；`lin-ai-code-ai` 等依赖库不应单独 `java -jar`。

常用验证命令：

```bash
mvn -pl lin-ai-code-app -am test
mvn -pl lin-ai-code-user -am package -DskipTests # 仅用于本地快速打包
curl http://localhost:8125/api/health/
```

User 和 App 的 API 文档分别位于 `http://localhost:8124/api/doc.html`、`http://localhost:8125/api/doc.html`。`/api/health/` 只是 App 的简单健康接口，不能替代生产环境的依赖就绪探针。

## 主要接口与调用

| 功能 | 方法与路径 | 所属服务 |
| --- | --- | --- |
| 注册、登录、注销 | `POST /api/user/register`、`POST /api/user/login`、`POST /api/user/logout` | User |
| 当前用户 | `GET /api/user/get/login` | User |
| 创建应用 | `POST /api/app/add` | App |
| 流式生成 | `POST /api/app/chat/gen/code?appId={id}`，JSON 请求体 `{"message":"..."}` | App |
| 对话历史 | `GET /api/chatHistory/app/{appId}` | App |
| 预览令牌 | `GET /api/app/preview-token/{appId}` | App |
| 预览文件 | `GET /api/static/{codeGenType}_{appId}/preview/{token}/...` | App |
| 部署 | `POST /api/app/deploy`，JSON 请求体 `{"appId":123}` | App |
| 下载源码 | `GET /api/app/download/{appId}` | App |

接口依赖登录 Session。以下以本地直连两个服务为例；浏览器联调时应通过同一入口路由，保证 Cookie 能在两个服务间共享：

```bash
curl -c cookies.txt -H 'Content-Type: application/json' \
  -d '{"userAccount":"demo","userPassword":"your_password"}' \
  http://localhost:8124/api/user/login

curl -b cookies.txt -H 'Origin: http://localhost:8125' -H 'Content-Type: application/json' \
  -d '{"initPrompt":"创建一个待办事项应用"}' \
  http://localhost:8125/api/app/add

curl -N -b cookies.txt -H 'Origin: http://localhost:8125' -H 'Accept: text/event-stream' \
  -H 'Content-Type: application/json' \
  -d '{"message":"增加任务筛选功能"}' \
  'http://localhost:8125/api/app/chat/gen/code?appId=YOUR_APP_ID'
```

生成接口返回 SSE：普通分片的 `data` 为包含 `d` 字段的 JSON，正常结束为 `done` 事件，业务错误可能以 `business-error` 事件返回。前端不能仅以 HTTP 200 判断生成成功。

对话页的可视化编辑依赖预览资源：App 服务返回 `index.html` 时注入元素选择脚本，iframe 通过 `postMessage` 回传所选元素的标签、选择器和当前文本；预览继续使用 `Content-Security-Policy: sandbox allow-scripts`，不授予生成页面 `allow-same-origin` 权限。前端会把选中元素信息附加到下一次生成请求，未进入编辑模式时预览交互保持正常。

## 生成文件与部署边界

- `tmp/code_output/{codeGenType}_{appId}` 保存生成源码；Vue 模式由 AI 文件工具写入，本地 `local/dev` 环境可调用本机 npm，生产环境须配置 `VUE_BUILDER_URL` 使用独立 Vue 构建容器生成 `dist`。
- `tmp/code_deploy/{deployKey}` 保存对外发布的静态文件；Vue 部署时会再次构建并复制 `dist`。
- 两个根目录目前由 `System.getProperty("user.dir")` 决定。不同工作目录启动会读写不同位置；多实例也没有共享产物机制。
- 返回的部署地址由 `app.deploy-host` 配置；生产示例使用 `https://aiapp.linwh.top/dist`，与宝塔 Nginx 的 `/dist/` 映射对应。
- Screenshot 在部署后异步经 Dubbo 获取部署 URL，用 Chrome 打开页面，上传 COS，并更新 `app.cover`。截图失败不回滚已完成的部署。

完整时序与数据归属见[项目数据流转图](docs/PROJECT_DATA_FLOW.md)。

## 生产前必须闭环

仓库已有单机 Docker Compose、隔离的 Vue 构建容器和 Nginx 配置片段；外部网关、数据库等基础设施须按 [部署说明](deploy/README.md)配置。仓库仍没有数据库迁移、统一 readiness/liveness、集中日志/链路追踪、指标告警、备份恢复和回滚流程。后续重点是：

1. 多 App 实例上线前解决生成源码和发布目录共享；当前 Compose 仅运行一个 App 实例。
2. 限制截图 URL 的协议、域名、解析结果与重定向目标，并隔离 Chrome；当前实现直接访问传入 URL，且使用 `--no-sandbox`。
3. 轮换现有本地配置中出现过的凭据，完善密码哈希、生产 Secret 管理和数据库迁移。
4. 为登录、SSE、部署、Dubbo、MySQL、Redis、Nacos、模型调用、磁盘和截图建立可观测性、限流/超时、备份与回滚演练。

详细的当前架构与部署拓扑见[项目架构图](docs/PROJECT_ARCHITECTURE.md)。`docs/DEPLOYMENT_READINESS_ASSESSMENT_2026-09-24.md` 是历史评估，其中关于 Spring Boot 插件、生产编排和隔离构建的结论已过时；其余风险仍需按当前实现重新验收。
