package com.ruoyi.project.laboratory.constant;

/**
 * 资产履历事件。
 *
 * <p>每一次「资产台账发生变化」的业务动作都对应这里的一个常量。常量自己承载两样东西：
 * <ul>
 *   <li>{@code actionName} —— 写入 {@code lab_asset_record.record_type} 的动作名，前端履历
 *       时间线按它显示节点标题，取值与字典口径一致；</li>
 *   <li>{@code contentTemplate} —— 写入 {@code lab_asset_record.record_content} 的文案模板。</li>
 * </ul>
 *
 * <p>引入它是为了把原先散落在 {@code LabAssetServiceImpl} 6 个调用点上的「动作名 + 文案 +
 * 参数个数」三件事收口到一处：改措辞只改这里，新增一种履历事件只加一个常量，
 * 调用方（Service）零改动 —— 满足开闭原则（OCP）。
 *
 * <p>文案模板里没有字面量 {@code %}，可以安全地交给 {@link String#format}。
 *
 * @author ruoyi
 */
public enum LabAssetEvent
{
    /** 新增资产时写入的入库履历，参数为资产名称。 */
    INBOUND("入库", "资产入库：%s"),

    /**
     * 资产所属实验室调整。
     *
     * <p>注意：实验室名称（原实验室 → 新实验室）写在 {@code from_value} / {@code to_value}
     * 两列里，文案本身不带参数 —— 这是原有行为，不要为了"更好看"把名称拼进模板。
     */
    TRANSFER("调拨", "资产所属实验室调整"),

    /** 单条删除 / 报废。 */
    SCRAP("报废", "资产删除或报废"),

    /** 批量删除 / 报废。 */
    SCRAP_BATCH("报废", "资产批量删除或报废"),

    /** 资产基础资料（编号、名称、型号、价格、购置日期等）变更。 */
    INFO_UPDATE("资料修改", "资产基础资料更新"),

    /** 资产状态变为「正常」。 */
    ENABLE("启用", "资产状态变更"),

    /** 资产状态变为「停用」。 */
    DISABLE("停用", "资产状态变更"),

    /** 资产状态变为「维修中」。 */
    REPAIR("维修", "资产状态变更");

    /** 动作名，写入 {@code lab_asset_record.record_type}。 */
    private final String actionName;

    /** 文案模板，交给 {@link #content(String...)} 填充。 */
    private final String contentTemplate;

    LabAssetEvent(String actionName, String contentTemplate)
    {
        this.actionName = actionName;
        this.contentTemplate = contentTemplate;
    }

    /**
     * 按资产状态码取「状态变更」类事件。
     *
     * <p>这是「状态码 → 动作名」规则的<b>唯一来源</b>：{@code LabStatusUtils.assetRecordType}
     * 已改为委托本方法，字典口径与履历写入不会再各写一张表。
     *
     * <p>取值边界与原有实现完全一致 —— 停用 → {@link #DISABLE}，正常 → {@link #ENABLE}，
     * 其余（含维修中与未知状态码）→ {@link #REPAIR}。
     *
     * @param status 资产状态码，可为 null
     */
    public static LabAssetEvent ofAssetStatus(String status)
    {
        if (LabConstants.ASSET_STATUS_DISABLED.equals(status))
        {
            return DISABLE;
        }
        return LabConstants.ASSET_STATUS_NORMAL.equals(status) ? ENABLE : REPAIR;
    }

    /**
     * 动作名，写入履历表的 record_type 列。
     */
    public String actionName()
    {
        return actionName;
    }

    /**
     * 按模板生成履历文案。
     *
     * <p>参数由调用方负责兜底（例如空值先转成 {@code "-"}），本方法不做空值美化 ——
     * {@code String...} 收到 null 会输出字面量 "null"，那与页面展示口径不符。
     *
     * @param args 模板占位符的实参，按顺序传入
     */
    public String content(String... args)
    {
        return String.format(contentTemplate, (Object[]) args);
    }
}
