package com.ruoyi.project.laboratory.integration;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import com.ruoyi.project.laboratory.domain.LabRepair;
import com.ruoyi.project.laboratory.mapper.LabAssetMapper;
import com.ruoyi.project.laboratory.mapper.LabAssetRecordMapper;
import com.ruoyi.project.laboratory.mapper.LabRepairMapper;
import com.ruoyi.project.laboratory.mapper.LabRepairRecordMapper;
import com.ruoyi.project.laboratory.mapper.LabRoomMapper;
import com.ruoyi.project.laboratory.service.ILabAssetService;
import com.ruoyi.project.laboratory.service.ILabRepairService;
import com.ruoyi.project.laboratory.service.ILabRoomService;
import com.ruoyi.project.laboratory.support.H2MySqlCompat;
import com.ruoyi.project.laboratory.support.LabIntegrationTestApplication;
import com.ruoyi.project.laboratory.support.LabTestSupport;

/**
 * 集成测试公共骨架。
 *
 * <p>只做三件事，把重复的样板收敛到一处：
 * <ol>
 * <li>挂上最小测试上下文（{@link LabIntegrationTestApplication}）与 integration profile；</li>
 * <li>{@code @BeforeEach} 清空 5 张表 + 铺基线数据（1 个房间 + 1 台「正常」资产）；</li>
 * <li>{@code @AfterEach} 清掉登录上下文，避免用例之间互相污染。</li>
 * </ol>
 *
 * <p><b>刻意不加 {@code @Transactional}。</b>本组用例要验证的正是「真实提交之后落到库里
 * 的样子」——资产被联动成维修中、履历落了几条、逻辑删除后行还在不在。加了测试级事务回滚，
 * 所有写操作都会在断言前被撤掉，测试看着全绿却什么都没验证到。
 *
 * <p>断言一律走 {@link JdbcTemplate} <b>直接查库</b>，不采信 Service 的返回值：
 * Service 返回 1 不代表数据真的落进去了，事务被标记 rollback-only 时返回值照样是 1。
 *
 * @author ruoyi
 */
