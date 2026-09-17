package com.ruoyi.project.laboratory.mapper;

import java.util.List;
import com.ruoyi.project.laboratory.domain.LabAssetRecord;

/**
 * Asset lifecycle record mapper.
 *
 * @author ruoyi
 */
public interface LabAssetRecordMapper
{
    public List<LabAssetRecord> selectLabAssetRecordList(Long assetId);

    public int insertLabAssetRecord(LabAssetRecord record);
}
