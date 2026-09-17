package com.ruoyi.project.laboratory.constant;

/**
 * 报修履历事件。
 *
 * <p>每一次「报修单发生变化」的业务动作都对应这里的一个常量。与 {@link LabAssetEvent}
 * 同构，常量自己承载动作名（写入 {@code lab_repair_record.action_name}）与文案模板
 * （写入 {@code lab_repair_record.record_content}）。
 *
 * <p>引入它是为了把原先散落在 {@code LabRepairServiceImpl} 上的动作名与文案收口到一处，
 * 让 Service 只声明「发生了什么事件」，不再自己拼字符串。
 *
 * <p>文案模板里没有字面量 {@code %}，可以安全地交给 {@link String#format}。
 *
 * @author ruoyi
 */
public enum LabRepairEvent
{
    /** 提交报修，参数为故障等级与故障描述。 */
    SUBMIT("提交报修", "故障等级：%s；%s"),

    /**
     * 状态流转，参数为流转前后的中文状态标签。
     *
     * <p><b>模板里的引号是全角 {@code “ ”}（U+201C / U+201D），不是英文直引号</b>，
     * 与改造前的履历文案逐字一致。这类字符肉眼几乎看不出差别，修改时务必整段复制，
     * 不要手打 —— 改错了单元测试未必报警，但生产环境的履历文案会变形。
     */
    STATUS_CHANGE("状态流转", "报修状态由“%s”变更为“%s”"),

    /** 非处理角色（提交人）修改本人在待审核状态的报修单信息。 */
    INFO_UPDATE("报修信息更新", "报修单信息已更新"),

    /** 处理角色（管理/维修）修改报修单信息，状态未发生变化。 */
    REPAIR_UPDATE("维修信息更新", "报修单信息已更新"),

    /** 提交人在报修完成后评分，参数为评分与评价内容。 */
    EVALUATE("维修评价", "评分：%s；%s");

    /** 动作名，写入 {@code lab_repair_record.action_name}。 */
    private final String actionName;

    /** 文案模板，交给 {@link #content(String...)} 填充。 */
    private final String contentTemplate;

    LabRepairEvent(String actionName, String contentTemplate)
    {
        this.actionName = actionName;
        this.contentTemplate = contentTemplate;
    }

    /**
     * 动作名，写入履历表的 action_name 列。
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
