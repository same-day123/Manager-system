package com.ruoyi.project.laboratory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabRoom;
import com.ruoyi.project.laboratory.mapper.LabAssetMapper;
import com.ruoyi.project.laboratory.mapper.LabRoomMapper;

/**
 * 实验室房间契约测试。
 *
 * <p>覆盖需求基线 BR-05 的房间侧校验（编号与名称必填、编号全局唯一）与
 * US-12 的跨模块约束（房间下还挂着资产时不允许删除），
 * 后者是防止台账出现孤儿数据的关键防线。
 *
 * <p>原有 1 个用例保持原样，作为回归防线。
 *
 * @author ruoyi
 */
@ExtendWith(MockitoExtension.class)
class LabRoomServiceImplTest
{
    @Mock
    private LabRoomMapper labRoomMapper;

    @Mock
    private LabAssetMapper labAssetMapper;

    @InjectMocks
    private LabRoomServiceImpl labRoomService;

    // ---------------------------------------------------------------- 原有用例（断言未改动）

    @Test
    @DisplayName("房间下还挂着资产时不允许删除，且不改动房间记录")
    void deleteRoomWithAssetsRejected()
    {
        when(labAssetMapper.countLabAssetByRoomId(1L)).thenReturn(1);

        assertThrows(ServiceException.class, () -> labRoomService.deleteLabRoomByRoomId(1L));
        verify(labRoomMapper, never()).deleteLabRoomByRoomId(anyLong());
        verify(labRoomMapper, never()).updateLabRoom(any(LabRoom.class));
    }

    // ---------------------------------------------------------------- 新增：编号与名称校验（UT-M01 ~ UT-M02）

    @Test
    @DisplayName("实验室编号缺失时拒绝新增")
    void insertRoomRejectsBlankRoomNo()
    {
        LabRoom room = new LabRoom();
        room.setRoomName("嵌入式实验室");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRoomService.insertLabRoom(room));
        assertEquals("实验室编号不能为空", ex.getMessage());
        verify(labRoomMapper, never()).insertLabRoom(any(LabRoom.class));
    }

    @Test
    @DisplayName("实验室名称缺失时拒绝新增，即便编号已经填了")
    void insertRoomRejectsBlankRoomName()
    {
        LabRoom room = new LabRoom();
        room.setRoomNo("ROOM-002");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRoomService.insertLabRoom(room));
        assertEquals("实验室名称不能为空", ex.getMessage());
        verify(labRoomMapper, never()).insertLabRoom(any(LabRoom.class));
    }

    // ---------------------------------------------------------------- 新增：编号唯一性（UT-M03）

    @Test
    @DisplayName("实验室编号已被别的房间占用时拒绝新增")
    void insertRoomRejectsDuplicatedRoomNo()
    {
        LabRoom occupied = new LabRoom();
        occupied.setRoomId(66L);
        when(labRoomMapper.checkRoomNoUnique("ROOM-003")).thenReturn(occupied);

        LabRoom room = new LabRoom();
        room.setRoomNo("ROOM-003");
        room.setRoomName("传感器实验室");

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRoomService.insertLabRoom(room));
        assertEquals("实验室编号已存在", ex.getMessage());
        verify(labRoomMapper, never()).insertLabRoom(any(LabRoom.class));
    }

    // ---------------------------------------------------------------- 新增：删除前资产计数（UT-M04）

    @Test
    @DisplayName("房间下存在两台资产时删除被拦下，房间的删除标记保持不变")
    void deleteRoomIsBlockedWhileAssetsRemain()
    {
        when(labAssetMapper.countLabAssetByRoomId(1L)).thenReturn(2);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> labRoomService.deleteLabRoomByRoomId(1L));
        assertEquals("实验室下存在资产，不能删除", ex.getMessage());
        // BR-06：删除是逻辑删除，但被拦下时连逻辑删除也不能发生
        verify(labRoomMapper, never()).updateLabRoom(any(LabRoom.class));
        verify(labRoomMapper, never()).deleteLabRoomByRoomId(anyLong());
    }
}
