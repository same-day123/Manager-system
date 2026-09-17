#!/bin/sh
# ===========================================================================
#  容器首次启动时的数据库初始化
#  由 mysql:8.0 官方镜像的 entrypoint 自动调用（挂载到 /docker-entrypoint-initdb.d/00-init.sh）
#
#  为什么需要这个脚本 —— 不能把 7 个 .sql 直接挂进 /docker-entrypoint-initdb.d
#    官方镜像会**按文件名字母序**依次执行 initdb 目录下的所有脚本。而本库对顺序
#    有强依赖（ry_20260417.sql 建 sys_* 表，laboratory_* 里引用它们）。按字母序会变成：
#        laboratory_demo_seed → laboratory_menu_role → laboratory_permission_fix
#        → laboratory_schema  → laboratory_upgrade   → laboratory_user_seed
#        → ry_20260417
#    也就是 ry_20260417.sql 最后才跑，前面每一个都会报「表不存在」。
#    所以：只挂这一个 .sh，在脚本里用显式列表锁定正确顺序。
#
#  顺序与 RuoYi-Vue-fast/sql/ 各文件头部注释里的 [N/7] 编号一一对应：
#     1 laboratory_schema          lab_* 业务表
#     2 ry_20260417                若依原生表（sys_user / sys_role / sys_menu / sys_job ...）
#     3 laboratory_menu_role       菜单、角色、权限标识（引用 sys_menu / sys_role）
#     4 laboratory_demo_seed       业务演示数据（引用 lab_* 与 sys_user）
#     5 laboratory_user_seed       演示账号（引用 sys_user / sys_user_role）
#     6 laboratory_upgrade         字段升级 + 4 组字典（引用 sys_dict_type）
#     7 laboratory_permission_fix  补齐实验室权限（幂等，修 D-21）
#    全部脚本都可重复执行（幂等），容器重建 / 重复导入都不会出错。
#
#  两个刻意的写法（审查要点，别"顺手优化"掉）：
#    1) 不用 set -e。initdb 目录里的 .sh 若没有执行位，官方 entrypoint 是
#       `. "$f"` **source** 进来的，set -e 会顺着漏进 entrypoint 自己的 shell，
#       改为影响后续它自己的流程。这里一律用显式判断 + die，行为在"被执行"和
#       "被 source"两种情况下完全一致。
#    2) root 连接方式实测一次再定。initdb 阶段 root 到底要不要口令，取决于基础
#       镜像版本内部的流程顺序，各版本不一致。先探测再走，避免"换个镜像 tag 就挂"。
#
#  依赖的环境变量（由 compose 注入）：
#    MYSQL_ROOT_PASSWORD  必填
#    MYSQL_DATABASE       默认 education_system
#    SQL_INCLUDE_CLEANUP  为 1 时追加 laboratory_cleanup.sql（界面精简，默认关闭）
# ===========================================================================

SQL_DIR=/sql
DB="${MYSQL_DATABASE:-education_system}"

die() {
    echo "[init] !! $1" >&2
    exit 1
}

echo "[init] 目标库: $DB ｜ 脚本目录: $SQL_DIR"

# ---- 连接方式探测 ---------------------------------------------------------
if mysql -uroot -e 'select 1' >/dev/null 2>&1; then
    MYSQL_CMD="mysql -uroot --default-character-set=utf8mb4"
    echo "[init] root 免密可连（本镜像在 initdb 阶段尚未设置 root 口令）"
elif [ -n "$MYSQL_ROOT_PASSWORD" ] && \
     MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e 'select 1' >/dev/null 2>&1; then
    # 用 MYSQL_PWD 而非 -p<password>：口令不会出现在进程列表里被 ps 看到，
    # 也不受口令里含空格/特殊字符的引号问题影响。
    export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
    MYSQL_CMD="mysql -uroot --default-character-set=utf8mb4"
    echo "[init] root 使用 MYSQL_ROOT_PASSWORD 连接"
else
    die "无法以 root 连接 MySQL（既不免密，MYSQL_ROOT_PASSWORD 也不对）"
fi

# ---- 按显式顺序导入 -------------------------------------------------------
for f in \
    laboratory_schema \
    ry_20260417 \
    laboratory_menu_role \
    laboratory_demo_seed \
    laboratory_user_seed \
    laboratory_upgrade \
    laboratory_permission_fix
do
    [ -f "$SQL_DIR/$f.sql" ] || die "缺少脚本 $SQL_DIR/$f.sql（检查 compose 里 ./RuoYi-Vue-fast/sql 的挂载）"
    echo "[init] ==> $f.sql"
    $MYSQL_CMD "$DB" < "$SQL_DIR/$f.sql" || die "导入 $f.sql 失败"
done

# ---- 可选：界面精简（默认不执行，避免改变演示数据的呈现）------------------
if [ "${SQL_INCLUDE_CLEANUP:-0}" = "1" ]; then
    echo "[init] ==> laboratory_cleanup.sql（因 SQL_INCLUDE_CLEANUP=1）"
    $MYSQL_CMD "$DB" < "$SQL_DIR/laboratory_cleanup.sql" || die "导入 laboratory_cleanup.sql 失败"
fi

# ---- 自检 ----------------------------------------------------------------
# 目的：如果初始化其实没成，就让容器在这里直接失败。
# 「容器起来了但一登录就报表不存在」这种半死状态拖到答辩现场最难收场。
LAB_TABLES=$($MYSQL_CMD -N -B -e \
    "select count(*) from information_schema.tables where table_schema='$DB' and table_name like 'lab\\_%'") \
    || die "自检查询失败"
SYS_USERS=$($MYSQL_CMD -N -B "$DB" -e "select count(*) from sys_user") \
    || die "自检查询失败"

echo "[init] 自检: lab_* 表 ${LAB_TABLES:-0} 张 ｜ sys_user ${SYS_USERS:-0} 行"

[ "${LAB_TABLES:-0}" -ge 5 ] || die "lab_* 表只有 ${LAB_TABLES:-0} 张（期望至少 5 张）"
[ "${SYS_USERS:-0}" -ge 1 ]  || die "sys_user 为空，若依基础数据未导入成功"

echo "[init] 数据库初始化完成，可以起来登录了"
