# 老林 AI 应用生成平台全栈代码评审报告

> 评审日期：2026-09-21
> 后端基线：`lin-ai-code-mother-microservices` 当前工作区
> 前端基线：`lin-ai-code-mother-frontend` 当前工作区
> 结论：**整体风险等级为严重（Critical），不建议在修复高危项前对公网开放。**

## 一、评审概述

### 1.1 技术栈与评审范围

后端是 Java 21、Spring Boot 3.5.3、Spring Session Redis、MyBatis-Flex、Dubbo/Nacos、LangChain4j、Redisson、Selenium/Chrome、腾讯云 COS 组成的 Maven 多模块系统；前端是 Vue 3、TypeScript、Vite、Pinia、Ant Design Vue、Axios、Markdown-It。

本次静态评审覆盖后端 7 个 Maven 模块约 7,970 行 Java、全部配置/Mapper/Prompt/测试，以及前端 `src` 下约 5,177 行 Vue/TypeScript、Vite/OpenAPI/依赖配置。排除生成物 `target`、`dist`、`node_modules`、`tmp` 和二进制图片。重点追踪了登录与会话、接口鉴权、AI 工具调用、代码生成与构建、静态预览、部署、截图、聊天历史、缓存、限流和前端渲染链路。

### 1.2 验证结果

| 验证项 | 结果 | 证据 |
|---|---|---|
| 前端生产构建 | 失败 | `vite.config.ts:18` 多出 `addApp`，esbuild 报 `Expected "}" but found "changeOrigin"` |
| 前端类型检查 | 失败 | 除配置语法错误外，`UserManagePage.vue:140` 把 `string` 传给 `number` |
| 前端 ESLint | 失败 | 15 个错误，包括生成 API 的 `@ts-ignore`、未使用参数、显式 `any` |
| 后端全量测试 | 失败 | `AppCreationQuotaServiceTest` 9 项中 4 项失败；实现使用 `insertSelective`，测试仍 mock/verify `insert` |
| 截图模块测试 | 失败 | 5 项中 4 项因 Mockito inline mock maker 无法在 JDK 21 自附加 Agent 而报错 |
| 第三方 CVE 在线扫描 | 未完成 | `npm audit` 因网络/DNS受限失败，提权审批基础设施也失败；本报告不据此宣称依赖无漏洞 |

### 1.3 总体风险与优化总结

当前存在 9 组高危问题，最严重的攻击链是：普通登录用户通过提示词影响 AI 工具参数 → 文件工具越过项目根目录读写宿主机文件 → AI 生成的 `package.json` 被宿主机直接执行 `npm install` 生命周期脚本 → 获得应用服务进程权限。另有公网 SQL 注入、存储型 XSS、同源不可信页面、任意项目源码公开、弱密码哈希、通配凭据 CORS、长期会话权限不刷新等风险。

整改原则分两类：

- **缺陷整改（必须修复）**：第 H/M 类条目，先切断 RCE、越权、注入、XSS 和数据泄露攻击链，再恢复构建与测试门禁。
- **迭代优化（提质升级）**：第 A/N 类条目，围绕异步任务、无状态化、数据库索引、依赖瘦身、可观测性和代码删减推进，不应阻塞紧急安全修复。

---

## 二、高危漏洞（优先修复，必须立即处理）

### H-01 AI 生成项目在宿主机执行依赖脚本，形成远程代码执行

- **问题描述 / 风险等级**：严重。用户可影响 Vue 项目文件，系统随后在应用服务宿主机执行 `npm install` 和 `npm run build`。npm 会自动执行 `preinstall/install/postinstall`，因此恶意 `package.json` 可运行任意系统命令。
- **举证依据**：
  - `lin-ai-code-ai/.../tools/FileWriteTool.java:27-46` 允许 AI 写入任意内容，包括 `package.json`。
  - `lin-ai-code-ai/src/main/resources/prompt/codegen-vue-project-system-prompt.txt:69-84,96-107` 明确要求 AI 创建 `package.json`。
  - `lin-ai-code-app/.../core/AiCodeGeneratorFacade.java:130-134` 工具调用完成后立即构建，且忽略构建返回值。
  - `lin-ai-code-app/.../core/builder/VueProjectBuilder.java:55-61,123-130` 在宿主机执行 `npm install`、`npm run build`。
- **漏洞原理与影响范围**：任意 Vue 项目生成请求均可触发。成功利用后可读取环境变量/AI 密钥/数据库口令、访问 Redis/Nacos、修改服务文件、横向访问内网，影响整个应用服务宿主机及其凭据可访问的外部系统。
- **详细修复步骤**：
  1. 立即临时关闭 Vue 工程生成与部署，直至隔离构建环境上线。
  2. 构建必须移到一次性、非特权容器/沙箱：只挂载单个项目目录，根文件系统只读，禁用宿主机 socket，限制 CPU/内存/PID/磁盘/时长，默认断网。
  3. 只允许服务端固定模板中的依赖与脚本；拒绝 AI 修改 `package.json` 的 `scripts`、`bin`、本地/URL/Git 依赖。
  4. 使用服务端固定 lockfile 执行 `npm ci --ignore-scripts`；不得在 API 服务 JVM 内启动包管理器。
  5. 构建产物仅回传 `dist`，沙箱结束后销毁工作目录。
- **修复校验方案**：构造含 `preinstall` 写标记文件的测试包，确认宿主机和项目外均无标记；验证容器内无法访问环境密钥、内网、宿主目录；超时/OOM 时任务被终止且 API 服务正常。
- **收益价值**：彻底切断从普通用户输入到宿主机命令执行的最高危链路，显著降低密钥泄露和横向移动风险。

### H-02 AI 文件工具可绝对路径和 `../` 越界读写删除

- **问题描述 / 风险等级**：严重。四个文件工具只在路径非绝对时拼接项目根目录，既接受绝对路径，也未对相对路径 `normalize` 后校验根目录，更未防符号链接逃逸。
- **举证依据**：
  - `FileReadTool.java:25-36`
  - `FileWriteTool.java:27-46`
  - `FileModifyTool.java:25-53`
  - `FileDeleteTool.java:27-47`
  - `FileDirReadTool.java:42-58`
  上述代码均使用 `Paths.get(input)`；绝对路径直接放行，相对路径 `projectRoot.resolve(input)` 后直接操作。