@SpringBootTest(classes = LabIntegrationTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("integration")
abstract class AbstractLabIntegrationTest
{
    /** 基线房间编号。 */
    protected static final String BASE_ROOM_NO = "LAB-001";

    /** 基线房间名称。 */
    protected static final String BASE_ROOM_NAME = "软件工程实验室";

    /** 基线资产编号。 */
    protected static final String BASE_ASSET_CODE = "ZC-0001";

    /** 基线资产名称。 */
    protected static final String BASE_ASSET_NAME = "数字示波器";

    /** 资产状态字典：正常（可提交报修）。 */
    protected static final String ASSET_NORMAL = "0";

    /** 资产状态字典：维修中。 */
    protected static final String ASSET_REPAIRING = "2";

    /** 报修状态字典：待审核。 */
    protected static final String REPAIR_PENDING_REVIEW = "0";

    /** 报修状态字典：待维修。 */
    protected static final String REPAIR_PENDING = "1";

    /** 报修状态字典：维修中。 */
    protected static final String REPAIR_REPAIRING = "2";

    /** 报修状态字典：已完成。 */
    protected static final String REPAIR_FINISHED = "3";

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected ILabRepairService labRepairService;

    @Autowired
    protected ILabAssetService labAssetService;

    @Autowired
    protected ILabRoomService labRoomService;

    @Autowired
    protected LabRepairMapper labRepairMapper;

    @Autowired
    protected LabAssetMapper labAssetMapper;

    @Autowired
    protected LabRoomMapper labRoomMapper;

    @Autowired
    protected LabRepairRecordMapper labRepairRecordMapper;

    @Autowired
    protected LabAssetRecordMapper labAssetRecordMapper;

    /** 基线房间主键，{@code @BeforeEach} 里铺好。 */
    protected Long roomId;

    /** 基线资产主键，状态「正常(0)」，可直接用于提交报修。 */
    protected Long assetId;

    @BeforeEach
    void resetDatabase()
    {
        // 先挂上 MySQL 方言垫片（date_format / date_sub 的同名函数），
        // 否则看板那两条「近 7 天趋势」查询在 H2 上根本解析不了（偏移 D-19）。
        // 幂等：H2 支持 create alias if not exists，重复调用无副作用。
        H2MySqlCompat.installIfNeeded(jdbcTemplate);

        // H2 内存库每次 JVM 启动都是空的，但同一个 Spring 上下文会被多个测试类共用，
        // 上前一个类的数据还在，所以每个用例前必须自己清现场。
        // 删除顺序：先明细表后主表，避免留下悬挂引用（H2 默认没有外键约束，但顺序仍照生产习惯走）。
        jdbcTemplate.execute("delete from lab_repair_record");
        jdbcTemplate.execute("delete from lab_asset_record");
        jdbcTemplate.execute("delete from lab_repair");
        jdbcTemplate.execute("delete from lab_asset");
        jdbcTemplate.execute("delete from lab_room");

        jdbcTemplate.update("insert into lab_room (room_name, room_no, college_name, status, del_flag, "
                + "create_by, create_time) values (?, ?, ?, '0', '0', 'system', now())",
                BASE_ROOM_NAME, BASE_ROOM_NO, "计算机学院");
        roomId = queryLong("select room_id from lab_room where room_no = ?", BASE_ROOM_NO);

        assetId = insertAsset(BASE_ASSET_CODE, BASE_ASSET_NAME, ASSET_NORMAL, roomId);
    }

    @AfterEach
    void clearLoginContext()
    {
        LabTestSupport.logout();
    }

    // ------------------------------------------------------------------
    // 铺数据的工具：直接走 JDBC，不经过 Service
    // ------------------------------------------------------------------

    /**
     * 直接落库插一台资产，返回主键。
     *
     * <p>用 JDBC 而不是 Service，是为了让「被测对象」保持单一：用例验证的是报修 / 删除 / 导入
     * 这些动作，基线数据不该顺带把 {@code insertLabAsset} 也测了。
     */
    protected Long insertAsset(String assetCode, String assetName, String status, Long roomId)
    {
        jdbcTemplate.update("insert into lab_asset (asset_code, asset_name, asset_type, model, price, "
                + "room_id, status, del_flag, create_by, create_time) "
                + "values (?, ?, 'instrument', 'TEST-MODEL', ?, ?, ?, '0', 'system', now())",
                assetCode, assetName, new BigDecimal("1000.00"), roomId, status);
        return queryLong("select asset_id from lab_asset where asset_code = ?", assetCode);
    }

    /**
     * 以给定角色身份提交一张报修单，返回报修单主键。
     *
     * <p>登录上下文用 {@link LabTestSupport}，普通用户 id 固定 100（<b>不能是 1</b>，
     * 否则 {@code SysUser.isAdmin()} 会短路成超级管理员，权限相关的断言全部失真）。
     */
    protected Long submitRepairAs(String roleKey, Long targetAssetId)
    {
        LabTestSupport.loginAs(roleKey);
        return doSubmitRepair(targetAssetId);
    }

    /** 以指定用户 id 的身份提交一张报修单，用于构造「他人提交的单据」。 */
    protected Long submitRepairAsUser(Long userId, Long targetAssetId)
    {
        LabTestSupport.loginAsUser(userId, "student_assistant");
        return doSubmitRepair(targetAssetId);
    }

    private Long doSubmitRepair(Long targetAssetId)
    {
        LabRepair repair = new LabRepair();
        repair.setAssetId(targetAssetId);
        repair.setFaultDescription("集成测试：设备加电后无反应");
        repair.setFaultLevel("1");
        labRepairService.insertLabRepair(repair);
        return repair.getRepairId();
    }

    // ------------------------------------------------------------------
    // 直接查库的断言工具
    // ------------------------------------------------------------------

    /** 查单列字符串。 */
    protected String queryString(String sql, Object... args)
    {
        return jdbcTemplate.queryForObject(sql, String.class, args);
    }

    /** 查单列 Long。 */
    protected Long queryLong(String sql, Object... args)
    {
        return jdbcTemplate.queryForObject(sql, Long.class, args);
    }

    /** 查计数（H2 的 count() 返回 BIGINT，交由 Spring 转成 Integer）。 */
    protected int countRows(String sql, Object... args)
    {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value.intValue();
    }

    /** 资产当前状态。 */
    protected String assetStatus(Long targetAssetId)
    {
        return queryString("select status from lab_asset where asset_id = ?", targetAssetId);
    }

    /** 报修单当前状态（不过滤 del_flag）。 */
    protected String repairStatus(Long repairId)
    {
        return queryString("select status from lab_repair where repair_id = ?", repairId);
    }
}
