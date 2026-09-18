-- ============================================================================
-- [5/7] 实验室演示账号与演示报修单
-- 用途：创建 6 个演示账号并绑定到 [3/7] 建好的实验室角色与岗位，
--       并在末尾插入演示用报修单与报修处理记录
-- 依赖：ry_20260417.sql、laboratory_schema.sql、laboratory_menu_role.sql、
--       laboratory_demo_seed.sql（资产与房间）
-- 幂等：是（全部使用 where not exists 判定，可重复执行）
-- 目标库：education_system
-- 说明：密码统一为 admin123，此处存的是 BCrypt 密文（复用若依原生 admin 账号的哈希）
--       报修单为什么放在本文件而不是 [4/7]：报修单要按 user_name 解析申请人与
--       维修人，而 6 个演示账号在本文件才创建（若依 sys_user 是自增主键，
--       写死 ID 会在新库上错位），所以只能排在账号创建之后。
-- ============================================================================
--
-- 演示账号一览：
--   labadmin / admin123   实验室管理员
--   asset01  / admin123   资产管理员
--   repair01 / admin123   维修工程师
--   room01   / admin123   房间管理员
--   student1 / admin123   学生助管
--   viewer01 / admin123   实验室观察员

set @default_password := '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2';
set @dept_rd := (select dept_id from sys_dept where dept_name = '研发部门' limit 1);
set @dept_test := (select dept_id from sys_dept where dept_name = '测试部门' limit 1);
set @dept_ops := (select dept_id from sys_dept where dept_name = '运维部门' limit 1);
set @post_manager := (select post_id from sys_post where post_code = 'se' limit 1);
set @post_user := (select post_id from sys_post where post_code = 'user' limit 1);

insert into sys_user (
    dept_id, user_name, nick_name, user_type, email, phonenumber, sex, avatar,
    password, status, del_flag, login_ip, login_date, create_by, create_time,
    update_by, update_time, remark
)
select seed.dept_id, seed.user_name, seed.nick_name, '00', seed.email, seed.phonenumber, seed.sex, '',
       @default_password, '0', '0', '127.0.0.1', sysdate(), 'admin', sysdate(), '', null, seed.remark
from (
    select @dept_rd as dept_id, 'labadmin' as user_name, '实验室管理员' as nick_name,
           'labadmin@example.com' as email, '13900001001' as phonenumber, '0' as sex, '实验室全流程演示账号' as remark
    union all select @dept_rd, 'asset01', '资产管理员', 'asset01@example.com', '13900001002', '1', '资产台账与导入导出演示账号'
    union all select @dept_ops, 'repair01', '维修工程师', 'repair01@example.com', '13900001003', '0', '报修审核与维修处理演示账号'
    union all select @dept_test, 'room01', '房间管理员', 'room01@example.com', '13900001004', '1', '实验室房间维护演示账号'
    union all select @dept_test, 'student1', '学生助管', 'student1@example.com', '13900001005', '0', '学生报修与评价演示账号'
    union all select @dept_test, 'viewer01', '实验室观察员', 'viewer01@example.com', '13900001006', '2', '只读浏览演示账号'
) seed
where not exists (select 1 from sys_user u where u.user_name = seed.user_name);

insert into sys_user_role (user_id, role_id)
select u.user_id, r.role_id
from (
    select 'labadmin' as user_name, 'lab_manager' as role_key
    union all select 'asset01', 'asset_keeper'
    union all select 'repair01', 'repair_engineer'
    union all select 'room01', 'room_keeper'
    union all select 'student1', 'student_assistant'
    union all select 'viewer01', 'lab_viewer'
) seed
join sys_user u on u.user_name = seed.user_name
join sys_role r on r.role_key = seed.role_key
where not exists (
    select 1 from sys_user_role ur where ur.user_id = u.user_id and ur.role_id = r.role_id
);

insert into sys_user_post (user_id, post_id)
select u.user_id, seed.post_id
from (
    select 'labadmin' as user_name, @post_manager as post_id
    union all select 'asset01', @post_user
    union all select 'repair01', @post_user
    union all select 'room01', @post_user
    union all select 'student1', @post_user
    union all select 'viewer01', @post_user
) seed
join sys_user u on u.user_name = seed.user_name
where seed.post_id is not null
  and not exists (
      select 1 from sys_user_post up where up.user_id = u.user_id and up.post_id = seed.post_id
  );

