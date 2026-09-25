# 服务器部署准入评估

评估日期：2026-09-24
评估范围：本仓库 Java 21 / Maven 后端微服务，以及其运行所需的 MySQL、Redis、Nacos、AI 模型、Chrome/Chromium 和 COS。
结论级别：**NO-GO，不具备公网生产部署资格；仅可在隔离的开发/测试环境运行。**

## 一、结论

当前项目不是“补一条启动命令”就能上线的状态，存在构建交付、凭据管理、主机隔离、代码执行、预览安全和运维可观测性等发布阻断项。任何直接把 `lin-ai-code-app` 暴露到公网的做法，都可能把生成项目的脚本执行权限、截图浏览器和服务账号权限串在一起。

在 P0 阻断项完成、凭据轮换、干净环境构建和动态安全验收前：

- 不开放公网 Vue 生成、构建、部署和静态预览。
- 不让 Dubbo/Nacos 端口暴露到公网。
- 不把当前工作区的 `target/`、`tmp/` 或 `application-local.yml` 当作部署制品。
- 不以 `mvn -DskipTests package` 的成功作为上线依据。

## 二、已执行的证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| `mvn test` | 失败 | App 模块 `AppCreationQuotaServiceTest` 34 个测试中 4 个失败，失败断言为配额检查应允许但实际返回 `false`。 |
| `mvn -DskipTests package` | 表面成功 | 只生成普通 JAR；没有 Spring Boot `BOOT-INF` 和 `Main-Class`。 |
| `java -jar` 三个服务 JAR | 失败 | 均报“没有主清单属性”。Spring Boot 插件仅定义在根 POM 的 `pluginManagement`，没有在服务模块声明并执行 `repackage`。 |
| 生产部署脚本/容器编排 | 缺失 | 仓库未发现 Dockerfile、Compose、systemd、Kubernetes、迁移 SQL 或可重复启动脚本。 |
| 数据库初始化 | 缺失 | README 明确说明仓库不包含表结构脚本，服务器必须人工准备。 |
| 工作区洁净度 | 不合格 | 存在未跟踪的 `tmp/`、生成代码和本地配置；生成目录约 71 MB。 |
| 凭据状态 | 高风险 | 本机被 `.gitignore` 忽略的 `application-local.yml` 仍包含 AI API Key、COS SecretId/SecretKey 和 Nacos 默认凭据，必须按已暴露处理。 |

## 三、发布阻断项

### P0-1：服务制品不可直接启动

运行服务模块未实际绑定 `spring-boot-maven-plugin`，当前产物是普通 Maven JAR。整改方式：在 `lin-ai-code-user`、`lin-ai-code-app`、`lin-ai-code-screenshot` 中声明 Spring Boot Maven Plugin，执行 `repackage`，并在 CI 验证：

```bash
unzip -l lin-ai-code-app/target/*.jar | grep BOOT-INF
java -jar lin-ai-code-app/target/*.jar --server.port=0
```

三个服务都必须通过同样的制品检查；不要只在 IDE 中运行主类。

### P0-2：AI 生成文件会进入宿主机 npm 构建

`lin-ai-code-app/src/main/java/com/lin/linaicodeapp/core/builder/VueProjectBuilder.java:43-55` 在应用服务进程内执行 `npm install` 和 `npm run build`。生成文件由 AI 工具写入，`package.json` 的脚本和依赖因此属于不可信输入。即使安装阶段使用 `--ignore-scripts`，后续 `npm run build` 仍会执行脚本。

整改方式：立即关闭公网 Vue 构建/部署；永久方案将构建放到一次性、非特权、无宿主机凭据、无内网访问、有限 CPU/内存/磁盘/网络的沙箱 Worker 中。固定模板、lockfile 和允许的构建命令，限制完整进程组超时并销毁工作目录。API JVM 不得启动包管理器。

### P0-3：生成物、源码和预览没有稳定的发布边界

`AppConstant.java:21-31` 使用 `user.dir/tmp` 作为输出/部署目录，部署 URL 固定为 `http://localhost`。`AppServiceImpl.java:144-177` 把源码或 `dist` 复制到本地部署目录后返回 localhost 地址；生产服务器、反向代理、静态域名和截图服务之间没有可验证的 URL contract。

整改方式：将输出根、发布根和公开域名全部配置化；发布采用“临时目录写入 -> 产物检查 -> 原子切换”；源码根不可公开；仅发布固定的已验证 `dist`/HTML 产物；部署 URL 由统一网关或独立静态域生成，不得返回 localhost。

当前 `StaticResourceController` 已有 token、路径规范化和 symlink 检查，但仍必须在动态测试中证明匿名、跨用户、越界、源码和配置文件均不可读。

### P0-4：截图服务是 SSRF 和浏览器逃逸边界

`lin-ai-code-screenshot/src/main/java/com/lin/linaicodemother/utils/WebScreenshotUtils.java:99-105` 直接让 Chrome 访问传入 URL，`ScreenshotServiceImpl` 没有看到目标 URL 的 scheme/host/私网/云元数据地址白名单校验。Chrome 还以 `--no-sandbox` 启动（同文件 `:134-143`）。