- **漏洞原理与影响范围**：提示词注入或模型误调用可传 `/etc/...`、应用配置绝对路径、`../../...` 或项目内符号链接，造成任意文件读取、覆盖和删除。删除工具的“重要文件名”黑名单只检查 basename，无法构成边界保护。
- **详细修复步骤**：
  1. 在所有工具进入文件系统前统一调用一个根路径解析函数，拒绝绝对路径和空路径。
  2. `root = ...toAbsolutePath().normalize()`；`target = root.resolve(input).normalize()`；若 `!target.startsWith(root)` 立即拒绝。
  3. 读取/修改/删除使用 `toRealPath(NOFOLLOW_LINKS)` 并再次校验根目录；写入时逐级拒绝符号链接，限制允许扩展名和单文件/项目总大小。
  4. 删除 basename 黑名单作为安全边界；关键模板文件是否可改由明确白名单决定。
- **修复校验方案**：为 `/etc/passwd`、`../../outside`、URL 编码变体、Windows 盘符、项目内指向外部的 symlink 建参数化测试；所有操作必须拒绝，合法项目内路径正常工作。
- **收益价值**：一次根因修复覆盖全部文件工具，阻断宿主机文件泄露和破坏，避免在每个调用方重复补丁。

### H-03 公网应用列表存在 ORDER BY SQL 注入

- **问题描述 / 风险等级**：高危。客户端可控 `sortField` 被直接作为 MyBatis-Flex 原生列拼入 SQL。
- **举证依据**：
  - `AppServiceImpl.java:321-344` 调用 `.orderBy(sortField, ...)`。
  - MyBatis-Flex 1.11.0 的该重载内部构造 `RawQueryColumn(column)`，内容原样输出，不是绑定参数。
  - `AppController.java:243-259` 的精选应用列表无需登录即可到达该逻辑。
  - `UserServiceImpl.java:155-165` 同样使用原始排序字段，虽当前仅管理员列表可达，也应同步修复。
- **漏洞原理与影响范围**：攻击者可传 SQL 表达式，例如时间型表达式，实施盲注或大量 `SLEEP` 查询拖垮连接池。影响应用库的可用性，并可能推断数据。
- **详细修复步骤**：
  1. 删除所有字符串直传 `orderBy`。
  2. 使用 `switch` 将 API 字段映射到固定 `QueryColumn`/方法引用，如 `createTime -> APP.CREATE_TIME`；未命中则使用固定默认排序或返回 400。
  3. `sortOrder` 仅接受 `ascend/descend`，不要允许任意 SQL 片段。
  4. 在网关/接口增加查询速率限制，但限流不能替代白名单。
- **修复校验方案**：请求 `{"sortField":"SLEEP(5)"}`、逗号、括号、注释符，均应 400 且 SQL 日志中不出现输入；合法字段生成预期 ORDER BY。
- **收益价值**：消除注入与数据库 DoS，查询计划更稳定，也统一前后端排序契约。

### H-04 AI 回复以原始 HTML 渲染，形成持久化 XSS

- **问题描述 / 风险等级**：高危。AI 回复被存入聊天历史，前端 Markdown 渲染显式开启 HTML，再经 `v-html` 注入 DOM。
- **举证依据**：
  - `src/components/MarkdownRenderer.vue:2` 使用 `v-html`。
  - 同文件 `20-23` 设置 `html: true`。
  - `src/pages/app/AppChatPage.vue:58-64` 把实时及历史 AI 内容传入该组件。
  - `JsonMessageStreamHandler.java:57-60` 和 `SimpleTextStreamHandler.java:35-38` 将完整 AI 回复持久化。
- **漏洞原理与影响范围**：模型输出或被提示词诱导输出 `<img onerror=...>`、SVG 事件等 HTML 后，应用所有者/管理员打开历史消息即执行脚本。可调用同源管理接口、读取页面数据并劫持会话操作。
- **详细修复步骤**：
  1. 最小修复：将 Markdown-It `html` 改为 `false`，代码块仍正常高亮，无需增加依赖。
  2. 若业务必须支持部分 HTML，再引入成熟 sanitizer 且只放行必要标签/属性/协议；禁止事件属性、`style`、SVG、MathML、`javascript:`。
  3. 加 CSP：禁止内联脚本与不受信脚本源，作为第二道防线。
- **修复校验方案**：实时回复和数据库历史分别注入事件属性、SVG、`javascript:` 链接；页面不得执行脚本，危险内容应转义为文本。
- **收益价值**：阻断聊天页面账号接管和管理员会话利用，同时保留 Markdown/代码展示能力。

### H-05 AI 生成页面与主站缺少浏览器隔离

- **问题描述 / 风险等级**：高危。不可信 HTML/JS 被直接放入无 `sandbox` 的 iframe；可视化编辑器还依赖同源 `contentDocument`，并对任意消息源使用 `postMessage('*')`。
- **举证依据**：
  - `AppChatPage.vue:183-189` iframe 未设置 `sandbox`。
  - `visualEditor.ts:120-133` 不校验 `event.source`、`event.origin` 和消息结构。
  - `visualEditor.ts:139-142` 向 `*` 发送消息。
  - `visualEditor.ts:151-167` 直接读取 iframe DOM，证明设计依赖同源能力。
  - `StaticResourceController.java:18-58` 由 API 服务直接托管生成内容。
- **漏洞原理与影响范围**：生成页可访问父页面 DOM、发起带 Cookie 的同源 API 请求、伪造元素选择消息；任何打开预览的用户均受影响。
- **详细修复步骤**：
  1. 生成内容迁移到独立站点/独立可注册域，不能与 API 共用 Cookie 域；该域不持有主站凭据。
  2. iframe 使用最小权限 `sandbox="allow-scripts"`，不要添加 `allow-same-origin`；按需追加能力而非全开。
  3. 可视化编辑改为受控桥接脚本，通过精确 `targetOrigin`、`event.source === iframe.contentWindow`、随机会话 nonce 和运行时 schema 校验通信。
  4. 生成站点设置严格 CSP、`Permissions-Policy`，禁止 top navigation、弹窗、下载等非必要能力。
