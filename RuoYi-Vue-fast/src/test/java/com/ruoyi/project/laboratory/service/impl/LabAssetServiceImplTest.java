package com.ruoyi.project.laboratory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.domain.LabAssetRecord;
import com.ruoyi.project.laboratory.domain.LabRoom;
import com.ruoyi.project.laboratory.mapper.LabAssetMapper;
import com.ruoyi.project.laboratory.mapper.LabAssetRecordMapper;
import com.ruoyi.project.laboratory.mapper.LabRoomMapper;
import com.ruoyi.project.laboratory.service.ILabRoomService;

/**
 * 资产台账契约测试。
 *
 * <p>覆盖需求基线 BR-05 的资产侧校验清单（必填项、归属实验室必须存在、编号全局唯一
 * 且在更新场景要排除自身）与 BR-04 的批量导入批次语义（逐条独立提交，单条失败不影响其他条）。
 *
 * <p>原有 1 个用例（维修中的资产不可删除）保持原样，作为回归防线。
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class LabAssetServiceImplTest
{
    /** 已有的实验室，供资产归属校验通过。 */
    private static final Long ROOM_ID = 1L;

    @Mock
    private LabAssetMapper labAssetMapper;

    @Mock
    private LabAssetRecordMapper labAssetRecordMapper;

    @Mock
    private LabRoomMapper labRoomMapper;

    @Mock
    private ILabRoomService labRoomService;

    @InjectMocks
    private LabAssetServiceImpl labAssetService;

    // ---------------------------------------------------------------- 原有用例（断言未改动）

    @Test
    @DisplayName("维修中的资产不允许删除，且不会调用任何删除语句")
    void deleteRepairingAssetRejected()
    {
        LabAsset asset = new LabAsset();
        asset.setAssetId(1L);
        asset.setStatus("2");
        when(labAssetMapper.selectLabAssetByAssetId(1L)).thenReturn(asset);

        assertThrows(ServiceException.class, () -> labAssetService.deleteLabAssetByAssetId(1L));
        verify(labAssetMapper, never()).deleteLabAssetByAssetId(anyLong());
    }

    // ---------------------------------------------------------------- 新增：入库校验（UT-A01 ~ UT-A04）

    @Test
    @DisplayName("资产编号缺失时拒绝入库")
    void insertAssetRejectsBlankAssetCode()
    {
        LabAsset asset = new LabAsset();
        asset.setAssetName("激光投影仪");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labAssetService.insertLabAsset(asset));
        assertEquals("资产编号不能为空", ex.getMessage());
        verify(labAssetMapper, never()).insertLabAsset(any(LabAsset.class));
    }

    @Test
    @DisplayName("资产名称缺失时拒绝入库，即便编号已经填了")
    void insertAssetRejectsBlankAssetName()
    {
        LabAsset asset = new LabAsset();
        asset.setAssetCode("AST-0001");
        asset.setRoomId(ROOM_ID);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labAssetService.insertLabAsset(asset));
        assertEquals("资产名称不能为空", ex.getMessage());
        verify(labAssetMapper, never()).insertLabAsset(any(LabAsset.class));
    }

    @Test
    @DisplayName("资产挂到不存在的实验室时拒绝入库，避免产生孤儿台账")
    void insertAssetRejectsUnknownRoom()
    {
        when(labRoomService.selectLabRoomByRoomId(ROOM_ID)).thenReturn(null);

        LabAsset asset = new LabAsset();
        asset.setAssetCode("AST-0002");
        asset.setAssetName("电子天平");
        asset.setRoomId(ROOM_ID);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labAssetService.insertLabAsset(asset));
        assertEquals("所属实验室不存在或已删除", ex.getMessage());
        verify(labAssetMapper, never()).insertLabAsset(any(LabAsset.class));
    }

    @Test
    @DisplayName("资产编号已被另一台资产占用时拒绝入库")
    void insertAssetRejectsDuplicatedAssetCode()
    {
        when(labRoomService.selectLabRoomByRoomId(ROOM_ID)).thenReturn(room(ROOM_ID));
        LabAsset occupied = new LabAsset();
        occupied.setAssetId(99L);
        when(labAssetMapper.checkAssetCodeUnique("AST-0003")).thenReturn(occupied);

        LabAsset asset = new LabAsset();
        asset.setAssetCode("AST-0003");
        asset.setAssetName("示波器");
        asset.setRoomId(ROOM_ID);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labAssetService.insertLabAsset(asset));
        assertEquals("资产编号已存在", ex.getMessage());
        verify(labAssetMapper, never()).insertLabAsset(any(LabAsset.class));
    }

    // ---------------------------------------------------------------- 新增：唯一性排除自身（UT-A05）

    @Test
    @DisplayName("唯一性校验命中自己时视为不重复，否则编辑保存会被自己卡住")
    void checkAssetCodeUniqueIgnoresItself()
    {
        LabAsset self = new LabAsset();
        self.setAssetId(5L);
        when(labAssetMapper.checkAssetCodeUnique("AST-0005")).thenReturn(self);

        LabAsset query = new LabAsset();
        query.setAssetId(5L);
        query.setAssetCode("AST-0005");

        assertTrue(labAssetService.checkAssetCodeUnique(query), "命中的就是自己，不应判为编号重复");
    }

    // ---------------------------------------------------------------- 新增：批量导入（UT-A06 ~ UT-A07）

    @Test
    @DisplayName("批量导入传空清单时直接拒绝，提示导入数据不能为空")
    void importAssetRejectsEmptyList()
    {
        ServiceException ex = assertThrows(ServiceException.class,
                () -> labAssetService.importLabAsset(new ArrayList<LabAsset>(), "asset01"));
        assertEquals("导入资产数据不能为空", ex.getMessage());
    }

    @Test
    @DisplayName("批量导入三条其中一条编号撞库：失败一条、成功两条，并在消息里汇总失败条数")
    void importAssetReportsPartialFailure()
    {
        when(labRoomService.selectLabRoomByRoomId(ROOM_ID)).thenReturn(room(ROOM_ID));
        LabAsset occupied = new LabAsset();
        occupied.setAssetId(99L);
        // 只有 AST-1001 撞库，另两条编号在全库唯一。
        when(labAssetMapper.checkAssetCodeUnique("AST-1001")).thenReturn(occupied);
        when(labAssetMapper.checkAssetCodeUnique("AST-1002")).thenReturn(null);
        when(labAssetMapper.checkAssetCodeUnique("AST-1003")).thenReturn(null);
        when(labAssetMapper.insertLabAsset(any(LabAsset.class))).thenReturn(1);

        List<LabAsset> batch = Arrays.asList(
                importRow("AST-1001", "已被占用的编号"),
                importRow("AST-1002", "正常入库一"),
                importRow("AST-1003", "正常入库二"));

        String result = labAssetService.importLabAsset(batch, "asset01");

        // BR-04：单条失败不影响其他条，成功数 + 失败数 = 总数
        assertTrue(result.contains("1 条失败数据"), "返回消息应汇总失败条数，实际为：" + result);
        assertTrue(result.contains("AST-1001"), "失败明细应指明是哪条资产，实际为：" + result);
        verify(labAssetMapper, times(2)).insertLabAsset(any(LabAsset.class));
        // 成功的两条各自写一条入库履历
        verify(labAssetRecordMapper, times(2)).insertLabAssetRecord(any(LabAssetRecord.class));
    }

    // ---------------------------------------------------------------- 辅助

    private static LabRoom room(Long roomId)
    {
        LabRoom room = new LabRoom();
        room.setRoomId(roomId);
        room.setRoomNo("ROOM-001");
        room.setRoomName("电子技术实验室");
        return room;
    }

    private static LabAsset importRow(String assetCode, String assetName)
    {
        LabAsset asset = new LabAsset();
        asset.setAssetCode(assetCode);
        asset.setAssetName(assetName);
        asset.setRoomId(ROOM_ID);
        return asset;
    }
}
