package com.ruoyi.project.laboratory.mapper;

import java.util.List;
import com.ruoyi.project.laboratory.domain.LabRepairRecord;

/**
 * Repair record mapper.
 *
 * @author ruoyi
 */
public interface LabRepairRecordMapper
{
    public List<LabRepairRecord> selectLabRepairRecordList(Long repairId);

    public int insertLabRepairRecord(LabRepairRecord record);
}
