## Why

`guard-find-server` 目前是 ContiNew Admin 4.2.0 的改名分支,429 个 Java 文件全部是模板:有 Sa-Token 登录、MyBatis-Plus CRUD 栈、Liquibase、文件存储、短信、WebSocket,但**没有任何业务域**——没有 C 端用户、没有寻人档案、没有广场信息流、没有采集。而 `guard-find-app` 需要的正是这套业务接口。天下无拐一期必须在这具空骨架上建立"家属发布结构化寻人档案 → 人工审核 → 广场就近扩散 → 好心人提交线索 → 家属收通知并确认找回"的闭环,同时把公开站点的历史寻亲档案收敛进自有库。

详细产品与模块设计见工作区 `doc/天下无拐App整体功能规划书.md`;本 change 只覆盖其中一期(MVP)的后端范围。

## What Changes

- 新增 **C 端身份体系**:独立 `gf_app_user` 表 + 独立 Sa-Token 登录域(`loginType=app`),与后台 RBAC、部门、菜单完全隔离。支持短信验证码登录(未注册即自动建档)、账号密码、注销冷静期。**不复用 `sys_user`**,因为 `sys_user.dept_id NOT NULL` 且部门停用会硬阻断登录。
- 新增 **寻人档案域** `gf_find_case` 及媒体/别名/版本/状态日志子表,含完整状态机(`DRAFT → PENDING_REVIEW → PUBLISHED → FOUND/ARCHIVED/OFFLINE/BLOCKED/ERASED`)、黄金 72 小时紧急标记、90 天时效自动降级、疑似重复检测。
- 新增 **广场信息流与检索**:`/app/plaza/feed` 支持最新/紧急/就近(行政区)/喜报四种流、游标分页、多维筛选、公开视图强制脱敏(不输出详细地址与联系方式)。
- 新增 **线索域**:结构化线索提交、状态流转与回复,家属联系方式默认不公开。
- 新增 **C 端通知**:`gf_message` 站内信 + WebSocket 实时下发 + 通知偏好;事件目录含审核结果、新线索、找回确认、临期提醒。**厂商推送不在本 change 范围**(划入二期)。
- 新增 **内容治理**:举报、敏感词库、审核队列接口、`ERASED` 权利人删除(物理清除识别字段并保留匿名审计)。
- 新增 **可插拔寻亲数据采集框架**:`CollectSource` SPI + robots 强制校验 + 域名串行限速 + `maxPages` 上限 + 原始快照留痕 + 指纹去重 + 候选-审核-合并入库。内置 `rangaihuijia`(公开寻亲库,robots 全站放行)适配器与 `local-fixture` 离线样例源。**采集数据一律先落候选表,过人审后才对外可见。**
- 新增 Liquibase changelog 文件与 `APP` 客户端配置;**不修改任何已应用的 changeset**。

## Capabilities

### New Capabilities

- `app-account`: C 端注册、验证码/密码登录、会话与 token 策略、资料与安全、注销冷静期,以及与后台登录域的隔离边界。
- `find-case`: 寻人档案的结构化字段、媒体、状态机、时效与紧急标记、版本留痕、重复检测、脱敏视图。
- `plaza-feed`: 广场公共信息流的排序/筛选/搜索/游标分页与状态过滤、曝光埋点、统计口径。
- `case-lead`: 线索提交、限频与反滥用、线索状态流转与双向回复。
- `app-notification`: C 端事件目录、站内信、WebSocket 下发、通知偏好、未读计数与角标一致性。
- `content-moderation`: 举报、敏感词与诈骗话术识别、审核队列与处置、删除权与下架。
- `case-data-collection`: 采集源配置、robots 与限速合规、抓取-解析-归一-去重-候选、候选审核合并、删除与撤回、离线样例测试。

### Modified Capabilities

无(`openspec/specs/` 为空,本仓库首次建立规格)。

## Impact

- **代码**:新增 `com.guardfind.server.app.*`、`.findcase.*`、`.lead.*`、`.notification.*`、`.moderation.*`、`.collect.*` 包(必须含 `.model` 与 `.mapper` 段,否则 Mapper 扫描与类型别名失效);C 端控制器落在 `guard-find-server-api`。
- **数据库**:新增约 20 张 `gf_*` 表,新建 `db/changelog/mysql/guardfind_business.sql` 等文件并追加到 `db.changelog-master.yaml`;同步 `postgresql/` 镜像;新表登记进 `continew-starter.tenant.ignore-tables`。
- **配置**:`sys_client` 增加 `APP` 行;`sa-token.extension.security.excludes` 增加 `/app/**` 公开路径;新增 `collect.*` 配置段(源开关默认 `false`);凭据用 `${ENV:}` 空默认值。
- **依赖**:新增 `org.jsoup:jsoup`(纯 Java,三端与鸿蒙无关);不引入 Elasticsearch、不引入新调度组件(复用 SnailJob / `@Scheduled` 双模式)、不引入 HTTP 客户端(复用 Hutool `HttpRequest`)。
- **不受影响**:`sys_user` 及全部后台 RBAC/菜单/租户逻辑语义不变;`guard-find-backend` 与 `guard-find-frontend` 不在本 change 范围。
- **风险**:需先验证 WebSocket 能否按 `app` 域 token 寻址(不通则改 `gf_ws_session` 订阅模型);`spotless:apply` 会在 compile 阶段改写新增文件;当前 `surefire` 全局 skip,新增单测需显式开启。
