# Tasks: 天下无拐一期 MVP 后端

按依赖自下而上分组。每组末尾的验证任务未通过前不进入下一组。

> 2026-11-11 逐项核对：勾选项均附实现位置或验证方式；"偏离"是实现与原文不同但经评估接受，
> "缺口"是没做或只做了一部分，保持未勾选。单测指 `mvn -o -pl guard-find-server-system -am test -Dtests.skip=false`
> （38 类 / 261 例，0 失败，1 例联网用例默认跳过），不起容器、不连 MySQL/Redis。

## 1. 前置验证与工程基线

- [x] 1.1 验证既有 WebSocket 能否按 `app` 域 token 定向(`WebSocketClientServiceImpl` / `MessageServiceImpl` / `WebSocketUtils.sendMessage` 的寻址键),据此确定 D9 分支:直接复用或引入 `gf_ws_session`
  - 结论：两条都没走。C 端用独立端点 `/app/websocket?token=`（`AppWebSocketConfiguration`），会话表放在进程内 `userId -> sessions`（`NotificationRealtimeServiceImpl`），不落 `gf_ws_session`。跨节点投递留到二期。
- [x] 1.2 确认 `continew-starter.encrypt.field` 与 `security-mask` 在 DO/Resp 上的可用注解与既有 `sys_user.phone` 的用法一致
  - `AppUserDO`、`FindCaseDO` 的联系方式字段用 `@FieldEncrypt`；公开视图与完整视图分成两个 DTO，从结构上避免敏感字段外泄。
- [x] 1.3 在根 POM 或 `-api` POM 增加"允许执行新增单测"的 Maven profile(绕开全局 `surefire.skip=true`),并本地跑通一条空测试
  - 偏离：做成根 POM 属性 `tests.skip`（默认 true），用 `-Dtests.skip=false` 开启，没有建 profile。效果相同，也不改变上游默认行为。
- [x] 1.4 `guard-find-server/pom.xml` 新增 `org.jsoup:jsoup` 版本管理,`guard-find-server-api/pom.xml` 引入
  - 偏离：依赖实际加在 `guard-find-server-system/pom.xml`，因为采集代码在 system 模块，api 模块通过传递依赖拿到。

## 2. 数据库与字典

- [x] 2.1 新建 `guard-find-server-api/src/main/resources/db/changelog/mysql/guardfind_main_table.sql`:C 端身份与档案域全部表(`gf_app_user`、`gf_app_device`、`gf_find_case`、`gf_find_case_media`、`gf_find_case_alias`、`gf_case_version`、`gf_case_status_log`、`gf_case_lead`、`gf_lead_reply`),首行 `-- liquibase formatted sql`,单一 `-- changeset guardfind:1`
  - 偏离（2.1–2.4 同）：四个文件合并成一个 `guardfind_business_table.sql`。基线是 `changeset guardfind:1`，后续增量按 `guardfind:2..n` 追加。列出的 20 张表都已在其中。
- [x] 2.2 新建 `guardfind_message_table.sql`:`gf_message`、`gf_notification_preference`、`gf_ws_session`(若 1.1 判定需要)
  - 1.1 判定不需要 `gf_ws_session`，所以没建。
- [x] 2.3 新建 `guardfind_moderation_table.sql`:`gf_report`、`gf_sensitive_word`、`gf_user_punishment`
- [x] 2.4 新建 `guardfind_collect_table.sql`:`gf_collect_source`、`gf_collect_raw`、`gf_collect_candidate`、`gf_collect_log`、`gf_collect_erase_log`、`gf_case_source_link`
- [x] 2.5 所有表补齐 `create_user/create_time/update_user/update_time/deleted` 审计列、`ENGINE=InnoDB utf8mb4`、表与列 `COMMENT`,并把业务唯一键包含 `deleted`(如 `uk_phone(phone,deleted)`、`uk_source(source_code,source_id,deleted)`、`uk_dedupe(dedupe_key,deleted)`)
- [ ] 2.6 在 `db.changelog-master.yaml` 追加四个 include(建表在前、数据在后),并写 `postgresql/` 镜像文件(include 保持注释)
  - master 里已按"建表在前、数据在后"加入 2 个 include。**缺口**：`postgresql/` 镜像没写，属于已记录的欠债（见验证报告 §2.6）。
