## ADDED Requirements

### Requirement: 广场公共信息流

系统 SHALL 提供公开可读的 `GET /app/plaza/feed`,支持 `sort` 取值 `latest | urgent | nearby | found`、`provinceCode`、`cityCode`、`keyword`、`size` 与游标 `cursor`,响应 SHALL 为 `{records, nextCursor, hasMore}`,并且 MUST 仅返回 `PUBLISHED` 状态档案。`DRAFT`、`REJECTED`、`WITHDRAWN`、`ARCHIVED`、`OFFLINE`、`BLOCKED`、`ERASED` SHALL 一律被排除。

#### Scenario: 已下架档案直链不可访问

- **WHEN** 一条档案被处置为 `OFFLINE` 后,用户以其 id 请求广场卡片或公开详情
- **THEN** 该档案不出现在任何流中,公开详情返回 404 且带"该信息已下架"说明

#### Scenario: 就近流按行政区过滤

- **WHEN** 请求 `sort=nearby&cityCode=130400`
- **THEN** 返回记录的失踪地区划码均属于该市级码,不混入其他城市档案

#### Scenario: 喜报流不混入最新流

- **WHEN** 请求 `sort=latest`
- **THEN** 结果不包含 `status=FOUND` 的档案;`sort=found` 仅返回已找回档案

### Requirement: 游标分页稳定性

信息流 SHALL 使用游标(排序键 + 主键)分页,MUST NOT 使用 offset 深翻。同一游标序列内不得出现重复记录或漏记录,即便期间有新档案发布。

#### Scenario: 翻页期间新数据写入不影响已翻内容

- **WHEN** 用户在第 1 页与第 2 页之间,系统新发布了一条排序更靠前的档案
- **THEN** 第 2 页不出现第 1 页已见过的记录,也不出现因偏移导致的漏记录

### Requirement: 卡片脱敏与筛选元数据

广场卡片 SHALL 只输出姓名、性别、年龄段、失踪年月、区划到市级、紧急标记与缩略图。系统 SHALL 提供 `GET /app/plaza/filters` 返回区划树、失踪情形、年龄段、血样状态等字典项,前端 MUST NOT 自行硬编码这些枚举。`GET /app/plaza/statistics` SHALL 返回累计建档数、已找回数、近 30 日新增数。

#### Scenario: 卡片不含详细地址

- **WHEN** 客户端拉取 `sort=latest` 结果
- **THEN** 每条记录的地址字段最多精确到区县级名称,不含街道门牌与联系方式

### Requirement: 搜索与埋点

系统 SHALL 提供 `GET /app/search`,对姓名、别名、失踪地点文本与特征描述做关键词匹配并叠加与广场一致的筛选与状态过滤;搜索词 SHALL 经敏感词与长度校验。一期 SHALL 使用 MySQL 索引实现,MUST NOT 为此引入 Elasticsearch。系统 SHALL 提供 `POST /app/plaza/track` 接收曝光与点击事件,允许未登录匿名上报但 MUST 限制频率。

#### Scenario: 别名可被检索

- **WHEN** 档案登记了别名"小虎",用户以"小虎"为关键词搜索
- **THEN** 该档案出现在结果中

#### Scenario: 超长与敏感搜索词被拒

- **WHEN** 搜索词长度超过 50 字符或命中禁用词
- **THEN** 系统返回校验错误,不执行查询
