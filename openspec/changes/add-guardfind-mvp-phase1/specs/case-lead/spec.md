## ADDED Requirements

### Requirement: 结构化线索提交

系统 SHALL 提供 `POST /app/find/case/{id}/lead`,参数含 `occurTime`、`occurAddress`、`description`(10–1000 字)、`mediaUrls`(≤ 3 张)、`contactMode`(`IN_APP | PHONE`)与 `contactValue`。提交 MUST 要求登录且该账号已绑定手机号。系统 SHALL 通过站内信立即通知档案所有人。

#### Scenario: 未登录提交被拒绝

- **WHEN** 未携带 token 的客户端提交线索
- **THEN** 系统返回未授权错误,不落库

#### Scenario: 线索即时触达家属

- **WHEN** 好心人对 `PUBLISHED` 档案提交线索成功
- **THEN** 档案所有人收到一条类型为 `CASE_LEAD` 的站内信,并在"我的档案-线索"列表中可见该线索

### Requirement: 线索反滥用限频

系统 SHALL 对线索提交施加多维限流:同一用户对同一档案 1 小时内最多 3 条,同一 IP 每小时最多 10 条,命中 SHALL 返回限流错误。

#### Scenario: 重复刷同一档案被拦截

- **WHEN** 同一用户在 1 小时内对同一档案提交第 4 条线索
- **THEN** 请求被拒绝且不产生新记录与通知

### Requirement: 线索状态流转与双向回复

系统 SHALL 提供 `PATCH /app/lead/{id}/status`(取值 `ACCEPTED | REJECTED | CONTACTED`,仅档案所有人可操作)与 `POST /app/lead/{id}/reply`。状态变更与回复 SHALL 通知线索提交人。

#### Scenario: 非所有人无法处置线索

- **WHEN** 与该档案无关的登录用户调用线索状态变更接口
- **THEN** 系统返回未授权,状态不变

#### Scenario: 处置结果回执提交人

- **WHEN** 家属将线索标记为 `ACCEPTED` 并回复"请提供当时的照片"
- **THEN** 提交人收到站内信,可在"我的线索"中看到回复内容

### Requirement: 联系方式默认不出现在响应中

线索响应 MUST NOT 回显提交人联系方式明文给非所有人;档案公开详情 MUST NOT 因存在线索而暴露家属联系方式。系统 SHALL 对线索描述执行诈骗话术识别,命中 SHALL 标记 `high_risk` 并推入审核队列,MUST NOT 直接向家属展示为普通线索。

#### Scenario: 高风险话术被隔离

- **WHEN** 线索描述包含"先转钱核实""加微信付款"等命中词库的话术
- **THEN** 该线索 `high_risk` 为真,家属端显示为"待核实",并出现在审核队列中
