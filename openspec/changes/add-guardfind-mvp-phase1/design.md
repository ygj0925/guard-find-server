## Context

`guard-find-server` 是 ContiNew Admin 4.2.0-SNAPSHOT 的改名分支(根包 `com.guardfind.server`,`top.continew.starter` 2.15.0 作为父 POM),Java 17 + Spring Boot 3.4.10 + MyBatis-Plus 3.5.14 + Sa-Token 1.44.0(jwt-simple,Redis 会话)+ Liquibase + Redisson/JetCache + SMS4J + X File Storage + SnailJob。429 个 Java 文件全部为模板,**无任何业务域**;`sys_*` 22 张表只服务后台 RBAC、字典、文件、消息、公告、日志。

约束(来自现状代码,非文档描述):

- Mapper 扫描与类型别名 glob 依赖 `${base-package}.**.mapper` 与 `${base-package}.**.model` 两个段名,新代码包路径不可偏离。
- 已应用的 Liquibase changeset 受 MD5 校验保护,而现有每个 SQL 文件恰好只有一个 `-- changeset`,因此新增表必须新建文件并在 master 追加 `include`。
- `sys_user.dept_id NOT NULL`,且 `AbstractLoginHandler.checkUserStatus()` 在部门停用时阻断登录;`GET /auth/user/route` 把角色映射成后台菜单。C 端用户不能落在 `sys_user` 上。
- 控制器返回裸 DTO,由 `@EnableGlobalResponse` 统一包裹 `{code,msg,data}`,HTTP 状态保持 200。
- `spotless:apply` 绑定在 `compile` 阶段,会为新文件补上游 Apache 头并按 P3C 格式化。
- 根 POM 将 `maven-surefire-plugin` 全局 `skip=true`,CI 只做 `compile` / `package`。
- 无 `RestTemplate` / `WebClient` Bean;外部 HTTP 走 Hutool `HttpRequest`(`plugin-schedule` 的 `JobClient` 是现成先例)。无 MapStruct,DO↔Resp 靠 `BeanUtil.copyProperties`。
- 调度任务遵循 `job/NoticePublishJob` 的双嵌套类形态:`snail-job.enabled=false`(dev 默认)走 `@Scheduled`,否则走 `@JobExecutor`。

## Goals / Non-Goals

**Goals:**

- 建立与后台 RBAC 完全隔离的 C 端身份域,支撑验证码登录 + 自动建档 + 设备登记。
- 建立寻人档案核心域与状态机,含媒体、版本、状态日志、时效与紧急标记。
- 提供公开可浏览的广场与搜索,并在服务端强制状态过滤与字段脱敏。
- 提供线索提交/处置/回复闭环与反滥用限流。
- 提供 C 端站内信与实时下发,事件文案集中定义。
- 提供可插拔采集框架、`rangaihuijia` 适配器、`local-fixture` 离线样例源,以及候选-审核-发布链路。
- 全部对外读接口在无外网、无 Redis 会话条件下也可通过单测验证核心逻辑(解析、归一、去重、状态机、可见性判定)。

**Non-Goals:**

- 厂商推送(FCM / APNs / Harmony Push Kit)、地图与地理半径检索、社区(帖子/评论/关注)、SOS 紧急扩散、人脸识别与跨年龄老化、后台审核界面。
- 引入 Elasticsearch / 新建 Maven 模块 / 改动 ContiNew 模板既有类的语义。
- 把采集数据直接发布到广场(必须过人审)。

## Decisions

**D1 — C 端身份:`gf_app_user` + 独立 Sa-Token 登录域,而非 `sys_user` 加 `user_type`。**
用 `StpBizUtil = new StpLogic("app")` 建立独立 token 空间,`sys_client` 增加 `APP` 行以承载 token 时效/并发策略。备选(加 `user_type` 列)被否:它要求为 C 端用户伪造部门以绕过 `dept_id NOT NULL`,并且后台 RBAC、菜单路由、租户隔离都会与 C 端流量纠缠,越权面显著变大。代价是需要新增 `AppAuthController` 而不复用 `AuthController` 的多态 `LoginReq`,可接受。

**D2 — 业务代码落在既有模块,不新建 Maven 模块。**
`app / findcase / lead / notification / moderation` 放 `guard-find-server-system`(与 `auth`、`system` 平级),C 端控制器放 `guard-find-server-api`(该模块已是部署单元且已容纳 `CaptchaController`、`DashboardController`)。采集域放 `guard-find-server-api` 的 `collect` 包,因为它依赖 `job` 目录既有的双模式调度形态。备选(新建 `guard-find-server-business`)被否:要改 5 处 POM 与 CI 矩阵,收益不足。

**D3 — 广场分页用游标而非 ContiNew 的 `PageResp`。**
`PageResp{records,total}` 依赖 `count(*)`,在信息流深翻时既慢又会在写入时漂移。新增 `FeedCursorResp{records,nextCursor,hasMore}`,游标为 `Base64(sortKey + id)`。管理端列表(审核队列、采集日志、候选、我的档案)仍用现成 `PageResp`,不重复造轮子。

**D4 — 状态机集中在 `CaseStatusEnum` + `CaseStatusTransfer` 表驱动。**
非法跃迁在单一处判定,避免散落 `if (status == ...)`。所有跃迁经 `CaseStatusService.change(caseId, target, reason, operator)`,同事务写 `gf_case_status_log` 并发通知。备选(状态机框架如 Cola StateMachine)被否:引依赖换不到等值收益。

