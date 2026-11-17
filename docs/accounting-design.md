# 简账扩展设计（2026-11-17）

使用现有 /app/auth C 端账号。原有 /app/ledger 和 /app/budget 保持契约，修复 groupId 未落库、缺少成员校验的问题；个人和共享流水仍由既有服务提供。

新增 /app/accounting/snapshot GET/PUT 用于简账完整个人账本跨设备备份/恢复，包含自定义分类、资产、标签、账期、周期等现有 ledger 模型不支持的数据。此备份不是旧 ledger 流水的投影，不自动导入旧流水，也不冒充逐笔增量同步。客户端明确展示两个数据源。后续统一领域模型时需要专门迁移。

存储 gf_accounting_snapshot(user_id 主键、version、payload LONGTEXT、operation_id、update_time)。user_id 只从 AppUserContextHolder 取得，客户端无法指定归属。无跨用户查询接口。PUT 需要 baseVersion、operationId、payload；版本 CAS 避免静默覆盖，重复 operationId+相同内容和基准版本幂等返回，冲突使用现有 4002 业务码。GET 不存在返回 version=0,payload=null。整个备份最大 5MB 字符，校验 JSON 格式、格式版本、核心集合与账单字段。备份仍为服务端可读取的个人财务数据，不承诺端到端加密。

迁移通过新增 accounting_snapshot.sql + master include；保留其他工作未提交改动。未运行迁移、未启动生产服务、未发送短信。单测覆盖身份归属、初始版本、CAS 冲突、幂等重试和无效负载。

原生提醒/锁/小组件和真正增量同步另有前端规划；本设计不宣称已经覆盖它们。

## 后续代码补齐（2026-11-17）

- 同步：GET snapshot 支持 knownVersion，相同版本不返回 payload；PATCH snapshot 接收受限集合的键值差量，使用原有 CAS 版本与 operationId。上传为实体差量；云端版本变化时仍下载完整快照，不冒充游标式增量下载。客户端持久化待确认操作并以三方合并处理并发，冲突暂停。
- 周期：AccountingRecurringWorker 通过条件配置启用，分页读取快照，按日/周/月生成确定性 ID 的实例，月末钳制，包含删除墓碑的幂等检查；CAS 失败留待下一轮，不覆盖 App 并发写入。默认关闭，未在当前环境启动。
- 票据：新增 gf_accounting_receipt，图片/PDF 二进制不进入公开静态目录。所有读写按 C 端用户过滤，检查文件头与 MIME，最大5MB；支持最近100张列表、读取与删除。总空间保护100MB，作为免费服务资源控制。并发上传可能短暂超过空间阈值，部署时需结合存储限额和网关限流。
- 识别：独立 HTTPS 兼容供应商网关，密钥仅服务端配置；缺少配置直接拒绝，不返回假结果。文字/图片只返回经验证的单笔草稿；音频转写为文字，最终入账须用户在 App 确认。默认禁用，未调用真实供应商。
- 组长移交：现组长与接收成员资格校验、组行锁、同一事务更新 owner 和成员角色；移交时刷新邀请码。
- 数据生命周期：票据和快照是新增个人数据，已提供默认关闭的 AccountingRetentionWorker，对已正式注销(status=4)或已物理删除的账号清理票据/快照，冷静期(status=3)保留；不修改上游注销状态流程。生产启用前仍须审核备份保留策略。本轮未执行历史清理或生产迁移。
