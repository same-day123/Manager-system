package com.ruoyi.project.laboratory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabRepair;
import com.ruoyi.project.laboratory.domain.LabRepairRecord;
import com.ruoyi.project.laboratory.mapper.LabRepairMapper;
import com.ruoyi.project.laboratory.mapper.LabRepairRecordMapper;
import com.ruoyi.project.laboratory.service.ILabAssetService;
import com.ruoyi.project.laboratory.support.LabTestSupport;

/**
 * 报修状态机契约测试 —— <b>走 {@code updateLabRepair} 公开入口</b>。
 *
 * <p><b>这个类是为了收口偏移 D-07 的遗留项。</b>D-07 修完后留下的原话是：
 * 「原 5 个状态机用例仍用 `ReflectionTestUtils` 打私有方法」。
 * 反射调用有两个真实代价：
 * <ol>
 * <li><b>钉不住调用路径</b>——反射能直接命中私有方法，所以"这个方法到底有没有被
 * `updateLabRepair` 调到、在哪个分支被调到"完全没被验证。真要有人把
 * {@code validateStatusChange(oldRepair, labRepair)} 那一行删掉，反射用例照样全绿。</li>
 * <li><b>扛不住重构</b>——私有方法改名、签名变化，编译器不会提醒测试，只能在运行时炸。</li>
 * </ol>
 *
 * <p>所以这里从 <b>公开入口</b> 打进去：装一个"具备报修处理权"的登录态
 * （{@code repair_engineer} 命中 {@code LabRoleUtils.canHandleRepair}），
 * 让调用落到 `updateLabRepair` 的 manager 分支，由它去触发状态机校验。
 * 这样断言的对象变成了**真实的调用链 + 真的被写库的字段**，而不只是一个私有方法的返回值。
 *
 * <p><b>与 {@link LabRepairServiceImplTest} 的关系</b>：那 5 个反射用例**保持原样保留**
 * （T1 卡明确要求"不删除或弱化已有断言"，它们是历史回归防线）。
 * 本类只做"新增一组更强口径的覆盖"，不替换、不改写旧用例。
 * 两组的合法/非法矩阵结果应当一致——若不一致，说明公开入口那条路径上还有额外分支，
 * 这本身就是有价值的信号。
 *
 * <p><b>口径</b>：状态字面量按 `docs/大作业/01-需求基线.md` 第 6 节 BR-01 硬编码，
 * <b>刻意不引用 `LabConstants`</b>——契约测试要钉的是"对外承诺的这张表"，
 * 如果跟着生产常量走，生产改了常量测试也跟着改，就白测了。
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class LabRepairStatusMachinePublicEntryTest
{
    /** 被操作的报修单主键。 */
    private static final Long REPAIR_ID = 9001L;

    /** 报修单关联的资产主键——推进状态时必须沿用旧值，不能被请求参数带跑。 */
    private static final Long ASSET_ID = 10L;

    /** 具备报修处理权的角色（BR-03：admin / teacher / lab_manager / repair_engineer 之一）。 */
    private static final String REPAIR_HANDLER_ROLE = "repair_engineer";

    /** BR-01 允许的四条流转。 */
    private static final String[][] LEGAL_TRANSITIONS = {
            { "0", "1" },
            { "0", "4" },
            { "1", "2" },
            { "2", "3" }
    };

    /** 全部五个状态。 */
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
    void loginAsRepairHandler()
    {
        // 没有登录上下文会抛「获取用户信息异常」，而且 manager 分支根本进不去
        // ——那样用例会以一种很难懂的方式失败，缺陷反而被掩盖。
        LabTestSupport.loginAs(REPAIR_HANDLER_ROLE);
    }

    @AfterEach
    void logout()
    {
        LabTestSupport.logout();
    }

    @Test
    @DisplayName("四条合法流转经公开入口逐一放行，且目标状态与旧资产一并被交给持久层")
    void legalTransitionsPassThroughPublicEntry()
    {
        for (String[] pair : LEGAL_TRANSITIONS)
        {
            when(labRepairMapper.selectLabRepairByRepairId(REPAIR_ID)).thenReturn(repairInStatus(pair[0]));
            when(labRepairMapper.updateLabRepair(any(LabRepair.class))).thenReturn(1);

            LabRepair request = new LabRepair();
            request.setRepairId(REPAIR_ID);
            request.setStatus(pair[1]);

            assertEquals(1, labRepairService.updateLabRepair(request),
                    "合法流转 " + pair[0] + " -> " + pair[1] + " 应当被接受并更新 1 行");
        }

        // 收集全部写入，验证"合法 = 真的往下写了"，而不只是"没抛异常"。
        // 只用 assertDoesNotThrow 的用例有个空洞：方法体被改成空实现也能过。
        ArgumentCaptor<LabRepair> captor = ArgumentCaptor.forClass(LabRepair.class);
        verify(labRepairMapper, times(LEGAL_TRANSITIONS.length)).updateLabRepair(captor.capture());
        List<LabRepair> written = captor.getAllValues();
        assertEquals(LEGAL_TRANSITIONS.length, written.size());
        for (int i = 0; i < LEGAL_TRANSITIONS.length; i++)
        {
            assertEquals(LEGAL_TRANSITIONS[i][1], written.get(i).getStatus(),
                    "持久层收到的目标状态应与请求一致");
            assertEquals(ASSET_ID, written.get(i).getAssetId(),
                    "推进状态时必须沿用单据原有的资产，不能被请求参数带上别的资产（D-11 的口子）");
        }
    }

    @Test
    @DisplayName("十六组非法流转经公开入口全部被拒，整轮下来一次写库动作都没发生")
    void illegalTransitionsAreRejectedAndNothingIsWritten()
    {
        Set<String> legal = new HashSet<String>();
        for (String[] pair : LEGAL_TRANSITIONS)
        {
            legal.add(pair[0] + "->" + pair[1]);
        }

        List<String> rejected = new ArrayList<String>();
        for (String from : ALL_STATUS)
        {
            for (String to : ALL_STATUS)
            {
                if (from.equals(to) || legal.contains(from + "->" + to))
                {
                    continue;
                }
                when(labRepairMapper.selectLabRepairByRepairId(REPAIR_ID)).thenReturn(repairInStatus(from));

                LabRepair request = new LabRepair();
                request.setRepairId(REPAIR_ID);
                request.setStatus(to);

                ServiceException ex = assertThrows(ServiceException.class,
                        () -> labRepairService.updateLabRepair(request),
                        "非法流转 " + from + " -> " + to + " 从公开入口竟然被放行了");
                assertEquals("非法的报修状态流转", ex.getMessage(),
                        "非法流转的异常消息是对外契约的一部分，前端会直接展示");
                rejected.add(from + "->" + to);
            }
        }

        // 25 组有序两两组合 - 5 组同状态 - 4 组合法 = 16 组非法
        assertEquals(16, rejected.size(), "非法流转用例数应为 16，实际 " + rejected.size());

        // 收尾统一断言"没有副作用"：非法流转不该留下任何痕迹
        verify(labRepairMapper, never()).updateLabRepair(any(LabRepair.class));
        verifyNoInteractions(labRepairRecordMapper);
        verifyNoInteractions(labAssetService);
    }

    @Test
    @DisplayName("状态不变的保存放行为普通更新，履历记的是「维修信息更新」而非「状态流转」")
    void unchangedStatusIsTreatedAsPlainUpdate()
    {
        for (String status : ALL_STATUS)
        {
            when(labRepairMapper.selectLabRepairByRepairId(REPAIR_ID)).thenReturn(repairInStatus(status));
            when(labRepairMapper.updateLabRepair(any(LabRepair.class))).thenReturn(1);

            LabRepair request = new LabRepair();
            request.setRepairId(REPAIR_ID);
            request.setStatus(status);

            assertEquals(1, labRepairService.updateLabRepair(request),
                    "状态与库中一致时应放行为普通更新（用于只改描述不改状态）");
        }

        ArgumentCaptor<LabRepairRecord> captor = ArgumentCaptor.forClass(LabRepairRecord.class);
        verify(labRepairRecordMapper, times(ALL_STATUS.length)).insertLabRepairRecord(captor.capture());
        for (LabRepairRecord record : captor.getAllValues())
        {
            assertEquals("维修信息更新", record.getActionName(),
                    "状态没变就不该写「状态流转」，否则处理时间线会被同状态的噪音记录填满");
            assertEquals(record.getFromStatus(), record.getToStatus(),
                    "非流转类履历的起止状态应当相同");
        }
    }

    /** 造一张库中已存在的报修单，状态为给定值。 */
    private static LabRepair repairInStatus(String status)
    {
        LabRepair repair = new LabRepair();
        repair.setRepairId(REPAIR_ID);
        repair.setStatus(status);
        repair.setAssetId(ASSET_ID);
        return repair;
    }
}
