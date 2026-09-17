import com.ruoyi.project.laboratory.constant.LabAssetEvent;
import com.ruoyi.project.laboratory.constant.LabRecordFactory;
import com.ruoyi.project.laboratory.constant.LabRepairEvent;
import com.ruoyi.project.laboratory.domain.LabAssetRecord;
import com.ruoyi.project.laboratory.domain.LabRepairRecord;
import com.ruoyi.project.laboratory.util.LabStatusUtils;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * T5 动态验证探针（脱离 Spring / 无需数据库 / 无需登录上下文）。
 *
 * <p>用途：直接调用事件枚举与履历工厂，把「改造后」的真实产物与「改造前」的
 * 字符串拼接结果逐字比对，覆盖全部 12 个事件常量与 10 条文案模板。
 *
 * <p>为什么需要它：单元测试用 Mockito 只断言"Mapper 被调用了"，集成测试只覆盖到
 * 其中 4 个事件；其余的文案一致性在运行期没有被真正渲染过。本探针把它们全部渲染一遍。
 *
 * <p>编译运行（在仓库根）：
 * <pre>
 * javac -encoding UTF-8 -cp RuoYi-Vue-fast/target/classes -d .workbuddy/logs/t5probe .workbuddy/tools/T5EventProbe.java
 * java -cp "RuoYi-Vue-fast/target/classes;.workbuddy/logs/t5probe" T5EventProbe <输出文件>
 * </pre>
 */
public class T5EventProbe
{
    private static int pass = 0;

    private static int fail = 0;

    private static PrintWriter out;

