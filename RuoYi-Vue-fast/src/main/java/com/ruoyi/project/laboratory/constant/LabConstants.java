package com.ruoyi.project.laboratory.constant;

/**
 * 实验室模块公共常量。
 *
 * <p>原本这些字面量在 LabRoomServiceImpl、LabAssetServiceImpl、LabRepairServiceImpl
 * 里各定义了一份，容易改漏。现统一收敛到这里，三个 Service 只引用不再重复声明。
 *
 * <p>资产状态、报修状态的取值与字典表 `lab_asset_status`、`lab_repair_status`
 * 的 dict_value 必须保持一致。
 *
 * @author ruoyi
 */
public final class LabConstants
{
    /** 逻辑删除标记：未删除。 */
    public static final String DEL_FLAG_NORMAL = "0";

    /** 逻辑删除标记：已删除。 */
    public static final String DEL_FLAG_DELETED = "2";

    /** 实验室状态：正常。 */
    public static final String ROOM_STATUS_NORMAL = "0";

    /** 实验室状态：停用。 */
    public static final String ROOM_STATUS_DISABLED = "1";

    /** 资产状态：正常（可提交报修）。 */
    public static final String ASSET_STATUS_NORMAL = "0";

    /** 资产状态：停用。 */
    public static final String ASSET_STATUS_DISABLED = "1";

    /** 资产状态：维修中。 */
    public static final String ASSET_STATUS_REPAIRING = "2";

    /** 报修状态：待审核。 */
    public static final String REPAIR_STATUS_PENDING_REVIEW = "0";

    /** 报修状态：待维修。 */
    public static final String REPAIR_STATUS_PENDING_REPAIR = "1";

    /** 报修状态：维修中。 */
    public static final String REPAIR_STATUS_REPAIRING = "2";

    /** 报修状态：已完成。 */
    public static final String REPAIR_STATUS_FINISHED = "3";

    /** 报修状态：已拒绝。 */
    public static final String REPAIR_STATUS_REJECTED = "4";

    private LabConstants()
    {
    }
}
