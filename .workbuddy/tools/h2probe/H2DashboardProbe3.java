import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 探针 v3：验证 `CREATE ALIAS <名> FOR "类.静态方法"` 这种引用形式
 * （比 Java 源码内联形式更干净：不需要运行时 javac，产物就是普通测试类）。
 *
 * 同时验证 date_sub 的第二个参数声明成 String 时，
 * H2 会把 `interval 6 day` 这个 INTERVAL 值转成字符串 `INTERVAL '6' DAY` 传进来。
 */
public class H2DashboardProbe3
{
    /** 待测的垫片实现，形态与将来落进 src/test 的类一致。 */
    public static final class Shim
    {
        public static String dateFormat(java.sql.Timestamp t, String pattern)
        {
            if (t == null || pattern == null)
            {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < pattern.length(); i++)
            {
                char ch = pattern.charAt(i);
                if (ch != '%')
                {
                    sb.append(ch);
                    continue;
                }
                i++;
                if (i >= pattern.length())
                {
                    sb.append('%');
                    break;
                }
                char spec = pattern.charAt(i);
                if (spec == 'Y') { sb.append("yyyy"); }
                else if (spec == 'y') { sb.append("yy"); }
                else if (spec == 'm') { sb.append("MM"); }
                else if (spec == 'c') { sb.append("M"); }
                else if (spec == 'd') { sb.append("dd"); }
                else if (spec == 'e') { sb.append("d"); }
                else if (spec == 'H') { sb.append("HH"); }
                else if (spec == 'i') { sb.append("mm"); }
                else if (spec == 's') { sb.append("ss"); }
                else if (spec == '%') { sb.append('%'); }
                else
                {
                    throw new IllegalArgumentException("未实现的格式符 %" + spec);
                }
            }
            return new java.text.SimpleDateFormat(sb.toString()).format(t);
        }

        public static java.sql.Date dateSub(java.sql.Date base, String intervalLiteral)
        {
            if (base == null)
            {
                return null;
            }
            return new java.sql.Date(base.getTime() - parseDays(intervalLiteral) * 86400000L);
        }

        static int parseDays(String intervalLiteral)
        {
            if (intervalLiteral == null)
            {
                throw new IllegalArgumentException("interval 字面量为 null");
            }
            String s = intervalLiteral.replace("INTERVAL", "").replace("'", "").replace("\"", "").trim();
            String[] parts = s.split("\\s+");
            int days = Integer.parseInt(parts[0].trim());
            if (parts.length > 1 && !"day".equalsIgnoreCase(parts[1]))
            {
                throw new IllegalArgumentException("H2 方言垫片只实现了 DAY 单位，收到：" + intervalLiteral);
            }
            return days;
        }
    }

    public static void main(String[] args) throws Exception
    {
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:v3;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
                Statement st = c.createStatement())
        {
            System.out.println("H2 = " + c.getMetaData().getDatabaseProductVersion());
            System.out.println();

            run(st, "R1  create alias date_format FOR 类.方法",
                    "create alias if not exists date_format for \"H2DashboardProbe3$Shim.dateFormat\"");
            run(st, "R2  create alias date_sub FOR 类.方法",
                    "create alias if not exists date_sub for \"H2DashboardProbe3$Shim.dateSub\"");
            run(st, "R3  date_format(timestamp, '%m-%d')",
                    "select date_format(timestamp '2026-09-17 10:20:30', '%m-%d')");
            run(st, "R4  date_format(timestamp, '%Y-%m-%d %H:%i:%s')",
                    "select date_format(timestamp '2026-09-17 10:20:30', '%Y-%m-%d %H:%i:%s')");
            run(st, "R5  date_sub(curdate(), interval 6 day)  ← 生产原样写法",
                    "select date_sub(curdate(), interval 6 day)");

            run(st, "R6  建表 + 造数据（含一条 10 天前的、窗口外）",
                    "create table if not exists lab_repair (repair_id bigint auto_increment primary key, "
                            + "create_time datetime, del_flag char(1), fault_level char(1), repair_cost decimal(10,2), "
                            + "asset_id bigint, applicant_id bigint)");
            run(st, "R7  插入今天 / 3 天前 / 10 天前三条",
                    "insert into lab_repair (create_time, del_flag, fault_level, repair_cost, asset_id, applicant_id) "
                            + "values (now(), '0', '1', 100.00, 1, 100)");
            exec(st, "R8  插入 3 天前", "insert into lab_repair (create_time, del_flag, fault_level, repair_cost, asset_id, applicant_id) "
                    + "values (dateadd('day', -3, now()), '0', '2', 200.00, 1, 100)");
            exec(st, "R9  插入 10 天前（应被 7 天窗口剔除）",
                    "insert into lab_repair (create_time, del_flag, fault_level, repair_cost, asset_id, applicant_id) "
                            + "values (dateadd('day', -10, now()), '0', '3', 999.00, 1, 100)");

            run(st, "R10 趋势查询整条形状（生产 SQL 原样）",
                    "select date_format(create_time, '%m-%d') as name, count(1) as value from lab_repair "
                            + "where del_flag = '0' and create_time >= date_sub(curdate(), interval 6 day) "
                            + "group by date_format(create_time, '%m-%d') order by min(create_time)");
            run(st, "R11 成本趋势整条形状",
                    "select date_format(create_time, '%m-%d') as name, ifnull(sum(repair_cost), 0) as value "
                            + "from lab_repair where del_flag = '0' "
                            + "and create_time >= date_sub(curdate(), interval 6 day) "
                            + "group by date_format(create_time, '%m-%d') order by min(create_time)");
        }
        System.out.println("\n（期望：R10 只出现 2 行 09-17 / 09-14，10 天前那条被窗口剔除）");
    }

    private static void run(Statement st, String label, String sql)
    {
        try
        {
            boolean hasResultSet = st.execute(sql);
            if (!hasResultSet)
            {
                System.out.println("[ OK ] " + label + "  => (无结果集)");
                return;
            }
            try (ResultSet rs = st.getResultSet())
            {
                StringBuilder sb = new StringBuilder();
                int cols = rs.getMetaData().getColumnCount();
                while (rs.next())
                {
                    for (int i = 1; i <= cols; i++)
                    {
                        sb.append(rs.getObject(i)).append('|');
                    }
                    sb.append("  ");
                }
                System.out.println("[ OK ] " + label + "  => " + sb.toString().trim());
            }
        }
        catch (Exception e)
        {
            System.out.println("[FAIL] " + label + "  => " + e.getClass().getSimpleName() + ": " + firstLine(e.getMessage()));
        }
    }

    private static void exec(Statement st, String label, String sql)
    {
        try
        {
            st.execute(sql);
            System.out.println("[ OK ] " + label);
        }
        catch (Exception e)
        {
            System.out.println("[FAIL] " + label + "  => " + e.getClass().getSimpleName() + ": " + firstLine(e.getMessage()));
        }
    }

    private static String firstLine(String s)
    {
        if (s == null)
        {
            return "";
        }
        int i = s.indexOf('\n');
        return i < 0 ? s : s.substring(0, i);
    }
}
