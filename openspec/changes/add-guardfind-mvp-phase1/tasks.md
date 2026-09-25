# Tasks: 天下无拐一期 MVP 后端

按依赖自下而上分组。每组末尾的验证任务未通过前不进入下一组。

## 1. 前置验证与工程基线

- [ ] 1.1 验证既有 WebSocket 能否按 `app` 域 token 定向(`WebSocketClientServiceImpl` / `MessageServiceImpl` / `WebSocketUtils.sendMessage` 的寻址键),据此确定 D9 分支:直接复用或引入 `gf_ws_session`
- [ ] 1.2 确认 `continew-starter.encrypt.field` 与 `security-mask` 在 DO/Resp 上的可用注解与既有 `sys_user.phone` 的用法一致
- [ ] 1.3 在根 POM 或 `-api` POM 增加"允许执行新增单测"的 Maven profile(绕开全局 `surefire.skip=true`),并本地跑通一条空测试
- [ ] 1.4 `guard-find-server/pom.xml` 新增 `org.jsoup:jsoup` 版本管理,`guard-find-server-api/pom.xml` 引入

## 2. 数据库与字典

- [ ] 2.1 新建 `guard-find-server-api/src/main/resources/db/changelog/mysql/guardfind_main_table.sql`:C 端身份与档案域全部表(`gf_app_user`、`gf_app_device`、`gf_find_case`、`gf_find_case_media`、`gf_find_case_alias`、`gf_case_version`、`gf_case_status_log`、`gf_case_lead`、`gf_lead_reply`),首行 `-- liquibase formatted sql`,单一 `-- changeset guardfind:1`
- [ ] 2.2 新建 `guardfind_message_table.sql`:`gf_message`、`gf_notification_preference`、`gf_ws_session`(若 1.1 判定需要)
- [ ] 2.3 新建 `guardfind_moderation_table.sql`:`gf_report`、`gf_sensitive_word`、`gf_user_punishment`
- [ ] 2.4 新建 `guardfind_collect_table.sql`:`gf_collect_source`、`gf_collect_raw`、`gf_collect_candidate`、`gf_collect_log`、`gf_collect_erase_log`、`gf_case_source_link`
- [ ] 2.5 所有表补齐 `create_user/create_time/update_user/update_time/deleted` 审计列、`ENGINE=InnoDB utf8mb4`、表与列 `COMMENT`,并把业务唯一键包含 `deleted`(如 `uk_phone(phone,deleted)`、`uk_source(source_code,source_id,deleted)`、`uk_dedupe(dedupe_key,deleted)`)
- [ ] 2.6 在 `db.changelog-master.yaml` 追加四个 include(建表在前、数据在后),并写 `postgresql/` 镜像文件(include 保持注释)
- [ ] 2.7 新建 `guardfind_main_data.sql`:字典(`case_type`、`age_group`、`blood_sample_status`、`report_reason`、`punishment_type`、`identity_type`、`client_type` 增加 `APP`)、`sys_client` 的 `APP` 行、`gf_sensitive_word` 初始词库、举报与处置类菜单
- [ ] 2.8 把全部 `gf_*` 表登记进 `application.yml` 的 `continew-starter.tenant.ignore-tables`
- [ ] 2.9 验证:本地 MySQL 启动服务,Liquibase 执行成功且 `DATABASECHANGELOG` 出现四个新 changeset;重复启动不报错

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

