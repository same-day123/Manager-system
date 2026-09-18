package com.ruoyi.project.laboratory.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabRepair;
import com.ruoyi.project.laboratory.support.LabTestSupport;

/**
 * 报修模块集成测试（IT-01 ~ IT-04、IT-06）。
 *
 * <p>验证的是「Service → Mapper → 数据库」这条完整链路，以及一次写操作跨多张表时的
 * 事务落库结果——这些是 Mockito 单测永远验证不了的部分。
 *
 * <p>覆盖的业务规则（编号口径取自 `docs/大作业/01-需求基线.md` 第 6 节，不得自行改写）：
 * <ul>
 * <li><b>BR-01 报修状态机</b>：IT-01 初始状态必须是「待审核」；IT-02 合法链 0→1→2→3；
 * IT-03 非法跳转 0→3 必须被拒且数据库不变；IT-09 已完成(3) 不可回退；IT-10 已拒绝(4) 不可复活
 * ——后两条钉的是 BR-01 里「3 → 任何状态 / 4 → 任何状态 全部禁止」这一句</li>
 * <li><b>BR-02 资产状态联动</b>：提交报修 → 资产「维修中」；完成 / 拒绝 / 删除待审核单 → 资产回到「正常」</li>
 * <li><b>BR-03 权限规则</b>：IT-06 行级过滤由 {@code LabRoleUtils.canViewAll()} 单点决定</li>
 * <li><b>BR-04 事务边界</b>：IT-01 三表同写（报修单 + 资产 + 履历）；IT-03 失败时三处都不变</li>
 * <li><b>BR-06 逻辑删除</b>：IT-04 删除后物理行仍在、{@code del_flag='2'}</li>
 * </ul>
 *
 * <p>IT-05（BR-05 校验规则）与 IT-07（BR-04 / BR-05）在
 * {@link LabAssetIntegrationTest} 里，按任务卡 T2 第 2.3 节的拆分执行。
 *
 * <p>断言全部用 JdbcTemplate 直查库，不采信 Service 返回值。
 *
 * @author ruoyi
 */
@DisplayName("报修模块集成测试")
class LabRepairIntegrationTest extends AbstractLabIntegrationTest
{
    /** 报修状态字典：已拒绝。基类没有这个常量，在此补齐。 */
    private static final String REPAIR_REJECTED = "4";

    @Test
    @DisplayName("IT-01 提交报修全链路：报修单落库 + 资产联动维修中 + 履历写入")
    void it01_submitRepairWritesOrderAssetStatusAndHistory()
    {
        LabTestSupport.loginAs("student_assistant");

        LabRepair repair = new LabRepair();
        repair.setAssetId(assetId);
        repair.setFaultDescription("示波器开机后屏幕全黑");
        repair.setFaultLevel("2");

        labRepairService.insertLabRepair(repair);

        Long repairId = repair.getRepairId();
        assertNotNull(repairId, "useGeneratedKeys 应把自增主键回填到实体上");

        // ① lab_repair 落库 1 条、状态「待审核」、编号以 BX 开头
        assertEquals(1, countRows("select count(1) from lab_repair"));
        assertEquals(REPAIR_PENDING_REVIEW, repairStatus(repairId));
        String repairCode = queryString("select repair_code from lab_repair where repair_id = ?", repairId);
        assertTrue(repairCode.startsWith("BX"), "报修编号应以 BX 开头，实际=" + repairCode);

        // 申请人三要素取自登录上下文，不允许由请求参数注入
        assertEquals(Long.valueOf(100L), queryLong("select applicant_id from lab_repair where repair_id = ?", repairId));
        assertEquals("测试用户", queryString("select applicant_name from lab_repair where repair_id = ?", repairId));
        assertEquals("13800000000", queryString("select applicant_phone from lab_repair where repair_id = ?", repairId));

        // ② 资产被联动为「维修中」（BR-02）
        assertEquals(ASSET_REPAIRING, assetStatus(assetId));

        // ③ 报修履历写入 1 条「提交报修」
        assertEquals(1, countRows("select count(1) from lab_repair_record where repair_id = ?", repairId));
        assertEquals("提交报修", queryString("select action_name from lab_repair_record where repair_id = ?", repairId));
    }