- [ ] 2.7 新建 `guardfind_main_data.sql`:字典(`case_type`、`age_group`、`blood_sample_status`、`report_reason`、`punishment_type`、`identity_type`、`client_type` 增加 `APP`)、`sys_client` 的 `APP` 行、`gf_sensitive_word` 初始词库、举报与处置类菜单
  - 已有：`guardfind_business_data.sql` 中的 `gf_case_type`、`gf_age_group`、`gf_blood_sample_status`、`gf_report_reason`、`gf_punishment_type`（另加 `gf_body_type`、`gf_reject_reason`、`gf_relation_type`），`client_type` 的 `APP` 项，`sys_client` 的 `APP` 行，敏感词初始词库，治理菜单。**缺口**：`identity_type` 字典没建（一期没有志愿者身份认证的录入入口）。
- [x] 2.8 把全部 `gf_*` 表登记进 `application.yml` 的 `continew-starter.tenant.ignore-tables`
- [x] 2.9 验证:本地 MySQL 启动服务,Liquibase 执行成功且 `DATABASECHANGELOG` 出现四个新 changeset;重复启动不报错
  - 实际执行过：空库首次启动显示 `Update has been successful`，`gf_*` 表全部落地；之后多轮浏览器验收都对同一库重启，没有报错（验证报告 §5）。changeset 数量按合并后的文件计，不是四个。

## 3. C 端身份域

- [x] 3.1 `com.guardfind.server.app.enums.*` 与 `model.entity.AppUserDO`、`AppDeviceDO`、`model.mapper.AppUserMapper`(注意 `.model` 与 `.mapper` 段名)
- [x] 3.2 `com.guardfind.server.app.util.StpAppUtil`(独立 `StpLogic("app")`)与 `AppUserContextHolder`,提供 `getAppUserId()`、登录与踢出
- [x] 3.3 `AppAuthController`(`com.guardfind.server.app.controller`):`POST /app/auth/sms-code`(多维 `@RateLimiter`,Redis `CAPTCHA:app:{phone}`,复用 `captcha.sms.*` 模板与 SMS4J)
- [x] 3.4 `AppAuthService.login`:`PHONE` 分支校验验证码并自动建档(默认昵称"无脚星友"+4 位随机)、`ACCOUNT` 分支 BCRYPT 校验与 5 次锁定;返回 `{token,tokenName,expiresIn,isNewUser,user}`
- [x] 3.5 token 策略从 `sys_client` 的 `APP` 行读取(有效期/活跃超时/并发/最大设备数),登录写 `sys_log` 并 upsert `gf_app_device`
- [x] 3.6 `POST /app/auth/logout`、`GET /app/auth/me`、`PUT /app/auth/profile`、`POST /app/auth/password`、`POST /app/auth/reset-password`、`DELETE /app/auth/account`(冷静期)与冷静期内登录撤销
- [x] 3.7 拦截配置:`AppInterceptor`/Sa-Token 注解只作用于 `/app/**`,并确认 `/system/**` 与 `/auth/**` 不接受 app token
- [x] 3.8 单测:验证码正确/过期/错误、自动建档幂等、密码锁定阈值、脱敏返回、C 端 token 打管理接口被拒(后一项为登录域隔离,由 `StpAppUtil` 独立 `loginType=app` 与 `security.excludes` 静态保证,未做容器级集成测试)

## 4. 寻人档案域

- [x] 4.1 `findcase.model.entity`:FindCaseDO(含 `@TableField(typeHandler=JacksonTypeHandler.class)` 的 JSON 列与 `autoResultMap`)、MediaDO、AliasDO、VersionDO、StatusLogDO 与各 Mapper
  - `FindCaseDO` 开了 `autoResultMap`。它没有 JSON 列：别名和媒体拆成了子表，所以不需要 typeHandler。
- [x] 4.2 `findcase.enums`:CaseStatusEnum、UrgencyLevelEnum、CaseTypeEnum、BloodSampleEnum 与 `CaseStatusTransfer` 合法跃迁表
  - 偏离：情形和血样状态走 `sys_dict`（`gf_case_type`、`gf_blood_sample_status`），没有做成枚举；合法跃迁表并进了 `CaseStatusEnum.canTransfer`。
- [x] 4.3 `findcase.model.req.CaseReq` / `query` / `resp`:公开 `CasePublicResp` 与完整 `CaseDetailResp` **两个独立 DTO**,敏感列加密与掩码注解
- [x] 4.4 `CaseService`:草稿创建/编辑、`submit` 校验(体貌至少一项、失踪时间不晚于当前、区划到区)、每日 5 条上限、疑似重复检测、写 `gf_case_version` 快照
  - 校验集中在 `CaseSubmitValidator`；疑似重复用 `CaseSimilarResp` 加 `forceSubmit`。
