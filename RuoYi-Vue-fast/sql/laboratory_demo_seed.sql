-- ============================================================================
-- [3/6] 实验室演示角色与演示资产
-- 用途：创建 6 个实验室演示角色（lab_manager / asset_keeper / repair_engineer /
--       room_keeper / student_assistant / lab_viewer），按角色分配实验室菜单权限，
--       并插入演示用实验室房间、资产及资产履历数据
-- 依赖：laboratory_menu_role.sql
-- 幂等：是（全部使用 where not exists 判定，可重复执行）
-- 目标库：education_system
-- ============================================================================

insert into sys_role (
    role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
    status, del_flag, create_by, create_time, remark
)
select '实验室管理员', 'lab_manager', 4, '1', 1, 1, '0', '0', 'admin', sysdate(), '实验室资产、房间、报修全流程管理'
where not exists (select 1 from sys_role where role_key = 'lab_manager');

insert into sys_role (
    role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
    status, del_flag, create_by, create_time, remark
)
select '资产管理员', 'asset_keeper', 5, '1', 1, 1, '0', '0', 'admin', sysdate(), '负责资产台账、导入导出、二维码与生命周期管理'
where not exists (select 1 from sys_role where role_key = 'asset_keeper');

insert into sys_role (
    role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
    status, del_flag, create_by, create_time, remark
)
select '维修工程师', 'repair_engineer', 6, '1', 1, 1, '0', '0', 'admin', sysdate(), '负责报修审核、维修处理和完工登记'
where not exists (select 1 from sys_role where role_key = 'repair_engineer');

insert into sys_role (
    role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
    status, del_flag, create_by, create_time, remark
)
select '房间管理员', 'room_keeper', 7, '1', 1, 1, '0', '0', 'admin', sysdate(), '负责实验室房间基础信息维护'
where not exists (select 1 from sys_role where role_key = 'room_keeper');

insert into sys_role (
    role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
    status, del_flag, create_by, create_time, remark
)
select '学生助管', 'student_assistant', 8, '1', 1, 1, '0', '0', 'admin', sysdate(), '协助提交报修、查看本人报修并完成评价'
where not exists (select 1 from sys_role where role_key = 'student_assistant');

insert into sys_role (
    role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
    status, del_flag, create_by, create_time, remark
)
select '实验室观察员', 'lab_viewer', 9, '1', 1, 1, '0', '0', 'admin', sysdate(), '只读查看实验室资产、房间和报修数据'
where not exists (select 1 from sys_role where role_key = 'lab_viewer');

set @lab_menu_id := (select menu_id from sys_menu where parent_id = 0 and path = 'laboratory' limit 1);
set @lab_manager_role_id := (select role_id from sys_role where role_key = 'lab_manager' limit 1);
set @asset_keeper_role_id := (select role_id from sys_role where role_key = 'asset_keeper' limit 1);
set @repair_engineer_role_id := (select role_id from sys_role where role_key = 'repair_engineer' limit 1);
set @room_keeper_role_id := (select role_id from sys_role where role_key = 'room_keeper' limit 1);
set @student_assistant_role_id := (select role_id from sys_role where role_key = 'student_assistant' limit 1);
set @lab_viewer_role_id := (select role_id from sys_role where role_key = 'lab_viewer' limit 1);

insert into sys_role_menu(role_id, menu_id)
select @lab_manager_role_id, m.menu_id
from sys_menu m
where (m.menu_id = @lab_menu_id or m.perms like 'laboratory:%')
  and @lab_manager_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @lab_manager_role_id and rm.menu_id = m.menu_id);

insert into sys_role_menu(role_id, menu_id)
select @asset_keeper_role_id, m.menu_id
from sys_menu m
where (m.menu_id = @lab_menu_id or m.perms in (
    'laboratory:room:list', 'laboratory:room:query',
    'laboratory:asset:list', 'laboratory:asset:query', 'laboratory:asset:add',
    'laboratory:asset:edit', 'laboratory:asset:remove', 'laboratory:asset:export',
    'laboratory:asset:import'
))
  and @asset_keeper_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @asset_keeper_role_id and rm.menu_id = m.menu_id);

