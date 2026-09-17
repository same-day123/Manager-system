package com.ruoyi.project.laboratory.constant;

import com.ruoyi.project.laboratory.domain.LabAssetRecord;
import com.ruoyi.project.laboratory.domain.LabRepairRecord;
import com.ruoyi.project.laboratory.util.LabSecurityUtils;

/**
 * 履历记录工厂（简单工厂 / Simple Factory，创建型模式）。
 *
 * <p><b>为什么需要它。</b>资产履历（{@code lab_asset_record}）与报修履历
 * （{@code lab_repair_record}）原先各自在 Service 里手工 {@code new} 对象、手工指定
 * 动作名、手工拼文案、手工兜底操作人，同样的四件事在 10 个调用点上重复了 10 遍。
 * 后果有三个：措辞要改就得改多处（容易漏）、新增一种履历事件必须改 Service
 * （违反 OCP）、操作人兜底漏写一处就产出操作人为空的脏履历。
 *
 * <p><b>本类做什么。</b>把「一次业务事件 → 一条履历对象」的组装规则集中到两个静态方法里：
 * <ol>
 *   <li>关联主键（assetId / repairId）；</li>
 *   <li>动作名与文案 —— 都来自事件枚举，本类不自己拼字符串；</li>
 *   <li>流转前值 / 流转后值；</li>
 *   <li>操作人兜底 —— 交给 {@link LabSecurityUtils#operatorName(String)}，
 *       并把同一个值同时写进 {@code operator_name} 与 {@code create_by}（原有行为）。</li>
 * </ol>
 *
 * <p><b>边界。</b>本类是无状态纯静态工具类：没有 Spring 注解、不持有 Mapper、不写库、
 * 不加缓存、不打日志。它只负责「把事件组装成对象」，落库仍由调用方的 Mapper 完成 ——
 * 这样单元测试不需要 Spring 上下文即可直接构造对象并断言。
 *
 * @author ruoyi
 */
public final class LabRecordFactory
{
    private LabRecordFactory()
    {
    }

    /**
     * 创建一条资产履历记录。
     *
     * @param assetId     所属资产 ID
     * @param event       发生的资产履历事件，决定动作名与文案模板
     * @param fromValue   变更前值，可为 null
     * @param toValue     变更后值，可为 null
     * @param operator    调用方已知的操作人账号，可为 null（为空时由
     *                    {@link LabSecurityUtils#operatorName(String)} 兜底）
     * @param contentArgs 文案模板的实参，按顺序传入；无占位符时可不传
     */
    public static LabAssetRecord assetRecord(Long assetId, LabAssetEvent event, String fromValue,
            String toValue, String operator, String... contentArgs)
    {
        String operatorName = LabSecurityUtils.operatorName(operator);
        LabAssetRecord record = new LabAssetRecord();
        record.setAssetId(assetId);
        record.setRecordType(event.actionName());
        record.setFromValue(fromValue);
        record.setToValue(toValue);
        record.setOperatorName(operatorName);
        record.setRecordContent(event.content(contentArgs));
        record.setCreateBy(operatorName);
        return record;
    }

    /**
     * 创建一条报修履历记录。
     *
     * @param repairId    所属报修单 ID
     * @param event       发生的报修履历事件，决定动作名与文案模板
     * @param fromStatus  流转前状态码，可为 null
     * @param toStatus    流转后状态码，可为 null
     * @param operator    调用方已知的操作人账号，可为 null（为空时由
     *                    {@link LabSecurityUtils#operatorName(String)} 兜底）
     * @param contentArgs 文案模板的实参，按顺序传入；无占位符时可不传
     */
    public static LabRepairRecord repairRecord(Long repairId, LabRepairEvent event, String fromStatus,
            String toStatus, String operator, String... contentArgs)
    {
        String operatorName = LabSecurityUtils.operatorName(operator);
        LabRepairRecord record = new LabRepairRecord();
        record.setRepairId(repairId);
        record.setActionName(event.actionName());
        record.setFromStatus(fromStatus);
        record.setToStatus(toStatus);
        record.setOperatorName(operatorName);
        record.setRecordContent(event.content(contentArgs));
        record.setCreateBy(operatorName);
        return record;
    }
}
