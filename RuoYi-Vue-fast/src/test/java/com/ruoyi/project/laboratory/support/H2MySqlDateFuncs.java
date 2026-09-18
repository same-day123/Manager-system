package com.ruoyi.project.laboratory.support;

/**
 * H2 方言垫片：把 MySQL 专有的两个日期函数在<b>测试侧</b>实现出来。
 *
 * <p><b>为什么需要它</b>：生产 `LabDashboardMapper.xml` 的「近 7 天趋势」两条查询用了
 * `date_format(create_time, '%m-%d')` 与 `date_sub(curdate(), interval 6 day)`，
 * 而 H2 1.4.199 <b>两个函数都没有</b>，导致看板在集成测试里跑不起来（偏移 D-19）。
 * 任务卡 T2 第 4 节明令**不许改生产 mapper XML 去迁就 H2**（那会改动线上行为），
 * 所以只能在测试侧补同名函数。
 *
 * <p><b>它是怎么被挂上去的</b>：H2 支持
 * `create alias &lt;名&gt; for "&lt;全限定类名&gt;.&lt;静态方法&gt;"`。
 * 安装动作在 {@link H2MySqlCompat} 里，本类只负责"函数体"。
 *
 * <p><b>⭐ 关键实现细节（踩过才知道）</b>：{@link #dateSub} 的第二个参数<b>必须声明成
 * {@code String}</b>。因为生产 SQL 传进来的是 MySQL 的 interval 字面量
 * `interval 6 day`，H2 会先把它解析成 `INTERVAL '6' DAY`，再往函数参数上转。
 * 实测结论（`.workbuddy/logs/h2-probe-dashboard2.txt`）：
 * <ul>
 * <li>声明成 {@code int} → `Data conversion error converting "INTERVAL '6' DAY"`；</li>
 * <li>声明成 {@code Object} → `Hexadecimal string contains non-hex character`；</li>
 * <li>声明成 {@code String} → <b>成功</b>，H2 会把 `INTERVAL '6' DAY` 原样当字符串传进来。</li>
 * </ul>
 * 所以这里按字符串解析，并且<b>解析不出来就抛异常</b>——宁可让用例红灯，
 * 也不能悄悄返回一个错误的日期把看板断言变成假绿灯。
 *
 * <p><b>语义对齐</b>：只实现生产 SQL 实际用到的语义，多余的格式符一律显式报错。
 *
 * <p><b>边界声明</b>：这是**测试专用**方言垫片，不参与生产打包（在 `src/test` 下）。
 * 它的存在不改变"生产 SQL 以 MySQL 为准"这一事实；真实 MySQL 上的行为不受任何影响。
 *
 * @author ruoyi
 */
public final class H2MySqlDateFuncs
{
    private H2MySqlDateFuncs()
    {
    }

    /**
     * MySQL {@code DATE_FORMAT(datetime, pattern)} 的等价实现。
     *
     * <p>只翻译生产 SQL 用到的格式符：{@code %Y %y %m %c %d %e %H %i %s %%}。
     * 出现别的格式符直接抛异常，避免"悄悄输出错字符串"。
     */
    public static String dateFormat(java.sql.Timestamp time, String pattern)
    {
        if (time == null || pattern == null)
        {
            return null;
        }
        StringBuilder javaPattern = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++)
        {
            char ch = pattern.charAt(i);
            if (ch != '%')
            {
                javaPattern.append(ch);
                continue;
            }
            i++;
            if (i >= pattern.length())
            {
                javaPattern.append('%');
                break;
            }
            char spec = pattern.charAt(i);
            if (spec == 'Y')
            {
                javaPattern.append("yyyy");
            }
            else if (spec == 'y')
            {
                javaPattern.append("yy");
            }
            else if (spec == 'm')
            {
                javaPattern.append("MM");
            }
            else if (spec == 'c')
            {
                javaPattern.append("M");
            }
            else if (spec == 'd')
            {
                javaPattern.append("dd");
            }
            else if (spec == 'e')
            {
                javaPattern.append("d");
            }
            else if (spec == 'H')
            {
                javaPattern.append("HH");
            }
            else if (spec == 'i')
            {
                javaPattern.append("mm");
            }
            else if (spec == 's')
            {
                javaPattern.append("ss");
            }
            else if (spec == '%')
            {
                javaPattern.append('%');
            }
            else
            {
                throw new IllegalArgumentException(
                        "H2 方言垫片未实现该 MySQL 日期格式符：%" + spec + "（原 pattern=" + pattern + "）");
            }
        }
        return new java.text.SimpleDateFormat(javaPattern.toString()).format(time);
    }

    /**
     * MySQL {@code DATE_SUB(date, interval N day)} 的等价实现（向前推 N 天）。
     *
     * <p>第二个参数由 H2 以字符串形式传入，形如 {@code INTERVAL '6' DAY}。
     */
    public static java.sql.Date dateSub(java.sql.Date base, String intervalLiteral)
    {
        if (base == null)
        {
            return null;
        }
        return new java.sql.Date(base.getTime() - parseDays(intervalLiteral) * 86400000L);
    }

    /**
     * 从 {@code INTERVAL '6' DAY} 这类字面量里取出天数。
     *
     * <p><b>只支持 DAY 单位</b>：生产 SQL 只用了 `interval 6 day`，用别的单位说明
     * 生产 SQL 变了、这个垫片需要同步升级——此时抛异常比默默算错要好。
     */
    static int parseDays(String intervalLiteral)
    {
        if (intervalLiteral == null)
        {
            throw new IllegalArgumentException("interval 字面量为 null，无法解析");
        }
        String normalized = intervalLiteral
                .replace("INTERVAL", "")
                .replace("'", "")
                .replace("\"", "")
                .trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty())
        {
            throw new IllegalArgumentException("interval 字面量格式不认识：" + intervalLiteral);
        }
        int days;
        try
        {
            days = Integer.parseInt(parts[0].trim());
        }
        catch (NumberFormatException e)
        {
            throw new IllegalArgumentException("interval 字面量里的数字解析失败：" + intervalLiteral, e);
        }
        if (parts.length > 1 && !"day".equalsIgnoreCase(parts[1]))
        {
            throw new IllegalArgumentException(
                    "H2 方言垫片只实现了 DAY 单位，收到：" + intervalLiteral
                            + "（生产 SQL 若改用别的单位，请同步升级本垫片）");
        }
        return days;
    }
}
