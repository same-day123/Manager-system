import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 探针 v2：专攻 `date_sub(curdate(), interval 6 day)` 这个写法在 H2 1.4.199 上
 * 能否被"测试侧同名自定义函数"吸收——卡点是第二个参数的类型是 INTERVAL
 * （v1 探针已证明：函数名找到了，但 INTERVAL -> INT 转换失败）。
 *
 * 每个场景用**独立的连接/独立的内存库**，避免别名互相污染。
 */
public class H2DashboardProbe2
{
    public static void main(String[] args) throws Exception
    {
        System.out.println("H2 = " + version());
        System.out.println();

        // 先单独看 INTERVAL 能不能转成字符串
        scenario("A. 基础：INTERVAL 能否 cast 成 VARCHAR",
                new String[] { "select cast(interval 6 day as varchar)" });

        scenario("B. 别名签名 (java.sql.Date, String) + 生产原样调用",
                new String[] {
                        "create alias date_sub as $$ java.sql.Date f(java.sql.Date d, String iv) "
                                + "{ return new java.sql.Date(d.getTime() - 6 * 86400000L); } $$",
                        "select date_sub(curdate(), interval 6 day)" });

        scenario("C. 别名签名 (java.sql.Date, Object) + 生产原样调用",
                new String[] {
                        "create alias date_sub as $$ java.sql.Date f(java.sql.Date d, Object iv) "
                                + "{ return new java.sql.Date(d.getTime() - 6 * 86400000L); } $$",
                        "select date_sub(curdate(), interval 6 day)" });

        scenario("D. 别名签名 (Object, Object) + 生产原样调用（最宽泛，看 H2 是否兜底匹配）",
                new String[] {
                        "create alias date_sub as $$ String f(Object d, Object iv) "
                                + "{ return d + \"|\" + iv; } $$",
                        "select date_sub(curdate(), interval 6 day)" });

        scenario("E. 别名签名 (java.sql.Date, int) + 生产原样调用（v1 的失败复现，作对照）",
                new String[] {
                        "create alias date_sub as $$ java.sql.Date f(java.sql.Date d, int n) "
                                + "{ return new java.sql.Date(d.getTime() - n * 86400000L); } $$",
                        "select date_sub(curdate(), interval 6 day)" });

        // 把两条真实挂掉的查询形状整条跑一遍（带 group by / order by）
        scenario("F. 趋势查询整条形状（date_format + date_sub + group by + order by）",
                new String[] {
                        "create table if not exists lab_repair (repair_id bigint auto_increment primary key, "
                                + "create_time datetime, del_flag char(1))",
                        "insert into lab_repair (create_time, del_flag) values (now(), '0')",
                        "create alias date_format as $$ String f(java.sql.Timestamp t, String p) "
                                + "{ return new java.text.SimpleDateFormat(p.replace(\"%m\",\"MM\").replace(\"%d\",\"dd\")).format(t); } $$",
                        "create alias date_sub as $$ java.sql.Date f(java.sql.Date d, String iv) "
                                + "{ return new java.sql.Date(d.getTime() - 6 * 86400000L); } $$",
                        "select date_format(create_time, '%m-%d') as name, count(1) as value from lab_repair "
                                + "where del_flag = '0' and create_time >= date_sub(curdate(), interval 6 day) "
                                + "group by date_format(create_time, '%m-%d') order by min(create_time)" });
    }

    private static void scenario(String label, String[] sqls)
    {
        System.out.println("== " + label);
        try (Connection c = DriverManager.getConnection(
                "jdbc:h2:mem:p" + Math.abs(label.hashCode()) + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
                Statement st = c.createStatement())
        {
            for (String sql : sqls)
            {
                String shortSql = sql.length() > 70 ? sql.substring(0, 70) + "..." : sql;
                try
                {
                    // DDL 与 DML 混在一起，必须用 execute()——用 executeQuery() 会在
                    // create alias / create table 上报 "Method is only allowed for a query"。
                    boolean hasResultSet = st.execute(sql);
                    if (!hasResultSet)
                    {
                        System.out.println("   [ OK ] " + shortSql + "  => (无结果集)");
                        continue;
                    }
                    try (ResultSet rs = st.getResultSet())
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
                        System.out.println("   [ OK ] " + shortSql + "  => " + sb.toString().trim());
                    }
                }
                catch (Exception e)
                {
                    System.out.println("   [FAIL] " + shortSql);
                    System.out.println("          " + e.getClass().getSimpleName() + ": "
                            + firstLine(e.getMessage()));
                }
            }
        }
        catch (Exception e)
        {
            System.out.println("   [FAIL] 连接/建库失败: " + e.getMessage());
        }
        System.out.println();
    }

    private static String version() throws Exception
    {
        try (Connection c = DriverManager.getConnection("jdbc:h2:mem:v;MODE=MySQL", "sa", ""))
        {
            return c.getMetaData().getDatabaseProductVersion();
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
