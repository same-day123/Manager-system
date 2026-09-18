package com.ruoyi.project.laboratory.support;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

/**
 * 集成测试专用「最小 Spring 上下文」入口。
 *
 * <p>为什么不直接把 {@code RuoYiApplication} 挂给 {@code @SpringBootTest}：
 * 那个入口会连带拉起 Quartz（JDBC JobStore 要连库建表并执行 DDL）、Druid 数据源、
 * SysConfig / SysDict 启动加载、Redis 连接池。测试环境里这些全都要真实中间件，
 * 跑一次几十秒还动不动失败。集成测试要验证的是「Service → Mapper → 数据库」这条
 * 链路与事务落库结果，不需要上面任何一样。
 *
 * <p>这里显式装配的是三样东西：
 * <ol>
 * <li>DataSource + MyBatis（由 {@code mybatis-spring-boot-starter} 的自动配置给出）；</li>
 * <li>{@code com.ruoyi.project.laboratory} 包下的 Service / Controller；</li>
 * <li>{@code com.ruoyi.project.laboratory.mapper} 下的 Mapper 接口。</li>
 * </ol>
 *
 * <p>被排除的三个自动配置各有原因：
 * <ul>
 * <li>{@link RedisAutoConfiguration} / {@link RedisRepositoriesAutoConfiguration}：
 * 本机测试不连 Redis，留着只会在启动时反复重连。</li>
 * <li>{@link SecurityAutoConfiguration}：它会注册一整套默认 HTTP Basic 过滤器链，
 * 对纯 Service 层测试毫无意义，还多一层干扰。注意——{@code SecurityContextHolder}
 * 属于 spring-security-core，<b>排除自动配置后依然可用</b>，T1 的
 * {@link LabTestSupport} 能原样复用。</li>
 * </ul>
 *
 * <h3>实测补充：还有两个自动配置没被排除，但它们是无害的</h3>
 *
 * <p>{@code @EnableAutoConfiguration} 只排除了上面三个，实测（`.workbuddy/logs/t2-run1.log`
 * 第 67~77 行）发现另有两条自动配置也进来了。<b>不要据此宣称「上下文里只有
 * DataSource + MyBatis」</b>——那与日志不符。二者的真实影响如下：
 *
 * <ul>
 * <li><b>Druid</b>（{@code DruidDataSourceAutoConfigure}，因为 {@code druid-spring-boot-starter}
 * 在 classpath 上）：它会创建一个 {@code DruidDataSource}，但 {@code spring.datasource.druid.url}
 * 并没有配置，于是回落到 {@code spring.datasource.url} —— 也就是
 * {@code application-integration.yml} 里那个 H2 内存库 URL。
 * 所以 Druid 只是把 H2 包了一层连接池，<b>数据仍然落在 H2 上</b>。
 * 日志里的 {@code Init DruidDataSource} / {@code {dataSource-1} inited} 就是它。</li>
 * <li><b>Quartz</b>（{@code QuartzAutoConfiguration}）：会建一个 {@code SchedulerFactoryBean}，
 * 默认 {@code spring.quartz.job-store-type=memory}，走 {@code RAMJobStore}
 * （日志：{@code RAMJobStore initialized.}），不建表、不连库。
 * 也就是说，任务卡担心的「Quartz JDBC JobStore 要连库建表」在这个上下文里<b>并不会发生</b>
 * —— 真正会把它切到 JDBC JobStore 的是 {@code com.ruoyi.framework.config.ScheduleConfig}，
 * 而它不在本入口的扫描范围内。</li>
 * </ul>
 *
 * <p>净效果：上下文启动约 3 秒，零外部中间件依赖。多出来的两个 Bean 是日志噪音，不是风险，
 * 因此本轮不为它们追加排除项——保持与任务卡 T2 第 2.2 节一致，避免为了「更干净」而偏离已定方案。
 *
 * <p>放在 {@code src/test} 下是刻意为之：{@code @SpringBootConfiguration} 只是给
 * {@code @SpringBootTest(classes = ...)} 提供一个配置入口，生产打包时 {@code src/test}
 * 不参与构建，对 main 的运行零影响。
 *
 * @author ruoyi
 */
@SpringBootConfiguration
@EnableAutoConfiguration(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        SecurityAutoConfiguration.class
})
@ComponentScan(basePackages = "com.ruoyi.project.laboratory")
@MapperScan("com.ruoyi.project.laboratory.mapper")
public class LabIntegrationTestApplication
{
}