-- ----------------------------------------------------------------------------
-- 演示报修单（4 条：3 条已完成 + 1 条已拒绝，对应线上库的 4 张工单）
-- 三个设计要点，都不是随手写的：
--   1. 申请人写 student1、维修人写 repair01 —— **不能写死 user_id**。除了想当然的
--      "自增主键在新库上会错位"之外，更硬的理由是：报修列表对非处理角色是按
--      applicant_id 过滤的（LabRoleUtils.canHandleRepair），工单不挂在 student1 名下，
--      学生助管登录后就一张都看不到，「查看本人报修」这条用户故事当场演示不出来。
--   2. create_time 用**相对当前时间**的写法（前 1~5 天），不用固定日期。原因：
--      首页看板的「报修趋势」「维修费用趋势」两张图只统计近 7 天
--      （LabDashboardMapper 写死 date_sub(curdate(), interval 6 day)），
--      固定成 2026-07 的老日期会让这两张图恒为 0，演示时看着像坏了。
--      **单号仍取固定值**（BX202607020001~004）以保证脚本可重复执行——这点与线上库
--      现状一致：线上 3001 的单号日期是 07-02，其 create_time 却是 06-30。
--   3. rating / evaluation_* 一律留空：答辩要**现场走一遍评价流程**，
--      预填了评分反而没有可演示的"已完成待评价"单子。
-- ----------------------------------------------------------------------------
insert into lab_repair (
    repair_code, asset_id, fault_description, fault_level,
    applicant_id, applicant_name, applicant_phone,
    repair_user_id, repair_user_name, repair_cost, finish_time,
    status, del_flag, create_by, create_time, remark
)
select seed.repair_code,
       a.asset_id,
       seed.fault_description,
       seed.fault_level,
       u.user_id,
       u.nick_name,
       u.phonenumber,
       ru.user_id,
       ru.nick_name,
       seed.repair_cost,
       seed.finish_time,
       seed.status,
       '0',
       'admin',
       seed.create_time,
       seed.remark
from (
    select 'BX202607020001' as repair_code, 'LAB-DEMO-2026-002' as asset_code,
           '服务器虚拟机启动缓慢，部分教学镜像无法正常挂载。' as fault_description,
           '2' as fault_level, 'student1' as applicant_user, 'repair01' as repair_user,
           0.00 as repair_cost, date_sub(sysdate(), interval 4 day) as finish_time,
           '3' as status, date_sub(sysdate(), interval 5 day) as create_time,
           '演示数据：已完成的服务器报修' as remark
    union all select 'BX202607020002', 'LAB-DEMO-2026-007',
           '交换机实验套件第3组端口无法建立连接，指示灯异常。',
           '3', 'student1', 'repair01', 0.00, date_sub(sysdate(), interval 3 day),
           '3', date_sub(sysdate(), interval 4 day), '演示数据：已完成的网络设备报修'
    union all select 'BX202607020003', 'LAB-DEMO-2026-013',
           '示波器探头接触不良，已更换备用探头后恢复正常。',
           '1', 'student1', 'repair01', 120.00, date_sub(sysdate(), interval 1 day),
           '3', date_sub(sysdate(), interval 2 day), '演示数据：已完成并产生维修成本的报修'
    union all select 'BX202607020004', 'LAB-DEMO-2026-020',
           '误报：设备策略配置错误，硬件无故障。',
           '1', 'student1', 'repair01', 0.00, null,
           '4', date_sub(sysdate(), interval 1 day), '演示数据：被拒绝的报修'
) seed
join lab_asset a on a.asset_code = seed.asset_code and a.del_flag = '0'
join sys_user u on u.user_name = seed.applicant_user
join sys_user ru on ru.user_name = seed.repair_user
where not exists (select 1 from lab_repair r where r.repair_code = seed.repair_code);

-- ----------------------------------------------------------------------------
-- 演示报修单（**在途 2 条**）：让「维修中资产」与「在途工单」自洽（见 D-24）
-- 上面 4 条工单全部落在终态（3 已完成 / 4 已拒绝），而下方 lab_asset 里有 2 台资产
-- status='2' 维修中（备注"报修中资产演示"）——**资产在修、却没有任何工单可点开**。
-- 业务规则只有单向的「提交报修 → 资产置维修中」，所以这不算规则错误，但答辩开场
-- 画面会出现「维修中资产 2 / 待处理工单 0」，观众第一反应是"数据坏了"。
-- 这里补上这两台资产对应的在途工单：
--   1. BX202607020005 待审核（status '0'）→ 现场可演示**审核**流程；
--   2. BX202607020006 维修中（status '2'）→ 现场可演示**状态推进**到已完成。
-- 日期同样取相对当前时间，理由与上面 4 条完全一致。
-- 注意 **repair_user 必须 left join**：待审核单还没有维修人，用 join 会让整行插不进去。
-- ----------------------------------------------------------------------------
insert into lab_repair (
    repair_code, asset_id, fault_description, fault_level,
    applicant_id, applicant_name, applicant_phone,
    repair_user_id, repair_user_name, repair_cost, finish_time,
    status, del_flag, create_by, create_time, remark
)
select seed.repair_code,
       a.asset_id,
       seed.fault_description,
       seed.fault_level,
       u.user_id,
       u.nick_name,
       u.phonenumber,
       ru.user_id,
       ru.nick_name,
       0.00,
       null,
       seed.status,
       '0',
       'admin',
       seed.create_time,
       seed.remark
