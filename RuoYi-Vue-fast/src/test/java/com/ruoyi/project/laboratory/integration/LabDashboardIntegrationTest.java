package com.ruoyi.project.laboratory.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.ruoyi.project.laboratory.service.ILabDashboardService;
import com.ruoyi.project.laboratory.support.H2MySqlCompat;
import com.ruoyi.project.laboratory.support.LabTestSupport;

/**
 * 运维工作台（看板）集成测试（DB-01 ~ DB-07）。
 *
 * <p><b>这一组是偏移 D-19 的收口。</b>D-19 原本的记录是「`LabDashboardMapper` 用了
 * `date_format()` 与 `date_sub()`，H2 1.4.199 两个都不支持，看板无法覆盖，只能容忍」。
 * 实测后确认不必容忍：H2 支持 `create alias ... for "类.方法"`，在<b>测试侧</b>注册
 * 同名函数即可让生产 SQL 原样跑起来——<b>生产 mapper XML 一个字都没改</b>
 * （T2 卡第 4 节唯一的硬约束）。垫片见
 * {@link H2MySqlCompat} / {@code H2MySqlDateFuncs}。
 *
 * <p>覆盖的 8 条 mapper 查询：4 项指标卡（{@code selectSummary}）+
 * 4 张图（{@code selectCharts}）。
 *
 * <p>对应需求基线里的用户故事 <b>US-13 首页运维看板</b>（`docs/大作业/01-需求基线.md`）。
 * 说明：BR-01~06 六条业务规则本身不覆盖看板读数，但看板的"数据范围"口径
 * 直接复用 <b>BR-03 权限规则</b>——非全局可见角色只看到本人数据，这里是它的第三个证据点
 * （另两处是 IT-06 的报修列表与 T1 的 `LabRoleUtilsTest`）。
 *
 * <p>断言一律用 Service 返回值 + `JdbcTemplate` 直查库交叉验证，不采信单侧。
 *
 * @author ruoyi
 */
@DisplayName("运维看板集成测试")
class LabDashboardIntegrationTest extends AbstractLabIntegrationTest
{
    /**
     * 报修状态字典：已拒绝。
     *
     * <p>其余状态（待审核 / 待维修 / 维修中 / 已完成）一律复用
     * {@link AbstractLabIntegrationTest} 里已有的常量，**不在子类里另定义一份**——
     * 同名字段会遮蔽父类常量，一旦两边取不同的值就会变成极难发现的 bug。
     */
    private static final String REPAIR_REJECTED = "4";

    /** 趋势窗口的边界：`date_sub(curdate(), interval 6 day)` 恰好卡在第 6 天，这条**必须计入**。 */
    private static final int WINDOW_EDGE_DAYS = 6;

    /** 窗口外一格：第 7 天，**必须被剔除**。 */
    private static final int JUST_OUTSIDE_WINDOW_DAYS = 7;

    /** 报修单编自增序号，保证同一用例内编号不撞。 */
    private int repairSeq = 0;

    @Autowired
    private ILabDashboardService labDashboardService;

    // ------------------------------------------------------------------ 方言垫片自检

    @Test
    @DisplayName("DB-01 方言垫片自检：date_format 与 date_sub 在测试库里真的算得出正确的值")
    void db01_h2DialectShimComputesCorrectValues()
    {
        // 这条不是"顺便测垫片"，而是**防止看板用例变成假绿灯**：
        // 如果别名没挂上或算错了，下面那些看板断言就不再可信。所以先把它钉死。
        assertEquals("01-02", H2MySqlCompat.probeDateFormat(jdbcTemplate),
                "date_format 应把 2026-01-02 03:04:05 按 '%m-%d' 格式化成 01-02");
        assertEquals(java.sql.Date.valueOf("2026-01-04"), H2MySqlCompat.probeDateSub(jdbcTemplate),
                "date_sub(date '2026-01-10', interval 6 day) 应得到 2026-01-04");
        assertEquals(2, H2MySqlCompat.aliasStatements().size(),
                "垫片应只注册两个别名；生产 SQL 用到别的 MySQL 函数时，这里要同步扩");
    }

    // ------------------------------------------------------------------ 指标卡

    @Test
    @DisplayName("DB-02 指标卡（全局可见角色）：4 项数字对得上库里的真实行数")
    void db02_summaryCountsMatchDatabaseForGlobalViewer()
    {
        Long repairingAsset = insertAsset("ZC-7001", "待修投影仪", ASSET_REPAIRING, roomId);
        insertAsset("ZC-7002", "闲置交换机", ASSET_NORMAL, roomId);
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, repairingAsset, 100L, 0);
        insertRepairRow(REPAIR_PENDING_REVIEW, "2", 0.00, assetId, 200L, 0);
        insertRepairRow(REPAIR_FINISHED, "3", 120.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_REJECTED, "1", 0.00, assetId, 200L, 0);