- **修复校验方案**：生成页尝试读取 `window.parent.document`、调用主站 API、导航顶层窗口、伪造其他窗口消息，均必须失败；合法元素选择仍可工作。
- **收益价值**：把“执行用户/AI 代码”限制在浏览器安全边界内，避免预览功能成为主站会话接管入口。

### H-06 生成项目源码和文件可被公开读取，且静态路径无根目录校验

- **问题描述 / 风险等级**：高危。静态接口从 `tmp/code_output` 返回任意存在文件，既无登录/所有权校验，也不限于 `dist`，还直接拼接通配路径。
- **举证依据**：`StaticResourceController.java:22-23,29-58` 将 `{deployKey}` 和剩余路径拼为 `PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath` 后直接返回；`AppVO.java:33-60` 暴露生成类型和 ID，可推导目录名；前端 `env.ts:20-27` 也按 `{codeGenType}_{appId}` 构造路径。
- **漏洞原理与影响范围**：任何人可请求 Vue 项目的 `package.json`、`src/*`、配置文件等，绕过 `AppController.java:357-382` 的所有者下载权限；路径未做 canonical/normalize 边界校验，也存在目录穿越防护缺失。
- **详细修复步骤**：
  1. 预览接口只允许读取构建产物根目录；Vue 仅 `dist`，HTML 模式只允许固定 `index.html/style.css/script.js`。
  2. 使用 H-02 相同的规范化根路径校验，拒绝目录、符号链接和未知扩展名。
  3. 私有预览使用所有者鉴权或短时签名 URL；公开部署从独立静态域/object storage 提供。
  4. 返回 `X-Content-Type-Options: nosniff`、合适 CSP 和缓存策略。
- **修复校验方案**：匿名访问 `package.json`、`src/App.vue`、`../` 编码变体均 403/404；所有者预览固定产物成功；下载接口权限不再可绕过。
- **收益价值**：保护用户源码和配置，统一预览/下载权限边界，并消除文件路径类漏洞。

### H-07 密码使用固定盐 MD5，登录无防爆破，后台新用户使用统一默认密码

- **问题描述 / 风险等级**：高危。密码是单轮快速哈希且全站固定盐，泄露后可高速离线破解；登录接口没有账号/IP限流；管理员创建用户统一密码 `12345678`。
- **举证依据**：
  - `UserServiceImpl.java:191-195`：`md5Hex(password + fixedSalt)`。
  - `UserController.java:60-66`：登录接口无 `@RateLimit` 或等价机制。
  - `UserController.java:96-105`：固定默认密码。
  - `UserController.java:114-120`：管理员接口直接返回含 `userPassword` 的 `User` 实体。
- **漏洞原理与影响范围**：撞库/爆破和哈希库攻击成本低；一个哈希被破解可复用同盐字典攻击全部用户；后台创建账号在首次修改前可被猜中。
- **详细修复步骤**：
  1. 使用 Argon2id 或 BCrypt 的自带随机盐；禁止自行拼盐。登录成功时识别旧 MD5 并渐进重哈希。
  2. 登录按账号+可信客户端 IP 双维度限速，连续失败指数退避/短期锁定并记录审计事件。
  3. 后台创建用户改为一次性高熵激活令牌或随机临时密码，首次登录强制设置新密码。
  4. API 永不返回 `User` 实体，只返回明确 DTO；立即移除密码哈希字段。
- **修复校验方案**：同一密码两次注册哈希不同；旧用户可登录并自动迁移；超过阈值返回 429；后台创建账号无法用固定密码登录；响应 JSON 无 `userPassword`。
- **收益价值**：显著提高凭据破解成本，降低撞库和内部哈希泄露造成的批量账号接管风险。

### H-08 通配凭据 CORS + Cookie 会话 + 无 CSRF 防护

- **问题描述 / 风险等级**：高危。服务允许任意 Origin 携带凭据，业务使用长期 Cookie 会话，写操作没有 CSRF token；生成代码接口甚至以 GET 修改数据库/文件并消耗 AI 额度。
- **举证依据**：
  - `CorsConfig.java:14-23`：`allowCredentials(true)` 与 `allowedOriginPatterns("*")`。
  - `application.yml:7-10,29-32`：Redis Session 与 30 天 Cookie。
  - `AppController.java:66-95`：GET SSE 保存聊天、生成并写代码。
  - 项目未引入/配置 Spring Security CSRF。
- **漏洞原理与影响范围**：恶意站点或被接管子域可在受害者会话下读写 API；GET 生成接口还会被预加载器、日志、分享链接和跨站请求意外触发，消息出现在 URL/代理日志中。
- **详细修复步骤**：
  1. CORS 改为环境配置的精确 HTTPS Origin 列表，禁止 `*`；只开放实际需要的 header/method。
  2. 引入 Spring Security 或等价过滤器，对所有状态变更实施 CSRF token；Cookie 设置 `HttpOnly`、`Secure`、明确 `SameSite`，缩短有效期。
  3. 生成接口改 POST；若需流式，使用 `fetch` 读取响应流，或先 POST 创建任务再用不可猜的一次性 token 订阅 SSE。
  4. 注销必须 invalidate 整个 Session，而非只删属性。
- **修复校验方案**：非白名单 Origin 预检失败且无 CORS 响应头；无/错 CSRF token 的写请求 403；URL/网关日志不再出现用户提示词。
- **收益价值**：阻断跨站代操作、AI 费用滥用与 URL 敏感信息泄露，恢复标准 HTTP 安全语义。

### H-09 应用服务信任 30 天前的会话用户对象，权限撤销不生效

