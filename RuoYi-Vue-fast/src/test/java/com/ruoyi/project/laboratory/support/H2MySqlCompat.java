package com.ruoyi.project.laboratory.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 把 {@link H2MySqlDateFuncs} 注册成 H2 的同名函数，让生产 mapper XML 里的
 * MySQL 专有写法能在测试库里跑起来。
 *
 * <p><b>这一段解决了什么</b>：偏移 D-19 原本的记录是「`LabDashboardMapper` 的看板查询
 * 在 H2 上跑不了，只能容忍」。实测后发现不必容忍——H2 支持
 * `create alias &lt;名&gt; for "&lt;全限定类名&gt;.&lt;静态方法&gt;"`，
 * 只要在测试库里注册同名函数，生产 SQL 一个字都不用改就能执行。
 * 于是看板从"未覆盖"变成"可覆盖"。
 *
 * <p><b>两条硬约束</b>：
 * <ol>
 * <li><b>绝不改生产 mapper XML</b>（T2 卡第 4 节明令）。所以垫片走"同名函数"这条缝，
 * 而不是改 SQL；改 SQL 会同时改动线上行为。</li>
 * <li><b>必须幂等</b>。别名挂在 H2 内存库上，而同一个内存库
 * （`DB_CLOSE_DELAY=-1`）会被整个测试 JVM 的所有用例类共用，所以安装动作会被反复触发。
 * 这里用 `create alias if not exists`，重复执行是廉价且无副作用的。</li>
 * </ol>
 *
 * <p><b>为什么用 `for "类.方法"` 而不是内联 Java 源码</b>：
 * 内联形式（`as $$ ... $$`）依赖 H2 的 SourceCompiler 在运行时调 javac，
 * 而且函数体里的 `;` 会被 Spring 的 SQL 脚本切分器截断。
 * `for "类.方法"` 形式引用的是编译好的普通测试类，既不依赖运行时编译器，
 * 也不受分号影响——实测两种形式都能用，取了更稳的那种。
 *
 * @author ruoyi
 */
public final class H2MySqlCompat
{
    /** 垫片类名，注册时拼成 `类名.方法名`。 */
    private static final String FUNCS = "com.ruoyi.project.laboratory.support.H2MySqlDateFuncs";

    /** 生产 `LabDashboardMapper.xml` 直接调用的两个 MySQL 函数名。 */
    public static final String MYSQL_DATE_FORMAT = "date_format";

    /** 见 {@link #MYSQL_DATE_FORMAT}。 */
    public static final String MYSQL_DATE_SUB = "date_sub";

    /** 只在第一次安装时置真，纯粹用于把"这次真的装了"这个事实暴露给用例断言。 */
    private static volatile boolean installedOnce = false;

    private H2MySqlCompat()
    {
    }

    /**
     * 安装（或确认已安装）两个同名函数。
     *
     * @return 本次调用是否**首次**完成安装
     */
    public static boolean installIfNeeded(JdbcTemplate jdbcTemplate)
    {
        boolean firstTime = !installedOnce;
        for (String ddl : aliasStatements())
        {
            jdbcTemplate.execute(ddl);
        }
        installedOnce = true;
        return firstTime;
    }

    /** 需执行的建别名语句，供用例直接断言"注册了哪两条"。 */
    public static List<String> aliasStatements()
    {
        List<String> statements = new ArrayList<String>();
        statements.add(alias(MYSQL_DATE_FORMAT, "dateFormat"));
        statements.add(alias(MYSQL_DATE_SUB, "dateSub"));
        return Collections.unmodifiableList(statements);
    }

    /**
     * 功能性自检：直接调用一次 {@code date_format}，返回结果。
     *
     * <p>刻意**不查 `information_schema`** 去确认"别名已注册"——那种元数据断言只能证明
     * 记录存在，证明不了函数真的能算对。功能探测更强，也不依赖 H2 元数据表的列名。
     */
    public static String probeDateFormat(JdbcTemplate jdbcTemplate)
    {
        return jdbcTemplate.queryForObject(
                "select date_format(timestamp '2026-01-02 03:04:05', '%m-%d')", String.class);
    }

    /** 功能性自检：{@code date_sub} 配合 MySQL 的 interval 字面量。 */
    public static java.sql.Date probeDateSub(JdbcTemplate jdbcTemplate)
    {
        return jdbcTemplate.queryForObject(
                "select date_sub(date '2026-01-10', interval 6 day)", java.sql.Date.class);
    }
    private static String alias(String functionName, String methodName)
    {
        // H2 的 FOR 子句要的是**双引号标识符**，用单引号会报
        // `Syntax error ... expected "identifier"`（.workbuddy/logs/h2-probe-dashboard3.txt 有记录）。
        return "create alias if not exists " + functionName + " for \"" + FUNCS + "." + methodName + "\"";
    }
}
