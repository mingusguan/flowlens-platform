# FlowLens Platform

FlowLens 是一个短视频流量监控平台骨架，前端采用 Art Design Pro 风格的 Vue3 管理台，后端采用 Spring Boot 3 + MyBatis-Plus。

## 模块

- `flowlens-admin`: Spring Boot 后端接口，包含登录鉴权、用户、角色、菜单/权限基础模块。
- `flowlens-ui`: Vue3 + Vite 前端管理台。
- `flowlens-admin/src/main/resources/db`: MySQL 初始化脚本。

## 默认账号

- 用户名: `admin`
- 密码: `admin123`

## 本地启动

```bash
mysql -uroot -p -e "create database if not exists flowlens default character set utf8mb4 collate utf8mb4_unicode_ci;"
set FLOWLENS_DB_USERNAME=root
set FLOWLENS_DB_PASSWORD=root
set FLOWLENS_SQL_INIT_MODE=always
mvn -pl flowlens-admin spring-boot:run
cd flowlens-ui
npm install
npm run dev
```

后端默认端口 `8088`，前端默认端口 `5173`。

默认 JDBC 地址为 `jdbc:mysql://localhost:3306/flowlens`。生产或已有数据环境不要开启 `FLOWLENS_SQL_INIT_MODE=always`，因为初始化脚本会重建系统表。

## Douyin cloud collector

Java only schedules the cloud collector. The actual Douyin WebSocket collection is handled by `collectors/douyin/saermart_adapter.py`, which imports `saermart/DouyinLiveWebFetcher` from a separate checkout.

Example:

```yaml
flowlens:
  live:
    collector:
      executable: python
      args:
        - E:/JAVA/flowlens-platform/collectors/douyin/saermart_adapter.py
        - --fetcher-path
        - E:/JAVA/DouyinLiveWebFetcher
        - --backend-url
        - http://127.0.0.1:8088
        - --anchor-id
        - "{anchorId}"
        - --token
        - "{reportToken}"
        - --live-id
        - "{liveId}"
```

The adapter reports normalized `COMMENT`, `GIFT`, `LIKE`, and `LIVE_END` events to `/api/live/report/cloud/events`.

Cloud auto-live probing is enabled by default when `flowlens.live.collector.cloud-enabled=true`.
The backend periodically runs the same adapter with `--probe`; when a configured anchor is detected live,
it creates a `LIVE` session automatically and then starts the cloud collector.

Useful probe settings:

```yaml
flowlens:
  live:
    collector:
      probe-enabled: true
      probe-delay-ms: 60000
      probe-timeout-seconds: 15
```