- **问题描述 / 风险等级**：高危。登录时完整 `User` 被写入 Redis Session；应用服务只验证 Session 对象的 ID，不查询当前用户。管理员被降权/用户被删除后，旧角色仍可在应用服务持续使用至会话过期。
- **举证依据**：
  - `UserServiceImpl.java:100-103` 把完整 User 放入 Session。
  - `InnerUserService.java:29-36` 静态方法直接返回 Session 中的 User。
  - `lin-ai-code-app/.../aop/AuthInterceptor.java:33-53` 管理员鉴权使用该旧角色。
  - `lin-ai-code-app/src/main/resources/application.yml:7-10,29-32` 会话最长 30 天。
  - 用户服务自己的 `UserServiceImpl.java:107-120` 会回查数据库，两个服务鉴权语义不一致。
- **详细修复步骤**：
  1. Session 只保存 `userId` 和最小会话元数据，禁止保存密码哈希/可变角色。
  2. 应用服务鉴权时通过 `InnerUserService.getById` 获取当前用户；不存在、禁用或角色不匹配立即拒绝并清理 Session。
  3. 中期将认证集中到网关/Spring Security；角色变更和封禁时主动撤销该用户全部 Session。
  4. 登录成功调用 `request.changeSessionId()` 防止会话固定。
- **修复校验方案**：管理员登录后降为普通用户，无需重新登录即失去所有管理接口；删除/封禁用户下一请求 401；登录前后 Session ID 不同。
- **收益价值**：确保权限变更实时生效，避免长期管理员权限残留，并减少 Redis 中敏感会话数据。

---

## 三、中危问题（限期修复，迭代内完成）

### M-01 代码保存/构建失败仍发送 `done`，前端显示假成功

- **证据与成因**：`AiCodeGeneratorFacade.java:151-170` 捕获解析/保存异常后只写日志；`130-134` 忽略 `buildProject` 的 `false`，随后完成 Flux；`AppController.java:88-95` 正常完成就追加 `done`。
- **影响**：用户看到生成完成，实际文件缺失或构建失败；随后预览 404，且错误难以追踪。
- **修复步骤**：保存/构建失败必须 `sink.error`/传播异常；只有产物存在且校验通过才完成流；前端区分业务失败与正常结束。
- **验收/收益**：故意制造解析和构建失败时收到 `business-error` 而非 `done`；消除假成功与脏状态。

### M-02 注册查重非原子，缺少数据库唯一约束与迁移基线

- **证据与成因**：`UserServiceImpl.java:50-71` 先 count 后 insert，两个并发请求可同时通过；仓库没有 Flyway/Liquibase/DDL，无法证明 `user_account` 唯一。
- **影响**：重复账号导致登录身份不确定；部署环境表结构不可重复验证。
- **修复步骤**：数据库添加 `UNIQUE(user_account)`，捕获重复键返回明确业务错误；引入最小 Flyway 迁移管理约束和索引。
- **验收/收益**：并发 20 次同账号注册只成功 1 次；数据库成为最终一致性边界。

### M-03 `@AuthCheck` 自调用失效，公开接口绕过管理员注解

- **证据与成因**：`UserController.java:126-129` 的公开 `/get/vo` 直接调用同类 `getUserById`；后者虽在 `114-116` 标注管理员权限，但 Spring AOP 不拦截 `this` 自调用。
- **影响**：未登录者可按 ID 获取账号、昵称、角色、简介等用户信息；与注释表达的权限意图不一致。
- **修复步骤**：公开接口直接调用 service 并明确其公开字段，或给 `/get/vo` 加实际需要的鉴权；不要依赖控制器方法互调复用安全规则。
- **验收/收益**：匿名请求按产品策略返回 401/最小公开资料；新增 MVC 权限回归测试。

### M-04 公共应用详情暴露非精选项目和初始化提示词

- **证据与成因**：`AppController.java:201-208` 无鉴权/公开状态检查；`AppVO.java:33-60` 包含 `initPrompt`、`deployKey`、`userId`；任何有效 ID 均可查询。
- **影响**：私有项目元数据、业务创意和提示词泄露；结合 H-06 可定位源码目录。
- **修复步骤**：拆分 `PublicAppVO` 与 `OwnerAppVO`；匿名仅返回已发布/精选且明确公开的最小字段，所有者/管理员接口返回完整信息。
- **验收/收益**：匿名查询非公开项目 404；公开响应不含 `initPrompt/deployKey`。

### M-05 分页参数缺少统一上下界，管理员接口可发起超大查询

- **证据与成因**：`PageRequest.java:14-29` 无校验；`UserController.java:165-177`、`AppController.java:318-330`、`ChatHistoryController.java:60-69` 无 pageSize 上限；普通列表只校验 `>20`，未校验 `<=0`。
- **影响**：大页查询占满数据库、网络和堆内存，负数导致异常；管理员会话被盗后可用于 DoS/批量导出。
- **修复步骤**：在统一入口约束 `pageNum >= 1`、普通接口 `1..20`、管理接口 `1..100`；DTO 使用 Bean Validation 并在控制器启用 `@Valid`。
- **验收/收益**：0、负数、超上限均 400；数据库不执行查询。

### M-06 删除应用吞掉聊天删除异常，造成孤儿数据

- **证据与成因**：`AppServiceImpl.java:353-369` 聊天删除失败仅记录日志，仍继续删除应用；无事务。
- **影响**：对话数据残留且失去正常访问入口，违反用户删除预期并增加隐私/存储风险。
- **修复步骤**：`removeById` 加事务，关联删除失败则整体回滚；或使用数据库外键级联/可靠异步清理，但不可静默成功。
- **验收/收益**：模拟聊天删除失败时应用仍存在；成功时两类记录一致删除。

### M-07 部署目录覆盖不清理旧文件，短 deployKey 也无唯一性保障

- **证据与成因**：`AppServiceImpl.java:136-164` 使用 6 位随机 key，并以 `copyContent(..., true)` 覆盖但不原子替换目录；未见 deployKey 唯一约束。
- **影响**：新版本删除的旧资源仍可公开访问；极端碰撞会覆盖其他应用部署目录；更新中可读到半成品。
- **修复步骤**：使用至少 128 bit 随机标识并建唯一索引；复制到临时目录、校验后原子 rename/swap，成功后清理旧目录。
- **验收/收益**：重新部署后废弃文件 404；并发部署只暴露一个完整版本；碰撞自动重试。

