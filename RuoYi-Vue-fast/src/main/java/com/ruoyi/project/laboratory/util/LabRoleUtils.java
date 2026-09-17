package com.ruoyi.project.laboratory.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.project.system.domain.SysUser;

/**
 * 实验室模块角色判定工具。
 *
 * <p>本模块所有"能否查看全部数据""能否处理报修"的判断都必须走这个类。
 * 之前这三处白名单分别散落在 LabRepairServiceImpl、LabDashboardServiceImpl
 * 以及前端 repair/index.vue 里，口径不一致，导致维修工程师、实验室管理员
 * 在报修列表中被按申请人过滤、看不到待处理单据，却又能看到"处理"按钮。
 *
 * @author ruoyi
 */
public final class LabRoleUtils
{
    /** 可处理报修单（审核、状态流转、删除非待审核单）的角色。 */
    private static final Set<String> REPAIR_HANDLER_ROLES = unmodifiableSet(
            "admin", "teacher", "lab_manager", "repair_engineer");

    /** 可查看全部实验室业务数据（报修列表、运维看板）的角色，含只读观察角色。 */
    private static final Set<String> GLOBAL_VIEWER_ROLES = unmodifiableSet(
            "admin", "teacher", "lab_manager", "asset_keeper", "repair_engineer", "lab_viewer");

    private LabRoleUtils()
    {
    }

    /**
     * 当前登录用户能否处理报修单。
     */
    public static boolean canHandleRepair()
    {
        return canHandleRepair(currentUser());
    }

    public static boolean canHandleRepair(SysUser user)
    {
        return hasAnyRole(user, REPAIR_HANDLER_ROLES);
    }

    /**
     * 当前登录用户能否查看全部实验室业务数据。为 false 时只能看到自己提交的报修单。
     */
    public static boolean canViewAll()
    {
        return canViewAll(currentUser());
    }

    public static boolean canViewAll(SysUser user)
    {
        return hasAnyRole(user, GLOBAL_VIEWER_ROLES);
    }

    /**
     * 取当前登录用户，未登录或上下文缺失时返回 null（调用方按"非管理员"处理，权限收缩最安全）。
     */
    public static SysUser currentUser()
    {
        try
        {
            return SecurityUtils.getLoginUser().getUser();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    /**
     * 判断用户是否命中给定角色集合。内置 admin 用户与 admin 角色始终视为命中。
     */
    public static boolean hasAnyRole(SysUser user, Set<String> roleKeys)
    {
        if (user == null)
        {
            return false;
        }
        if (user.isAdmin())
        {
            return true;
        }
        if (user.getRoles() == null || user.getRoles().isEmpty())
        {
            return false;
        }
        return user.getRoles().stream()
                .anyMatch(role -> role.getRoleKey() != null && roleKeys.contains(role.getRoleKey()));
    }

    /** 处理报修的角色标识，供前端/文档对齐口径时参考。 */
    public static Set<String> repairHandlerRoles()
    {
        return REPAIR_HANDLER_ROLES;
    }

    /** 全局可见的角色标识，供前端/文档对齐口径时参考。 */
    public static Set<String> globalViewerRoles()
    {
        return GLOBAL_VIEWER_ROLES;
    }

    private static Set<String> unmodifiableSet(String... roleKeys)
    {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(roleKeys)));
    }
}
