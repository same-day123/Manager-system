# ===========================================================================
#  高校实验室资产与报修管理平台 —— 前端镜像（多阶段构建）
#
#  构建：docker build -t lab-asset-frontend:local ./RuoYi-Vue3
#  运行：见仓库根 docker-compose.yml
#
#  两个构建期事实（都真实核对过仓库，不是照抄模板）：
#    1) 仓库里只有 yarn.lock（yarn classic，lockfile v1），**没有**
#       package-lock.json。所以必须走 yarn —— `npm ci` 会因为缺 lock 文件
#       直接报错退出。
#    2) package.json 没有 packageManager 字段，corepack 会退回 yarn 1.22.x，
#       正好与 lockfile v1 匹配，可以直接 curl 到 registry 装依赖。
#
#  运行阶段用 nginx 托管静态产物；站点配置见同目录 nginx.conf。
# ===========================================================================

# ---------- 构建阶段 ----------
FROM node:22-alpine AS builder
WORKDIR /app

# 先只拷依赖清单：改业务代码（src/）不会让 yarn install 这一层失效
COPY package.json yarn.lock ./
RUN corepack enable && yarn install --frozen-lockfile

# .dockerignore 已排除 node_modules/ dist/ .git/，这一步 context 很小
COPY . .

# build:prod = vite build，读取 .env.production
# （VITE_APP_BASE_API='/prod-api' —— nginx 的反代前缀必须与此一致）
RUN yarn build:prod

# ---------- 运行阶段 ----------
# alpine 版本够用：只托管静态文件，不需要 glibc。
# 构建阶段用的 sass-embedded 在锁文件里已同时收录 linux-x64 与 linux-musl-x64
# 两种预编译产物，所以 alpine 与 debian 系基础镜像都能装通；
# 万一将来升级 sass-embedded 后 musl 产物缺失，把上面改成 node:22-slim 即可。
FROM nginx:alpine

COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# 健康检查：nginx:alpine 自带 busybox wget，够用来探首页
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1

# nginx 官方镜像的 CMD 已是 nginx -g 'daemon off;'，这里不重复声明
