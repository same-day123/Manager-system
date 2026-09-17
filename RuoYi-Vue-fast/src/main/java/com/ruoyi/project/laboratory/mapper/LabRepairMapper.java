package com.ruoyi.project.laboratory.mapper;

import java.util.List;
import com.ruoyi.project.laboratory.domain.LabRepair;

/**
 * Laboratory repair mapper.
 *
 * @author ruoyi
 */
public interface LabRepairMapper
{
    public LabRepair selectLabRepairByRepairId(Long repairId);

    public List<LabRepair> selectLabRepairList(LabRepair labRepair);

    public int insertLabRepair(LabRepair labRepair);

    public int updateLabRepair(LabRepair labRepair);

    public int deleteLabRepairByRepairId(Long repairId);

    public int deleteLabRepairByRepairIds(Long[] repairIds);
}
