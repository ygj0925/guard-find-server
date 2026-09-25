## ADDED Requirements

### Requirement: C 端身份与后台账号隔离

系统 SHALL 使用独立的 `gf_app_user` 表与独立的 Sa-Token 登录域(`loginType = "app"`)承载 C 端用户,并且 MUST NOT 复用 `sys_user` 作为 App 用户主表。C 端 token MUST NOT 被后台管理接口接受,后台 token MUST NOT 被接受为 C 端身份。

#### Scenario: App token 访问管理接口被拒绝

- **WHEN** 客户端持有效的 C 端 token 调用 `/system/user` 等管理接口
- **THEN** 系统返回未授权错误(`code != 0`),且不泄露任何管理数据

#### Scenario: 后台登录链路行为不变

- **WHEN** 管理员通过既有 `POST /auth/login` 登录
- **THEN** 登录流程、`UserContext`、菜单路由与权限集合与引入本 change 前完全一致

### Requirement: 短信验证码登录并自动建档

系统 SHALL 提供 `POST /app/auth/sms-code`(参数 `phone`、`scene`)与 `POST /app/auth/login`(`{authType:"PHONE", phone, code}`)。验证码 SHALL 为 6 位、有效期 5 分钟、存于 Redis 键 `CAPTCHA:app:{phone}`,并 SHALL 在手机号未注册时自动建档后直接签发 token。响应 SHALL 为 `{token, tokenName, expiresIn, isNewUser, user}`。

#### Scenario: 首次验证码登录自动注册

- **WHEN** 一个未注册的手机号用有效验证码调用 `POST /app/auth/login`
- **THEN** 系统创建 `gf_app_user` 记录,`isNewUser` 为 `true`,默认昵称为"无脚星友"加 4 位随机字符,并返回可用 token

#### Scenario: 验证码错误被拒绝

- **WHEN** 提交的验证码与 Redis 中缓存值不一致或已过期
- **THEN** 系统返回校验失败错误,不创建用户、不签发 token,并删除该验证码

#### Scenario: 验证码接口多维限流

- **WHEN** 同一手机号在 1 分钟内第二次请求验证码,或当日第 11 次请求
- **THEN** 系统以限流错误拒绝,并且不调用短信通道

### Requirement: 账号密码登录与凭据安全

系统 SHALL 支持 `POST /app/auth/login` 的 `{authType:"ACCOUNT", account, password}` 形式,密码 MUST 以 BCRYPT 存储;连续 5 次密码错误 SHALL 锁定账号 15 分钟。手机号、身份证等个人敏感字段 MUST 通过字段加密列存储,并在响应中脱敏。

#### Scenario: 密码错误达阈值触发锁定

- **WHEN** 同一账号连续 5 次密码校验失败
- **THEN** 第 6 次尝试直接返回账号锁定错误,即使密码正确也不签发 token

#### Scenario: 手机号密文存储与脱敏返回

- **WHEN** 读取 `GET /app/auth/me`
- **THEN** 响应中的手机号以掩码形式返回(如 `138****5678`),数据库中该列为密文

### Requirement: 会话策略取自客户端配置

系统 SHALL 从 `sys_client` 中 `APP` 客户端行读取 token 有效期、活跃超时、并发策略与最大登录设备数,MUST NOT 在代码中硬编码这些数值。默认 SHALL 为有效期 30 天、允许并发、最多 3 台设备。

#### Scenario: 超出最大设备数按策略处理

- **WHEN** 同一账号在第 4 台设备登录且客户端配置 `max_login_count=3`、溢出模式为踢出最早会话
- **THEN** 最早设备的 token 失效,新登录成功,并写登录日志

#### Scenario: 注销后 token 立即失效

- **WHEN** 已登录用户调用 `POST /app/auth/logout`
- **THEN** 该 token 后续请求全部返回未授权

### Requirement: 资料完善与账号注销冷静期

系统 SHALL 提供 `PUT /app/auth/profile`(昵称、头像、性别、身份类型 `GUARDIAN|VOLUNTEER|PUBLIC`、常驻省市区)与 `DELETE /app/auth/account`(申请注销)。注销 SHALL 进入 15 日冷静期,期内登录可撤销;期满 SHALL 物理清除可识别个人信息并保留匿名审计记录。

#### Scenario: 冷静期内登录撤销注销

- **WHEN** 用户提交注销申请后在第 5 天重新登录
- **THEN** 系统提示撤销注销,用户选择撤销后账号状态回到 `NORMAL`,数据完整

#### Scenario: 冷静期满清除识别信息

- **WHEN** 注销冷静期届满且用户未登录撤销
- **THEN** 昵称、手机号、头像等识别字段被清除,账号状态为 `CANCELLED`,该用户名下档案按删除权规则转为 `ERASED`