**D5 — 敏感字段列加密 + 响应脱敏双保险。**
手机号/身份证沿用 `continew-starter.encrypt.field` 的 AES 列加密与 `security-mask` 注解。公开视图是**另一个 DTO**(`CasePublicResp`)而不是运行时裁剪,确保不可能通过换接口拿到未脱敏字段。

**D6 — 采集:原文快照与候选分离,候选再分离出档案。**
`gf_collect_raw`(唯一键 `source_code + source_id`)保证可追溯与幂等,`gf_collect_candidate` 承载归一化与 `dedupe_key`,`gf_find_case` 只在审批后写入并保留 `source_type/source_id/source_url`。好处:删除请求可按来源批量定位;站点改版时原始数据不丢,可重解析而不重新抓取。

**D7 — 采集解析用 Jsoup + CSS selector,配置化留给后续。**
新增 `org.jsoup:jsoup`(纯 Java,无原生依赖,对鸿蒙零影响)。`RangAiHuiJiaSource` 内以常量定义选择器并配合宽松取值(字段缺失即跳过该字段而非整条)。备选(DSL 化配置选择器入库)被否:一期只有两个源,过度设计。

**D8 — 采集测试不依赖外网。**
`local-fixture` 源读取 `src/test/resources/collect/fixtures/*.html`(按真实页面结构构造的合成样例,覆盖缺日期、字段错位、脏 HTML、404、超时、分页边界),与真实适配器共用同一 `CandidateMapper`。真实网络的 `RangAiHuiJiaSource` 单测只在显式 profile 下执行,不进默认路径。

**D9 — 通知按 `user_id` 定向,不复用 `sys_message`。**
`sys_message` 是后台广播语义 + 租户隔离 + 收件人指向 `sys_user`。新增 `gf_message`。实时通道先验证既有 `WebSocketUtils.sendMessage(token, payload)` 能否按 `app` 域身份寻址;不能则引入 `gf_ws_session`(连接建立时登记 `user_id` → session,按 `user_id` 定向)。

**D10 — 单测绕过全局 surefire skip 的方式。**
新增测试为纯 JUnit 5 + Mockito 切片(不起 Spring 上下文,不需要 MySQL),通过 `mvn test -Dsurefire.skip=false -DskipTests=false` 执行,并在 CI 增加一条执行该命令的作业。既有 `GuardFindServerApplicationTests.contextLoads()` 仍需活库,不纳入默认路径。

## Risks / Trade-offs

- [WebSocket 无法按 `app` 域 token 寻址] → 实施第一步即验证;不通则按 D9 引入 `gf_ws_session`,该决策不阻塞其余模块。
- [目标站点不可达或改版(实测 `baobeihuijia.com` 已返回 521)] → 解析失败降级为"记录并跳过 + 告警",绝不让主服务受影响;`local-fixture` 保证链路可常绿验证。
- [采集授权未确认即上线] → 源 `enabled` 默认 `false` + robots 必查 + 域名串行限速 + `maxPages` 上限 + UA 明示;代码层不提供"忽略 robots"的开关。
- [`gf_message` 与 `sys_message` 双轨造成认知负担] → 命名与包路径分离(`notification` 域),并在 README 记录取舍。
- [`spotless:apply` 改写新文件] → 接受该行为,提交前跑一次 `mvn compile` 让格式落定,不使用 `-Dspotless.apply.skip` 绕过。
- [游标分页与后台 `PageResp` 两套并存] → 限定游标只用于信息流类接口,列表类一律 `PageResp`。
- [一期表数量大(约 20 张)] → 全部走独立 changelog 文件 + 幂等 `CREATE TABLE IF NOT EXISTS`,便于分环境灰度与回滚。
- [C 端公开读接口被批量爬取] → 广场与详情按 IP + 设备限流,不开放任何列表型导出接口。

## Migration Plan

1. 新增 changelog 文件(`guardfind_business.sql`、`guardfind_collect.sql`)与 `db.changelog-master.yaml` 的 `include`,同步 `postgresql/` 镜像(文件成对、include 保持注释);新表登记进 `continew-starter.tenant.ignore-tables`。
2. 数据变更:`sys_client` 插入 `APP` 行;字典插入失踪情形、年龄段、血样状态、举报理由、处置类型等;`gf_sensitive_word` 初始化词库;区划数据一期用既有 `china.json` 派生的静态字典。
3. 配置变更:`sa-token.extension.security.excludes` 增加 `/app/plaza/**`、`/app/search`、`/app/auth/login`、`/app/auth/sms-code`、`/app/find/case/*/detail` 等公开路径;新增 `collect.*` 配置段。
4. 部署顺序:先库(Liquibase 自动) → 后服务;`collect.sources.*.enabled` 全为 `false` 上线,采集开关由运维在确认授权后单独打开。
5. 回滚:业务表均为新增,回滚服务版本即可,不需回滚库;已产生的 `gf_*` 数据保留不影响模板功能。

## Open Questions

- Q1:既有 WebSocket 是否支持 `app` 域 token 寻址?(实施步骤 1 验证,影响 D9 分支)
- Q2:`rangaihuijia` 是否已取得站方书面授权?未获授权前该源 `enabled` 保持 `false`,一期只用 `local-fixture` 演示。
- Q3:区划字典是否需要在 `gf_region` 建独立表(数据量约 3000 行),还是直接用 `sys_dict` 承载?一期倾向 `sys_dict`,若筛选器响应体过大再拆表。
- Q4:档案图片是否需要在审批时做人脸打码(避免广场展示未成年人面部)?属合规策略问题,需产品与法务确认。
