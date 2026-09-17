package com.ruoyi.project.laboratory.service.impl;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.project.laboratory.mapper.LabDashboardMapper;
import com.ruoyi.project.laboratory.service.ILabDashboardService;
import com.ruoyi.project.laboratory.util.LabRoleUtils;

/**
 * Laboratory dashboard service implementation.
 *
 * @author ruoyi
 */
@Service
public class LabDashboardServiceImpl implements ILabDashboardService
{
    @Autowired
    private LabDashboardMapper labDashboardMapper;

    @Override
    public Map<String, Object> selectSummary()
    {
        Long applicantId = dashboardApplicantId();
        Map<String, Object> data = new HashMap<String, Object>();
        // 资产口径是全局量（资产不属于任何申请人），不随角色过滤；
        // 报修口径跟随申请人，非全局可见角色只会看到自己提交的单据。
        data.put("assetTotal", labDashboardMapper.selectAssetTotal());
        data.put("repairingAssetTotal", labDashboardMapper.selectRepairingAssetTotal());
        data.put("pendingRepairTotal", labDashboardMapper.selectPendingRepairTotal(applicantId));
        data.put("finishedRepairTotal", labDashboardMapper.selectFinishedRepairTotal(applicantId));
        return data;
    }

    @Override
    public Map<String, Object> selectCharts()
    {
        Long applicantId = dashboardApplicantId();
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("repairTrend", labDashboardMapper.selectRepairTrend(applicantId));
        data.put("faultLevelDistribution", labDashboardMapper.selectFaultLevelDistribution(applicantId));
        data.put("roomRepairRanking", labDashboardMapper.selectRoomRepairRanking(applicantId));
        data.put("repairCostTrend", labDashboardMapper.selectRepairCostTrend(applicantId));
        return data;
    }

    /**
     * 看板数据范围：能查看全部实验室业务数据的角色不过滤申请人，其余只看自己的数据。
     * 口径与报修列表、详情完全一致，统一由 {@link LabRoleUtils} 判定。
     */
    private Long dashboardApplicantId()
    {
        return LabRoleUtils.canViewAll() ? null : SecurityUtils.getUserId();
    }
}