    public static void main(String[] args) throws Exception
    {
        String target = args.length > 0 ? args[0] : "t5_probe_result.txt";
        out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(target), Charset.forName("UTF-8")));
        emit("=== T5 动态验证：事件枚举 + 履历工厂（无 Spring / 无数据库）===");
        emit("Java: " + System.getProperty("java.version"));
        emit("");

        // ---------- 1. 资产履历事件：动作名 ----------
        emit("--- 1. LabAssetEvent 动作名 ---");
        action("INBOUND", LabAssetEvent.INBOUND, "入库");
        action("TRANSFER", LabAssetEvent.TRANSFER, "调拨");
        action("SCRAP", LabAssetEvent.SCRAP, "报废");
        action("SCRAP_BATCH", LabAssetEvent.SCRAP_BATCH, "报废");
        action("INFO_UPDATE", LabAssetEvent.INFO_UPDATE, "资料修改");
        action("ENABLE", LabAssetEvent.ENABLE, "启用");
        action("DISABLE", LabAssetEvent.DISABLE, "停用");
        action("REPAIR", LabAssetEvent.REPAIR, "维修");

        // ---------- 2. 资产履历事件：文案（与改造前的拼接结果比对） ----------
        emit("");
        emit("--- 2. LabAssetEvent 文案（对比「改造前的拼接写法」）---");
        eq("INBOUND", LabAssetEvent.INBOUND.content("数字万用表"), "资产入库：" + "数字万用表");
        eq("TRANSFER", LabAssetEvent.TRANSFER.content(), "资产所属实验室调整");
        eq("SCRAP", LabAssetEvent.SCRAP.content(), "资产删除或报废");
        eq("SCRAP_BATCH", LabAssetEvent.SCRAP_BATCH.content(), "资产批量删除或报废");
        eq("INFO_UPDATE", LabAssetEvent.INFO_UPDATE.content(), "资产基础资料更新");
        eq("ENABLE", LabAssetEvent.ENABLE.content(), "资产状态变更");
        eq("DISABLE", LabAssetEvent.DISABLE.content(), "资产状态变更");
        eq("REPAIR", LabAssetEvent.REPAIR.content(), "资产状态变更");

        // ---------- 3. 报修履历事件 ----------
        emit("");
        emit("--- 3. LabRepairEvent 动作名 + 文案 ---");
        action("SUBMIT", LabRepairEvent.SUBMIT, "提交报修");
        action("STATUS_CHANGE", LabRepairEvent.STATUS_CHANGE, "状态流转");
        action("INFO_UPDATE", LabRepairEvent.INFO_UPDATE, "报修信息更新");
        action("REPAIR_UPDATE", LabRepairEvent.REPAIR_UPDATE, "维修信息更新");
        action("EVALUATE", LabRepairEvent.EVALUATE, "维修评价");
        eq("SUBMIT.content", LabRepairEvent.SUBMIT.content("2", "屏幕全黑"),
                "故障等级：" + "2" + "；" + "屏幕全黑");
        eq("STATUS_CHANGE.content", LabRepairEvent.STATUS_CHANGE.content("待审核", "已拒绝"),
                "报修状态由“" + "待审核" + "”变更为“" + "已拒绝" + "”");
        eq("INFO_UPDATE.content", LabRepairEvent.INFO_UPDATE.content(), "报修单信息已更新");
        eq("REPAIR_UPDATE.content", LabRepairEvent.REPAIR_UPDATE.content(), "报修单信息已更新");
        eq("EVALUATE.content", LabRepairEvent.EVALUATE.content("5", "很及时"),
                "评分：" + "5" + "；" + "很及时");

        // ---------- 4. 全角字符逐字节确认 ----------
        emit("");
        emit("--- 4. 全角引号 / 分号 逐字节确认 ---");
        String t = LabRepairEvent.STATUS_CHANGE.content("X", "Y");
        // 报修状态由“X”变更为“Y”  → 下标：由=4 “=5 X=6 ”=7
        intEq("全角左引号 U+201C 位置", t.indexOf('\u201C'), 5);
        intEq("全角右引号 U+201D 位置（紧跟在 X 后）", t.indexOf('\u201D'), 7);
        intEq("全角左引号第二次出现（变更为 之后）", t.lastIndexOf('\u201C'), 11);
        intEq("全角右引号第二次出现（结尾）", t.lastIndexOf('\u201D'), 13);
        String s = LabRepairEvent.SUBMIT.content("1", "d");
        // 故障等级：1；d  → 下标：：=4 1=5 ；=6
        intEq("全角分号 U+FF1B 位置", s.indexOf('\uFF1B'), 6);
        intEq("模板里不得出现英文直引号", t.indexOf('"'), -1);

        // ---------- 5. 状态码 → 事件（唯一来源） ----------
        emit("");
        emit("--- 5. LabAssetEvent.ofAssetStatus + LabStatusUtils.assetRecordType（同一张表）---");
        resolve("0 正常", "0", LabAssetEvent.ENABLE, "启用");
        resolve("1 停用", "1", LabAssetEvent.DISABLE, "停用");
        resolve("2 维修中", "2", LabAssetEvent.REPAIR, "维修");
        resolve("null", null, LabAssetEvent.REPAIR, "维修");
        resolve("9 未知", "9", LabAssetEvent.REPAIR, "维修");

        // ---------- 6. 工厂组装契约 ----------
        emit("");
        emit("--- 6. LabRecordFactory 组装契约（无登录上下文 → 操作人回落 system）---");
        LabAssetRecord ar = LabRecordFactory.assetRecord(7L, LabAssetEvent.INBOUND, null, "ZC-9001",
                null, "数字万用表");
        eq("资产 record.assetId", String.valueOf(ar.getAssetId()), "7");
        eq("资产 record.recordType", ar.getRecordType(), "入库");
        eq("资产 record.fromValue(应为 null)", String.valueOf(ar.getFromValue()), "null");
        eq("资产 record.toValue", ar.getToValue(), "ZC-9001");
        eq("资产 record.recordContent", ar.getRecordContent(), "资产入库：数字万用表");
        eq("资产 record.operatorName", ar.getOperatorName(), "system");
        eq("资产 record.createBy == operatorName", ar.getCreateBy(), ar.getOperatorName());

        LabAssetRecord sr = LabRecordFactory.assetRecord(9L, LabAssetEvent.TRANSFER, "信工楼 A301",
                "信工楼 B204", "labadmin");
        eq("调拨 fromValue", sr.getFromValue(), "信工楼 A301");
        eq("调拨 toValue", sr.getToValue(), "信工楼 B204");
        eq("调拨 recordContent（无占位符，名称在 from/to 列）", sr.getRecordContent(), "资产所属实验室调整");
        eq("调拨 operatorName 用传入账号", sr.getOperatorName(), "labadmin");

        LabRepairRecord rr = LabRecordFactory.repairRecord(49L, LabRepairEvent.STATUS_CHANGE, "0", "4",
                null, "待审核", "已拒绝");
        eq("报修 record.repairId", String.valueOf(rr.getRepairId()), "49");
        eq("报修 record.actionName", rr.getActionName(), "状态流转");
        eq("报修 record.fromStatus", rr.getFromStatus(), "0");
        eq("报修 record.toStatus", rr.getToStatus(), "4");
        eq("报修 record.recordContent", rr.getRecordContent(), "报修状态由“待审核”变更为“已拒绝”");
        eq("报修 record.operatorName", rr.getOperatorName(), "system");
        eq("报修 record.createBy == operatorName", rr.getCreateBy(), rr.getOperatorName());

        // 无 contentArgs 的事件（与改造前「整条字面量」等价）
        LabRepairRecord rr2 = LabRecordFactory.repairRecord(50L, LabRepairEvent.REPAIR_UPDATE, "2", "2",
                "repair01");
        eq("维修信息更新 recordContent", rr2.getRecordContent(), "报修单信息已更新");
        eq("维修信息更新 operatorName", rr2.getOperatorName(), "repair01");

        emit("");
        emit("=== 结论：" + (fail == 0 ? "PASS" : "FAIL") + " （通过 " + pass + " 项，失败 " + fail + " 项）===");
        out.flush();
        out.close();
        System.out.println("probe -> " + target + " : pass=" + pass + " fail=" + fail);
        if (fail > 0)
        {
            System.exit(1);
        }
    }

    private static void emit(String s)
    {
        out.println(s);
    }

    private static void action(String name, LabAssetEvent e, String expected)
    {
        eq(name + ".actionName", e.actionName(), expected);
    }

    private static void action(String name, LabRepairEvent e, String expected)
    {
        eq(name + ".actionName", e.actionName(), expected);
    }

    private static void resolve(String label, String status, LabAssetEvent expectedEvent, String expectedAction)
    {
        LabAssetEvent got = LabAssetEvent.ofAssetStatus(status);
        eq("ofAssetStatus(" + label + ")", got.name(), expectedEvent.name());
        eq("assetRecordType(" + label + ") —— 与 ofAssetStatus 同源",
                LabStatusUtils.assetRecordType(status), expectedAction);
    }

    private static void intEq(String name, int actual, int expected)
    {
        report(actual == expected, name, String.valueOf(expected), String.valueOf(actual));
    }

    private static void eq(String name, String actual, String expected)
    {
        report(expected.equals(actual), name, expected, actual);
    }

    private static void report(boolean ok, String name, String expected, String actual)
    {
        if (ok)
        {
            pass++;
            emit("[OK]   " + name + " = " + actual);
        }
        else
        {
            fail++;
            emit("[FAIL] " + name + " | expected=[" + expected + "] actual=[" + actual + "]");
        }
    }
}
