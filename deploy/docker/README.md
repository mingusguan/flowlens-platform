# FlowLens Docker Deployment

## 文件说明

- `Dockerfile`: 构建后端镜像，包含 Java 17、Python 3、Node.js 和抖音采集器依赖。
- `docker-compose.yml`: 拉取并启动阿里云镜像服务中的 `flowlens-admin` 镜像，默认启用抖音云采集器。
- `.env.example`: 环境变量模板。实际部署时可以创建 `.env`，Docker Compose 会自动读取。
- `commitid`: 服务器部署时读取的镜像 tag，通常写 Git commit id 或阿里云 ACR 构建出来的 tag。
- `deploy.sh`: 读取 `commitid`，拉取镜像并重启服务。

## 镜像仓库

`.env` 中配置阿里云镜像地址：

```env
FLOWLENS_IMAGE_REPOSITORY=registry.cn-hangzhou.aliyuncs.com/your-namespace/flowlens-admin
FLOWLENS_IMAGE_TAG=latest
```

`deploy.sh` 会优先用 `commitid` 文件里的值覆盖 `FLOWLENS_IMAGE_TAG`。例如：

```bash
echo 6f2a9c1 > commitid
bash deploy.sh
```

也可以直接传参部署，脚本会同步更新 `commitid`：

```bash
bash deploy.sh 6f2a9c1
```

部署前先登录阿里云镜像服务：

```bash
docker login registry.cn-hangzhou.aliyuncs.com
```

## MySQL

当前 `docker-compose.yml` 不再启动 MySQL，默认连接已经在宿主机或其他服务中启动的数据库：

```env
FLOWLENS_DB_URL=jdbc:mysql://host.docker.internal:13306/flowlens-platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
FLOWLENS_DB_USERNAME=flowlens
FLOWLENS_DB_PASSWORD=flowlens_change_me
```

如果 MySQL 是另一个 Docker Compose 服务，并且没有暴露到宿主机端口，可以把 `FLOWLENS_DB_URL` 里的主机名改成那个 MySQL 服务名，同时让两个 compose 使用同一个 Docker network。

## 抖音云采集器路径

`saermart_adapter.py` 已经打进后端镜像，容器内路径固定为：

```text
/app/collectors/douyin/saermart_adapter.py
```

`DouyinLiveWebFetcher` 不在本仓库内，需要单独放在宿主机，并挂载到容器内：

```text
宿主机: FLOWLENS_DOUYIN_FETCHER_HOST_PATH
容器内: FLOWLENS_DOUYIN_FETCHER_PATH
```

服务器推荐放置路径：

```env
FLOWLENS_DOUYIN_FETCHER_HOST_PATH=/data/mingus/flowlens/DouyinLiveWebFetcher
FLOWLENS_DOUYIN_FETCHER_PATH=/opt/DouyinLiveWebFetcher
FLOWLENS_DOUYIN_USE_LOCAL_DEPS=false
```

`FLOWLENS_DOUYIN_USE_LOCAL_DEPS=false` 表示 Docker 容器忽略 `DouyinLiveWebFetcher/_deps`，使用镜像内安装的 Linux Python 依赖。这个值建议保持为 `false`，因为本地 Windows `_deps` 里可能包含 `.dll`，Linux 容器无法使用。

## 启动

```bash
bash deploy.sh
```

查看日志：

```bash
docker compose logs -f flowlens-admin
```

## 验证云采集探测

把 `<live-id>` 替换成抖音直播间 URL 后缀：

```bash
docker compose exec flowlens-admin python3 /app/collectors/douyin/saermart_adapter.py \
  --probe \
  --fetcher-path /opt/DouyinLiveWebFetcher \
  --live-id <live-id>
```

能输出 JSON 即说明容器内 Python 依赖和 `DouyinLiveWebFetcher` 挂载路径可用。

## 首次数据库初始化

因为 MySQL 不由当前 compose 启动，所以首次初始化需要手动在已有 MySQL 上执行：

```text
flowlens-admin/src/main/resources/db/schema.sql
flowlens-admin/src/main/resources/db/data.sql
```

注意：`schema.sql` 包含删表语句，只适合新库初始化。