### M-08 游标分页只使用时间，可能丢失同一时间戳消息

- **证据与成因**：`ChatHistoryServiceImpl.java:188-190` 仅 `create_time < lastCreateTime`，排序也只按时间；同一时间值跨页时其余记录永远跳过。
- **影响**：聊天历史缺消息，审计记录不完整。
- **修复步骤**：游标包含 `(createTime,id)`，查询条件为 `time < t OR (time = t AND id < idCursor)`，排序 `create_time DESC,id DESC`，添加复合索引。
- **验收/收益**：同一时间插入超过 pageSize 条记录，多页遍历无重复无遗漏。

### M-09 限流在鉴权前执行，且 APPID 维度可被匿名请求消耗

- **证据与成因**：`AppController.java:66-77` 的 `@RateLimit` AOP 在控制器内部登录检查前运行；`RateLimitAspect.java:103-118` 只按公开 appId 计数。
- **影响**：匿名攻击者可持续请求公开 appId，提前耗尽该应用的 AI 限额，阻断真实所有者。
- **修复步骤**：先完成认证/所有权校验，再按 `userId:appId` 限流；认证失败不得消耗业务配额。
- **验收/收益**：匿名请求不改变限流器计数，不同用户/应用互不影响。

### M-10 缓存键在参数归一化前生成，可被无限变体污染且更新不失效

- **证据与成因**：`AppController.java:243-264` 以整个请求对象生成 key，随后才强制 priority；攻击者可改变被忽略字段制造大量等价 key。管理员更新/删除未 `CacheEvict`，数据最长陈旧 5 分钟。
- **影响**：Redis 内存和序列化开销上升，精选状态短期错误。
- **修复步骤**：缓存 key 仅包含验证后的 `pageNum/pageSize/sort`；精选变更、删除时清理对应缓存。数据量不大时可先删除该缓存，避免拥有复杂失效逻辑。
- **验收/收益**：等价请求只产生一个 key；精选修改后下一请求立即一致。

### M-11 AI/截图任务并发与资源缺少有界控制

- **证据与成因**：`AppServiceImpl.java:228-244` 每次部署新建虚拟线程；`WebScreenshotUtils.java:99` 用 `synchronized` 串行单浏览器；同一 app 可并发开启多个生成流并写同一目录。
- **影响**：大量虚拟线程排队、重复截图、文件互相覆盖、AI 成本失控；截图服务形成单点瓶颈。
- **修复步骤**：为生成/构建/截图设置有界队列和每用户/每 app 单飞锁；重复任务合并；暴露排队/失败状态和队列指标。
- **验收/收益**：并发压测下活跃任务、队列、内存有硬上限，同 app 不并行写目录。

### M-12 配置含默认 root/Nacos 凭据，AI 请求日志被硬编码开启

- **证据与成因**：应用/用户 YAML 写有 MySQL `root/root` 和 Nacos `nacos/nacos`；`ReasoningStreamingChatModelConfig.java:50-51` 忽略配置字段，强制 `logRequests(true)`、`logResponses(true)`。
- **影响**：误用默认配置时基础设施可被接管；生产日志持久化用户提示词和生成代码，可能含隐私/密钥。
- **修复步骤**：所有凭据只从 Secret/环境变量注入且无默认值，启动时缺失即失败；生产关闭模型请求/响应正文日志并做字段脱敏；轮换已使用默认口令的环境。
- **验收/收益**：仓库扫描无真实/默认凭据；生产日志不出现 prompt、源码和 Authorization/API key。

### M-13 HTTP 错误统一返回 200，监控和客户端无法依赖协议语义

- **证据与成因**：`GlobalExceptionHandler.java:23-41` 返回普通 `BaseResponse`，没有设置 HTTP 状态。
- **影响**：429/401/403/500 在代理、监控、重试、缓存层均被视为成功，告警和限流行为失真。
- **修复步骤**：异常处理返回 `ResponseEntity` 或 `@ResponseStatus` 映射真实状态；SSE 在提交响应前失败用 HTTP 状态，提交后用明确事件并关闭流。
- **验收/收益**：接口契约测试验证状态码；APM 可准确统计失败率和告警。

### M-14 前端管理功能存在确定性业务缺陷

- **证据与成因**：
  - `GlobalHeader.vue:88` 路径 `/admin/ChatManage` 与路由 `router/index.ts:40` 的 `/admin/chatManage` 大小写不一致。
  - `ChatManagePage.vue:16` 使用 `assistant`，后端枚举 `ChatHistoryMessageTypeEnum.java:12-13` 使用 `ai`。
  - `ChatManagePage.vue:182-191` 删除操作不调用后端，直接提示成功。
  - `UserLoginPage.vue:53-56` 忽略权限守卫传入的 redirect，登录后总回首页。
- **影响**：管理员菜单跳转失败、AI 消息筛选为空、删除形成虚假成功、用户登录后不能返回目标页。
- **修复步骤**：统一路由常量和枚举；未实现删除前移除按钮，或补齐管理员删除 API；仅接受站内相对路径作为登录 redirect。
- **验收/收益**：四条 UI E2E 路径全部通过，避免错误运营操作。

### M-15 构建与测试门禁当前不可用

- **证据与成因**：`vite.config.ts:18` 存在语法残片；`UserManagePage.vue:136-140` ID 类型不匹配；`AppCreationQuotaService.java:40,46` 使用 `insertSelective` 而测试 `AppCreationQuotaServiceTest.java:57,67,91,96` 仍 mock/verify `insert`；截图测试缺 JDK 21 Mockito Agent 配置。
- **影响**：任何提交都无法得到可信绿灯，回归缺陷会继续进入主分支。
- **修复步骤**：修复语法和类型；测试与真实方法对齐；按 Mockito 官方方式在 Surefire 配置 `-javaagent` 或改用无需 inline mock 的测试替身；CI 强制 `mvn clean verify`、`npm ci && npm run build && eslint`。
- **验收/收益**：干净环境连续两次全绿，构建不依赖 IDE/本机缓存。