from (
    select 'BX202607020005' as repair_code, 'LAB-DEMO-2026-009' as asset_code,
           '靶场环境批量创建虚拟机时报存储卷挂载失败，疑为磁盘阵列故障。' as fault_description,
           '3' as fault_level, 'student1' as applicant_user, null as repair_user,
           '0' as status, date_sub(sysdate(), interval 1 day) as create_time,
           '演示数据：待审核报修（可现场演示审核流程）' as remark
    union all select 'BX202607020006', 'LAB-DEMO-2026-024',
           '离心机运行时异响明显、转速不稳，需停机检修转子轴承。',
           '2', 'student1', 'repair01',
           '2', date_sub(sysdate(), interval 3 day),
           '演示数据：维修中报修（可现场演示状态推进）'
) seed
join lab_asset a on a.asset_code = seed.asset_code and a.del_flag = '0'
join sys_user u on u.user_name = seed.applicant_user
left join sys_user ru on ru.user_name = seed.repair_user
where not exists (select 1 from lab_repair r where r.repair_code = seed.repair_code);

-- 报修处理记录：给上面第一张"已完成"的单子补一条状态流转记录，
-- 让报修详情的处理时间线（US-03）有内容可看。
insert into lab_repair_record (
    repair_id, action_name, from_status, to_status, operator_name,
    record_content, create_by, create_time, remark
)
select r.repair_id, '状态流转', '2', '3', 'admin',
       '报修状态由“维修中”变更为“已完成”', 'admin', r.finish_time, '演示数据'
from lab_repair r
where r.repair_code = 'BX202607020001'
  and not exists (
      select 1 from lab_repair_record rec
      where rec.repair_id = r.repair_id and rec.action_name = '状态流转'
  );

-- ----------------------------------------------------------------------------
-- 在途 2 张单子的履历：让报修详情时间线在「未完结」状态下也有内容（US-03）。
-- 文案与生产代码逐字对齐（LabRepairEvent + LabRepairServiceImpl:86~88）：
--   提交报修 → '故障等级：%s；%s'，实参是 **fault_level 的原码**（'1'/'2'/'3'）与故障描述
--   —— 生产代码直接透传 fault_level，没有转成"普通/紧急/严重"标签，这里保持一致，
--   不"顺手美化"，否则又会出现脚本与运行时两套文案。
-- ----------------------------------------------------------------------------
insert into lab_repair_record (
    repair_id, action_name, from_status, to_status, operator_name,
    record_content, create_by, create_time, remark
)
select r.repair_id, '提交报修', null, '0', u.nick_name,
       concat('故障等级：', r.fault_level, '；', r.fault_description),
       u.user_name, r.create_time, '演示数据'
from lab_repair r
join sys_user u on u.user_name = 'student1'
where r.repair_code in ('BX202607020005', 'BX202607020006')
  and not exists (
      select 1 from lab_repair_record rec
      where rec.repair_id = r.repair_id and rec.action_name = '提交报修'
  );

-- 维修中那张单子再补两条流转记录，时间线才是完整的「待审核 → 待维修 → 维修中」。
-- 幂等判据用 from_status + to_status（同一张单子不会重复走同一条边）。
insert into lab_repair_record (
    repair_id, action_name, from_status, to_status, operator_name,
    record_content, create_by, create_time, remark
)
select r.repair_id, '状态流转', seed.from_status, seed.to_status, 'repair01',
       seed.record_content, 'repair01', seed.create_time, '演示数据'
from lab_repair r
join (
    select '0' as from_status, '1' as to_status,
           '报修状态由“待审核”变更为“待维修”' as record_content,
           date_sub(sysdate(), interval 2 day) as create_time
    union all select '1', '2',
           '报修状态由“待维修”变更为“维修中”',
           date_sub(sysdate(), interval 2 day)
) seed
where r.repair_code = 'BX202607020006'
  and not exists (
      select 1 from lab_repair_record rec
      where rec.repair_id = r.repair_id
        and rec.action_name = '状态流转'
        and rec.from_status = seed.from_status
        and rec.to_status = seed.to_status
  );
