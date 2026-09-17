-- ============================================================================
-- [4/6] 实验室演示账号
-- 用途：创建 6 个演示账号并绑定到 [3/6] 建好的实验室角色与岗位
-- 依赖：ry_20260417.sql、laboratory_menu_role.sql、laboratory_demo_seed.sql
-- 幂等：是（全部使用 where not exists 判定，可重复执行）
-- 目标库：education_system
-- 说明：密码统一为 123456，此处存的是 BCrypt 密文
-- ============================================================================
--
-- 演示账号一览：
--   labadmin / 123456    实验室管理员
--   asset01  / 123456    资产管理员
--   repair01 / 123456    维修工程师
--   room01   / 123456    房间管理员
--   student1 / 123456    学生助管
--   viewer01 / 123456    实验室观察员

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