---

## 四、低危问题（酌情修复，日常迭代优化）

### L-01 输入校验只散落在前端或业务方法

- **依据**：DTO 基本无 Bean Validation；`AppUpdateRequest.appName`、头像/封面 URL、管理员字段缺服务端长度与格式约束。
- **方案/收益**：在 DTO trust boundary 增加 `@NotBlank/@Size/@Min/@Max` 并统一错误响应；防止超长数据、数据库异常和规则漂移。

### L-02 Session 注销不彻底

- **依据**：`UserServiceImpl.java:134-142` 只 `removeAttribute`，Session ID 和其他属性仍保留。
- **方案/收益**：调用 `request.getSession(false).invalidate()`；减少旧会话残留。

### L-03 前端事件监听器未正确释放

- **依据**：`HomePage.vue:134-155` 在 `onMounted` 回调里 `return` 清理函数，Vue 不会使用该返回值；`AppChatPage.vue:760-769` 匿名 message listener 无法移除，局部 EventSource 也未在卸载时关闭。
- **方案/收益**：具名 handler + `onUnmounted` 移除；EventSource 提升为组件变量并关闭，避免重复监听和后台计费流。

### L-04 `window.open` 未切断 opener

- **依据**：`HomePage.vue:128`、`AppChatPage.vue:680,687`、`AppEditPage.vue:267` 使用 `_blank` 未传 `noopener`。
- **方案/收益**：`window.open(url, '_blank', 'noopener,noreferrer')`；防止目标页反向控制来源页。

### L-05 全局异常日志等级和错误内容不合理

- **依据**：`GlobalExceptionHandler.java:23-41` 将参数错误等业务异常完整堆栈记为 error；`AppServiceImpl.java:165-166` 把底层异常 message 返回客户端。
- **方案/收益**：预期业务异常记 warn/结构化字段，系统异常使用 traceId 且对外固定文案；降低日志噪声和内部路径泄露。

### L-06 静态资源 MIME 识别不完整

- **依据**：`StaticResourceController.java:67-73` 仅识别少量扩展名，SVG、webp、字体、JSON 等均为 octet-stream。
- **方案/收益**：使用 Spring/Java `Files.probeContentType` 后叠加安全白名单和 `nosniff`；改善资源兼容性，避免手写映射继续膨胀。

### L-07 注释、命名和包结构存在噪声

- **依据**：`ratelimter` 拼写错误；多个 `AuthInterceptor` 文件尾有大量空行；大量 `@Author/@Date` 不提供维护价值；中英文空格/格式不统一。
- **方案/收益**：修正包名（一次完成引用迁移）、删除模板注释和空行、保留解释“为什么”的注释；减少搜索噪声和误导。

---

## 五、架构&性能优化建议（长期优化）

### A-01 把代码生成改成异步任务，而不是占用 HTTP/SSE 回调

- **现状证据**：模型流完成后同步构建，单次 `npm install` 最长 300 秒、build 180 秒（`VueProjectBuilder.java:78-90`）；进程输出也未消费，缓冲区满时可能假死。
- **分步方案**：API 仅创建任务并返回 taskId；worker 在隔离沙箱生成/构建；Redis/DB 记录 `QUEUED/RUNNING/SUCCEEDED/FAILED`；SSE 只订阅状态；进程必须合并并持续消费 stdout/stderr。
- **收益**：请求线程和模型回调不再阻塞，超时/重试/取消可控，吞吐和故障隔离显著提升。

### A-02 生成物从本地磁盘迁移到版本化制品存储

- **现状证据**：`AppConstant.java:18-31` 把输出/部署目录和 host 硬编码到当前进程工作目录；多实例间看不到彼此文件，重启/迁移也可能丢失。
- **分步方案**：短期把目录配置化并固定单实例；中期 worker 上传不可变制品到 COS/对象存储，数据库记录 artifact version；部署通过 CDN/静态域切换版本。
- **收益**：应用服务无状态化，可水平扩展、回滚和审计，部署不再依赖某台机器磁盘。

### A-03 建立数据库迁移和关键索引

- **建议索引/约束**：`user(user_account UNIQUE)`；`app(deploy_key UNIQUE)`；`app(user_id,code_gen_type,is_delete)`；`app(priority,create_time,id)`；`chat_history(app_id,create_time,id,is_delete)`。
- **步骤**：先采样慢 SQL/重复数据，清洗后以 Flyway 迁移上线；索引前后用 `EXPLAIN ANALYZE` 和 P95 对比。
- **收益**：保证身份/部署唯一性，降低分页、额度和聊天历史查询扫描量。

### A-04 统一认证授权边界

- **现状证据**：user/app 各复制一份 AOP；一个会回查数据库，一个信任 Session，安全语义已经漂移。
- **分步方案**：先用 Spring Security FilterChain 统一身份解析和 DTO；再由网关完成外部认证，服务内用受认证 principal；Dubbo 开启服务鉴权/mTLS，内部接口不返回完整 User。
- **收益**：删除重复 AOP，权限撤销、审计和错误语义统一，减少下一次越权缺陷。

### A-05 缩减父 POM 和 common 模块的传递依赖

- **现状证据**：根 POM 把 Web/AOP/Knife4j/Hutool 作为所有子模块的直接依赖；common 又携带 COS SDK、MyBatis codegen、Hikari，导致 model/client 等纯模型模块也继承运行时无关组件。
- **分步方案**：父 POM 只保留 `dependencyManagement`；每个运行服务声明自己的 starter；COS 类/依赖移到 screenshot；代码生成器移到独立开发工具或 Maven profile。
- **收益**：减少启动类路径、镜像体积、CVE 攻击面和依赖冲突，构建边界更清晰。

### A-06 优化 AI 成本与并发控制

