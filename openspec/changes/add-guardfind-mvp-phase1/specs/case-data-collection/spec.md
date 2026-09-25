## ADDED Requirements

### Requirement: 可插拔采集源

系统 SHALL 定义 `CollectSource` 接口(标识、列页拉取、详情页解析、字段映射为候选),采集主流程 MUST NOT 依赖任何具体站点实现。一期 SHALL 内置两个实现:`rangaihuijia`(公开寻亲库,列表 `GET {base}/xunren/category/{categoryId}?p-page={n}`,详情 `GET {base}/xunren/view/{id}`)与 `local-fixture`(从仓库内 HTML 样例读取,供无外网测试与演示)。新增站点 SHALL 仅需新增一个 `CollectSource` 实现与一行源配置。

#### Scenario: 换源不改主流程

- **WHEN** 注册一个新的 `CollectSource` 实现并在 `gf_collect_source` 增加一行配置
- **THEN** 编排器、去重、落库、审核链路代码无修改即可处理该源的数据

#### Scenario: 解析器从真实结构样例产出候选

- **WHEN** `local-fixture` 加载包含姓名、性别、生日、失踪时身高、失踪日期、失踪地址、特征描述、发生经过、编号的详情页样例
- **THEN** 产出的候选记录字段与样例一一对应,`source_id` 等于页面编号,`source_url` 为完整可访问链接

#### Scenario: 脏数据不中断整批

- **WHEN** 某详情页缺失失踪日期、字段错位、或返回 404 / 超时
- **THEN** 该条被记录为失败明细并跳过,同批次其余记录正常入库,任务整体不失败

### Requirement: 采集合规约束

采集 SHALL 在每轮开始时读取目标站点 `robots.txt` 并以 Redis 缓存 24 小时;命中 `Disallow` 或解析失败 MUST 中止该源本轮任务并产生告警日志,MUST NOT 绕过。系统 SHALL 实施域名级串行抓取与不低于配置值(默认 2 秒)的间隔,单轮页数 SHALL 受 `maxPages`(默认 5)限制。请求 User-Agent MUST 明示产品标识与联系入口。采集 MUST NOT 绕过登录、验证码、接口签名,不采集非公开内容。每个源的 `enabled` 开关 SHALL 默认为 `false`,须显式开启。

#### Scenario: robots 禁止时中止

- **WHEN** 目标站点 `robots.txt` 对当前路径返回 `Disallow: /xunren/`
- **THEN** 本轮任务中止、不产生任何抓取请求、写入失败告警,并保留中止原因

#### Scenario: 限速实际生效

- **WHEN** 配置 `crawlDelayMs=2000` 且单轮抓取 5 个详情页
- **THEN** 相邻两次同域请求的时间间隔均不小于 2000ms,日志中可见实测间隔

#### Scenario: 源默认关闭

- **WHEN** 新增一条源配置但未显式置 `enabled=true`
- **THEN** 调度任务跳过该源,不发起任何请求

### Requirement: 原文留痕与最小化存储

系统 SHALL 在 `gf_collect_raw` 保存原始快照、`sha256`、`fetch_time` 与完整 `source_url`,`(source_code, source_id)` MUST 唯一。候选与后续发布的档案 MUST 携带来源链接与采集时间以便追溯与删除。存储字段 SHALL 限于寻亲必要信息。原文正文 SHALL 保留 180 天后自动清理正文,仅留哈希与来源标识。

#### Scenario: 每条候选可回溯来源

- **WHEN** 查询任一条采集候选
- **THEN** 响应包含其来源站点标识、来源编号与可点击的原始页面 URL 及抓取时间

#### Scenario: 原文到期清理

- **WHEN** 一条 `gf_collect_raw` 记录已存在 181 天
- **THEN** 清理任务将其正文置空但保留 `sha256`、`source_url` 与 `fetch_time`

### Requirement: 归一化与去重

系统 SHALL 对采集字段执行归一化(日期精度到日、区划名称映射为行政区划码、性别与情形映射为枚举、姓名与别名去空白与全半角差异),并 SHALL 以 `dedupe_key = SHA256(规范化姓名 + 性别 + 失踪日期 + 失踪地区划到市)` 去重。同一 `dedupe_key` 的候选 SHALL 保留信息最完整者为主候选,其余标 `DUPLICATED` 并指向主候选。与站内已有档案命中同一 `dedupe_key` 时 SHALL 提示合并而非新建。

#### Scenario: 同批次重复条目收敛

- **WHEN** 一轮采集到两条 `dedupe_key` 相同的候选,其中一条多填了特征描述
- **THEN** 信息更全的一条状态为 `PENDING`,另一条为 `DUPLICATED` 且 `primary_id` 指向前者

#### Scenario: 重跑幂等

- **WHEN** 对同一源同一页区间连续执行两次采集
- **THEN** 第二次新增候选数为 0,原文快照不产生重复行

### Requirement: 候选须经人工审核方可对外

采集数据 MUST NOT 直接进入广场。系统 SHALL 将解析结果落 `gf_collect_candidate`(状态 `PENDING | APPROVED | REJECTED | MERGED | DUPLICATED | ERASED`),经 `POST /system/collect/candidate/{id}/approve` 才写入 `gf_find_case`(`source_type=COLLECT`、`source_id`、`source_url`,状态 `PUBLISHED`)。审批与合并 MUST 幂等,重复审批同一候选 SHALL 返回既有档案而非再建。

#### Scenario: 未审核候选不可见

- **WHEN** 存在 `PENDING` 候选而用户请求广场流或搜索
- **THEN** 结果中不含该候选对应的任何档案

#### Scenario: 审批幂等

- **WHEN** 对同一候选并发调用两次 approve
- **THEN** 最终只存在一条 `gf_find_case` 记录,两次调用返回同一个档案编号

#### Scenario: 合并保留双来源

- **WHEN** 审核人把候选合并到一条家属自行发布的档案
- **THEN** 该档案保留原发布信息,并新增一条来源关联记录指向采集候选,不产生新档案

### Requirement: 采集任务调度与可观测

系统 SHALL 以既有的双模式任务形态提供采集调度(`snail-job.enabled=false` 时走 Spring `@Scheduled`,否则走 `@JobExecutor`),并 SHALL 提供 `POST /system/collect/run/{sourceCode}` 手动触发与 `GET /system/collect/log` 查询批次日志(页数、抓取数、新增、重复、失败、耗时、错误摘要)。调度线程 MUST 以忽略租户上下文的方式运行。

#### Scenario: 手动触发受页数上限保护

- **WHEN** 运维以默认参数调用 `POST /system/collect/run/rangaihuijia`
- **THEN** 本轮最多抓取 5 个列表页,日志记录实际页数与跳过原因

#### Scenario: 批次指标可查

- **WHEN** 一轮采集完成
- **THEN** `GET /system/collect/log` 返回该批次的抓取数、新增数、重复数、失败数与总耗时

### Requirement: 删除与撤回请求响应

系统 SHALL 提供按 `source_url` 或 `source_id` 批量下架采集数据的接口,执行后候选与由它审批产生的档案 SHALL 转为 `ERASED`,并写入 `gf_collect_erase_log` 记录请求方、范围、时间与执行结果。

#### Scenario: 站方请求按来源批量清除

- **WHEN** 以某个来源编号调用撤回接口
- **THEN** 命中的候选与其生成的档案全部 `ERASED`,对外不可见,且删除审计可查询
