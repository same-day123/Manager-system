package com.ruoyi.project.laboratory.service.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.ruoyi.project.laboratory.domain.LabAsset;
import com.ruoyi.project.laboratory.mapper.LabAssetMapper;
import com.ruoyi.project.laboratory.mapper.LabAssetRecordMapper;
import com.ruoyi.project.laboratory.mapper.LabRoomMapper;
import com.ruoyi.project.laboratory.service.ILabRoomService;

@ExtendWith(MockitoExtension.class)
class LabAssetServiceImplTest
{
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

    @Test
    void deleteRepairingAssetRejected()
    {
        LabAsset asset = new LabAsset();
        asset.setAssetId(1L);
        asset.setStatus("2");
        when(labAssetMapper.selectLabAssetByAssetId(1L)).thenReturn(asset);

        assertThrows(ServiceException.class, () -> labAssetService.deleteLabAssetByAssetId(1L));
        verify(labAssetMapper, never()).deleteLabAssetByAssetId(anyLong());
    }
}