insert into sys_role_menu(role_id, menu_id)
select @repair_engineer_role_id, m.menu_id
from sys_menu m
where (m.menu_id = @lab_menu_id or m.perms in (
    'laboratory:room:list', 'laboratory:room:query',
    'laboratory:asset:list', 'laboratory:asset:query',
    'laboratory:repair:list', 'laboratory:repair:query', 'laboratory:repair:edit',
    'laboratory:repair:export', 'laboratory:repair:audit'
))
  and @repair_engineer_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @repair_engineer_role_id and rm.menu_id = m.menu_id);

insert into sys_role_menu(role_id, menu_id)
select @room_keeper_role_id, m.menu_id
from sys_menu m
where (m.menu_id = @lab_menu_id or m.perms in (
    'laboratory:room:list', 'laboratory:room:query', 'laboratory:room:add',
    'laboratory:room:edit', 'laboratory:room:export',
    'laboratory:asset:list', 'laboratory:asset:query'
))
  and @room_keeper_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @room_keeper_role_id and rm.menu_id = m.menu_id);

insert into sys_role_menu(role_id, menu_id)
select @student_assistant_role_id, m.menu_id
from sys_menu m
where (m.menu_id = @lab_menu_id or m.perms in (
    'laboratory:asset:query',
    'laboratory:repair:list', 'laboratory:repair:query', 'laboratory:repair:add',
    'laboratory:repair:edit', 'laboratory:repair:remove', 'laboratory:repair:evaluate'
))
  and @student_assistant_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @student_assistant_role_id and rm.menu_id = m.menu_id);

insert into sys_role_menu(role_id, menu_id)
select @lab_viewer_role_id, m.menu_id
from sys_menu m
where (m.menu_id = @lab_menu_id or m.perms in (
    'laboratory:room:list', 'laboratory:room:query', 'laboratory:room:export',
    'laboratory:asset:list', 'laboratory:asset:query', 'laboratory:asset:export',
    'laboratory:repair:list', 'laboratory:repair:query', 'laboratory:repair:export'
))
  and @lab_viewer_role_id is not null
  and not exists (select 1 from sys_role_menu rm where rm.role_id = @lab_viewer_role_id and rm.menu_id = m.menu_id);