- [ ] 4.1 `findcase.model.entity`:FindCaseDO(含 `@TableField(typeHandler=JacksonTypeHandler.class)` 的 JSON 列与 `autoResultMap`)、MediaDO、AliasDO、VersionDO、StatusLogDO 与各 Mapper
- [ ] 4.2 `findcase.enums`:CaseStatusEnum、UrgencyLevelEnum、CaseTypeEnum、BloodSampleEnum 与 `CaseStatusTransfer` 合法跃迁表
- [ ] 4.3 `findcase.model.req.CaseReq` / `query` / `resp`:公开 `CasePublicResp` 与完整 `CaseDetailResp` **两个独立 DTO**,敏感列加密与掩码注解
- [ ] 4.4 `CaseService`:草稿创建/编辑、`submit` 校验(体貌至少一项、失踪时间不晚于当前、区划到区)、每日 5 条上限、疑似重复检测、写 `gf_case_version` 快照
- [ ] 4.5 `CaseStatusService.change(...)` 同事务写状态日志 + 发通知;`found/renew/withdraw/erase` 四个动作接口
- [ ] 4.6 时效任务:90 天 `PUBLISHED → ARCHIVED` 与到期前 7 天提醒,按 `NoticePublishJob` 双嵌套类形态放 `com.guardfind.server.job.CaseExpiryJob`,worker `@TenantIgnore`
- [ ] 4.7 `CaseController` 路由:`POST/PUT /app/find/case`、`GET /app/find/case/{id}`、`GET /app/find/case/{id}/full`(记访问日志)、`GET /app/find/case/mine`、`/{id}/submit|found|renew|withdraw|erase`
- [ ] 4.8 媒体上传限制校验(≤9 张、单张 ≤5MB、类型白名单),采集来源图片本地化到自有存储
- [ ] 4.9 单测:非法跃迁被拒、缺体貌字段提交失败、重复检测需 `forceSubmit`、状态日志与版本序号递增、`ERASED` 后读接口不可见、紧急标记规则

## 5. 广场与搜索

- [ ] 5.1 `FeedCursorResp{records,nextCursor,hasMore}` 与游标编解码工具(`Base64(sortKey|id)`)
- [ ] 5.2 `PlazaService.feed(sort,provinceCode,cityCode,keyword,cursor,size)`:`latest/urgent/nearby/found` 四流,服务端强制 `status=PUBLISHED` 过滤,索引走 `idx_status_urgency_missing_time`、`idx_region`
- [ ] 5.3 `GET /app/plaza/filters`(读 `sys_dict` 与区划)、`GET /app/plaza/statistics`、`POST /app/plaza/track`(限流、允许匿名)
- [ ] 5.4 `GET /app/search`:姓名/别名/地点/特征描述关键词匹配,长度与敏感词校验,复用同一套状态过滤
- [ ] 5.5 卡片视图脱敏落 DTO(区划到市、失踪年月、无详细地址与联系方式)
- [ ] 5.6 公开路径写入 `sa-token.extension.security.excludes` 并加 `@SaIgnore`,同时给公开读接口配 IP 限流
- [ ] 5.7 单测:四种流的结果集状态纯净、`nearby` 不跨市、`latest` 不含 `FOUND`、游标翻页不重不漏(翻页期间插入新记录)、直链访问 `OFFLINE` 档案返回 404

## 6. 线索与治理

- [ ] 6.1 `lead` 域:`LeadDO`/`LeadReplyDO` 与 Mapper,`POST /app/find/case/{id}/lead`(必登录且已绑手机、描述 10–1000 字、图片 ≤3)
- [ ] 6.2 线索限频:同用户同档案 1h/3 条、同 IP 1h/10 条(Redis 计数)
- [ ] 6.3 `GET /app/find/case/{id}/leads`(仅所有人/志愿者)、`PATCH /app/lead/{id}/status`、`POST /app/lead/{id}/reply`,均发通知
- [ ] 6.4 `moderation` 域:`gf_sensitive_word` DFA 匹配器(ILLEGAL/ABUSE 拒绝、FRAUD 标高风险入队),词库带缓存即时失效
- [ ] 6.5 `POST /app/report`(同人对同目标去重、`hit_count` 累加)、`GET /app/report/mine`
- [ ] 6.6 `GET /system/moderation/pending`、`/{id}/approve`、`/{id}/reject`(理由码必填)、`/report/{id}/handle`;`@SaCheckPermission` 与 `@Log` 配置
- [ ] 6.7 单测:重复举报合并、命中词库拒绝、FRAUD 标高风险、非所有人无法处置线索、审核通过后台广场可见

## 7. C 端通知

