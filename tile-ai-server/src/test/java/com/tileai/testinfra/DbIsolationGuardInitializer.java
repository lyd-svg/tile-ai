package com.tileai.testinfra;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * 测试的数据库隔离护栏:**连的不是测试库就当场失败**。
 *
 * <h2>为什么需要它</h2>
 *
 * 2026-10-07 发生过一次真实的数据事故:集成测试连的是**用户正在使用的演示库
 * {@code tile_ai}**,其中若干测试做无条件全表删除(删 {@code t_store} 全表、
 * 删 {@code t_quote_param} 的关键行),跑一次全量测试就把用户的真实数据删掉了。
 *
 * 现在的第一道防线在 {@code pom.xml} 的 surefire 配置里 —— 它把测试子进程的
 * {@code DB_URL} / {@code SPRING_DATASOURCE_URL} 等钉到 {@code tile_ai_test},
 * 并改用一个**只对 tile_ai_test 有权限**的受限账号。
 *
 * <h2>这道护栏补的是第一道防线的一个洞</h2>
 *
 * surefire 的 {@code environmentVariables} **只对 fork 出来的测试进程生效**。
 * 两种情况下它不生效,而且**都是静默的**:
 *
 * <ul>
 *   <li>在 IDE 里直接点"运行测试" —— 根本不经过 Maven;</li>
 *   <li>{@code mvn -DforkCount=0 test} —— 不 fork,配置被忽略。</li>
 * </ul>
 *
 * 这两种情况下测试会**悄悄落回用户库**。本护栏在**任何 Spring 测试上下文启动之前**
 * 检查真正生效的数据源 URL,不是测试库就抛异常中止 —— 宁可测试红,也不能动用户数据。
 *
 * <h2>为什么用 ApplicationContextInitializer</h2>
 *
 * 它在 environment 准备好之后、**任何连接建立之前**运行,拿到的是
 * {@code environment.getProperty("spring.datasource.url")} ——
 * 那是**真正生效**的值(含 {@code SPRING_DATASOURCE_URL} 这类环境变量的覆盖),
 * 比读 {@code System.getenv} 猜要权威。
 *
 * <h2>它只在测试期存在</h2>
 *
 * 注册文件在 {@code src/test/resources/META-INF/spring.factories},
 * **不在主代码里** —— 所以对 {@code spring-boot:run} 与生产零影响,
 * 应用照常连它该连的库。
 *
 * @see com.tileai.common.SchemaMigrationTest 唯一绕过 Spring 的测试,它自带同一道护栏
 */
public class DbIsolationGuardInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    /** 从 JDBC URL 里抠出库名。{@code jdbc:mysql://host:port/dbname?params} */
    private static final Pattern DATABASE_NAME = Pattern.compile("^jdbc:mysql://[^/]+/([^?;/]+)");

    /** 测试库的命名约定。**必须是后缀,不是"包含"** —— {@code tile_ai_test} 含 {@code tile_ai}。 */
    private static final String TEST_DB_SUFFIX = "_test";

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Environment env = applicationContext.getEnvironment();
        String url = env.getProperty("spring.datasource.url");
        check(url, env.getProperty("spring.datasource.username"));
    }

    /**
     * 库名不以 {@code _test} 结尾就抛异常。
     *
     * <p>包级可见,便于单测直接调。
     *
     * @param url      实际生效的 JDBC URL,可能为 {@code null}(配置缺失)
     * @param username 实际生效的用户名,只用于把错误信息写得可执行
     */
    static void check(String url, String username) {
        String name = databaseName(url);
        if (name == null || !name.endsWith(TEST_DB_SUFFIX)) {
            throw new IllegalStateException("""
                    测试被中止:数据库不是测试库。

                      实际连接   : %s
                      实际库名   : %s
                      实际用户名 : %s

                    测试库固定为 tile_ai_test(以 %s 结尾)。这条护栏是为 2026-10-07 的
                    那次事故加的 —— 当时集成测试连的是用户的演示库,其中若干测试做
                    无条件全表删除,把用户真实数据删掉了。

                    怎么修:
                      · 用 Maven 跑(推荐):mvn -s D:\\dev-tools\\maven-settings.xml test "-Ddb.verify=true"
                        —— pom.xml 的 surefire 配置会自动把它钉到 tile_ai_test。
                      · 在 IDE 里跑:请在运行配置里设 DB_URL 与 SPRING_DATASOURCE_URL
                        指向 tile_ai_test。
                    """.formatted(url, name, username, TEST_DB_SUFFIX));
        }
    }

    /** 从 JDBC URL 取库名;取不到返回 {@code null}。 */
    static String databaseName(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = DATABASE_NAME.matcher(url.trim());
        return matcher.find() ? matcher.group(1) : null;
    }
}