insert into lab_asset (
    asset_code, asset_name, asset_type, model, price, purchase_date,
    room_id, status, del_flag, create_by, create_time, remark
)
select * from (
    select 'LAB-DEMO-2026-001' as asset_code, 'GPU深度学习工作站' as asset_name, '计算机设备' as asset_type,
           'RTX 4090 / 128GB / 4TB' as model, 48600.00 as price, '2026-03-18' as purchase_date,
           1001 as room_id, '0' as status, '0' as del_flag, 'admin' as create_by, sysdate() as create_time,
           '软件工程实验室人工智能课程使用' as remark
    union all select 'LAB-DEMO-2026-002', '机架式教学服务器', '计算机设备', '2U / 32核 / 256GB', 72800.00, '2026-03-20', 1001, '0', '0', 'admin', sysdate(), '课程虚拟机与容器实验'
    union all select 'LAB-DEMO-2026-003', '网络存储NAS', '计算机设备', '8盘位 / 80TB', 23800.00, '2026-03-22', 1001, '0', '0', 'admin', sysdate(), '实验数据集中存储'
    union all select 'LAB-DEMO-2026-004', '教师投屏一体机', '办公设备', '86英寸触控屏', 16800.00, '2026-03-24', 1001, '0', '0', 'admin', sysdate(), '课堂演示与投屏'
    union all select 'LAB-DEMO-2026-005', '学生实验终端02', '计算机设备', 'i7 / 32GB / 1TB', 7600.00, '2026-03-26', 1001, '0', '0', 'admin', sysdate(), '学生上机实验'
    union all select 'LAB-DEMO-2026-006', '嵌入式开发套件', '仪器设备', 'STM32 + 传感器模块', 3200.00, '2026-03-28', 1001, '1', '0', 'admin', sysdate(), '停用状态演示'

    union all select 'LAB-DEMO-2026-007', '核心路由器实验平台', '仪器设备', 'AR系列 / 多协议', 18600.00, '2026-04-01', 1002, '0', '0', 'admin', sysdate(), '网络路由协议实验'
    union all select 'LAB-DEMO-2026-008', '入侵检测设备', '仪器设备', 'IDS-2000', 25600.00, '2026-04-02', 1002, '0', '0', 'admin', sysdate(), '网络安全攻防演示'
    union all select 'LAB-DEMO-2026-009', '网络靶场服务器', '计算机设备', '双路CPU / 192GB', 63800.00, '2026-04-03', 1002, '2', '0', 'admin', sysdate(), '报修中资产演示'
    union all select 'LAB-DEMO-2026-010', '无线控制器', '仪器设备', 'AC控制器 / 64 AP', 9800.00, '2026-04-04', 1002, '0', '0', 'admin', sysdate(), '无线网络实验'
    union all select 'LAB-DEMO-2026-011', '光纤熔接工具箱', '耗材工具', '光纤熔接机套装', 12800.00, '2026-04-05', 1002, '0', '0', 'admin', sysdate(), '综合布线课程'
    union all select 'LAB-DEMO-2026-012', '日志审计平台', '计算机设备', '日志采集与审计系统', 35800.00, '2026-04-06', 1002, '0', '0', 'admin', sysdate(), '安全运维课程'

    union all select 'LAB-DEMO-2026-013', '迈克尔逊干涉仪', '仪器设备', 'MZ-III', 12600.00, '2026-04-08', 1003, '0', '0', 'admin', sysdate(), '大学物理光学实验'
    union all select 'LAB-DEMO-2026-014', '函数信号发生器', '仪器设备', '60MHz 双通道', 5600.00, '2026-04-09', 1003, '0', '0', 'admin', sysdate(), '电学实验'
    union all select 'LAB-DEMO-2026-015', '光电效应实验仪', '仪器设备', 'GD-IV', 8800.00, '2026-04-10', 1003, '0', '0', 'admin', sysdate(), '近代物理实验'
    union all select 'LAB-DEMO-2026-016', '万用表套件', '耗材工具', '数字万用表 20套', 4200.00, '2026-04-11', 1003, '0', '0', 'admin', sysdate(), '基础测量工具'
    union all select 'LAB-DEMO-2026-017', '力学气垫导轨', '仪器设备', '1500mm 数显计时', 9200.00, '2026-04-12', 1003, '1', '0', 'admin', sysdate(), '设备暂时停用演示'
    union all select 'LAB-DEMO-2026-018', '光学平台', '仪器设备', '1800x1200 隔振台', 21800.00, '2026-04-13', 1003, '0', '0', 'admin', sysdate(), '光学综合实验'

    union all select 'LAB-DEMO-2026-019', '高效液相色谱仪', '仪器设备', 'HPLC-1260', 168000.00, '2026-04-15', 1004, '0', '0', 'admin', sysdate(), '分析化学精密仪器'
    union all select 'LAB-DEMO-2026-020', '电子分析天平', '仪器设备', '0.1mg 精度', 14800.00, '2026-04-16', 1004, '0', '0', 'admin', sysdate(), '称量分析实验'
    union all select 'LAB-DEMO-2026-021', '智能通风橱', '仪器设备', '1500mm 变风量', 28600.00, '2026-04-17', 1004, '0', '0', 'admin', sysdate(), '安全防护设备'
    union all select 'LAB-DEMO-2026-022', '实验室pH计', '仪器设备', '台式精密pH计', 3600.00, '2026-04-18', 1004, '0', '0', 'admin', sysdate(), '溶液酸碱度测量'
    union all select 'LAB-DEMO-2026-023', '恒温水浴锅', '仪器设备', '双列六孔', 4600.00, '2026-04-19', 1004, '0', '0', 'admin', sysdate(), '样品恒温处理'
    union all select 'LAB-DEMO-2026-024', '高速离心机', '仪器设备', '16000rpm', 22800.00, '2026-04-20', 1004, '2', '0', 'admin', sysdate(), '报修中资产演示'
    union all select 'LAB-DEMO-2026-025', '气相色谱仪', '仪器设备', 'GC-7890', 132000.00, '2026-04-21', 1004, '0', '0', 'admin', sysdate(), '有机样品检测'
) seed
where not exists (select 1 from lab_asset a where a.asset_code = seed.asset_code);

insert into lab_asset_record (
    asset_id, record_type, from_value, to_value, operator_name, record_content, create_by, create_time, remark
)
select a.asset_id, '入库', null, a.asset_code, 'admin', concat('演示数据入库：', a.asset_name), 'admin', sysdate(), '演示种子数据'
from lab_asset a
where a.asset_code between 'LAB-DEMO-2026-001' and 'LAB-DEMO-2026-025'
  and not exists (
      select 1 from lab_asset_record r
      where r.asset_id = a.asset_id and r.record_type = '入库' and r.record_content like '演示数据入库：%'
  );