整改方式：截图只接受系统生成的短时签名 URL 或固定域名；拒绝 `file:`、`data:`、环回、RFC1918、链路本地、云元数据和重定向到上述地址；在独立非特权容器/账号中运行 Chrome，恢复 sandbox；固定浏览器版本和驱动，不在运行时下载驱动；设置并发、页面大小、请求数和总超时上限。

### P0-5：凭据和敏感数据处理不符合生产要求

本机 local profile 含真实格式的 AI/COS 凭据和 Nacos 默认口令；AI 配置还打开请求/响应日志。`UserServiceImpl.java:198-202` 使用固定盐 MD5 保存密码，不适合作为生产密码哈希。

整改方式：立即撤销并轮换已出现过的 AI、COS、Nacos、数据库和 Redis 凭据，并清理日志、镜像、备份和工单中的旧值；生产 Secret 由 Secret Manager/环境注入，缺失即启动失败；关闭请求/响应正文日志。密码迁移到 Argon2id 或 BCrypt，并对现有用户采用登录时渐进迁移。

### P0-6：测试门禁和可观测性不足

当前测试不通过；只有 App 服务提供简单业务健康接口，未发现统一的 liveness/readiness、依赖检查、指标、traceId、审计日志或告警配置。没有 CI/CD、回滚、备份恢复和容量基线。

整改方式：修复失败测试并将 `mvn clean verify` 设为发布门禁；加入 Spring Boot Actuator 的存活/就绪探针（敏感端点仅内网）、结构化日志和 traceId、Micrometer 指标、错误率/延迟/队列/AI 调用/磁盘/Chrome/Redis/MySQL 告警；补充备份恢复演练和可回滚版本策略。

## 四、次级但必须闭环的问题

- README 只描述本地 IDE 启动，没有服务器拓扑、端口白名单、反向代理、TLS、进程账号、目录权限、资源配额和回滚步骤。
- 没有数据库版本化迁移和唯一约束交付物；`user_account`、`deploy_key` 等并发一致性无法靠人工建表保证。
- 生产应通过网关统一暴露 HTTP；8124/8125/8127 和 Dubbo 50051/50052/50053 只允许内网安全组访问。
- SSE、AI 调用、构建任务和截图任务应有请求超时、取消、并发上限和状态持久化，不能依赖单个 HTTP 请求长期占用线程/连接。
- 预览页面必须使用独立站点和最小 iframe sandbox；前端 Markdown、`postMessage`、`v-html`、下载和新窗口流程需要单独完成 XSS/消息源校验。
- 当前工作区的生成内容、`target/` 和未跟踪文件不得进入部署包；必须从干净 clone 生成带版本号的制品并计算校验和。

## 五、推荐整改顺序

1. **立即止血（当天）**：下线公网 Vue 构建/部署/预览和截图入口；轮换所有本机及历史凭据；关闭模型正文日志；清理工作区生成物和 secrets。
2. **恢复可交付制品（1 个迭代）**：补齐 Spring Boot repackage、固定 JDK/Maven/Node、修复全部单元测试、补数据库迁移、补 Docker 或 systemd 运行定义和最小配置模板。
3. **建立安全边界（上线前）**：构建 Worker 沙箱、路径与 symlink 防护、静态发布域/签名 token、截图 URL allowlist、独立预览域、CSRF/CORS/会话策略、密码哈希升级。
4. **建立运维闭环（上线前）**：网关/TLS/安全组、探针、指标日志告警、备份恢复、灰度与回滚、容量压测和故障演练。

## 六、服务器部署验收门槛

以下条件全部满足才可从 NO-GO 变更为“可小流量生产”：

```bash
mvn clean verify
```

并且：

- 三个服务均生成带 `BOOT-INF`/`Main-Class` 的可启动 JAR，`java -jar` 可在干净服务器启动。
- 生产配置不激活 local profile，不含明文 Secret；Secret 缺失时启动失败。
- MySQL 迁移可重复执行，Redis/Nacos/COS/AI 连通性和权限已验证。
- API 只经 TLS 网关暴露；Dubbo/Nacos、管理端点和数据库不对公网开放。
- 恶意 `package.json`、绝对路径、`..`、symlink、源码读取、跨用户预览、SSRF、XSS、CSRF 和伪造消息的测试全部拒绝。
- Vue 构建和截图在隔离 Worker/容器内运行，资源、并发、超时和清理策略可观测。
- 发布、回滚、数据库备份恢复、日志检索和告警升级各至少演练一次。
- 以全新 clone、固定工具版本和无本地缓存环境重复构建，产物校验和可追溯。

## 七、建议的最小生产拓扑

```text
Internet
  -> TLS Gateway / WAF
      -> user HTTP (内网 8124)
      -> app HTTP  (内网 8125)
      -> static preview domain (独立域)

app -> MySQL / Redis / Nacos / AI Provider (私网或受控出口)
app -> Build Worker (一次性沙箱)
app -> Screenshot Worker (非特权 Chrome 沙箱)
Nacos、Dubbo、MySQL、Redis -> 仅内网安全组
```

在此之前，项目可以继续作为开发环境验证功能，但不能把“能在 IDE 启动”或“跳过测试能打包”解释为服务器部署资格。
