-- ============================================================================
-- [2/6] 实验室菜单与角色初始化
-- 用途：创建「实验室管理」顶级菜单、房间/资产/报修三个业务菜单及其按钮权限，
--       创建 teacher 角色并授权，同时精简若依默认菜单（隐藏监控/工具/官网入口）
-- 依赖：ry_20260417.sql
-- 幂等：是（全部使用 where not exists 判定，可重复执行）
-- 目标库：education_system
-- 说明：文件末尾的「菜单精简」UPDATE 与 laboratory_cleanup.sql 内容重复
-- ============================================================================

INSERT INTO sys_role (
    role_name, role_key, role_sort, data_scope, menu_check_strictly, dept_check_strictly,
    status, del_flag, create_by, create_time, remark
)
SELECT '维修教师', 'teacher', 3, '1', 1, 1, '0', '0', 'admin', sysdate(), '实验室资产与报修管理教师'
WHERE NOT EXISTS (SELECT 1 FROM sys_role WHERE role_key = 'teacher');

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name, is_frame,
    is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT '实验室管理', 0, 5, 'laboratory', NULL, '', 'Laboratory', 1,
       0, 'M', '0', '0', '', 'education', 'admin', sysdate(), '实验室资产与报修管理'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE parent_id = 0 AND path = 'laboratory');

SET @lab_parent_id := (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'laboratory' LIMIT 1);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name, is_frame,
    is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT '实验室房间', @lab_parent_id, 1, 'room', 'laboratory/room/index', '', 'LaboratoryRoom', 1,
       0, 'C', '0', '0', 'laboratory:room:list', 'tree', 'admin', sysdate(), '实验室房间菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:room:list');

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name, is_frame,
    is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT '资产台账', @lab_parent_id, 2, 'asset', 'laboratory/asset/index', '', 'LaboratoryAsset', 1,
       0, 'C', '0', '0', 'laboratory:asset:list', 'table', 'admin', sysdate(), '资产台账菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:asset:list');

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name, is_frame,
    is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT '设备报修', @lab_parent_id, 3, 'repair', 'laboratory/repair/index', '', 'LaboratoryRepair', 1,
       0, 'C', '0', '0', 'laboratory:repair:list', 'bug', 'admin', sysdate(), '设备报修菜单'
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:list');

SET @room_menu_id := (SELECT menu_id FROM sys_menu WHERE perms = 'laboratory:room:list' LIMIT 1);
SET @asset_menu_id := (SELECT menu_id FROM sys_menu WHERE perms = 'laboratory:asset:list' LIMIT 1);
SET @repair_menu_id := (SELECT menu_id FROM sys_menu WHERE perms = 'laboratory:repair:list' LIMIT 1);

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '房间查询', @room_menu_id, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:room:query', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:room:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '房间新增', @room_menu_id, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:room:add', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:room:add');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '房间修改', @room_menu_id, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:room:edit', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:room:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '房间删除', @room_menu_id, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:room:remove', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:room:remove');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '房间导出', @room_menu_id, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:room:export', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:room:export');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '资产查询', @asset_menu_id, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:asset:query', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:asset:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '资产新增', @asset_menu_id, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:asset:add', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:asset:add');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '资产修改', @asset_menu_id, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:asset:edit', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:asset:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '资产删除', @asset_menu_id, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:asset:remove', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:asset:remove');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '资产导出', @asset_menu_id, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:asset:export', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:asset:export');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '资产导入', @asset_menu_id, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:asset:import', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:asset:import');

INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '报修查询', @repair_menu_id, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:query', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:query');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '报修新增', @repair_menu_id, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:add', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:add');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '报修修改', @repair_menu_id, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:edit', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:edit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '报修删除', @repair_menu_id, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:remove', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:remove');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '报修导出', @repair_menu_id, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:export', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:export');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '报修处理', @repair_menu_id, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:audit', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:audit');
INSERT INTO sys_menu (menu_name, parent_id, order_num, path, component, query, route_name, is_frame, is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark)
SELECT '维修评价', @repair_menu_id, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'laboratory:repair:evaluate', '#', 'admin', sysdate(), '' WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:repair:evaluate');

SET @teacher_role_id := (SELECT role_id FROM sys_role WHERE role_key = 'teacher' LIMIT 1);
SET @common_role_id := (SELECT role_id FROM sys_role WHERE role_key = 'common' LIMIT 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @teacher_role_id, m.menu_id
FROM sys_menu m
WHERE (m.perms LIKE 'laboratory:%' OR m.menu_id = @lab_parent_id)
  AND @teacher_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = @teacher_role_id AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @common_role_id, m.menu_id
FROM sys_menu m
WHERE m.perms IN (
    'laboratory:asset:query', 'laboratory:repair:list', 'laboratory:repair:query',
    'laboratory:repair:add', 'laboratory:repair:edit', 'laboratory:repair:remove',
    'laboratory:repair:evaluate'
)
  AND @common_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rm WHERE rm.role_id = @common_role_id AND rm.menu_id = m.menu_id);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @common_role_id, @lab_parent_id
WHERE @common_role_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu WHERE role_id = @common_role_id AND menu_id = @lab_parent_id);

UPDATE sys_menu
SET visible = '1'
WHERE parent_id = 0
  AND path IN ('monitor', 'tool', 'http://ruoyi.vip');

UPDATE sys_menu
SET visible = '1'
WHERE perms IN ('system:dept:list', 'system:post:list', 'system:notice:list');

UPDATE sys_menu SET order_num = '1' WHERE parent_id = 0 AND path = 'laboratory';
UPDATE sys_menu SET order_num = '9', menu_name = '基础配置' WHERE parent_id = 0 AND path = 'system';
