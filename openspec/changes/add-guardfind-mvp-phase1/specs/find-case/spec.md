## ADDED Requirements

### Requirement: 结构化寻人档案

系统 SHALL 以 `gf_find_case` 为主表存储寻人档案,字段 SHALL 分为身份(姓名、别名 `gf_find_case_alias`、性别、出生日期、失踪时年龄、近照 `gf_find_case_media`)、时空(失踪时间、省市区划码、详细地址、可选经纬度)、体貌(身高、体型、发型、口音、疤痕胎记、衣着、随身物品、特征描述)、情境(失踪情形枚举 `TRAFFICKING|MISSING|LOST|NO_CONTACT|LOST_ELDERLY`、经过描述、家庭背景与线索资料)与关联(报警情况、血样采集情况、跟进志愿者)五组。公开视图 MUST NOT 输出详细地址与联系方式。

#### Scenario: 缺少体貌信息不允许提交审核

- **WHEN** 用户对未填写任何体貌字段(身高/疤痕胎记/特征描述均为空)的档案调用 `POST /app/find/case/{id}/submit`
- **THEN** 系统拒绝并返回指明具体缺失字段的校验错误

#### Scenario: 公开详情脱敏

- **WHEN** 未登录用户调用 `GET /app/find/case/{id}`
- **THEN** 响应包含姓名、性别、年龄、失踪年月、省市区划与体貌描述,但不含详细地址、家属手机号与身份证号

#### Scenario: 完整视图仅限有权访问者

- **WHEN** 档案所有人调用 `GET /app/find/case/{id}/full`
- **THEN** 系统返回联系方式并写入一条访问日志;非所有人调用同一接口返回未授权

### Requirement: 档案状态机

系统 SHALL 强制档案状态沿 `DRAFT → PENDING_REVIEW → PUBLISHED → FOUND | ARCHIVED | OFFLINE | BLOCKED | ERASED` 流转,`REJECTED` 与 `WITHDRAWN` SHALL 可回到 `PENDING_REVIEW`。所有状态变更 MUST 写入 `gf_case_status_log`(记录前后状态、操作人、原因、时间),并 MUST 触发对应通知。非法状态跃迁 SHALL 被拒绝。

#### Scenario: 草稿不可直接被检索

- **WHEN** 一条 `DRAFT` 档案存在,用户请求广场任何流
- **THEN** 该档案不出现在结果中,直接访问其公开详情返回 404

#### Scenario: 非法跃迁被拒绝

- **WHEN** 对 `ERASED` 档案调用 `POST /app/find/case/{id}/renew`
- **THEN** 系统返回状态冲突错误,不产生状态日志与通知

#### Scenario: 找回确认需记录依据

- **WHEN** 所有人调用 `POST /app/find/case/{id}/found` 但未提供确认方式(可选值 `POLICE|GUARDIAN|DNA`)
- **THEN** 系统拒绝变更状态

### Requirement: 紧急标记与时效自动降级

档案失踪时间在 72 小时内 SHALL 自动标记 `urgency_level = URGENT`。`PUBLISHED` 状态超过 90 天无人确认 SHALL 由调度任务转为 `ARCHIVED`,并在降级前 7 天通过站内信提醒所有人续期;所有人可通过 `POST /app/find/case/{id}/renew` 续期重置时效。

#### Scenario: 新发布档案自动进入紧急档

- **WHEN** 提交一条失踪时间为 12 小时前的档案并通过审核
- **THEN** 该档案 `urgency_level` 为 `URGENT`,并出现在 `sort=urgent` 的流中

#### Scenario: 超期档案退出首屏

- **WHEN** 一条 `PUBLISHED` 档案的最后确认时间距今 91 天且调度任务执行
- **THEN** 状态转为 `ARCHIVED`,不再出现在任何广场流中,但档案所有人仍可在"我的发布"中看到并续期

### Requirement: 疑似重复检测与版本留痕

系统 SHALL 在提交审核时按"姓名规范化 + 性别 + 失踪日期 ±7 天 + 区划到市"检测疑似重复,命中 SHALL 要求调用方显式携带 `forceSubmit=true` 或改走合并流程,MUST NOT 静默创建重复档案。每次提交审核 SHALL 在 `gf_case_version` 写入全量快照与序号。

#### Scenario: 重复提交需显式确认

- **WHEN** 提交的档案与已有 `PUBLISHED` 档案命中重复规则且未带 `forceSubmit`
- **THEN** 系统返回冲突错误并附带疑似命中的档案编号列表,不创建新记录

#### Scenario: 修改历史可追溯

- **WHEN** 一条档案被驳回后修改并第二次提交
- **THEN** `gf_case_version` 存在 `seq=1` 与 `seq=2` 两条快照,审核人可查看差异

### Requirement: 媒体上传与本地化

系统 SHALL 复用既有文件与分片上传能力接收档案图片与视频,单档案图片上限 9 张、单张上限 5MB,非图片/视频类型 SHALL 被拒绝。来自采集的媒体 SHALL 落地到自有存储,MUST NOT 长期热链第三方站点地址。

#### Scenario: 超出图片数量上限

- **WHEN** 提交携带 10 张图片的档案
- **THEN** 系统拒绝并返回数量超限错误
