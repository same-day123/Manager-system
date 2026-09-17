import { checkRole } from '@/utils/permission'

/**
 * 实验室模块角色口径（前端侧）。
 *
 * 必须与后端 com.ruoyi.project.laboratory.util.LabRoleUtils 保持一致：
 * 后端改角色白名单时，这里要同步改，否则会出现"按钮看得到但接口 403"
 * 或"有权限却看不到按钮"。
 */

/** 可处理报修单（审核、状态流转、删除非待审核单）的角色。 */
export const LAB_REPAIR_HANDLER_ROLES = ['admin', 'teacher', 'lab_manager', 'repair_engineer']

/** 可查看全部实验室业务数据的角色（只读观察角色也在内），与后端 canViewAll 对齐。 */
export const LAB_GLOBAL_VIEWER_ROLES = [
  'admin',
  'teacher',
  'lab_manager',
  'asset_keeper',
  'repair_engineer',
  'lab_viewer'
]

/** 当前登录用户能否处理报修单。 */
export function canHandleLabRepair() {
  return checkRole(LAB_REPAIR_HANDLER_ROLES)
}

/** 当前登录用户能否查看全部实验室业务数据。 */
export function canViewAllLabData() {
  return checkRole(LAB_GLOBAL_VIEWER_ROLES)
}