- **现状证据**：创建应用先调用 AI 路由，之后才检查创建额度（`AppServiceImpl.java:202-216`）；同 app 可并发生成；服务缓存最多 1,000 个实例且每个保留聊天记忆关联。
- **分步方案**：AI 调用前做用户总额度/余额预检并预留配额；每 app 单飞；设置全局模型并发、token/日预算；完成/删除应用时主动失效服务缓存。
- **收益**：防止无效路由和重复生成付费，成本可预测，文件一致性更好。

### A-07 截图服务使用受限容器和有界 worker

- **现状证据**：`WebScreenshotUtils.java:134-142` Chrome 使用 `--no-sandbox` 渲染不可信生成页；单 WebDriver 全局复用且串行。
- **分步方案**：恢复 Chrome sandbox，运行于非特权容器；限制网络目的地/协议/重定向；有界 worker 池，每任务新隔离 context/超时，失败重建；只接受服务端签名的部署 URL。
- **收益**：降低浏览器逃逸和 SSRF 面，提升并发可预测性与会话隔离。

### A-08 增加可观测性与容量基线

- **现状证据**：健康接口仅返回 `ok`，没有数据库/Redis/Nacos/队列/磁盘/AI 延迟指标。
- **分步方案**：启用 Actuator 受保护端点；采集 API、DB、Redis、Dubbo、AI token、构建耗时/失败率、队列长度、磁盘占用；为错误统一 traceId，并建立 P95/P99 与预算告警。
- **收益**：能在用户报障前定位资源瓶颈，支撑容量规划和优化验收。

### A-09 缓存应以测量为前提

- **现状证据**：精选列表缓存引入 Redis 多态序列化与失效复杂度，但暂无命中率/延迟数据；`RedisCacheManagerConfig.java:29-33` 开启默认多态类型信息，扩大反序列化风险面。
- **分步方案**：先采集查询 P95/缓存命中率；数据量小时直接删除缓存；确需缓存则使用明确 DTO serializer、固定 key 和事件驱动失效，禁止宽泛默认类型。
- **收益**：减少无收益复杂度与反序列化攻击面，保留真正有效的缓存。

---

## 六、代码规范优化建议

### 6.1 Ponytail 全仓删减清单

以下为优化建议，不属于必须修复的缺陷：

1. `delete:` 删除已废弃且零调用的 `core/CodeParser.java`（约 91 行）与 `core/CodeFileSaver.java`（约 80 行），替代物已在 `core/parser`、`core/saver` 使用。
2. `delete:` 删除前端未引用的 `src/stores/counter.ts`（12 行）。
3. `delete:` 删除空的三个 Mapper XML；BaseMapper 已覆盖当前能力，替代物为空。
4. `delete:` 删除零调用的 `VueProjectBuilder.buildProjectAsync`、`AiCodeGeneratorFacade.generateAndSaveCode`、`AiCodeGenTypeRoutingService.routeCodeGenType` 及仅为它存在的提示词（确认无外部反射调用后）。
5. `shrink:` `SimpleTextStreamHandler` 无状态，不需要每次 `new` 一个策略层；直接把两段流处理保留为两个私有方法即可。若未来真有第三种协议再抽象。
6. `native:` DTO 校验使用 Jakarta Validation，路径/MIME 使用 `java.nio.file.Path/Files`，不要继续增加 Hutool 包装。
7. `yagni:` 移除 `AppModuleMapper` 上未实际参与嵌套映射的 `uses = UserModuleMapper.class`；用户字段本就由 service 手动设置。

保守估算可直接删除 **约 230-300 行、3 个空资源文件，并从生产类路径移除至少 2 组开发/存储依赖**。实际净值应以删除后 `mvn clean verify` 和 `npm run build` 为准。

### 6.2 规范统一

- API DTO 使用明确入参/出参类型，禁止 Controller 返回数据库实体。
- 所有公共集合/分页接口统一字段命名：前端传 Java 属性名，后端白名单映射数据库列；禁止前端直接知道 `create_time`。
- 使用构造器注入，移除 `@Resource`/字段注入残留；删除无用 import。
- `ErrorCode` 同时维护业务码和 HTTP status，避免两套错误协议漂移。
- 生成 API 文件应在 ESLint 中以生成目录为单位排除，或修正生成模板，不要在每个文件放 `@ts-ignore`。
- `package.json` 明确声明直接使用的 `@ant-design/icons-vue`、`dayjs`；当前 `npm ls --depth=0` 显示两者均不是直接依赖，不能依赖传递依赖偶然提升。
- 注释说明约束和原因，不重复翻译代码；删除时间/作者模板、过时 TODO 和“后续改用”但未删除的旧实现。
- 测试命名保持 `should...`；安全边界至少覆盖路径、鉴权、SQL 排序白名单、XSS、CSRF、并发唯一性六类回归。

---

## 七、双维度落地清单（问题改进+体系优化）

### 7.1 问题改进清单（漏洞/Bug，必须修复）

