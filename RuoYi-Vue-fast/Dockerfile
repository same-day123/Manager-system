# ===========================================================================
#  高校实验室资产与报修管理平台 —— 后端镜像（多阶段构建）
#
#  构建：docker build -t lab-asset-backend:local ./RuoYi-Vue-fast
#  运行：见仓库根 docker-compose.yml（推荐 compose 一键起全套）
#
#  设计要点
#    1) 两段式：构建阶段含完整 Maven + JDK，运行阶段只有 JRE —— 运行镜像里
#       没有源码、没有依赖仓库、没有 Maven，体积与攻击面都小得多。
#    2) pom.xml 先单独拷再预热依赖：改 Java 代码不会触发重下依赖，层缓存命中。
#    3) 运行阶段非 root（uid/gid 1000）。容器内进程被打穿时拿不到 root。
#    4) 时区显式设为 Asia/Shanghai —— 报修时间线、看板的「今日/近 7 天」聚合
#       全都按日期分组，容器默认 UTC 会让跨零点的那批数据落到错误的日期里。
#    5) 镜像里不写任何密码：数据库、Redis、令牌密钥一律由运行期环境变量注入
#       （application-druid.yml / application.yml 已用 ${VAR:默认值} 留好口子）。
# ===========================================================================

# ---------- 构建阶段 ----------
# 与 pom.xml 的 java.version=1.8 对齐；该 tag 在 Docker Hub 上真实存在
# （备选：maven:3.9-eclipse-temurin-17，产物同为 Java 8 字节码）
FROM maven:3.9-eclipse-temurin-8 AS builder
WORKDIR /build

# 依赖预热。先只拷 pom.xml，这样只改 src 时这一层仍命中缓存。
# dependency:go-offline 偶有解析不全的插件（多模块/插件版本浮动时会报错），
# 这里容错续跑：真正的质量门禁是下一行的 package，编译错误绝不会被掩盖。
COPY pom.xml .
RUN mvn -B -q dependency:go-offline || true

COPY src ./src

# 不在这里跑测试：测试由 CI 的 backend job（= deploy/local-pipeline.ps1 阶段 2）
# 单独负责，镜像构建只负责出包，避免同一套用例跑两遍。
RUN mvn -B clean package -DskipTests

# ---------- 运行阶段 ----------
FROM eclipse-temurin:8-jre-jammy

ENV TZ=Asia/Shanghai
# 显式建组再建用户：某些基础镜像的 /etc/login.defs 未开 USERGROUPS_ENAB，
# 只写 useradd -m 不会顺带建同名组，后面的 chown ruoyi:ruoyi 就会失败。
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone \
 && groupadd -g 1000 ruoyi \
 && useradd -m -u 1000 -g 1000 -s /bin/bash ruoyi

WORKDIR /app

# pom.xml 里 finalName 取 ${project.artifactId}，所以产物就是 target/ruoyi.jar
COPY --from=builder /build/target/ruoyi.jar app.jar

# 文件上传根目录。application.yml 里 ruoyi.profile 写死成 Windows 路径
# D:/ruoyi/uploadPath（容器里不存在），运行期由环境变量 RUOYI_PROFILE 覆盖到
# /home/ruoyi/uploadPath（Spring Boot 的 relaxed binding 负责映射，无需改配置文件）。
# 这里先把目录建好并授权，否则首次上传资产图片/故障照片时会 Permission denied。
RUN mkdir -p /home/ruoyi/uploadPath \
 && chown -R ruoyi:ruoyi /app /home/ruoyi

USER ruoyi
EXPOSE 8080

# 本项目 pom 未引入 spring-boot-starter-actuator（离线环境拉 jar 有风险），
# 没有 /actuator/health 可用，所以用 bash 内建的 /dev/tcp 做端口连通性探测。
# start-period 给 60s：Spring 上下文 + Druid 初始化 + Quartz 调度器启动需要时间。
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD bash -c 'cat < /dev/null > /dev/tcp/127.0.0.1/8080' || exit 1

# exec 形式：java 直接成为 PID 1，能收到 SIGTERM 走优雅停机
# （若写成 shell 形式，PID 1 是 sh，docker stop 会等到超时才 SIGKILL）
ENTRYPOINT ["java","-jar","/app/app.jar"]
