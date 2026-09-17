package com.ruoyi.project.laboratory.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabRepair;

/**
 * 报修状态机契约测试。
 *
 * <p>状态机是硬编码在 {@link LabRepairServiceImpl#validateStatusChange} 里的，
 * 前端 repair/index.vue 的 nextStatusOptions 与它保持同一张表。
 * 这里把「全部合法流转」「全部非法流转」两个方向都跑一遍，
 * 防止以后有人放宽条件时把非法流转放过去（尤其是 3 已完成 / 4 已拒绝 这两种终态）。
 */
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

    @Test
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

    private static LabRepair repairWithStatus(String status)
    {
        LabRepair repair = new LabRepair();
        repair.setStatus(status);
        return repair;
    }
}