| 优先级 | 类型 | 核心诉求 | 落地执行步骤 | 验收标准 | 迭代周期 |
|---|---|---|---|---|---|
| P0 | 安全/RCE | 禁止宿主机执行 AI 生成脚本 | 暂停 Vue 构建；迁移隔离 sandbox；固定依赖/lockfile；`--ignore-scripts` | 恶意 lifecycle 脚本无法在宿主和沙箱外执行 | 24 小时止血，1 周完成 |
| P0 | 安全/文件 | 封闭 AI 文件根目录 | 统一 Path normalize/realpath/startsWith；拒绝绝对路径/symlink | 越界路径矩阵全部拒绝 | 24 小时 |
| P0 | 安全/注入 | 消除排序 SQL 注入 | 固定字段到 QueryColumn 的 switch 白名单 | 注入输入 400，SQL 无原文 | 24 小时 |
| P0 | 安全/XSS | 禁止 AI HTML 进入主站 DOM | Markdown `html:false`；加 CSP 回归测试 | XSS payload 不执行 | 24 小时 |
| P0 | 安全/隔离 | 隔离生成页面 | 独立域 + sandbox iframe + 精确 postMessage | 生成页不能读父 DOM/调用主站凭据 API | 3-5 天 |
| P0 | 安全/泄露 | 禁止匿名读取源码 | 预览只读固定产物；权限/签名 URL；路径边界 | 匿名源码请求 403/404 | 24-48 小时 |
| P0 | 认证 | 升级密码与防爆破 | Argon2id/BCrypt；旧哈希迁移；登录限流；去默认密码 | 哈希随机盐、爆破 429、无固定密码 | 3-5 天 |
| P0 | Web 安全 | 收紧 CORS/CSRF | 精确 Origin；CSRF；生成改 POST；安全 Cookie | 跨站写失败，无 token 403 | 3-5 天 |
| P0 | 授权 | 权限撤销实时生效 | Session 仅 userId；每次加载当前用户；角色变更撤销 Session | 降权后下一请求立即 403 | 2-3 天 |
| P1 | 数据一致性 | 注册唯一 | 唯一索引 + 重复键处理 + 迁移 | 并发注册仅 1 个成功 | 本迭代 |
| P1 | 鉴权 | 修复 AOP 自调用绕过 | 公开接口独立授权/DTO；加 MVC 测试 | 匿名访问符合明确策略 | 本迭代 |
| P1 | 隐私 | 隐藏私有 App/prompt | Public/Owner DTO 分离；公开状态检查 | 非公开 404，公开 DTO 最小化 | 本迭代 |
| P1 | 可靠性 | 失败不得假成功 | 保存/构建异常向 SSE 传播；产物校验后 done | 失败只有 error，无 done | 本迭代 |
| P1 | 数据一致性 | 删除原子化 | 事务或 DB 级联；取消吞异常 | 失败回滚、无孤儿聊天 | 本迭代 |
| P1 | 分页/DoS | 统一分页边界 | DTO Validation + 控制器 `@Valid` | 非法页码不访问 DB | 本迭代 |
| P1 | 部署 | 原子部署与唯一 key | 128 bit key + 唯一索引 + 临时目录 swap | 无陈旧文件/半成品/覆盖 | 本迭代 |
| P1 | 业务 | 修复聊天游标 | `(time,id)` 游标 + 复合索引 | 全量遍历无重无漏 | 本迭代 |
| P1 | 限流 | 认证后按用户+app限流 | 调整 AOP/过滤器顺序和 key | 匿名不消耗配额 | 本迭代 |
| P1 | 配置 | 移除默认凭据/正文日志 | Secret 注入；关闭模型正文日志；轮换口令 | 扫描和日志无敏感值 | 本迭代 |
| P1 | 前端 Bug | 恢复管理员真实功能 | 修路由/枚举；删除按钮接 API 或移除；安全 redirect | 对应 E2E 全过 | 本迭代 |
| P1 | 工程质量 | 恢复全绿门禁 | 修 Vite/type；对齐 mock；配置 Mockito Agent | Maven、build、type、lint 全绿 | 1-2 天 |
| P2 | 会话 | 注销彻底 | invalidate Session | 旧 cookie 下一请求 401 | 下个迭代 |
| P2 | 前端资源 | 清理 listener/stream | `onUnmounted` 移除并关闭 | 反复进出页面无监听增长 | 下个迭代 |
| P2 | 错误协议 | 返回真实 HTTP status | 异常到 status 映射 | 401/403/429/500 契约测试通过 | 下个迭代 |

### 7.2 体系优化清单（架构/性能/规范升级）

| 优先级 | 类型 | 核心诉求 | 落地执行步骤 | 验收标准 | 迭代周期 |
|---|---|---|---|---|---|
| P1 | 架构 | 构建任务异步化 | taskId + worker + 状态机 + 有界队列 | API 快速返回；任务可取消/重试 | 1-2 个迭代 |
| P1 | 架构 | 服务无状态化 | 版本化制品上传 COS；独立静态域/CDN | 多实例任意节点可预览/部署 | 2 个迭代 |
| P1 | 数据 | 迁移与索引体系 | Flyway + 唯一/复合索引 + EXPLAIN 基线 | 新环境一键建库；P95 达标 | 1 个迭代 |
| P1 | 安全架构 | 统一认证授权 | Spring Security/网关 principal；Dubbo mTLS/鉴权 | 删除重复 AOP；权限语义一致 | 1-2 个迭代 |
| P2 | 性能/成本 | AI 并发与预算 | 预留额度；单飞；token/日预算；缓存主动失效 | 成本与并发有硬上限 | 1 个迭代 |
| P2 | 性能 | 有界截图 worker | sandbox Chrome；URL allowlist；队列/超时 | 压测无无界线程，失败隔离 | 1 个迭代 |
| P2 | 依赖 | Maven 模块瘦身 | 父 POM 仅 dependencyManagement；拆 COS/codegen | dependency tree 无跨模块无关 SDK | 1 个迭代 |
| P2 | 缓存 | 删除或简化精选缓存 | 先测命中率；无收益则删除；否则固定 DTO/key | 有量化 P95/命中率收益 | 下个迭代 |
| P2 | 可观测性 | 建立运行基线 | Actuator、traceId、AI/构建/队列/磁盘指标 | 仪表盘与告警演练通过 | 1 个迭代 |
| P3 | 规范/删减 | 清理死代码与空层 | 删除旧 parser/saver/counter/XML/零调用方法 | 构建全绿，净减约 230-300 行 | 日常迭代 |
| P3 | 规范 | 统一 DTO/枚举/路由/依赖声明 | 生成模板、Validation、直接依赖、命名整改 | lint 0 error；API 契约一致 | 日常迭代 |

### 7.3 建议执行顺序

1. **第一天止血**：关闭 Vue 宿主机构建；修路径边界、SQL 排序、Markdown HTML、静态源码访问。
2. **第一周安全闭环**：完成隔离构建域、预览独立域、密码迁移、CORS/CSRF、会话权限刷新。
3. **本迭代稳定性**：恢复 CI，全量修复假成功、事务、分页、游标、部署原子性和管理端 Bug。
4. **后续两个迭代演进**：异步 worker、对象制品、统一认证、迁移索引、依赖瘦身、可观测性。

完成 P0 前不得公网发布；完成 P1 且全量门禁连续稳定后，方可进行受控灰度。
