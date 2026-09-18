-- ============================================================================
-- 集成测试专用 H2 建表脚本（H2 1.4.199 / MODE=MySQL）
--
-- 来源：由 T0 产出的 RuoYi-Vue-fast/sql/laboratory_schema.sql **逐字段转换**而来，
--       只做方言改写，不动任何字段名 / 类型 / 长度 / 索引（保证与生产库同源）。
--
-- 转换规则（本文件逐条落实）：
--   1. bigint(20)        -> bigint          （H2 不接受整型带显示宽度）
--   2. 列上的 comment ''  -> 删除            （H2 不支持内联列注释）
--   3. 表尾 engine=innodb ... comment=''  -> 只留 ")"
--   4. primary key / key idx_xxx  -> 原样保留（MODE=MySQL 下内联 KEY 可用，
--      已用 H2 Shell 实测通过，见 .workbuddy/logs/h2-probe3-out.txt）
--   5. create table if not exists -> 保留
--
-- 与生产库的已知差异（**不是漏写，是 H2 能力边界**）：
--   · H2 的 sysdate() 只到「日」精度（MySQL 是 DATETIME），
--     所以 mapper 里 create_time / update_time 写进去后时间部分是 00:00:00。
--     → 任何依赖 create_time 排序的断言在 H2 上都不可靠，用例里已避开。
--   · H2 1.4.199 **本身没有 date_format() / date_sub()**，
--     但**不必为此牺牲看板覆盖**：H2 支持 create alias ... for "类.方法"，
--     测试侧注册同名函数即可让生产 SQL 原样执行（生产 mapper XML 一个字都不用改）。
--     垫片见 src/test/java/.../support/H2MySqlDateFuncs.java 与 H2MySqlCompat.java，
--     安装动作在 AbstractLabIntegrationTest#resetDatabase()，看板用例见
--     LabDashboardIntegrationTest（DB-01 专门自检这个垫片）。
--   探测与实现依据：.workbuddy/logs/h2-probe-dashboard{,2,3}.txt
-- ============================================================================

-- 1. 实验室房间表
create table if not exists lab_room (
    room_id       bigint       not null auto_increment,
    room_name     varchar(100) not null,
    room_no       varchar(50)  not null,
    college_name  varchar(100) default null,
    manager_id    bigint       default null,
    manager_name  varchar(64)  default null,
    status        char(1)      default '0',
    del_flag      char(1)      default '0',
    create_by     varchar(64)  default '',
    create_time   datetime     default null,
    update_by     varchar(64)  default '',
    update_time   datetime     default null,
    remark        varchar(500) default null,
    primary key (room_id),
    key idx_lab_room_no (room_no)
);

-- 2. 实验室资产表
create table if not exists lab_asset (
    asset_id      bigint       not null auto_increment,
    asset_code    varchar(64)  not null,
    asset_name    varchar(100) not null,
    asset_type    varchar(50)  default null,
    model         varchar(100) default null,
    price         decimal(10,2) default '0.00',
    purchase_date date         default null,
    room_id       bigint       default null,
    status        char(1)      default '0',
    del_flag      char(1)      default '0',
    create_by     varchar(64)  default '',
    create_time   datetime     default null,
    update_by     varchar(64)  default '',
    update_time   datetime     default null,
    remark        varchar(500) default null,
    primary key (asset_id),
    key idx_lab_asset_code (asset_code),
    key idx_lab_asset_room_id (room_id),
    key idx_lab_asset_status (status)
);

-- 3. 设备报修表
create table if not exists lab_repair (
    repair_id          bigint        not null auto_increment,
    repair_code        varchar(64)   not null,
    asset_id           bigint        not null,
    fault_description  varchar(500)  not null,
    fault_level        char(1)       default '1',
    applicant_id       bigint        default null,
    applicant_name     varchar(64)   default null,
    applicant_phone    varchar(20)   default null,
    repair_user_id     bigint        default null,
    repair_user_name   varchar(64)   default null,
    repair_cost        decimal(10,2) default '0.00',
    finish_time        datetime      default null,
    status             char(1)       default '0',
    attachment_urls    varchar(1000) default null,
    rating             int           default null,
    evaluation_content varchar(500)  default null,
    evaluation_time    datetime      default null,
    del_flag           char(1)       default '0',
    create_by          varchar(64)   default '',
    create_time        datetime      default null,
    update_by          varchar(64)   default '',
    update_time        datetime      default null,
    remark             varchar(500)  default null,
    primary key (repair_id),
    key idx_lab_repair_code (repair_code),
    key idx_lab_repair_asset_id (asset_id),
    key idx_lab_repair_applicant_id (applicant_id),
    key idx_lab_repair_status (status)
);

-- 4. 报修处理记录表
create table if not exists lab_repair_record (
    record_id      bigint       not null auto_increment,
    repair_id      bigint       not null,
    action_name    varchar(50)  not null,
    from_status    char(1)      default null,
    to_status      char(1)      default null,
    operator_name  varchar(64)  default '',
    record_content varchar(500) default '',
    create_by      varchar(64)  default '',
    create_time    datetime,
    remark         varchar(500) default null,
    primary key (record_id),
    key idx_repair_record_repair_id (repair_id)
);

-- 5. 资产生命周期记录表
create table if not exists lab_asset_record (
    record_id      bigint       not null auto_increment,
    asset_id       bigint       not null,
    record_type    varchar(50)  not null,
    from_value     varchar(200) default null,
    to_value       varchar(200) default null,
    operator_name  varchar(64)  default '',
    record_content varchar(500) default '',
    create_by      varchar(64)  default '',
    create_time    datetime,
    remark         varchar(500) default null,
    primary key (record_id),
    key idx_asset_record_asset_id (asset_id)
);
