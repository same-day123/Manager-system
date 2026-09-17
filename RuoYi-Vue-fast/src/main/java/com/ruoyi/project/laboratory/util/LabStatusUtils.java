package com.ruoyi.project.laboratory.util;

import com.ruoyi.project.laboratory.constant.LabAssetEvent;
import com.ruoyi.project.laboratory.constant.LabConstants;

/**
 * 实验室模块状态中文标签。
 *
 * <p>这些标签专门用于写入业务流水（`lab_repair_record` / `lab_asset_record`）的
 * 文本描述，让历史记录读起来是人话；页面展示仍走字典 `lab_repair_status` /
 * `lab_asset_status`，两边取值口径保持一致。
 *
 * <p>原先 {@code repairStatusLabel} 与 {@code assetStatusLabel} 分别散落在
 * LabRepairServiceImpl、LabAssetServiceImpl 内，现统一到这里。
 *
 * @author ruoyi
 */
public final class LabStatusUtils
{
    private LabStatusUtils()
    {
    }

    /**
     * 报修状态码转中文标签，未知状态原样返回。
     */
    public static String repairStatusLabel(String status)
    {
        if (LabConstants.REPAIR_STATUS_PENDING_REVIEW.equals(status))
        {
            return "待审核";
        }
        if (LabConstants.REPAIR_STATUS_PENDING_REPAIR.equals(status))
        {
            return "待维修";
        }
        if (LabConstants.REPAIR_STATUS_REPAIRING.equals(status))
        {
            return "维修中";
        }
        if (LabConstants.REPAIR_STATUS_FINISHED.equals(status))
        {
            return "已完成";
        }
        if (LabConstants.REPAIR_STATUS_REJECTED.equals(status))
        {
            return "已拒绝";
        }
        return status;
    }

    /**
     * 资产状态码转中文标签，未知状态原样返回。
     */
    public static String assetStatusLabel(String status)
    {
        if (LabConstants.ASSET_STATUS_NORMAL.equals(status))
        {
            return "正常";
        }
        if (LabConstants.ASSET_STATUS_DISABLED.equals(status))
        {
            return "停用";
        }
        if (LabConstants.ASSET_STATUS_REPAIRING.equals(status))
        {
            return "维修中";
        }
        return status;
    }

    /**
     * 资产状态变更时写入履历表的记录类型（字典口径的中文动作名）。
     *
     * <p>判定规则自 T5 起只存在于 {@link LabAssetEvent#ofAssetStatus(String)}，本方法退化为
     * 它的薄封装 —— 字典口径与履历写入共用同一张表，不会再出现"改了一处漏了另一处"。
     * 对外的取值与改造前完全一致：停用 → "停用"，正常 → "启用"，其余 → "维修"。
     *
     * @see LabAssetEvent#ofAssetStatus(String)
     */
    public static String assetRecordType(String status)
    {
        return LabAssetEvent.ofAssetStatus(status).actionName();
    }
}