    @Test
    @DisplayName("IT-02 状态机全流程 0→1→2→3：每步落库正确、履历累计 4 条、终态资产回归正常")
    void it02_statusMachineWalksFromPendingReviewToFinished()
    {
        Long repairId = submitRepairAs("student_assistant", assetId);
        assertEquals(REPAIR_PENDING_REVIEW, repairStatus(repairId));
        assertEquals(ASSET_REPAIRING, assetStatus(assetId));

        // 切换成具备报修处理权的角色（LabRoleUtils.canHandleRepair 命中 repair_engineer）
        LabTestSupport.loginAs("repair_engineer");

        transition(repairId, REPAIR_PENDING);
        assertEquals(REPAIR_PENDING, repairStatus(repairId), "审核通过后应为「待维修」");

        transition(repairId, REPAIR_REPAIRING);
        assertEquals(REPAIR_REPAIRING, repairStatus(repairId), "开始维修后应为「维修中」");

        transition(repairId, REPAIR_FINISHED);
        assertEquals(REPAIR_FINISHED, repairStatus(repairId), "维修完成后应为「已完成」");

        // 履历：提交 1 条 + 三次状态流转 = 4 条
        assertEquals(4, countRows("select count(1) from lab_repair_record where repair_id = ?", repairId));
        assertEquals(3, countRows("select count(1) from lab_repair_record where repair_id = ? and action_name = '状态流转'",
                repairId));

        // 终态后资产从「维修中」回到「正常」（BR-02）
        assertEquals(ASSET_NORMAL, assetStatus(assetId));
    }

    @Test
    @DisplayName("IT-03 非法流转被拦截：抛业务异常且状态、履历、资产三处均不变")
    void it03_illegalTransitionIsRejectedAndDatabaseStaysUntouched()
    {
        Long repairId = submitRepairAs("student_assistant", assetId);
        int recordsBefore = countRows("select count(1) from lab_repair_record where repair_id = ?", repairId);

        LabTestSupport.loginAs("repair_engineer");
        LabRepair jump = new LabRepair();
        jump.setRepairId(repairId);
        jump.setStatus(REPAIR_FINISHED); // 0 待审核 → 3 已完成，跨了两级，非法

        ServiceException ex = assertThrows(ServiceException.class, () -> labRepairService.updateLabRepair(jump));
        assertEquals("非法的报修状态流转", ex.getMessage());

        // 报修单状态没被改
        assertEquals(REPAIR_PENDING_REVIEW, repairStatus(repairId));
        // 履历条数没变（非法流转不该留下「状态流转」痕迹）
        assertEquals(recordsBefore, countRows("select count(1) from lab_repair_record where repair_id = ?", repairId));
        // 资产也没被误回滚成「正常」——单据还在待审核，资产应继续是维修中
        assertEquals(ASSET_REPAIRING, assetStatus(assetId));
    }

    @Test
    @DisplayName("IT-04 删除待审核单：逻辑删除行仍在，资产状态回滚为正常")
    void it04_deletingPendingRepairRollsAssetStatusBack()
    {
        Long repairId = submitRepairAs("student_assistant", assetId);
        assertEquals(ASSET_REPAIRING, assetStatus(assetId), "提交报修后资产应处于维修中");

        // 申请人本人删除自己那张待审核单
        LabTestSupport.loginAs("student_assistant");
        assertEquals(1, labRepairService.deleteLabRepairByRepairId(repairId));

        // 逻辑删除：物理行还在，del_flag 变成 '2'（BR-05）
        assertEquals(1, countRows("select count(1) from lab_repair where repair_id = ?", repairId));
        assertEquals("2", queryString("select del_flag from lab_repair where repair_id = ?", repairId));
        // 业务查询里已经查不到了
        assertEquals(0, countRows("select count(1) from lab_repair where repair_id = ? and del_flag = '0'", repairId));

        // 资产从「维修中」回滚为「正常」（BR-02）
        assertEquals(ASSET_NORMAL, assetStatus(assetId));
    }

    @Test
    @DisplayName("IT-06 行级权限过滤：同一份数据，普通申请人只见自己的、处理角色见全部")
    void it06_rowLevelFilteringFollowsViewAllCapability()
    {
        // 铺 3 张单：1 张属于 100L，2 张属于 200L。
        // 每张单必须独占一台「正常」资产——提交报修会把资产置成维修中，
        // 同一台资产第二次提交会被「该资产当前不可报修」拦下。
        Long assetFor100 = assetId;
        Long assetFor200a = insertAsset("ZC-3001", "备用示波器", ASSET_NORMAL, roomId);
        Long assetFor200b = insertAsset("ZC-3002", "备用万用表", ASSET_NORMAL, roomId);

        submitRepairAsUser(100L, assetFor100);
        submitRepairAsUser(200L, assetFor200a);
        submitRepairAsUser(200L, assetFor200b);
        assertEquals(3, countRows("select count(1) from lab_repair where del_flag = '0'"));

        // ① 普通申请人 100L：LabRoleUtils.canViewAll() 为 false，只能看到自己那条
        LabTestSupport.loginAsUser(100L, "student_assistant");
        List<LabRepair> ownOnly = labRepairService.selectLabRepairList(new LabRepair());
        assertEquals(1, ownOnly.size(), "普通申请人只应看到自己提交的 1 条");
        assertEquals(Long.valueOf(100L), ownOnly.get(0).getApplicantId());

        // ② 换一个普通申请人 200L：看到的是另外 2 条，证明过滤真的按申请人走，
        //    而不是「碰巧只查出 1 条」
        LabTestSupport.loginAsUser(200L, "student_assistant");
        assertEquals(2, labRepairService.selectLabRepairList(new LabRepair()).size());

        // ③ 处理角色：canViewAll() 命中 repair_engineer，看到全部 3 条
        LabTestSupport.loginAs("repair_engineer");
        assertEquals(3, labRepairService.selectLabRepairList(new LabRepair()).size());
    }

