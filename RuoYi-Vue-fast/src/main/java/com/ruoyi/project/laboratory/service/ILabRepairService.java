package com.ruoyi.project.laboratory.service;

import java.util.List;
import com.ruoyi.project.laboratory.domain.LabRepair;
import com.ruoyi.project.laboratory.domain.LabRepairRecord;

/**
 * Laboratory repair service.
 *
 * @author ruoyi
 */
public interface ILabRepairService
{
    public LabRepair selectLabRepairByRepairId(Long repairId);

    public List<LabRepair> selectLabRepairList(LabRepair labRepair);

    public int insertLabRepair(LabRepair labRepair);

    public int updateLabRepair(LabRepair labRepair);

    public int auditLabRepair(LabRepair labRepair);

    public int evaluateLabRepair(LabRepair labRepair);

    public List<LabRepairRecord> selectLabRepairRecords(Long repairId);

    public int deleteLabRepairByRepairId(Long repairId);

    public int deleteLabRepairByRepairIds(Long[] repairIds);
}