- [ ] 7.1 `notification` 域:`MessageDO`、`PreferenceDO`、(按需)`WsSessionDO` 与 Mapper
- [ ] 7.2 `NotificationEventEnum` 集中定义八类事件的标题/内容模板/`path`(`CASE_APPROVED|CASE_REJECTED|CASE_LEAD|LEAD_REPLIED|CASE_FOUND|CASE_EXPIRING|REPORT_RESULT|CASE_PUBLISHED`)
- [ ] 7.3 `AppMessageService.send(userId, event, bizId, vars)`:先落库后推送,推送异常 MUST NOT 回滚业务事务
- [ ] 7.4 实时通道接线(按 1.1 的结论):`/websocket?token=` 或 `gf_ws_session` 按 `user_id` 定向
- [ ] 7.5 `GET /app/message/unread`、`GET /app/message`、`PATCH /app/message/{id}/read`、`PATCH /app/message/read`、偏好读写;强制类消息不可关闭站内信
- [ ] 7.6 `CASE_LEAD` 的短信订阅:默认关闭、显式开启后发送、复用 `captcha.sms.*` 之外的独立模板与限流
- [ ] 7.7 把 M2/M6/审核的调用点接入通知(状态变更、新线索、回执、找回、临期、举报结果)
- [ ] 7.8 单测:未读计数与列表一致、越权读他人消息被拒、关闭订阅后不发、推送失败不影响落库

## 8. 采集框架

- [ ] 8.1 `collect.source.CollectSource` SPI(`code()`、`fetchListPage(cursor)`、`fetchDetail(id)`、`toCandidate(raw)`)与 `CollectCursor`、`RawPage`、`CandidateDraft` 模型
- [ ] 8.2 `RobotsPolicy`:拉取并 Redis 缓存 `robots.txt` 24h,Disallow 命中或解析失败 → 中止本轮 + 告警日志;不提供忽略开关
- [ ] 8.3 `DomainRateLimiter`:域名级串行、间隔不低于 `crawlDelayMs`(默认 2000)、UA 明示含联系入口
- [ ] 8.4 `HttpFetcher` 基于 Hutool `HttpRequest`:超时、指数退避重试 ≤2 次、4xx 不重试、失败明细记录
- [ ] 8.5 `CandidateMapper`:日期归一到日、区划名 → 行政区划码、性别/情形枚举化、姓名与别名全半角与空白规范化、`dedupe_key` 计算
- [ ] 8.6 `CollectOrchestrator.run(sourceCode, maxPages)`:原文快照 upsert(`source_code+source_id` 唯一)、解析失败跳过不断批、批次统计落 `gf_collect_log`
- [ ] 8.7 去重与收敛:同 `dedupe_key` 保留信息最全为主候选,其余 `DUPLICATED` + `primary_id`;与 `gf_find_case` 命中则给合并提示
- [ ] 8.8 `RangAiHuiJiaSource`(Jsoup):列表 `/xunren/category/{id}?p-page=n`、详情 `/xunren/view/{id}`,选择器以常量集中定义并宽松取值
- [ ] 8.9 `LocalFixtureSource`:读取 fixture HTML,与真实源共用 `CandidateMapper`
- [ ] 8.10 `CandidateService`:`approve`(幂等,写 `gf_find_case` + `gf_case_source_link`)、`merge`、`reject`、`eraseBySource`(批量 `ERASED` + `gf_collect_erase_log`)
- [ ] 8.11 `CollectController`(`/system/collect/**`,`@SaCheckPermission` + `@Log`):源 CRUD、`POST /run/{sourceCode}?maxPages=5`、`GET /log`、候选列表与审批
- [ ] 8.12 `CollectJob` 双模式调度(`@Scheduled` / `@JobExecutor`),worker `@TenantIgnore`,逐源判断 `enabled`(默认 `false`)
- [ ] 8.13 原文正文 180 天清理任务(保留 `sha256`、`source_url`、`fetch_time`)
- [ ] 8.14 fixture 覆盖:正常页、缺失踪日期、字段错位、脏 HTML、404、超时、分页边界、同 `dedupe_key` 重复
- [ ] 8.15 单测:解析字段一一对应、脏数据不断批、重跑幂等(新增 0)、robots 禁止即中止、限速间隔 ≥ 配置值、审批并发只产生一条档案、未审批候选不出现在任何读接口
- [ ] 8.16 真实源的联网集成测试放在显式 profile 下(默认不执行),并在 README 写明授权前置

## 9. 交付核对

- [ ] 9.1 `mvn -B -DskipTests package` 通过(接受 spotless 改写,提交格式化结果)
- [ ] 9.2 新增单测全部通过并记录执行命令与用例数
- [ ] 9.3 对照 `guard-find-app` 的接口契约清单逐项核对路径、方法、请求/响应字段与错误码,差异以规格为准修正
- [ ] 9.4 出一份"实机验证 / 静态检查"分离的验证报告,未验证项显式标注(含采集真实联网、WebSocket 三端实机)
