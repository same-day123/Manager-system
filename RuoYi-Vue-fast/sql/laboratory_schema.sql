-- ============================================================================
-- [1/7] 实验室业务表结构
-- 用途：创建 lab_room / lab_asset / lab_repair / lab_repair_record / lab_asset_record
--       五张业务表，使仓库在空库上可以完整重建（补齐 D-02 缺口）
-- 依赖：无（本脚本是本套脚本的第一个；它与 ry_20260417.sql 之间没有外键依赖，
--       但实验室表是业务表，放在基础表之前执行更符合阅读顺序）
-- 幂等：是（全部 create table if not exists，可重复执行）
-- 目标库：education_system
-- 说明：
--   1. 字段名与大小写**逐一对应** resources/mybatis/laboratory/ 下三个 Mapper XML 的
--      resultMap，不增不减。注意 lab_asset.room_name、lab_repair.asset_name / asset_code /
--      room_name 来自 SQL 联表，不是本表的物理列，**不要**在这里建。
--   2. 字符集统一 utf8mb4 / utf8mb4_general_ci。**Collation 必须写死**：MySQL 8 的默认
--      collation 是 utf8mb4_0900_ai_ci，不写就会与线上库（utf8mb4_general_ci）不一致。
--   3. 本脚本与线上库里已存在的表**有意保留了几处差异**（唯一索引等），
--      取舍理由与迁移方式见文件末尾「与线上库的差异」段。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 实验室房间表
-- ----------------------------------------------------------------------------
create table if not exists lab_room (
    room_id       bigint(20)   not null auto_increment comment '实验室ID',
    room_name     varchar(100) not null                comment '实验室名称',
    room_no       varchar(50)  not null                comment '房间号（如：信工楼 A301）',
    college_name  varchar(100) default null            comment '所属学院',
    manager_id    bigint(20)   default null            comment '管理员ID（关联 sys_user.user_id）',
    manager_name  varchar(64)  default null            comment '管理员姓名',
    status        char(1)      default '0'             comment '状态（0正常 1停用）',
    del_flag      char(1)      default '0'             comment '删除标志（0存在 2删除）',
    create_by     varchar(64)  default ''              comment '创建者',
    create_time   datetime     default null            comment '创建时间',
    update_by     varchar(64)  default ''              comment '更新者',
    update_time   datetime     default null            comment '更新时间',
    remark        varchar(500) default null            comment '备注',
    primary key (room_id),
    key idx_lab_room_no (room_no)
) engine=innodb auto_increment=1 default charset=utf8mb4 collate=utf8mb4_general_ci comment='实验室房间表';

-- ----------------------------------------------------------------------------
-- 2. 实验室资产表
-- ----------------------------------------------------------------------------
create table if not exists lab_asset (
    asset_id      bigint(20)   not null auto_increment comment '资产ID',
    asset_code    varchar(64)  not null                comment '资产编号（高校固定资产号）',
    asset_name    varchar(100) not null                comment '资产名称',
    asset_type    varchar(50)  default null            comment '资产类型（字典 lab_asset_type）',
    model         varchar(100) default null            comment '规格型号',
    price         decimal(10,2) default '0.00'         comment '购置价格',
    purchase_date date         default null            comment '购置日期',
    room_id       bigint(20)   default null            comment '所属实验室ID（关联 lab_room.room_id）',
    status        char(1)      default '0'             comment '资产状态（0正常 1停用 2维修中）',
    del_flag      char(1)      default '0'             comment '删除标志（0存在 2删除）',
    create_by     varchar(64)  default ''              comment '创建者',
    create_time   datetime     default null            comment '创建时间',
    update_by     varchar(64)  default ''              comment '更新者',
    update_time   datetime     default null            comment '更新时间',
    remark        varchar(500) default null            comment '备注',
    primary key (asset_id),
    key idx_lab_asset_code (asset_code),
    key idx_lab_asset_room_id (room_id),
    key idx_lab_asset_status (status)
) engine=innodb auto_increment=1 default charset=utf8mb4 collate=utf8mb4_general_ci comment='实验室资产表';

-- ----------------------------------------------------------------------------
-- 3. 设备报修表
--    注意：attachment_urls / rating / evaluation_content / evaluation_time 四个字段
--    （laboratory_upgrade.sql 里以 alter table 形式补的那批）**必须在这里一次性建好**。
--    若缺失，laboratory_upgrade.sql 的存储过程检测到"列已存在"就跳过，不报错也不建列，
--    一跑 Java 就 Unknown column。
-- ----------------------------------------------------------------------------
create table if not exists lab_repair (
    repair_id          bigint(20)    not null auto_increment comment '报修ID',
    repair_code        varchar(64)   not null                comment '报修单号（业务自动生成）',
    asset_id           bigint(20)    not null                comment '报修资产ID（关联 lab_asset.asset_id）',
    fault_description  varchar(500)  not null                comment '故障描述',
    fault_level        char(1)       default '1'             comment '故障等级（字典 lab_fault_level）',
    applicant_id       bigint(20)    default null            comment '申请人ID（关联 sys_user.user_id）',
    applicant_name     varchar(64)   default null            comment '申请人姓名',
    applicant_phone    varchar(20)   default null            comment '申请人电话',
    repair_user_id     bigint(20)    default null            comment '维修人ID（关联 sys_user.user_id）',
    repair_user_name   varchar(64)   default null            comment '维修人姓名',
    repair_cost        decimal(10,2) default '0.00'          comment '维修成本',
    finish_time        datetime      default null            comment '完成时间',
    status             char(1)       default '0'             comment '报修状态（0待审核 1待维修 2维修中 3已完成 4已拒绝）',
    attachment_urls    varchar(1000) default null            comment '故障图片（多张以逗号分隔）',
    rating             int           default null            comment '维修评分（1~5）',
    evaluation_content varchar(500)  default null            comment '维修评价',
    evaluation_time    datetime      default null            comment '评价时间',
    del_flag           char(1)       default '0'             comment '删除标志（0存在 2删除）',
    create_by          varchar(64)   default ''              comment '创建者',
    create_time        datetime      default null            comment '创建时间',
    update_by          varchar(64)   default ''              comment '更新者',
    update_time        datetime      default null            comment '更新时间',
    remark             varchar(500)  default null            comment '备注',
    primary key (repair_id),
    key idx_lab_repair_code (repair_code),
    key idx_lab_repair_asset_id (asset_id),
    key idx_lab_repair_applicant_id (applicant_id),
    key idx_lab_repair_status (status)
) engine=innodb auto_increment=1 default charset=utf8mb4 collate=utf8mb4_general_ci comment='设备报修表';

