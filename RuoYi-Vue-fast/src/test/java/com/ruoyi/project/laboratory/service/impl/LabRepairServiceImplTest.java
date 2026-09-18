package com.ruoyi.project.laboratory.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.domain.LabRepair;
import com.ruoyi.project.laboratory.domain.LabRepairRecord;
import com.ruoyi.project.laboratory.mapper.LabRepairMapper;
import com.ruoyi.project.laboratory.mapper.LabRepairRecordMapper;
import com.ruoyi.project.laboratory.service.ILabAssetService;
import com.ruoyi.project.laboratory.support.LabTestSupport;

/**
 * 报修流程契约测试。
 *
 * <p>分两块：
 * <ul>
 * <li><b>状态机契约（原有 5 个，保持原样）</b>——状态机硬编码在
 * {@link LabRepairServiceImpl#validateStatusChange} 这个私有方法里，前端 repair/index.vue
 * 的 nextStatusOptions 与它保持同一张表。这里把「全部合法流转」「全部非法流转」
 * 两个方向都跑一遍，防止以后有人放宽条件时把非法流转放过去
 * （尤其是 3 已完成 / 4 已拒绝 这两种终态）。这 5 个是本模块现有的回归防线，不删不改。</li>
 * <li><b>提交校验与评价规则（新增 7 个）</b>——这两块此前完全没有测试。
 * 全部从公开入口（{@code insertLabRepair} / {@code evaluateLabRepair}）进入，
 * 断言异常消息与落库前的对象状态，不对私有方法做反射调用。</li>
 * </ul>
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class LabRepairServiceImplTest
{
    /** 0 待审核 → 1 待维修 / 4 已拒绝；1 待维修 → 2 维修中；2 维修中 → 3 已完成。 */
    private static final String[][] LEGAL_TRANSITIONS = {
            { "0", "1" },
            { "0", "4" },
            { "1", "2" },
            { "2", "3" }
    };

    private static final String[] ALL_STATUS = { "0", "1", "2", "3", "4" };

    @Mock
    private LabRepairMapper labRepairMapper;

    @Mock
    private LabRepairRecordMapper labRepairRecordMapper;

    @Mock
    private ILabAssetService labAssetService;

    @InjectMocks
    private LabRepairServiceImpl labRepairService;

    @BeforeEach
    void loginBeforeEach()
    {
        // 提交与评价都要取当前登录用户，没有上下文会抛「获取用户信息异常」——
        // 那会让缺陷被掩盖成一次通过，属于假绿灯。
        LabTestSupport.loginAs("student_assistant");
    }

    @AfterEach
    void logoutAfterEach()
    {
        LabTestSupport.logout();
    }

    // ---------------------------------------------------------------- 状态机契约（原有，断言未改动）

    @Test
    @DisplayName("跨环节跳转被拒绝：待审核的单不能直接改到已完成")
    void validateStatusChangeRejectsIllegalTransition()
    {
        LabRepairServiceImpl service = new LabRepairServiceImpl();
        LabRepair oldRepair = new LabRepair();
        oldRepair.setStatus("0");
        LabRepair nextRepair = new LabRepair();
        nextRepair.setStatus("3");

        assertThrows(ServiceException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateStatusChange", oldRepair, nextRepair));
    }

    @Test
    @DisplayName("相邻环节的合法流转被接受：待审核可以推进到待维修")
    void validateStatusChangeAllowsPendingReviewToPendingRepair()
    {
        LabRepairServiceImpl service = new LabRepairServiceImpl();
        LabRepair oldRepair = new LabRepair();
        oldRepair.setStatus("0");
        oldRepair.setAssetId(10L);
        LabRepair nextRepair = new LabRepair();
        nextRepair.setStatus("1");

        assertDoesNotThrow(
                () -> ReflectionTestUtils.invokeMethod(service, "validateStatusChange", oldRepair, nextRepair));
    }

    @Test
    @DisplayName("四条合法流转逐一枚举，全部不应抛异常")
    void allLegalTransitionsAreAccepted()
    {
        LabRepairServiceImpl service = new LabRepairServiceImpl();
        for (String[] pair : LEGAL_TRANSITIONS)
        {
            try
            {
                ReflectionTestUtils.invokeMethod(service, "validateStatusChange",
                        repairWithStatus(pair[0]), repairWithStatus(pair[1]));
            }
            catch (Exception e)
            {
                fail("合法流转 " + pair[0] + " -> " + pair[1] + " 被拒绝：" + e.getMessage());
            }
        }
    }

    @Test
    @DisplayName("十六组非法流转逐一枚举，必须全部被拒绝")
    void allIllegalTransitionsAreRejected()
    {
        LabRepairServiceImpl service = new LabRepairServiceImpl();
        Set<String> legal = new HashSet<String>();
        for (String[] pair : LEGAL_TRANSITIONS)
        {
            legal.add(pair[0] + "->" + pair[1]);
        }

        int checked = 0;
        for (String from : ALL_STATUS)
        {
            for (String to : ALL_STATUS)
            {
                if (from.equals(to) || legal.contains(from + "->" + to))
                {
                    continue;
                }
                checked++;
                assertThrows(ServiceException.class,
                        () -> ReflectionTestUtils.invokeMethod(service, "validateStatusChange",
                                repairWithStatus(from), repairWithStatus(to)),
                        "非法流转 " + from + " -> " + to + " 竟然被放行");
            }
        }
        // 5 个状态的有序两两组合共 25 组，扣掉 5 组"状态未变"与 4 组合法流转，剩 16 组必须全部被拒。
        if (checked != 16)
        {
            fail("非法流转用例数应为 16，实际 " + checked);
        }
    }

    @Test
    @DisplayName("状态不变时不校验流转，直接放行以支持只改描述不改状态")
    void keepingSameStatusIsNoOp()
    {
        LabRepairServiceImpl service = new LabRepairServiceImpl();
        for (String status : ALL_STATUS)
        {
            // 状态不变时 validateStatusChange 直接返回，用于"只改描述不改状态"的保存场景。
            assertDoesNotThrow(
                    () -> ReflectionTestUtils.invokeMethod(service, "validateStatusChange",
                            repairWithStatus(status), repairWithStatus(status)));
        }
    }

    // ---------------------------------------------------------------- 提交校验（新增 UT-R01 ~ UT-R05）

    @Test
    @DisplayName("提交报修未选资产时被拦下，且不产生任何写库动作")
    void insertRepairRejectsWhenAssetNotSelected()
    {
        LabRepair request = new LabRepair();
        request.setFaultDescription("投影仪开机后无信号");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRepairService.insertLabRepair(request));
        assertEquals("请选择报修资产", ex.getMessage());
        verifyNoInteractions(labRepairMapper);
    }

    @Test
    @DisplayName("提交报修时故障描述为空字符串，同样被拦下")
    void insertRepairRejectsBlankFaultDescription()
    {
        LabRepair request = new LabRepair();
        request.setAssetId(1L);
        request.setFaultDescription("");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRepairService.insertLabRepair(request));
        assertEquals("故障描述不能为空", ex.getMessage());
        verify(labRepairMapper, never()).insertLabRepair(any(LabRepair.class));
    }

    @Test
    @DisplayName("提交报修指向的资产查不到时被拦下，提示资产不存在或已删除")
    void insertRepairRejectsMissingAsset()
    {
        when(labAssetService.selectLabAssetByAssetId(1L)).thenReturn(null);

        LabRepair request = new LabRepair();
        request.setAssetId(1L);
        request.setFaultDescription("键盘部分按键失灵");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRepairService.insertLabRepair(request));
        assertEquals("资产不存在或已删除", ex.getMessage());
        verify(labRepairMapper, never()).insertLabRepair(any(LabRepair.class));
    }

    @Test
    @DisplayName("资产已处于维修中时不允许重复报修，避免同一台设备挂多张单")
    void insertRepairRejectsAssetAlreadyUnderRepair()
    {
        LabAsset repairing = new LabAsset();
        repairing.setAssetId(1L);
        repairing.setStatus("2");
        when(labAssetService.selectLabAssetByAssetId(1L)).thenReturn(repairing);

        LabRepair request = new LabRepair();
        request.setAssetId(1L);
        request.setFaultDescription("机箱风扇异响");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRepairService.insertLabRepair(request));
        assertEquals("该资产当前不可报修", ex.getMessage());
        verify(labRepairMapper, never()).insertLabRepair(any(LabRepair.class));
    }

    @Test
    @DisplayName("正常提交报修：单据落为待审核、申请人取自登录态、资产被联动为维修中、并写下一条提交履历")
    void insertRepairWritesOrderAssetStatusAndHistory()
    {
        LabAsset normal = new LabAsset();
        normal.setAssetId(1L);
        normal.setAssetCode("AST-0001");
        normal.setStatus("0");
        when(labAssetService.selectLabAssetByAssetId(1L)).thenReturn(normal);
        when(labRepairMapper.insertLabRepair(any(LabRepair.class))).thenReturn(1);

        LabRepair request = new LabRepair();
        request.setAssetId(1L);
        request.setFaultLevel("2");
        request.setFaultDescription("显示器右下角出现竖条纹");

        int rows = labRepairService.insertLabRepair(request);

        assertEquals(1, rows);
        // 落库字段
        assertEquals("0", request.getStatus(), "新提交的报修单应停在待审核");
        assertEquals("0", request.getDelFlag(), "新单不应带删除标记");
        assertEquals(LabTestSupport.NORMAL_USER_ID.longValue(), request.getApplicantId().longValue(),
                "申请人应取自登录上下文");
        assertEquals(LabTestSupport.NORMAL_NICK_NAME, request.getApplicantName());
        assertEquals(LabTestSupport.NORMAL_PHONE, request.getApplicantPhone());
        assertTrue(request.getRepairCode().startsWith("BX"), "报修单号应以 BX 开头");

        // BR-02 资产状态联动
        ArgumentCaptor<LabAsset> assetCaptor = ArgumentCaptor.forClass(LabAsset.class);
        verify(labAssetService).updateLabAsset(assetCaptor.capture());
        assertEquals(1L, assetCaptor.getValue().getAssetId().longValue());
        assertEquals("2", assetCaptor.getValue().getStatus(), "提交报修后资产应联动为维修中");

        // BR-04 事务内三件事之一：履历
        ArgumentCaptor<LabRepairRecord> recordCaptor = ArgumentCaptor.forClass(LabRepairRecord.class);
        verify(labRepairRecordMapper).insertLabRepairRecord(recordCaptor.capture());
        assertEquals("提交报修", recordCaptor.getValue().getActionName());
        assertEquals("0", recordCaptor.getValue().getToStatus());
        assertEquals(LabTestSupport.NORMAL_USER_NAME, recordCaptor.getValue().getOperatorName());
    }

    // ---------------------------------------------------------------- 评价规则（新增 UT-R08 ~ UT-R09）

    @Test
    @DisplayName("评分只接受 1 到 5：0 分与 6 分被拒，1 分与 5 分这两个边界值放行")
    void evaluateRepairEnforcesRatingRangeIncludingBoundaries()
    {
        when(labRepairMapper.selectLabRepairByRepairId(7L)).thenReturn(finishedRepairOwnedBy(7L));
        when(labRepairMapper.updateLabRepair(any(LabRepair.class))).thenReturn(1);

        for (Integer outOfRange : new Integer[] { 0, 6 })
        {
            LabRepair request = new LabRepair();
            request.setRepairId(7L);
            request.setRating(outOfRange);
            ServiceException ex = assertThrows(ServiceException.class,
                    () -> labRepairService.evaluateLabRepair(request),
                    "评分 " + outOfRange + " 超出了 1~5 的范围，应被拒绝");
            assertEquals("维修评分必须在1到5之间", ex.getMessage());
        }

        for (Integer boundary : new Integer[] { 1, 5 })
        {
            LabRepair request = new LabRepair();
            request.setRepairId(7L);
            request.setRating(boundary);
            request.setEvaluationContent("边界值放行校验");
            assertDoesNotThrow(() -> labRepairService.evaluateLabRepair(request),
                    "评分 " + boundary + " 是合法边界，不应被拒绝");
        }

        verify(labRepairMapper, times(2)).updateLabRepair(any(LabRepair.class));
    }

    @Test
    @DisplayName("维修中的单不能评价，只有已完成状态才开放评价入口")
    void evaluateRepairRejectsOrderNotFinishedYet()
    {
        LabRepair repairing = new LabRepair();
        repairing.setRepairId(8L);
        repairing.setApplicantId(LabTestSupport.NORMAL_USER_ID);
        repairing.setAssetId(1L);
        repairing.setStatus("2");
        when(labRepairMapper.selectLabRepairByRepairId(8L)).thenReturn(repairing);

        LabRepair request = new LabRepair();
        request.setRepairId(8L);
        request.setRating(5);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRepairService.evaluateLabRepair(request));
        assertEquals("只能评价已完成的报修单", ex.getMessage());
        verify(labRepairMapper, never()).updateLabRepair(any(LabRepair.class));
    }

    // ---------------------------------------------------------------- 辅助

    /** 构造一张「已完成」且申请人就是当前登录用户的报修单。 */
    private static LabRepair finishedRepairOwnedBy(Long repairId)
    {
        LabRepair repair = new LabRepair();
        repair.setRepairId(repairId);
        repair.setApplicantId(LabTestSupport.NORMAL_USER_ID);
        repair.setAssetId(1L);
        repair.setStatus("3");
        return repair;
    }

    private static LabRepair repairWithStatus(String status)
    {
        LabRepair repair = new LabRepair();
        repair.setStatus(status);
        return repair;
    }
}
