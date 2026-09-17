-- ============================================================================
-- [可选] 界面精简
-- 用途：隐藏若依默认的监控/工具/官网入口以及部门/岗位/公告菜单，把「实验室管理」
--       提到顶级菜单第一位，把「系统管理」改名为「基础配置」
-- 依赖：laboratory_menu_role.sql
-- 幂等：是（仅 UPDATE，可重复执行）
-- 目标库：education_system
-- 注意：本文件内容与 laboratory_menu_role.sql 末尾的 UPDATE 完全重复。单独保留
--       的意义是「在不重跑菜单结构脚本的前提下也能重新套用界面精简」。
-- ============================================================================

UPDATE sys_menu
SET visible = '1'
WHERE parent_id = 0
  AND path IN ('monitor', 'tool', 'http://ruoyi.vip');

UPDATE sys_menu
SET visible = '1'
WHERE perms IN (
    'system:dept:list',
    'system:post:list',
    'system:notice:list'
);

UPDATE sys_menu
SET order_num = '1'
WHERE parent_id = 0
  AND path = 'laboratory';

UPDATE sys_menu
SET order_num = '9'
WHERE parent_id = 0
  AND path = 'system';

UPDATE sys_menu
SET menu_name = '基础配置'
WHERE parent_id = 0
  AND path = 'system';
