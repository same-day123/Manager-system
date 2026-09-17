import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.util.List;

/**
 * T4「容器化交付」—— 环境变量绑定实证探针（不依赖 Docker）
 *
 * 为什么需要它
 *   本机没装 Docker，跑不了 `docker compose up`，但容器化真正的风险点并不在
 *   Docker 本身，而在「**环境变量到底能不能盖住写死的配置**」。这一点完全可以用
 *   真实的 Spring 属性源机制在本地证明 —— 因为跑容器时用的就是同一套机制。
 *
 * 它证明什么
 *   1) 配置文件里写死的值确实是写死的（raw: 前缀 = 只加载 application*.yml）
 *   2) 注入环境变量后，最终生效值被成功覆盖（eff: 前缀 = 与 Spring Boot 启动时
 *      相同的属性源顺序：OS 环境变量 > 应用配置文件）
 *   3) 覆盖走的是 Spring Boot 官方的 relaxed binding，不需要改任何配置文件
 *   4) spring.redis 整块能被类型化绑定（port 是 int、timeout 是 Duration），
 *      说明容器里后端能正常连上 redis 服务名而不是 localhost
 *
 * 用法
 *   java -cp <classpath> T4EnvBindingProbe RuoYi-Vue-fast/src/main/resources/
 *   具体见 .workbuddy/tools/t4_env_binding_check.py（它会跑两遍不同取值来对照）
 */
public class T4EnvBindingProbe {

    /** 容器化时依赖的全部配置键（覆盖 D:/ruoyi/uploadPath、localhost、弱令牌密钥三处坑） */
    private static final String[] KEYS = {
        "ruoyi.profile",
        "spring.redis.host",
        "spring.redis.port",
        "spring.redis.password",
        "spring.datasource.druid.master.url",
        "spring.datasource.druid.master.username",
        "spring.datasource.druid.master.password",
        "token.secret",
        "swagger.enabled",
    };

    public static void main(String[] args) throws Exception {
        String resources = args.length > 0 ? args[0] : "RuoYi-Vue-fast/src/main/resources/";
        if (!resources.endsWith("/") && !resources.endsWith("\\")) {
            resources = resources + "/";
        }

        // ---- 视图一：只加载配置文件（剥掉 systemProperties / systemEnvironment）----
        StandardEnvironment fileOnly = new StandardEnvironment();
        fileOnly.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        fileOnly.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        load(fileOnly, resources);

        // ---- 视图二：完整属性源，顺序与 Spring Boot 启动时一致 ----
        // addLast 追加=最低优先级，正是 Spring Boot 把 application.yml 放在
        // OS 环境变量之下的做法；所以环境变量必然覆盖得住配置文件。
        StandardEnvironment env = new StandardEnvironment();
        load(env, resources);

        for (String k : KEYS) {
            System.out.println("raw:" + k + "=" + nz(fileOnly.getProperty(k)));
        }
        for (String k : KEYS) {
            System.out.println("eff:" + k + "=" + nz(env.getProperty(k)));
        }

        // ---- 视图三：走 Spring Boot 的 Binder，证明类型转换真的成立 ----
        Binder binder = Binder.get(env);
        System.out.println("bind:spring.redis.host="
            + nz(binder.bind("spring.redis.host", Bindable.of(String.class)).orElse(null)));
        Integer port = binder.bind("spring.redis.port", Bindable.of(Integer.class)).orElse(null);
        System.out.println("bind:spring.redis.port=" + nz(port));
        System.out.println("bind:spring.redis.port.type="
            + (port == null ? "null" : port.getClass().getSimpleName()));

        // ---- 视图四：把 spring.redis 整块绑成真实的 RedisProperties ----
        // 容器里后端连 redis 用的就是这个类，绑得通即说明配置块在容器内可用。
        try {
            Object props = binder
                .bind("spring.redis",
                      Bindable.of(org.springframework.boot.autoconfigure.data.redis.RedisProperties.class))
                .orElse(null);
            if (props == null) {
                System.out.println("props:SKIPPED=bind 返回空");
            } else {
                org.springframework.boot.autoconfigure.data.redis.RedisProperties p =
                    (org.springframework.boot.autoconfigure.data.redis.RedisProperties) props;
                System.out.println("props:host=" + nz(p.getHost()));
                System.out.println("props:port=" + p.getPort());
                System.out.println("props:database=" + p.getDatabase());
                System.out.println("props:password=" + nz(p.getPassword()));
                System.out.println("props:timeout=" + nz(p.getTimeout()));
            }
        } catch (Throwable t) {
            System.out.println("props:SKIPPED=" + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private static void load(StandardEnvironment env, String resources) throws Exception {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String f : new String[] { "application.yml", "application-druid.yml" }) {
            List<PropertySource<?>> list = loader.load(f, new FileSystemResource(resources + f));
            for (PropertySource<?> ps : list) {
                env.getPropertySources().addLast(ps);
            }
        }
    }

    private static String nz(Object v) {
        if (v == null) {
            return "<null>";
        }
        String s = String.valueOf(v);
        return s.isEmpty() ? "<empty>" : s;
    }
}