- [x] 4.5 `CaseStatusService.change(...)` 同事务写状态日志 + 发通知;`found/renew/withdraw/erase` 四个动作接口
- [x] 4.6 时效任务:90 天 `PUBLISHED → ARCHIVED` 与到期前 7 天提醒,按 `NoticePublishJob` 双嵌套类形态放 `com.guardfind.server.job.CaseExpiryJob`,worker `@TenantIgnore`
- [x] 4.7 `CaseController` 路由:`POST/PUT /app/find/case`、`GET /app/find/case/{id}`、`GET /app/find/case/{id}/full`(记访问日志)、`GET /app/find/case/mine`、`/{id}/submit|found|renew|withdraw|erase`
- [ ] 4.8 媒体上传限制校验(≤9 张、单张 ≤5MB、类型白名单),采集来源图片本地化到自有存储
  - 已有：张数和类型白名单（`CaseSubmitValidator`），单张大小限制（`AppFileController`）。**缺口**：候选审批时没有把采集来源的照片复制到自有存储，`CollectCandidateDO` 的注释写了"审批时本地化"，但没实现。
- [ ] 4.9 单测:非法跃迁被拒、缺体貌字段提交失败、重复检测需 `forceSubmit`、状态日志与版本序号递增、`ERASED` 后读接口不可见、紧急标记规则
  - 已覆盖（`CaseRulesTest`，7 例）：非法跃迁、ERASED 终态、对外可见集合只有 PUBLISHED、缺体貌字段、时空与告知校验、每日上限、媒体白名单。**缺口**：`forceSubmit` 重复检测、日志与版本序号、紧急标记规则这三项依赖 Mapper 与服务编排，没有单测。

## 5. 广场与搜索

- [x] 5.1 `FeedCursorResp{records,nextCursor,hasMore}` 与游标编解码工具(`Base64(sortKey|id)`)
- [x] 5.2 `PlazaService.feed(sort,provinceCode,cityCode,keyword,cursor,size)`:`latest/urgent/nearby/found` 四流,服务端强制 `status=PUBLISHED` 过滤,索引走 `idx_status_urgency_missing_time`、`idx_region`
- [x] 5.3 `GET /app/plaza/filters`(读 `sys_dict` 与区划)、`GET /app/plaza/statistics`、`POST /app/plaza/track`(限流、允许匿名)
- [x] 5.4 `GET /app/search`:姓名/别名/地点/特征描述关键词匹配,长度与敏感词校验,复用同一套状态过滤
- [x] 5.5 卡片视图脱敏落 DTO(区划到市、失踪年月、无详细地址与联系方式)
- [x] 5.6 公开路径写入 `sa-token.extension.security.excludes` 并加 `@SaIgnore`,同时给公开读接口配 IP 限流
  - `/app/**` 整体放行后台鉴权，C 端登录态由 `AppAuthInterceptor` 处理；`PlazaController` 上有 `@SaIgnore` 与 IP 维度的 `@RateLimiter`。
- [ ] 5.7 单测:四种流的结果集状态纯净、`nearby` 不跨市、`latest` 不含 `FOUND`、游标翻页不重不漏(翻页期间插入新记录)、直链访问 `OFFLINE` 档案返回 404
  - 已覆盖（`FeedCursorTest`，4 例）：游标往返、篡改或旧版本游标被拒、排序键毫秒精度、只有 found 流包含已找回。**缺口**：结果集纯净、nearby 不跨市、翻页期间插入新记录这三项要靠 SQL 实测，需要真实库，没有覆盖。另外 OFFLINE 直链按契约返回 `code="500"` 加提示文案，不是 404，契约里已注明。

## 6. 线索与治理

- [x] 6.1 `lead` 域:`LeadDO`/`LeadReplyDO` 与 Mapper,`POST /app/find/case/{id}/lead`(必登录且已绑手机、描述 10–1000 字、图片 ≤3)
- [x] 6.2 线索限频:同用户同档案 1h/3 条、同 IP 1h/10 条(Redis 计数)
- [x] 6.3 `GET /app/find/case/{id}/leads`(仅所有人/志愿者)、`PATCH /app/lead/{id}/status`、`POST /app/lead/{id}/reply`,均发通知
  - 无权查看时返回业务码 `4003`（`LeadServiceImplTest`）。
- [x] 6.4 `moderation` 域:`gf_sensitive_word` DFA 匹配器(ILLEGAL/ABUSE 拒绝、FRAUD 标高风险入队),词库带缓存即时失效
- [x] 6.5 `POST /app/report`(同人对同目标去重、`hit_count` 累加)、`GET /app/report/mine`
- [x] 6.6 `GET /system/moderation/pending`、`/{id}/approve`、`/{id}/reject`(理由码必填)、`/report/{id}/handle`;`@SaCheckPermission` 与 `@Log` 配置
  - 路由实际是 `/{bizType}/{id}/approve|reject`，以便同时覆盖档案与线索两类审核对象。
