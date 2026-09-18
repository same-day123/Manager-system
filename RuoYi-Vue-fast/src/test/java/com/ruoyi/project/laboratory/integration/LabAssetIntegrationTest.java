package com.ruoyi.project.laboratory.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.domain.LabRoom;
import com.ruoyi.project.laboratory.support.LabTestSupport;

/**
 * 资产与房间模块集成测试（IT-05、IT-07）。
 *
 * <p>两条用例都在验证「一批操作里既有成功又有失败时，数据库最终长什么样」——
 * 这是事务边界真正起作用的地方：跨模块引用完整性（房间下有资产不能删）与
 * 批量导入的批次语义（成功的落库、失败的只计数不污染）。
 *
 * <p>覆盖的业务规则（编号口径取自 `docs/大作业/01-需求基线.md` 第 6 节，不得自行改写）：
 * <ul>
 * <li><b>BR-04 事务边界</b>：{@code importLabAsset} 要求「逐条独立提交，单条失败不影响其他条，
 * 最终返回失败汇总」——IT-07 断言成功 2 条 + 失败 1 条 = 提交 3 条</li>
 * <li><b>BR-05 校验规则</b>：房间下有资产时不能删除（IT-05）；资产编号全局唯一（IT-07 的撞号失败）</li>
 * <li><b>BR-06 逻辑删除</b>：IT-05 被拦截时房间的 {@code del_flag} 必须仍是 {@code '0'}；
 * 反证步骤里资产逻辑删除后房间才可删，也说明「房间下有没有资产」是按 {@code del_flag='0'} 统计的</li>
 * </ul>
 *
 * <p>BR-01 / BR-02 / BR-03 在 {@link LabRepairIntegrationTest} 里。
 *
 * @author ruoyi
 */
@DisplayName("资产与房间模块集成测试")
class LabAssetIntegrationTest extends AbstractLabIntegrationTest
{
    @Test
    @DisplayName("IT-05 跨模块协作：房间下挂着资产时不可删除，资产清空后才可删")
    void it05_roomWithAssetsCannotBeDeleted()
    {
        // 基线房间下已经挂着 1 台资产（assetId，del_flag='0'）
        LabTestSupport.loginAs("lab_manager");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRoomService.deleteLabRoomByRoomId(roomId));
        assertEquals("实验室下存在资产，不能删除", ex.getMessage());

        // 房间没有被逻辑删除，业务查询里仍然可见
        assertEquals("0", queryString("select del_flag from lab_room where room_id = ?", roomId));
        assertEquals(1, labRoomService.selectLabRoomList(new LabRoom()).size());

        // 反证：把资产逻辑删除后，同一个房间就能删了。
        // 这一步是必需的——它证明拦下来的是「资产存在」这个事实，
        // 而不是方法无条件抛异常（否则上面那条断言有可能是假绿灯）。
        jdbcTemplate.update("update lab_asset set del_flag = '2' where asset_id = ?", assetId);
        assertEquals(1, labRoomService.deleteLabRoomByRoomId(roomId));
        assertEquals("2", queryString("select del_flag from lab_room where room_id = ?", roomId));
    }

    @Test
    @DisplayName("IT-07 批量导入批次语义：1 条撞号失败、2 条成功落库并各写 1 条入库履历")
    void it07_batchImportCountsFailureAndWritesIntakeHistory()
    {
        LabTestSupport.loginAs("lab_manager");

        List<LabAsset> batch = new ArrayList<LabAsset>();
        batch.add(importedAsset("ZC-9001", "数字万用表"));
        batch.add(importedAsset("ZC-9002", "可编程直流电源"));
        // 第 3 条故意用基线资产的编号，触发「资产编号已存在」
        batch.add(importedAsset(BASE_ASSET_CODE, "与库中编号重复的设备"));

        String message = labAssetService.importLabAsset(batch, "lab_tester");

        // ① 批次语义：返回文案如实报出失败条数与失败明细
        assertTrue(message.contains("1 条失败数据"), "导入结果应报出失败条数，实际=" + message);
        assertTrue(message.contains(BASE_ASSET_CODE), "失败明细应指出是哪个编号，实际=" + message);

        // ② 净增 2 条：基线 1 条 + 成功的 2 条；失败那条没有落库
        assertEquals(3, countRows("select count(1) from lab_asset where del_flag = '0'"));
        assertEquals(0, countRows("select count(1) from lab_asset where asset_code = ? and asset_name = ?",
                BASE_ASSET_CODE, "与库中编号重复的设备"));

        // ③ 成功的两条各写 1 条「入库」履历；失败的那条不写
        for (String assetCode : new String[] { "ZC-9001", "ZC-9002" })
        {
            Long importedId = queryLong("select asset_id from lab_asset where asset_code = ?", assetCode);
            assertEquals(1, countRows("select count(1) from lab_asset_record where asset_id = ? and record_type = '入库'",
                    importedId), "资产 " + assetCode + " 应恰好有 1 条入库履历");
        }
        assertEquals(2, countRows("select count(1) from lab_asset_record where record_type = '入库'"),
                "基线资产是直接落库铺的，不该有入库履历，总数应只有成功导入的 2 条");
    }

    /** 构造一条待导入的资产（只给导入场景关心的字段）。 */
    private LabAsset importedAsset(String assetCode, String assetName)
    {
        LabAsset asset = new LabAsset();
        asset.setAssetCode(assetCode);
        asset.setAssetName(assetName);
        asset.setAssetType("instrument");
        asset.setRoomId(roomId);
        return asset;
    }
}
