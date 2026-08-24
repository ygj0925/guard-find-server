# Guard Find Server

`guard-find-server` 是 Guard Find 用户侧服务端。它是一个 Java 17 / Spring Boot 多模块 Maven reactor，提供认证、通用系统能力、文件存储、租户和调度基础设施，并为用户侧领域服务提供扩展位置。

## 模块与制品

- `guard-find-server-api`：可部署主 API，薄包主类为 `com.guardfind.server.GuardFindServerApplication`，根制品为 `com.guardfind.server:guard-find-server`。
- `guard-find-server-system`：认证与系统管理能力。
- `guard-find-server-common`：跨模块基础设施与 CRUD 基类。
- `guard-find-server-plugin`：open、tenant、schedule、generator 插件聚合及其 `guard-find-server-*` 子制品。
- `guard-find-server-extension`：扩展聚合；`guard-find-server-extension-schedule-server` 的启动类为 `com.guardfind.server.extension.scheduling.GuardFindServerScheduleApplication`。

## 本地构建与启动

```powershell
mvn -B -Dspotless.apply.skip=true -DskipTests package
# 或在 IDE 运行 com.guardfind.server.GuardFindServerApplication
```

主服务默认使用 `dev` profile；部署时通过 `DB_*`、`REDIS_*`、`SCHEDULE_*` 等环境变量提供基础设施连接参数。默认构建为 thin jar，产物位于 `guard-find-server-api/target/app/`。

## 容器部署

`docker/docker-compose.yml` 的 Compose 项目名为 `guard-find-server`，服务均采用 `guard-find-server-*` 前缀：MySQL 映射 `13306`、Redis 映射 `16379`、API 映射 `18000/17889`，独立调度服务映射 `18001/17888`。部署前需将构建输出复制到相应 Docker build context，并填入密码和生产环境配置。

## 归因与许可

本工程保留上游许可证头和 Apache-2.0 许可文本。它依赖第三方 `top.continew.starter`（ContiNew Starter）；该外部依赖的 Maven 坐标、配置前缀和 API 名称不属于 Guard Find 的内部命名迁移范围。