- [ ] 6.7 单测:重复举报合并、命中词库拒绝、FRAUD 标高风险、非所有人无法处置线索、审核通过后台广场可见
  - 已覆盖：命中词库拒绝、FRAUD 标高风险、按最坏类别判定、拆字规避也能命中（`SensitiveWordTrieTest`，5 例）；非所有人或非志愿者无权查看线索（`LeadServiceImplTest`）。**缺口**：重复举报合并、审核通过后进入广场，没有单测。

## 7. C 端通知

- [x] 7.1 `notification` 域:`MessageDO`、`PreferenceDO`、(按需)`WsSessionDO` 与 Mapper
  - 实体是 `AppMessageDO` 与 `AppMessageMapper`（加 App 前缀，避免与模板里的 bean 和 alias 冲突，见验证报告 §5）；不需要 `WsSessionDO`。
- [x] 7.2 `NotificationEventEnum` 集中定义八类事件的标题/内容模板/`path`(`CASE_APPROVED|CASE_REJECTED|CASE_LEAD|LEAD_REPLIED|CASE_FOUND|CASE_EXPIRING|REPORT_RESULT|CASE_PUBLISHED`)
- [x] 7.3 `AppMessageService.send(userId, event, bizId, vars)`:先落库后推送,推送异常 MUST NOT 回滚业务事务
- [x] 7.4 实时通道接线(按 1.1 的结论):`/websocket?token=` 或 `gf_ws_session` 按 `user_id` 定向
  - 端点为 `/app/websocket?token=`。
- [x] 7.5 `GET /app/message/unread`、`GET /app/message`、`PATCH /app/message/{id}/read`、`PATCH /app/message/read`、偏好读写;强制类消息不可关闭站内信
- [x] 7.6 `CASE_LEAD` 的短信订阅:默认关闭、显式开启后发送、复用 `captcha.sms.*` 之外的独立模板与限流
- [ ] 7.7 把 M2/M6/审核的调用点接入通知(状态变更、新线索、回执、找回、临期、举报结果)
  - 已接入：`CASE_APPROVED`、`CASE_REJECTED`、`CASE_LEAD`、`LEAD_REPLIED`、`CASE_FOUND`、`CASE_EXPIRING`、`REPORT_RESULT`。**缺口**：`CASE_PUBLISHED` 只在枚举里定义，没有任何调用点发送它。
- [ ] 7.8 单测:未读计数与列表一致、越权读他人消息被拒、关闭订阅后不发、推送失败不影响落库
  - 已覆盖（`AppMessageServiceImplTest`，5 例）：推送失败和短信失败不外抛且消息已落库、关闭订阅后不落库也不推送、强制类事件不受订阅影响、偏好默认值。**缺口**：未读计数与列表一致、越权读他人消息被拒，没有单测。

## 8. 采集框架

- [x] 8.1 `collect.source.CollectSource` SPI(`code()`、`fetchListPage(cursor)`、`fetchDetail(id)`、`toCandidate(raw)`)与 `CollectCursor`、`RawPage`、`CandidateDraft` 模型
  - 偏离：SPI 是 `code()`、`fetchListPageUrls(config, page)`、`fetchDetail(config, url)`，解析直接产出 `CandidateDraft`；游标用页码，记在 `gf_collect_source.last_cursor`，没有单独的 `CollectCursor` 类型。
- [x] 8.2 `RobotsPolicy`:拉取并 Redis 缓存 `robots.txt` 24h,Disallow 命中或解析失败 → 中止本轮 + 告警日志;不提供忽略开关
  - 2026-11-11 修了一个合规缺陷：`Disallow: /` 原先被跳过（全站禁止不生效），规则也是整串匹配而不是前缀匹配（`Disallow: /xunren` 拦不住 `/xunren/view/1`）。现在两者都已按 robots 惯例实现（`RobotsAndRateLimitTest`）。