    @Test
    @DisplayName("IT-09 终态不可回退：已完成的单退回维修中被拒，状态、履历、资产三处都不变")
    void it09_finishedRepairCannotRollBack()
    {
        Long repairId = submitRepairAs("student_assistant", assetId);
        LabTestSupport.loginAs("repair_engineer");
        transition(repairId, REPAIR_PENDING);
        transition(repairId, REPAIR_REPAIRING);
        transition(repairId, REPAIR_FINISHED);
        assertEquals(ASSET_NORMAL, assetStatus(assetId), "完成后资产应已回到正常");

        int recordsBefore = countRows("select count(1) from lab_repair_record where repair_id = ?", repairId);

        // BR-01 明写「3 → 任何状态」全部禁止。这条是最容易被"顺手放开"的一条：
        // 维修工改错了想撤回、或前端传了旧状态，一旦放行就等于账实不符。
        LabRepair rollback = new LabRepair();
        rollback.setRepairId(repairId);
        rollback.setStatus(REPAIR_REPAIRING);

        ServiceException ex = assertThrows(ServiceException.class, () -> labRepairService.updateLabRepair(rollback));
        assertEquals("非法的报修状态流转", ex.getMessage());

        assertEquals(REPAIR_FINISHED, repairStatus(repairId), "已完成的单应仍停在已完成");
        assertEquals(recordsBefore, countRows("select count(1) from lab_repair_record where repair_id = ?", repairId),
                "被拒的流转不该留下履历");
        assertEquals(ASSET_NORMAL, assetStatus(assetId), "被拒之后资产状态不该被再度改动");
    }

    @Test
    @DisplayName("IT-10 终态不可复活：已拒绝的单改回待维修被拒，资产不会被重新占用")
    void it10_rejectedRepairCannotBeRevived()
    {
        Long repairId = submitRepairAs("student_assistant", assetId);
        LabTestSupport.loginAs("repair_engineer");

        // 0 → 4（拒绝）本身是 BR-01 的合法流转，顺手把 BR-02「拒绝后资产回到正常」也验了
        LabRepair reject = new LabRepair();
        reject.setRepairId(repairId);
        reject.setStatus(REPAIR_REJECTED);
        assertEquals(1, labRepairService.updateLabRepair(reject), "0 → 4 是合法流转");
        assertEquals(REPAIR_REJECTED, repairStatus(repairId));
        assertEquals(ASSET_NORMAL, assetStatus(assetId), "拒绝后资产应回到正常（BR-02）");

        int recordsBefore = countRows("select count(1) from lab_repair_record where repair_id = ?", repairId);

        LabRepair revive = new LabRepair();
        revive.setRepairId(repairId);
        revive.setStatus(REPAIR_PENDING);

        ServiceException ex = assertThrows(ServiceException.class, () -> labRepairService.updateLabRepair(revive));
        assertEquals("非法的报修状态流转", ex.getMessage());

        assertEquals(REPAIR_REJECTED, repairStatus(repairId), "已拒绝的单不能被复活");
        assertEquals(recordsBefore, countRows("select count(1) from lab_repair_record where repair_id = ?", repairId),
                "被拒的复活不该留下履历");
        // 复活若被放行，这条路径上资产会被再次置成维修中——正是要防的双重占用
        assertEquals(ASSET_NORMAL, assetStatus(assetId), "复活被拒后资产应保持正常");
    }

    /** 以处理角色的身份推进一次状态流转，并断言确实更新了 1 行。 */
    private void transition(Long repairId, String targetStatus)
    {
        LabRepair update = new LabRepair();
        update.setRepairId(repairId);
        update.setStatus(targetStatus);
        assertEquals(1, labRepairService.updateLabRepair(update), "状态流转应更新 1 行，目标状态=" + targetStatus);
    }
}
