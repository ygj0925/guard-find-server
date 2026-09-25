## ADDED Requirements

### Requirement: C 端站内信独立存储

系统 SHALL 使用 `gf_message`(`receiver_id, type, title, content, biz_type, biz_id, path, read_status, read_time`)存储 C 端消息,MUST NOT 复用面向后台的 `sys_message`。接口 SHALL 为 `GET /app/message/unread`、`GET /app/message`、`PATCH /app/message/{id}/read`、`PATCH /app/message/read`。

#### Scenario: 未读计数与列表一致

- **WHEN** 用户读完列表中全部消息后再次请求 `GET /app/message/unread`
- **THEN** 返回计数为 0,且与列表中的 `read_status` 完全一致

#### Scenario: 越权读取他人消息

- **WHEN** 用户以他人消息 id 调用标记已读或读取详情
- **THEN** 系统返回未授权,目标消息状态不变

### Requirement: 事件目录驱动通知

系统 SHALL 为下列事件生成站内信并尽力通过 WebSocket 实时下发:审核通过 `CASE_APPROVED`、审核驳回 `CASE_REJECTED`、新线索 `CASE_LEAD`、线索回执 `LEAD_REPLIED`、找回确认 `CASE_FOUND`、临期提醒 `CASE_EXPIRING`、举报处理结果 `REPORT_RESULT`、采集候选转正 `CASE_PUBLISHED`。事件到文案的映射 SHALL 由枚举集中定义,MUST NOT 散落在业务代码里拼接字符串。

#### Scenario: 审核动作产生通知

- **WHEN** 审核人驳回一条档案并选择预设理由
- **THEN** 发布人在 5 秒内收到 `CASE_REJECTED` 站内信,内容含档案编号与驳回理由

#### Scenario: 无 WebSocket 连接时不丢消息

- **WHEN** 事件产生时接收方不在线
- **THEN** 站内信仍然落库,接收方下次拉取列表可见,系统不因推送失败而回滚业务事务

### Requirement: 通知偏好

系统 SHALL 提供 `GET /app/notification/preference` 与 `PUT /app/notification/preference`,支持按事件类型配置 `inboxEnabled` 与 `smsEnabled`。`CASE_LEAD` 的短信订阅 SHALL 默认关闭且仅在用户主动开启后发送。审核结果类消息 MUST NOT 允许关闭站内信。

#### Scenario: 关闭订阅后不再下发

- **WHEN** 用户关闭 `CASE_FOUND` 的站内信开关后发生找回确认
- **THEN** 该用户不再收到该类消息,但档案状态与状态日志正常变更

#### Scenario: 强制类消息不可关闭

- **WHEN** 用户尝试关闭 `CASE_APPROVED` 与 `CASE_REJECTED` 的站内信
- **THEN** 系统拒绝该偏好写入并返回说明

### Requirement: 实时通道身份可寻址

系统 SHALL 保证 C 端用户可通过其 `app` 域 token 建立 WebSocket 连接并只收到发给自己的消息。若既有 WebSocket 实现不能按 `app` 域身份寻址,实现 SHALL 引入 `gf_ws_session` 订阅表并在连接后按 `user_id` 定向推送,MUST NOT 通过广播全量消息再靠客户端过滤来实现。

#### Scenario: 连接鉴权失败被拒绝

- **WHEN** 客户端以无效或过期 token 建立 `/websocket` 连接
- **THEN** 握手被拒绝,服务端不登记会话

#### Scenario: 消息只送达目标用户

- **WHEN** 两个用户同时在线且系统向其中一个发送 `CASE_LEAD`
- **THEN** 仅目标用户的连接收到该帧

### Requirement: 设备登记为二期推送预留

系统 SHALL 在登录时把 `platform`、`deviceId`、`appVersion`、`osVersion` 登记进 `gf_app_device`,`pushToken` 允许为空。一期 MUST NOT 依赖厂商推送通道达成任何通知承诺。

#### Scenario: 三端平台标识正确落库

- **WHEN** iOS、Android、HarmonyOS 客户端分别登录
- **THEN** 对应设备记录的 `platform` 分别为 `IOS`、`ANDROID`、`HARMONY`,同一设备重复登录不产生重复记录
