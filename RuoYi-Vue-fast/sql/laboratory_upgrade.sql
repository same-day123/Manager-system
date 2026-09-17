-- ============================================================================
-- [6/7] 实验室功能增强
-- 用途：为 lab_repair 表增补故障图片/评分/评价字段（幂等兜底，建表脚本已带上这些列），
--       创建 lab_repair_record（报修处理记录）与 lab_asset_record（资产生命周期记录）
--       两张表，初始化 4 组业务字典，并补发资产导入、维修评价两个按钮权限
-- 依赖：laboratory_schema.sql、laboratory_menu_role.sql、laboratory_demo_seed.sql
-- 幂等：是（用 information_schema 判列 + 存储过程实现幂等；MySQL 不支持
--       ADD COLUMN IF NOT EXISTS，所以这里的存储过程写法是必要的，请勿改成裸 ALTER）
-- 目标库：education_system
-- ============================================================================

drop procedure if exists upgrade_lab_repair_columns;
delimiter //
create procedure upgrade_lab_repair_columns()
begin
    if not exists (
        select 1 from information_schema.columns
        where table_schema = database() and table_name = 'lab_repair' and column_name = 'attachment_urls'
    ) then
        alter table lab_repair add column attachment_urls varchar(1000) default null comment '故障图片';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = database() and table_name = 'lab_repair' and column_name = 'rating'
    ) then
        alter table lab_repair add column rating int default null comment '维修评分';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = database() and table_name = 'lab_repair' and column_name = 'evaluation_content'
    ) then
        alter table lab_repair add column evaluation_content varchar(500) default null comment '维修评价';
    end if;

    if not exists (
        select 1 from information_schema.columns
        where table_schema = database() and table_name = 'lab_repair' and column_name = 'evaluation_time'
    ) then
        alter table lab_repair add column evaluation_time datetime default null comment '评价时间';
    end if;
end//
delimiter ;
call upgrade_lab_repair_columns();
drop procedure if exists upgrade_lab_repair_columns;

create table if not exists lab_repair_record (
    record_id bigint(20) not null auto_increment comment '记录ID',
    repair_id bigint(20) not null comment '报修ID',
    action_name varchar(50) not null comment '操作名称',
    from_status char(1) default null comment '原状态',
    to_status char(1) default null comment '新状态',
    operator_name varchar(64) default '' comment '操作人',
    record_content varchar(500) default '' comment '记录内容',
    create_by varchar(64) default '' comment '创建者',
    create_time datetime comment '创建时间',
    remark varchar(500) default null comment '备注',
    primary key (record_id),
    key idx_repair_record_repair_id (repair_id)
) engine=innodb auto_increment=1 comment='报修处理记录表';

create table if not exists lab_asset_record (
    record_id bigint(20) not null auto_increment comment '记录ID',
    asset_id bigint(20) not null comment '资产ID',
    record_type varchar(50) not null comment '记录类型',
    from_value varchar(200) default null comment '原值',
    to_value varchar(200) default null comment '新值',
    operator_name varchar(64) default '' comment '操作人',
    record_content varchar(500) default '' comment '记录内容',
    create_by varchar(64) default '' comment '创建者',
    create_time datetime comment '创建时间',
    remark varchar(500) default null comment '备注',
    primary key (record_id),
    key idx_asset_record_asset_id (asset_id)
) engine=innodb auto_increment=1 comment='资产生命周期记录表';

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select '实验室资产类型', 'lab_asset_type', '0', 'admin', sysdate(), '实验室资产类型'
where not exists (select 1 from sys_dict_type where dict_type = 'lab_asset_type');

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select '实验室资产状态', 'lab_asset_status', '0', 'admin', sysdate(), '实验室资产状态'
where not exists (select 1 from sys_dict_type where dict_type = 'lab_asset_status');

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select '故障等级', 'lab_fault_level', '0', 'admin', sysdate(), '报修故障等级'
where not exists (select 1 from sys_dict_type where dict_type = 'lab_fault_level');

insert into sys_dict_type(dict_name, dict_type, status, create_by, create_time, remark)
select '报修处理状态', 'lab_repair_status', '0', 'admin', sysdate(), '报修处理状态'
where not exists (select 1 from sys_dict_type where dict_type = 'lab_repair_status');

insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '仪器设备', '仪器设备', 'lab_asset_type', '', 'primary', 'Y', '0', 'admin', sysdate(), '仪器设备'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_type' and dict_value = '仪器设备');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '计算机设备', '计算机设备', 'lab_asset_type', '', 'success', 'N', '0', 'admin', sysdate(), '计算机设备'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_type' and dict_value = '计算机设备');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '办公设备', '办公设备', 'lab_asset_type', '', 'info', 'N', '0', 'admin', sysdate(), '办公设备'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_type' and dict_value = '办公设备');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 4, '耗材工具', '耗材工具', 'lab_asset_type', '', 'warning', 'N', '0', 'admin', sysdate(), '耗材工具'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_type' and dict_value = '耗材工具');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 9, '其他', '其他', 'lab_asset_type', '', 'default', 'N', '0', 'admin', sysdate(), '其他'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_type' and dict_value = '其他');

insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '正常', '0', 'lab_asset_status', '', 'success', 'Y', '0', 'admin', sysdate(), '正常'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_status' and dict_value = '0');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '停用', '1', 'lab_asset_status', '', 'info', 'N', '0', 'admin', sysdate(), '停用'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_status' and dict_value = '1');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '维修中', '2', 'lab_asset_status', '', 'warning', 'N', '0', 'admin', sysdate(), '维修中'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_asset_status' and dict_value = '2');

insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '普通', '1', 'lab_fault_level', '', 'info', 'Y', '0', 'admin', sysdate(), '普通'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_fault_level' and dict_value = '1');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '紧急', '2', 'lab_fault_level', '', 'warning', 'N', '0', 'admin', sysdate(), '紧急'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_fault_level' and dict_value = '2');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '严重', '3', 'lab_fault_level', '', 'danger', 'N', '0', 'admin', sysdate(), '严重'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_fault_level' and dict_value = '3');

insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 1, '待审核', '0', 'lab_repair_status', '', 'info', 'Y', '0', 'admin', sysdate(), '待审核'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_repair_status' and dict_value = '0');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 2, '待维修', '1', 'lab_repair_status', '', 'warning', 'N', '0', 'admin', sysdate(), '待维修'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_repair_status' and dict_value = '1');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 3, '维修中', '2', 'lab_repair_status', '', 'primary', 'N', '0', 'admin', sysdate(), '维修中'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_repair_status' and dict_value = '2');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 4, '已完成', '3', 'lab_repair_status', '', 'success', 'N', '0', 'admin', sysdate(), '已完成'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_repair_status' and dict_value = '3');
insert into sys_dict_data(dict_sort, dict_label, dict_value, dict_type, css_class, list_class, is_default, status, create_by, create_time, remark)
select 5, '已拒绝', '4', 'lab_repair_status', '', 'danger', 'N', '0', 'admin', sysdate(), '已拒绝'
where not exists (select 1 from sys_dict_data where dict_type = 'lab_repair_status' and dict_value = '4');

set @asset_menu_id := (select menu_id from sys_menu where perms = 'laboratory:asset:list' limit 1);
set @repair_menu_id := (select menu_id from sys_menu where perms = 'laboratory:repair:list' limit 1);

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '资产导入', @asset_menu_id, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:asset:import', '#', 'admin', sysdate(), ''
where @asset_menu_id is not null and not exists (select 1 from sys_menu where perms = 'laboratory:asset:import');

insert into sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
select '维修评价', @repair_menu_id, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:evaluate', '#', 'admin', sysdate(), ''
where @repair_menu_id is not null and not exists (select 1 from sys_menu where perms = 'laboratory:repair:evaluate');

set @teacher_role_id := (select role_id from sys_role where role_key = 'teacher' limit 1);
set @common_role_id := (select role_id from sys_role where role_key = 'common' limit 1);

insert into sys_role_menu(role_id, menu_id)
select @teacher_role_id, m.menu_id
from sys_menu m
where m.perms in ('laboratory:asset:import', 'laboratory:repair:evaluate')
  and @teacher_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @teacher_role_id and rm.menu_id = m.menu_id);

insert into sys_role_menu(role_id, menu_id)
select @common_role_id, m.menu_id
from sys_menu m
where m.perms = 'laboratory:repair:evaluate'
  and @common_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @common_role_id and rm.menu_id = m.menu_id);
