package com.ruoyi.project.laboratory.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.project.laboratory.domain.LabRoom;
import com.ruoyi.project.laboratory.mapper.LabAssetMapper;
import com.ruoyi.project.laboratory.mapper.LabRoomMapper;

@ExtendWith(MockitoExtension.class)
class LabRoomServiceImplTest
{
    @Mock
    private LabRoomMapper labRoomMapper;

    @Mock
    private LabAssetMapper labAssetMapper;

    @InjectMocks
    private LabRoomServiceImpl labRoomService;

    @Test
    void deleteRoomWithAssetsRejected()
    {
        when(labAssetMapper.countLabAssetByRoomId(1L)).thenReturn(1);

        assertThrows(ServiceException.class, () -> labRoomService.deleteLabRoomByRoomId(1L));
        verify(labRoomMapper, never()).deleteLabRoomByRoomId(anyLong());
        verify(labRoomMapper, never()).updateLabRoom(any(LabRoom.class));
    }
}