- [x] 8.3 `DomainRateLimiter`:域名级串行、间隔不低于 `crawlDelayMs`(默认 2000)、UA 明示含联系入口
- [x] 8.4 `HttpFetcher` 基于 Hutool `HttpRequest`:超时、指数退避重试 ≤2 次、4xx 不重试、失败明细记录
- [x] 8.5 `CandidateMapper`:日期归一到日、区划名 → 行政区划码、性别/情形枚举化、姓名与别名全半角与空白规范化、`dedupe_key` 计算
- [x] 8.6 `CollectOrchestrator.run(sourceCode, maxPages)`:原文快照 upsert(`source_code+source_id` 唯一)、解析失败跳过不断批、批次统计落 `gf_collect_log`
- [x] 8.7 去重与收敛:同 `dedupe_key` 保留信息最全为主候选,其余 `DUPLICATED` + `primary_id`;与 `gf_find_case` 命中则给合并提示
- [x] 8.8 `RangAiHuiJiaSource`(Jsoup):列表 `/xunren/category/{id}?p-page=n`、详情 `/xunren/view/{id}`,选择器以常量集中定义并宽松取值
- [x] 8.9 `LocalFixtureSource`:读取 fixture HTML,与真实源共用 `CandidateMapper`
- [x] 8.10 `CandidateService`:`approve`(幂等,写 `gf_find_case` + `gf_case_source_link`)、`merge`、`reject`、`eraseBySource`(批量 `ERASED` + `gf_collect_erase_log`)
  - 删除权接口叫 `erase(CollectEraseReq)`；审批幂等靠 `gf_case_source_link` 的唯一键加 `DuplicateKeyException` 兜底。
- [x] 8.11 `CollectController`(`/system/collect/**`,`@SaCheckPermission` + `@Log`):源 CRUD、`POST /run/{sourceCode}?maxPages=5`、`GET /log`、候选列表与审批
- [x] 8.12 `CollectJob` 双模式调度(`@Scheduled` / `@JobExecutor`),worker `@TenantIgnore`,逐源判断 `enabled`(默认 `false`)
- [x] 8.13 原文正文 180 天清理任务(保留 `sha256`、`source_url`、`fetch_time`)
  - 2026-11-11 补上：`CollectOrchestrator.purgeExpiredRawContent()` 只把 `content` 置空，`sha256`、`source_url`、`fetched_at` 保留。`CollectJob` 每轮先执行它，没有启用的源时也照常执行。
- [ ] 8.14 fixture 覆盖:正常页、缺失踪日期、字段错位、脏 HTML、404、超时、分页边界、同 `dedupe_key` 重复
  - 已有：正常页、缺失踪日期、字段错位、脏 HTML、404、空页（分页边界）、列表页，以及同指纹重复（`CandidateMapperTest`）。**缺口**：超时没有 fixture，只在 `HttpFetcher` 的重试逻辑里处理。
- [ ] 8.15 单测:解析字段一一对应、脏数据不断批、重跑幂等(新增 0)、robots 禁止即中止、限速间隔 ≥ 配置值、审批并发只产生一条档案、未审批候选不出现在任何读接口
  - 已覆盖：解析字段与脏数据（`CandidateMapperTest`，9 例）；robots 全站禁止或采集路径被禁即中止、robots 不可达即中止、前缀与 `$` 规则、Crawl-delay 取较严者、实测间隔不低于配置值（`RobotsAndRateLimitTest`，7 例）；robots 中止时不发起任何抓取、重跑幂等（新增 0）、180 天阈值（`CollectOrchestratorImplTest`，3 例）。**缺口**：审批并发只产生一条档案（要靠真实库唯一键）、未审批候选不进读接口，没有单测。
- [x] 8.16 真实源的联网集成测试放在显式 profile 下(默认不执行),并在 README 写明授权前置
  - `RangAiHuiJiaLiveTest` 用 `@EnabledIfSystemProperty(collect.live=true)` 控制，不带该参数时跳过。README 的"真实采集源联网测试"一节写明了须先取得书面授权。**本身没有联网执行过**（尚未取得授权）。

## 9. 交付核对

- [x] 9.1 `mvn -B -DskipTests package` 通过(接受 spotless 改写,提交格式化结果)
- [x] 9.2 新增单测全部通过并记录执行命令与用例数
  - 命令和用例数见本文件顶部说明，以及验证报告 §10。
- [ ] 9.3 对照 `guard-find-app` 的接口契约清单逐项核对路径、方法、请求/响应字段与错误码,差异以规格为准修正
  - 已核对并修正：响应码类型（验证报告 §5.2）、线索越权 `4003`、WS 端点与关闭码。**缺口**：没有做全量逐字段核对。业务码 `4002/4005/4006/4290` 仍以 `"500"` 加文案返回，契约里已注明。
- [x] 9.4 出一份"实机验证 / 静态检查"分离的验证报告,未验证项显式标注(含采集真实联网、WebSocket 三端实机)
  - `doc/一期实施验证报告.md`。
