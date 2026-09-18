import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * H2 1.4.199 看板方言探针：判断 LabDashboardMapper.xml 里用到的 MySQL 专有写法
 * （date_format / date_sub + interval 语法）能否在测试侧被"同名自定义函数"补齐。
 *
 * 只读探测，不改任何生产代码。输出直接打 stdout。
 */
public class H2DashboardProbe
{
    private static Connection conn;

    public static void main(String[] args) throws Exception
    {
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:probe;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        System.out.println("H2 version = " + conn.getMetaData().getDatabaseProductVersion());
        System.out.println();

        exec("P1  curdate()", "select curdate()");
        exec("P2  date_sub(curdate(), interval 6 day)  ← 生产 SQL 的原样写法",
                "select date_sub(curdate(), interval 6 day)");
        exec("P3  单独 interval 6 day 作为表达式",
                "select interval 6 day");
        exec("P4  dateadd('day', -6, curdate())  ← H2 原生等价写法",
                "select dateadd('day', -6, curdate())");

        // ---- 自定义函数：能否注册同名 date_format / date_sub ----
        define("C1  create alias date_format (Java 源码形式)",
                "create alias if not exists date_format as $$ "
                        + "String dateFormat(java.sql.Timestamp t, String p) { "
                        + "if (t == null) { return null; } "
                        + "String pat = p.replace(\"%m\", \"MM\").replace(\"%d\", \"dd\"); "
                        + "return new java.text.SimpleDateFormat(pat).format(t); } $$");
        exec("C2  select date_format(timestamp '2026-09-17 10:20:30', '%m-%d')  ← 验证别名可用",
                "select date_format(timestamp '2026-09-17 10:20:30', '%m-%d')");
        exec("C3  别名生效后再试一次生产原样写法（关键：看是不是语法层就过不去）",
                "select date_sub(curdate(), interval 6 day)");

        define("C4  create alias date_sub (Java 源码形式)",
                "create alias if not exists date_sub as $$ "
                        + "java.sql.Date dateSub(java.sql.Date d, int n) { "
                        + "if (d == null) { return null; } "
                        + "return new java.sql.Date(d.getTime() - n * 86400000L); } $$");
        exec("C5  date_sub(curdate(), 6)  ← 去掉 interval 语法、只留函数名",
                "select date_sub(curdate(), 6)");
        exec("C6  date_sub(curdate(), interval 6 day)  ← 带 interval 语法",
                "select date_sub(curdate(), interval 6 day)");

        conn.close();
    }

    private static void exec(String label, String sql)
    {
        try (Statement st = conn.createStatement())
        {
            try (ResultSet rs = st.executeQuery(sql))
            {
                StringBuilder sb = new StringBuilder();
                int cols = rs.getMetaData().getColumnCount();
                while (rs.next())
                {
                    for (int i = 1; i <= cols; i++)
                    {
                        sb.append(rs.getObject(i)).append(' ');
                    }
                }
                System.out.println("[ OK ] " + label + "  => " + sb.toString().trim());
            }
        }
        catch (Exception e)
        {
            System.out.println("[FAIL] " + label + "  => " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void define(String label, String sql)
    {
        try (Statement st = conn.createStatement())
        {
            st.execute(sql);
            System.out.println("[ OK ] " + label);
        }
        catch (Exception e)
        {
            System.out.println("[FAIL] " + label + "  => " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
