package com.ruoyi.project.laboratory.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.project.laboratory.support.LabTestSupport;
import com.ruoyi.project.system.domain.SysUser;

/**
 * 权限口径契约测试（需求基线 BR-03）。
 *
 * <p>{@link LabRoleUtils} 是全系统唯一一处角色白名单。这一块此前完全没有测试，
 * 而它一旦写错不会抛异常、不会报错，只会让列表多出或少掉几行数据 ——
 * 也就是静默越权或静默丢数据，是三个零覆盖区里最危险的一个。
 *
 * <p>用例全部走公开入口：无参重载走线程上下文，带 {@link SysUser} 的重载走显式入参
 * （后者是 {@code LabRoleUtils} 特意留出的测试友好入口），不触碰私有的角色集合字段。
 *
 * @author ruoyi
 */
class LabRoleUtilsTest
{
    @BeforeEach
    void clearContextBeforeEach()
    {
        // 权限判定读的是线程上下文，必须先清干净，否则上一个用例的登录态会串进来。
        LabTestSupport.logout();
    }

    @AfterEach
    void clearContextAfterEach()
    {
        LabTestSupport.logout();
    }

    @Test
    @DisplayName("没有任何登录上下文时，处理报修与查看全量数据都判定为不可用")
    void anonymousUserIsDeniedBothJudgements()
    {
        assertFalse(LabRoleUtils.canHandleRepair(), "未登录不应具备报修处理权");
        assertFalse(LabRoleUtils.canViewAll(), "未登录不应看到全量数据");
        // 显式传 null 也必须落在「拒绝」一侧：权限收缩才是安全的默认值。
        assertFalse(LabRoleUtils.canHandleRepair(null), "用户为空时应拒绝报修处理权");
        assertFalse(LabRoleUtils.canViewAll(null), "用户为空时应拒绝全量可见");
    }

    @Test
    @DisplayName("内置超级管理员即使一个角色都没配，两项判定也直接放行")
    void builtInAdminBypassesRoleList()
    {
        SysUser admin = LabTestSupport.user(1L);
        assertTrue(admin.isAdmin(), "id 为 1 的用户应被识别为内置管理员");
        assertTrue(admin.getRoles().isEmpty(), "本用例刻意不给角色，以验证短路逻辑");
        assertTrue(LabRoleUtils.canHandleRepair(admin), "内置管理员应可处理报修");
        assertTrue(LabRoleUtils.canViewAll(admin), "内置管理员应可查看全量数据");
    }

    @Test
    @DisplayName("实验室管理员可处理报修，且凡有处理权的角色必然同时具备全量可见性")
    void labManagerCanHandleRepairAndHandlerRolesAreSubsetOfViewers()
    {
        SysUser manager = LabTestSupport.user(LabTestSupport.NORMAL_USER_ID, "lab_manager");
        assertTrue(LabRoleUtils.canHandleRepair(manager), "实验室管理员应可处理报修");
        assertTrue(LabRoleUtils.canViewAll(manager), "实验室管理员应可查看全量数据");

        // BR-03 的结构性约束：能改单子的人不可能看不到单子。
        // 这条不复制白名单，而是直接对工具类导出的两个角色集合做包含关系断言，
        // 将来有人只改一处、漏改另一处时会在这里红灯。
        for (String handlerRole : LabRoleUtils.repairHandlerRoles())
        {
            SysUser holder = LabTestSupport.user(LabTestSupport.NORMAL_USER_ID, handlerRole);
            assertTrue(LabRoleUtils.canViewAll(holder),
                    "角色 " + handlerRole + " 具备报修处理权，却看不到全量数据 —— 两个角色集合已失配");
        }
    }

    @Test
    @DisplayName("资产管理员只能看不能改：全量可见判定通过，报修处理判定被拒")
    void assetKeeperCanViewButCannotHandle()
    {
        SysUser keeper = LabTestSupport.user(LabTestSupport.OTHER_USER_ID, "asset_keeper");
        assertTrue(LabRoleUtils.canViewAll(keeper), "资产管理员应可查看全量数据");
        assertFalse(LabRoleUtils.canHandleRepair(keeper), "资产管理员不应具备报修处理权（只读角色）");
    }

    @Test
    @DisplayName("申请人一侧的角色两项判定都是拒绝，避免普通用户越权操作他人单据")
    void applicantSideRolesAreDeniedBoth()
    {
        SysUser student = LabTestSupport.user(LabTestSupport.NORMAL_USER_ID, "student_assistant");
        assertFalse(LabRoleUtils.canHandleRepair(student), "学生助管不应具备报修处理权");
        assertFalse(LabRoleUtils.canViewAll(student), "学生助管不应看到全量数据");

        SysUser keeper = LabTestSupport.user(LabTestSupport.NORMAL_USER_ID, "room_keeper");
        assertFalse(LabRoleUtils.canHandleRepair(keeper), "房间管理员不应具备报修处理权");
        assertFalse(LabRoleUtils.canViewAll(keeper), "房间管理员不应看到全量数据");
    }
}
