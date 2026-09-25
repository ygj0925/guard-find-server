## ADDED Requirements

### Requirement: 举报与处置

系统 SHALL 提供 `POST /app/report`(`targetType` 取 `CASE | LEAD | USER`,`reasonType` 为字典枚举,`description`、`mediaUrls` 可选)与 `GET /app/report/mine`。举报 MUST 去重(同一举报人对同一目标仅有一条待处理举报)。处置接口 SHALL 为 `POST /system/moderation/report/{id}/handle`,处置结果 SHALL 通知举报人与被举报人,并可对被举报用户施加 `MUTE | BAN` 记录于 `gf_user_punishment`。

#### Scenario: 重复举报合并计数

- **WHEN** 同一用户对同一档案第 3 次提交相同理由的举报
- **THEN** 系统不新增举报记录,而是将该条举报的 `hit_count` 加 1

#### Scenario: 举报达阈值提升审核优先级

- **WHEN** 某档案的有效举报数达到 3
- **THEN** 该档案进入审核队列顶部,且广场排序权重下调

### Requirement: 敏感词与诈骗话术识别

系统 SHALL 维护 `gf_sensitive_word`(类别 `ILLEGAL | ABUSE | FRAUD`),对档案文本与线索文本执行匹配:命中 `ILLEGAL`/`ABUSE` SHALL 拒绝提交;命中 `FRAUD` SHALL 放行但标记高风险并进入人工审核队列。词库 SHALL 可通过后台维护并即时生效。

#### Scenario: 违法内容直接拒绝发布

- **WHEN** 档案特征描述包含 `ILLEGAL` 类词库命中项
- **THEN** 提交被拒绝并返回内容违规提示

#### Scenario: 求助文本不被误杀

- **WHEN** 正常寻亲描述未命中词库
- **THEN** 提交与流转不受影响,不产生额外标记

### Requirement: 审核队列

系统 SHALL 提供 `GET /system/moderation/pending`(按 `CASE | COLLECT_CANDIDATE` 与区划筛选、按提交时间升序)、`POST /system/moderation/{id}/approve`、`POST /system/moderation/{id}/reject`(必须携带预设模板理由码,可附自由文本)。审核动作 MUST 记录审核人、理由与时间,并 MUST 触发状态变更与通知。

#### Scenario: 驳回理由必填且来自模板

- **WHEN** 审核人驳回时未提供理由码
- **THEN** 系统拒绝该审核动作

#### Scenario: 审核通过后广场可见

- **WHEN** 审核人通过一条 `PENDING_REVIEW` 档案
- **THEN** 状态转为 `PUBLISHED`,下一次广场 `sort=latest` 请求即可返回该档案

### Requirement: 删除权与下架

系统 SHALL 支持 `POST /app/find/case/{id}/erase`(权利人请求或账号注销级联),状态转 `ERASED` 并 MUST 物理清除可识别个人字段(姓名、别名、照片、详细地址、联系方式),仅保留不可回溯的匿名统计与审计。下架 `OFFLINE` SHALL 保留数据但对外不可见。`ERASED` MUST NOT 出现在任何读接口、导出或采集候选中。

#### Scenario: 删除后所有出口不可见

- **WHEN** 一条档案被 `ERASED`
- **THEN** 广场、搜索、我的档案、后台列表与采集候选查询均不返回该记录,其媒体文件被清理

#### Scenario: 下架与删除语义不同

- **WHEN** 档案被 `OFFLINE` 而非 `ERASED`
- **THEN** 对外不可见但数据与媒体保留,可由人工恢复为 `PUBLISHED`