-- ----------------------------------------------------------------------------
-- 4. 报修处理记录表
--    字段与 laboratory_upgrade.sql 第 48~61 行**保持完全一致**（索引名也不改）。
--    重复定义无害：两边都是 if not exists，后执行的一方直接跳过。
-- ----------------------------------------------------------------------------
create table if not exists lab_repair_record (
    record_id      bigint(20)   not null auto_increment comment '记录ID',
    repair_id      bigint(20)   not null                comment '报修ID',
    action_name    varchar(50)  not null                comment '操作名称',
    from_status    char(1)      default null            comment '原状态',
    to_status      char(1)      default null            comment '新状态',
    operator_name  varchar(64)  default ''              comment '操作人',
    record_content varchar(500) default ''              comment '记录内容',
    create_by      varchar(64)  default ''              comment '创建者',
    create_time    datetime                             comment '创建时间',
    remark         varchar(500) default null            comment '备注',
    primary key (record_id),
    key idx_repair_record_repair_id (repair_id)
) engine=innodb auto_increment=1 default charset=utf8mb4 collate=utf8mb4_general_ci comment='报修处理记录表';

-- ----------------------------------------------------------------------------
-- 5. 资产生命周期记录表
--    字段与 laboratory_upgrade.sql 第 63~76 行**保持完全一致**（索引名也不改）。
-- ----------------------------------------------------------------------------
create table if not exists lab_asset_record (
    record_id      bigint(20)   not null auto_increment comment '记录ID',
    asset_id       bigint(20)   not null                comment '资产ID',
    record_type    varchar(50)  not null                comment '记录类型（入库/状态变更/调拨等）',
    from_value     varchar(200) default null            comment '原值',
    to_value       varchar(200) default null            comment '新值',
    operator_name  varchar(64)  default ''              comment '操作人',
    record_content varchar(500) default ''              comment '记录内容',
    create_by      varchar(64)  default ''              comment '创建者',
    create_time    datetime                             comment '创建时间',
    remark         varchar(500) default null            comment '备注',
    primary key (record_id),
    key idx_asset_record_asset_id (asset_id)
) engine=innodb auto_increment=1 default charset=utf8mb4 collate=utf8mb4_general_ci comment='资产生命周期记录表';

-- ============================================================================
-- 与线上库（education_system）的差异 —— 有意为之，不是漏写
-- ============================================================================
-- 本脚本的基准选择：**字段类型/长度/默认值以线上库为准，索引策略以设计意图为准。**
-- 逐条对照（线上库用 show create table 导出）：
--
-- 1) 索引：线上库 lab_asset 有 UNIQUE KEY uni_asset_code(asset_code)、lab_repair 有
--    UNIQUE KEY uni_repair_code(repair_code)；本脚本只建**普通索引**。
--    理由：本系统删除一律是**逻辑删除**（del_flag='2'），行永远留在表里。而
--    LabAssetServiceImpl.checkAssetCodeUnique / LabRoomServiceImpl 的唯一性判断都带
--    `del_flag='0'` 条件，即"应用层认为编号可以重用"。两者叠加就会出现：
--    资产 A 被删除 → 重建同编号 A → 应用层校验放行 → 数据库唯一索引拒绝 → 接口 500。
--    即"删了资产就建不回同编号"。这是真缺陷，故不把唯一索引写进新脚本。
--    → 线上库那两个唯一键由 laboratory_index_fix.sql 幂等删除（不参与 [N/7] 顺序）。
--
-- 2) lab_asset.price / lab_repair.repair_cost：线上库是 decimal(10,2) default '0.00'，
--    本脚本照抄线上库。（T0 卡原写 decimal(12,2)，两者对本系统金额量级无差别，
--    但三方 schema 必须一致，故以线上库为准。）
--
-- 3) lab_asset.room_id：线上库**可空**（default null），本脚本照抄线上库。
--    T0 卡原写 not null；改可空是为兼容"资产先入库、后归属房间"的场景。
--
-- 4) lab_room：线上库**一个二级索引都没有**（room_no 查询走全表扫描）。
--    本脚本补 idx_lab_room_no —— 房间按编号查是列表页的高频路径。
--
-- 5) lab_asset.status 的 comment：线上库写的是历史文本「0在用 1闲置 2报修中 3报废」，
--    与代码、字典**都不一致**。代码 LabConstants 的 ASSET_STATUS_NORMAL/DISABLED/REPAIRING
--    是 0/1/2，字典 lab_asset_status 也是「0正常 1停用 2维修中」。本脚本采用**代码口径**，
--    线上库那句注释已过期，属历史遗留描述错误。
--
-- 6) 字符集：线上库五张表都是 utf8mb4 / utf8mb4_general_ci，本脚本显式写死，
--    避免 MySQL 8 默认的 utf8mb4_0900_ai_ci 造成 collation 不一致。
-- ============================================================================
