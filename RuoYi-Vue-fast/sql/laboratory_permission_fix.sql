-- ============================================================================
-- [7/7] 权限口径修复
-- 用途：新增首页运维工作台的独立权限 laboratory:dashboard:view（原先借用
--       laboratory:repair:list，导致「看板」与「报修列表」共用一个权限点），
--       并把新权限授予所有当前持有 laboratory:repair:list 的角色，
--       再按「可查看全部实验室数据」的角色清单兜底补齐（见文件末尾）。
-- 依赖：laboratory_menu_role.sql、laboratory_demo_seed.sql
-- 幂等：是（where not exists 判定，可重复执行）
-- 目标库：education_system
-- 安全性：授权策略为「权限只增不减」，不会有任何角色因本脚本掉权限
-- ============================================================================

SET @lab_parent_id := (SELECT menu_id FROM sys_menu WHERE parent_id = 0 AND path = 'laboratory' LIMIT 1);

INSERT INTO sys_menu (
    menu_name, parent_id, order_num, path, component, query, route_name, is_frame,
    is_cache, menu_type, visible, status, perms, icon, create_by, create_time, remark
)
SELECT '运维工作台', @lab_parent_id, 8, '', '', '', '', 1,
       0, 'F', '0', '0', 'laboratory:dashboard:view', '#', 'admin', sysdate(), '首页运维工作台数据查看权限'
WHERE @lab_parent_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms = 'laboratory:dashboard:view');

SET @dashboard_menu_id := (SELECT menu_id FROM sys_menu WHERE perms = 'laboratory:dashboard:view' LIMIT 1);

-- 授予所有已持有「报修列表」权限的角色，保证权限只增不减。
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, @dashboard_menu_id
FROM sys_role_menu rm
JOIN sys_menu source_menu ON source_menu.menu_id = rm.menu_id
                         AND source_menu.perms = 'laboratory:repair:list'
WHERE @dashboard_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu exist_rm
      WHERE exist_rm.role_id = rm.role_id AND exist_rm.menu_id = @dashboard_menu_id
  );

-- 兜底授予 teacher 角色（该角色在 laboratory_menu_role.sql 中创建，避免执行顺序差异导致漏授）。
SET @teacher_role_id := (SELECT role_id FROM sys_role WHERE role_key = 'teacher' LIMIT 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT @teacher_role_id, @dashboard_menu_id
WHERE @teacher_role_id IS NOT NULL
  AND @dashboard_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu
      WHERE role_id = @teacher_role_id AND menu_id = @dashboard_menu_id
  );

-- ---------------------------------------------------------------------------
-- 兜底补齐：按「可查看全部实验室数据」的角色清单授权。
--
-- 为什么需要：上面的授权依据是「持有 laboratory:repair:list」，这会漏掉 asset_keeper ——
-- 它属于 LabRoleUtils.canViewAll()（资产管理员理应看到工作台），但没有报修列表权限，
-- 于是登录后首页两个看板接口直接 403，首页指标全 0、图表空白。
--
-- 角色清单必须与后端 LabRoleUtils.GLOBAL_VIEWER_ROLES 保持一致：
--   admin / teacher / lab_manager / asset_keeper / repair_engineer / lab_viewer
-- 新增全局可见角色时，这里和 labPermission.js 都要同步改。
-- ---------------------------------------------------------------------------
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.role_id, @dashboard_menu_id
FROM sys_role r
WHERE r.role_key IN ('admin', 'teacher', 'lab_manager', 'asset_keeper', 'repair_engineer', 'lab_viewer')
  AND r.del_flag = '0'
  AND @dashboard_menu_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_menu exist_rm
      WHERE exist_rm.role_id = r.role_id AND exist_rm.menu_id = @dashboard_menu_id
  );