        // repair_engineer 命中 LabRoleUtils.canViewAll → 不过滤申请人
        LabTestSupport.loginAs("repair_engineer");
        Map<String, Object> summary = labDashboardService.selectSummary();

        assertEquals(3L, number(summary, "assetTotal"), "资产总数应等于库里 del_flag='0' 的资产行数");
        assertEquals(1L, number(summary, "repairingAssetTotal"), "维修中资产应为 1 台");

        // 与"直查库"交叉验证，避免 Service 与 mapper 同时错成一样
        assertEquals(number(summary, "assetTotal"), (long) countRows("select count(1) from lab_asset where del_flag = '0'"));
        assertEquals(number(summary, "repairingAssetTotal"),
                (long) countRows("select count(1) from lab_asset where del_flag = '0' and status = '2'"));

        assertEquals(2L, number(summary, "pendingRepairTotal"), "待审核报修应为 2 条");
        assertEquals(1L, number(summary, "finishedRepairTotal"), "已完成报修应为 1 条");
    }

    @Test
    @DisplayName("DB-03 指标卡（普通角色）：报修口径收紧到本人，但资产口径仍是全局量")
    void db03_summaryRepairCountsAreScopedToApplicantForOrdinaryRole()
    {
        // 100L：2 条待审核 + 1 条已完成；200L：3 条待审核 + 5 条已完成
        for (int i = 0; i < 2; i++)
        {
            insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, assetId, 100L, 0);
        }
        insertRepairRow(REPAIR_FINISHED, "1", 50.00, assetId, 100L, 0);
        for (int i = 0; i < 3; i++)
        {
            insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, assetId, 200L, 0);
        }
        for (int i = 0; i < 5; i++)
        {
            insertRepairRow(REPAIR_FINISHED, "1", 10.00, assetId, 200L, 0);
        }

        // student_assistant 不命中 canViewAll → 只看本人
        LabTestSupport.loginAsUser(100L, "student_assistant");
        Map<String, Object> summary = labDashboardService.selectSummary();

        assertEquals(2L, number(summary, "pendingRepairTotal"), "普通角色只应数到自己那 2 条待审核");
        assertEquals(1L, number(summary, "finishedRepairTotal"), "普通角色只应数到自己那 1 条已完成");

        // 资产不属于任何申请人，口径**不随角色收缩**——这是 service 里的显式设计决定，
        // 钉住它可以防止后人"顺手统一"成也按申请人过滤。
        assertEquals(1L, number(summary, "assetTotal"), "资产口径是全局量，不应随登录角色收缩");

        // 同一个登录态下换全局角色，报修口径立刻放开
        LabTestSupport.loginAs("repair_engineer");
        assertEquals(5L, number(labDashboardService.selectSummary(), "pendingRepairTotal"),
                "全局可见角色应看到全部 5 条待审核");
    }

    // ------------------------------------------------------------------ 趋势图（date_format + date_sub 主战场）

    @Test
    @DisplayName("DB-04 报修趋势：近 7 天窗口按天分组，第 6 天算在内、第 7 天被剔除")
    void db04_repairTrendGroupsByDayWithinSevenDayWindow()
    {
        // 边界值设计：窗口边界第 6 天与窗外第 7 天各造 1 条，
        // 用来证明过滤条件真的生效，而不是"碰巧把数据都装进来了"。
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_PENDING_REVIEW, "2", 0.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_PENDING_REVIEW, "3", 0.00, assetId, 100L, 3);
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, assetId, 100L, WINDOW_EDGE_DAYS);
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, assetId, 100L, JUST_OUTSIDE_WINDOW_DAYS);
        assertEquals(5, countRows("select count(1) from lab_repair where del_flag = '0'"),
                "造数自检：库里应该有 5 条，其中 1 条在窗口外");

        LabTestSupport.loginAs("repair_engineer");
        List<Map<String, Object>> trend = chartRows("repairTrend");

        assertEquals(3, trend.size(), "窗口内只应有 3 个日期分组（今天 / 3 天前 / 6 天前）");
        assertEquals(Arrays.asList(dayLabel(WINDOW_EDGE_DAYS), dayLabel(3), dayLabel(0)),
                names(trend), "分组应按日期升序，且第 6 天在内、第 7 天已剔除");
        assertEquals(1L, value(trend.get(0)), "6 天前应 1 条（窗口边界，必须计入）");
        assertEquals(1L, value(trend.get(1)), "3 天前应 1 条");
        assertEquals(2L, value(trend.get(2)), "今天应 2 条");
        assertEquals(4L, totalValue(trend), "窗口内合计 4 条，不含第 7 天那条");
    }

    @Test
    @DisplayName("DB-05 故障等级分布：按等级分组计数，顺序按等级升序")
    void db05_faultLevelDistributionGroupsByLevel()
    {
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_PENDING_REVIEW, "2", 0.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_PENDING_REVIEW, "3", 0.00, assetId, 100L, 0);

        LabTestSupport.loginAs("repair_engineer");
        List<Map<String, Object>> distribution = chartRows("faultLevelDistribution");

        assertEquals(3, distribution.size(), "3 个故障等级应给出 3 个分组");
        assertEquals(Arrays.asList("1", "2", "3"), names(distribution), "分组应按故障等级升序");
        assertEquals(2L, value(distribution.get(0)), "等级 1 有 2 条");
        assertEquals(1L, value(distribution.get(1)), "等级 2 有 1 条");
        assertEquals(1L, value(distribution.get(2)), "等级 3 有 1 条");
    }

    @Test
    @DisplayName("DB-06 实验室报修排行：按数量倒序、未分配实验室兜底成一行、最多 8 行")
    void db06_roomRepairRankingOrdersAndCapsAtEightRows()
    {
        // 造 9 个房间各 1 条报修 → 加上「未分配实验室」共 10 个分组，用来撞 limit 8 的上限。
        for (int i = 1; i <= 9; i++)
        {
            Long room = insertRoom("LAB-R" + i, "排行实验室" + i);
            Long asset = insertAsset("ZC-R" + i, "排行设备" + i, ASSET_NORMAL, room);
            insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, asset, 100L, 0);
        }
        // 一台没有归属实验室的资产 → 走 ifnull(room_name, '未分配实验室') 兜底分支，且给 2 条使其排第一
        Long unassignedAsset = insertAssetWithoutRoom("ZC-NO-ROOM", "未归属设备");
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, unassignedAsset, 100L, 0);
        insertRepairRow(REPAIR_PENDING_REVIEW, "1", 0.00, unassignedAsset, 100L, 0);

        LabTestSupport.loginAs("repair_engineer");
        List<Map<String, Object>> ranking = chartRows("roomRepairRanking");

        assertEquals(8, ranking.size(), "mapper 里有 limit 8，10 个分组最多只能返回 8 行");
        assertEquals("未分配实验室", names(ranking).get(0),
                "资产没有归属实验室时应兜底成「未分配实验室」，且 2 条使其排在第一位");
        assertEquals(2L, value(ranking.get(0)), "未分配实验室应有 2 条");
        // 倒序校验：后面的分组都不大于前面的
        List<Long> values = values(ranking);
        for (int i = 1; i < values.size(); i++)
        {
            assertTrue(values.get(i - 1) >= values.get(i),
                    "排行应按报修数倒序，第 " + i + " 行 " + values.get(i - 1) + " 不应小于 " + values.get(i));
        }
    }

    @Test
    @DisplayName("DB-07 维修成本趋势：窗口内按天求和，窗口外那条的钱不计入")
    void db07_repairCostTrendSumsCostWithinWindow()
    {
        insertRepairRow(REPAIR_FINISHED, "1", 100.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_FINISHED, "1", 50.00, assetId, 100L, 0);
        insertRepairRow(REPAIR_FINISHED, "1", 200.00, assetId, 100L, 3);
        insertRepairRow(REPAIR_FINISHED, "1", 10.00, assetId, 100L, WINDOW_EDGE_DAYS);
        // 窗口外这条金额很大，是"钱有没有被算错"的探针：多算了它，合计会明显不对
        insertRepairRow(REPAIR_FINISHED, "1", 9999.00, assetId, 100L, JUST_OUTSIDE_WINDOW_DAYS);

        LabTestSupport.loginAs("repair_engineer");
        List<Map<String, Object>> costTrend = chartRows("repairCostTrend");

        assertEquals(3, costTrend.size(), "窗口内只应有 3 个日期分组");
        assertEquals(Arrays.asList(dayLabel(WINDOW_EDGE_DAYS), dayLabel(3), dayLabel(0)),
                names(costTrend), "分组应按日期升序");
        assertEquals(new BigDecimal("10.00"), decimalValue(costTrend.get(0)), "6 天前 10.00");
        assertEquals(new BigDecimal("200.00"), decimalValue(costTrend.get(1)), "3 天前 200.00");
        assertEquals(new BigDecimal("150.00"), decimalValue(costTrend.get(2)), "今天 100.00 + 50.00 应被求和");
        assertEquals(new BigDecimal("360.00"), totalDecimalValue(costTrend),
                "合计 360.00；若等于 10359.00 说明窗口外的 9999.00 被误算了进来");
    }

    // ------------------------------------------------------------------ 造数与取值工具

    /**
     * 直接落库插一条报修单，`create_time` = 现在往前推 {@code daysAgo} 天。
     *
     * <p>刻意走 JDBC 而不是 Service：Service 会生成编号、联动资产、写履历，
     * 那些属于报修用例的被测对象；看板用例要的是"库里摆好一批数据"，别把别的东西一起测了。
     */
    private Long insertRepairRow(String status, String faultLevel, double cost, Long targetAssetId,
            Long applicantId, int daysAgo)
    {
        String code = "BX-DASH-" + (++repairSeq);
        Timestamp createTime = atDaysAgo(daysAgo);
        jdbcTemplate.update("insert into lab_repair (repair_code, asset_id, fault_description, fault_level, "
                + "applicant_id, applicant_name, applicant_phone, repair_cost, status, del_flag, "
                + "create_by, create_time) values (?, ?, ?, ?, ?, ?, ?, ?, ?, '0', 'system', ?)",
                code, targetAssetId, "集成测试：看板口径造数", faultLevel, applicantId, "测试用户",
                "13800000000", new BigDecimal(Double.toString(cost)), status, createTime);
        return queryLong("select repair_id from lab_repair where repair_code = ?", code);
    }

    /** 直接落库插一个房间，返回主键。 */
    private Long insertRoom(String roomNo, String roomName)
    {
        jdbcTemplate.update("insert into lab_room (room_name, room_no, college_name, status, del_flag, "
                + "create_by, create_time) values (?, ?, '计算机学院', '0', '0', 'system', ?)",
                roomName, roomNo, atDaysAgo(0));
        return queryLong("select room_id from lab_room where room_no = ?", roomNo);
    }

    /** 插一台"没有归属实验室"的资产（`room_id` 为 null），用于撞 `ifnull` 兜底分支。 */
    private Long insertAssetWithoutRoom(String assetCode, String assetName)
    {
        jdbcTemplate.update("insert into lab_asset (asset_code, asset_name, asset_type, model, price, "
                + "room_id, status, del_flag, create_by, create_time) "
                + "values (?, ?, 'instrument', 'TEST-MODEL', ?, null, ?, '0', 'system', ?)",
                assetCode, assetName, new BigDecimal("1000.00"), ASSET_NORMAL, atDaysAgo(0));
        return queryLong("select asset_id from lab_asset where asset_code = ?", assetCode);
    }

    /**
     * 取一张图的数据行。
     *
     * <p>{@code selectCharts} 一次返回 4 张图，每张都是 {@code List<Map<String,Object>>}。
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> chartRows(String chartKey)
    {
        Object raw = labDashboardService.selectCharts().get(chartKey);
        assertNotNull(raw, "看板应返回图表「" + chartKey + "」，实际为 null");
        return (List<Map<String, Object>>) raw;
    }

    private static Timestamp atDaysAgo(int days)
    {
        return new Timestamp(System.currentTimeMillis() - days * 86400000L);
    }

    /** 与 mapper 里 {@code date_format(create_time, '%m-%d')} 同口径的期望标签。 */
    private static String dayLabel(int daysAgo)
    {
        return new SimpleDateFormat("MM-dd").format(atDaysAgo(daysAgo));
    }

    private static List<String> names(List<Map<String, Object>> rows)
    {
        List<String> names = new ArrayList<String>();
        for (Map<String, Object> row : rows)
        {
            names.add(String.valueOf(row.get("name")));
        }
        return names;
    }

    private static List<Long> values(List<Map<String, Object>> rows)
    {
        List<Long> values = new ArrayList<Long>();
        for (Map<String, Object> row : rows)
        {
            values.add(((Number) row.get("value")).longValue());
        }
        return values;
    }

    private static long value(Map<String, Object> row)
    {
        return ((Number) row.get("value")).longValue();
    }

    private static long totalValue(List<Map<String, Object>> rows)
    {
        long total = 0L;
        for (Map<String, Object> row : rows)
        {
            total += value(row);
        }
        return total;
    }

    /** 金额列的取值（`sum(repair_cost)` 在 H2 里回的是 BigDecimal）。 */
    private static BigDecimal decimalValue(Map<String, Object> row)
    {
        Object raw = row.get("value");
        return raw instanceof BigDecimal ? (BigDecimal) raw : new BigDecimal(String.valueOf(raw));
    }

    private static BigDecimal totalDecimalValue(List<Map<String, Object>> rows)
    {
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows)
        {
            total = total.add(decimalValue(row));
        }
        return total;
    }

    /** 指标卡的数字（可能是 Long / Integer / BigInteger，统一取 long）。 */
    private static long number(Map<String, Object> summary, String key)
    {
        Object raw = summary.get(key);
        assertNotNull(raw, "指标卡应返回「" + key + "」");
        return ((Number) raw).longValue();
    }
}
