package com.ruoyi.project.laboratory.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * Laboratory dashboard mapper.
 *
 * @author ruoyi
 */
public interface LabDashboardMapper
{
    public Long selectAssetTotal();

    public Long selectRepairingAssetTotal();

    public Long selectPendingRepairTotal(@Param("applicantId") Long applicantId);

    public Long selectFinishedRepairTotal(@Param("applicantId") Long applicantId);

    public List<Map<String, Object>> selectRepairTrend(@Param("applicantId") Long applicantId);

    public List<Map<String, Object>> selectFaultLevelDistribution(@Param("applicantId") Long applicantId);

    public List<Map<String, Object>> selectRoomRepairRanking(@Param("applicantId") Long applicantId);

    public List<Map<String, Object>> selectRepairCostTrend(@Param("applicantId") Long applicantId);
}
