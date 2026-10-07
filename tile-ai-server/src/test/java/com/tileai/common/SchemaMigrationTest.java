package com.tileai.common;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.2 验收:空库执行迁移可跑到最新版本、可重复执行,且 Schema 与架构 §8 逐条一致。
 *
 * <p>本测试需要真实 MySQL 8,默认不参与构建(否则无库环境下 {@code mvn package} 会失败)。
 * 显式启用:
 *
 * <pre>
 *   mvn -s D:\dev-tools\maven-settings.xml test "-Ddb.verify=true"
 * </pre>
 *
 * <p>刻意<b>不</b>做成"检测不到数据库就 SKIP"——那样会重演 T-12 的陷阱:
 * 跳过看起来像通过。这里没库就是失败,必须显式开关才会运行。
 *
 * <p><b>本测试不做破坏性操作</b>(不 DROP / 不 FLY Clean)。因此若库中已有 schema,
 * 首次 {@code migrate()} 会是空操作 —— 此时"空库"这一前提由 <b>控制台打印</b>的
 * {@code 起始为空库=} 与 {@code 本次执行迁移数=} 如实呈现,不靠断言假装。
 * 要做严格意义上的空库验证,请先 {@code DROP DATABASE tile_ai} 再运行。
 *
 * <p>断言口径:表名 / 字段名 / 字段类型取自 §8.1–§8.7 的逐表清单;
 * <b>索引不属于 §8 的内容</b>(§8 只规定了字段),因此只断言 §8 明确写了"唯一"的四项。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL 8;用 -Ddb.verify=true 显式启用(T-0.2 验证)")
class SchemaMigrationTest {

    /**
     * 期望 Schema:每行 {@code 表名 | 字段名 类型 ; 字段名 类型 ; …}。
     *
     * <p>列分隔符用 {@code ;} 而<b>不是</b> {@code ,} —— 因为类型本身就含逗号
     * ({@code decimal(10,2)}),用逗号切会把 {@code decimal(10} 与 {@code 2)} 拆开。
     *
     * <p>字段名按 {@code information_schema.COLUMN_NAME} 的原样书写(不带反引号),
     * 即使该列名是保留字(如 {@code t_plan_image.view})。
     */
    private static final String EXPECTED_SCHEMA = """
            t_user                 | id bigint unsigned ; username varchar(50) ; password_hash varchar(100) ; nickname varchar(50) ; status varchar(20) ; last_login_at datetime ; created_at datetime ; updated_at datetime
            t_admin_user           | id bigint unsigned ; username varchar(50) ; password_hash varchar(100) ; role varchar(30) ; status varchar(20) ; created_at datetime
            t_operation_log        | id bigint unsigned ; admin_id bigint unsigned ; action varchar(64) ; target_type varchar(64) ; target_id varchar(64) ; detail json ; ip varchar(45) ; created_at datetime
            t_product              | id bigint unsigned ; sku_code varchar(64) ; name varchar(200) ; spec varchar(100) ; material varchar(50) ; surface varchar(50) ; color_pattern varchar(200) ; price_per_sqm decimal(10,2) ; applicable_spaces json ; style_tags json ; status varchar(20) ; cover_image varchar(500) ; created_at datetime ; updated_at datetime
            t_product_image        | id bigint unsigned ; product_id bigint unsigned ; url varchar(500) ; angle varchar(50) ; sort int
            t_recommend_combo      | id bigint unsigned ; name varchar(200) ; space varchar(50) ; style varchar(50) ; ref_price_min decimal(12,2) ; ref_price_max decimal(12,2) ; cover_image varchar(500) ; status varchar(20) ; sort int ; created_at datetime ; updated_at datetime
            t_recommend_combo_item | id bigint unsigned ; combo_id bigint unsigned ; product_id bigint unsigned ; part varchar(10) ; note varchar(255) ; sort int
            t_unit_template        | id bigint unsigned ; name varchar(200) ; room_count int ; area_range varchar(50) ; structure varchar(50) ; layout_json json ; status varchar(20) ; created_at datetime ; updated_at datetime
            t_layout               | id bigint unsigned ; plan_id bigint unsigned ; layout_enc varbinary(16384)
            t_plan                 | id bigint unsigned ; user_id bigint unsigned ; name varchar(200) ; space varchar(50) ; style varchar(50) ; layout_enc varbinary(16384) ; total_amount decimal(12,2) ; share_token varchar(64) ; quote_expired tinyint(1) ; is_deleted tinyint(1) ; created_at datetime ; updated_at datetime
            t_plan_part            | id bigint unsigned ; plan_id bigint unsigned ; part varchar(10) ; product_id bigint unsigned ; sku_code varchar(64) ; product_name varchar(200) ; price_per_sqm decimal(10,2) ; gross_area decimal(10,4) ; area decimal(10,4) ; loss_rate decimal(5,4) ; material_fee decimal(12,2) ; source varchar(10)
            t_plan_quote           | id bigint unsigned ; plan_id bigint unsigned ; material_total decimal(12,2) ; aux_total decimal(12,2) ; aux_unit_price decimal(10,2) ; aux_area decimal(10,4) ; loss_rate decimal(5,4) ; total_amount decimal(12,2) ; rules_snapshot json
            t_plan_image           | id bigint unsigned ; plan_id bigint unsigned ; url varchar(2048) ; view varchar(30) ; is_cover tinyint(1) ; created_at datetime
            t_furnish_style        | id bigint unsigned ; space varchar(50) ; style varchar(50) ; prompt text ; cover_image varchar(500) ; status varchar(20) ; updated_at datetime
            t_quote_param          | id bigint unsigned ; param_key varchar(50) ; param_value decimal(10,4) ; remark varchar(255) ; updated_at datetime
            t_store                | id bigint unsigned ; name varchar(200) ; address varchar(500) ; phone varchar(50) ; map_url varchar(500) ; updated_at datetime
            t_dict                 | id bigint unsigned ; dict_type varchar(30) ; dict_key varchar(50) ; dict_label varchar(100) ; sort int ; status varchar(20) ; updated_at datetime
            t_generation_task      | id bigint unsigned ; task_id varchar(64) ; user_id bigint unsigned ; plan_id bigint unsigned ; type varchar(20) ; status varchar(20) ; progress int ; payload json ; result_ref varchar(2048) ; error varchar(1000) ; external_task_id varchar(128) ; created_at datetime ; updated_at datetime
            t_chat_session         | id bigint unsigned ; user_id bigint unsigned ; title varchar(200) ; created_at datetime
            t_chat_message         | id bigint unsigned ; session_id bigint unsigned ; role varchar(20) ; content text ; refs json ; created_at datetime
            t_outbox               | id bigint unsigned ; aggregate_type varchar(30) ; aggregate_id varchar(64) ; queue varchar(100) ; payload json ; status varchar(20) ; retry_count int ; next_retry_at datetime ; created_at datetime ; sent_at datetime
            t_import_batch         | id bigint unsigned ; batch_id varchar(64) ; biz_type varchar(30) ; mode varchar(20) ; status varchar(20) ; total_count int ; success_count int ; failed_count int ; errors json ; error_report_url varchar(500) ; created_at datetime ; updated_at datetime
            """;

    /** §8 中明确写了"唯一"的字段 —— 只有这四项能对照架构文档断言。 */
    private static final Set<String> EXPECTED_UNIQUES = Set.of(
            "t_user.username",
            "t_admin_user.username",
            "t_product.sku_code",
            "t_generation_task.task_id");

    /**
     * 期望的迁移版本集合({@code flyway_schema_history} 里应<b>完全相等</b>)。
     *
     * <p>新增迁移必须同步加在这里 —— 它同时是「空库首次迁移应执行 N 个版本」的基数
     * (见 {@link #migrationsReachLatestAndAreRepeatable})。
     */
    private static final List<String> EXPECTED_MIGRATIONS =
            List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12");

    private static Connection conn;
    private static String schema;
    private static boolean startedEmpty;
    private static MigrateResult firstRun;

    @BeforeAll
    static void migrateOnce() throws SQLException {
        String url = env("DB_URL",
                "jdbc:mysql://localhost:3306/tile_ai?useUnicode=true&characterEncoding=utf8"
                        + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false");
        String user = env("DB_USERNAME", "root");
        String pass = env("DB_PASSWORD", "");

        schema = databaseName(url);
        assertTrue(schema.matches("[A-Za-z0-9_]+"),
                "从 DB_URL 解析出的库名不是合法标识符 —— 解析结果: '" + schema + "'");

        // 护栏(2026-10-07):本类是**唯一绕过 Spring** 直连库的测试,而它会跑 V1–V12
        // (其中 V9 含 DROP TABLE)。库名必须在这里就挡住 —— 位置在 CREATE DATABASE **之前**。
        // 挡的是"把用户库当测试库"这类事故:2026-10-07 真发生了一次,用户的真实数据被删。
        // Spring 侧另有一道同名护栏,见 com.tileai.testinfra.DbIsolationGuardInitializer。
        assertTrue(schema.endsWith("_test"),
                "拒绝在非测试库上跑迁移:从 DB_URL 解析出的库名是 '" + schema + "',不以 _test 结尾。"
                        + "测试库固定为 tile_ai_test —— 请检查 DB_URL / SPRING_DATASOURCE_URL 是否被指到了用户库。");

        try (Connection server = DriverManager.getConnection(serverUrl(url), user, pass);
             Statement st = server.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + schema
                    + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            startedEmpty = !hasAnyTable(st);
        }

        conn = DriverManager.getConnection(url, user, pass);
        firstRun = flyway().migrate();

        System.out.println("[T-0.2] 库=" + schema
                + "  起始为空库=" + startedEmpty
                + "  本次执行迁移数=" + firstRun.migrationsExecuted
                + "  成功=" + firstRun.success);
    }

    @Test
    @DisplayName("迁移已跑到最新版本,且重复执行不再产生任何变更(幂等)")
    void migrationsReachLatestAndAreRepeatable() {
        assertTrue(firstRun.success, "首次迁移应成功");
        if (startedEmpty) {
            assertEquals(EXPECTED_MIGRATIONS.size(), firstRun.migrationsExecuted,
                    "空库首次迁移应执行全部 " + EXPECTED_MIGRATIONS.size() + " 个版本");
        }

        MigrateResult again = flyway().migrate();
        assertEquals(0, again.migrationsExecuted,
                "第二次迁移不应执行任何版本(Flyway 版本表已记录)");
    }

    @Test
    @DisplayName("flyway_schema_history 记录了 EXPECTED_MIGRATIONS 里的全部版本且都成功")
    void historyHasAllVersions() throws SQLException {
        Set<String> versions = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT version FROM flyway_schema_history WHERE success = 1 AND version IS NOT NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    versions.add(rs.getString(1));
                }
            }
        }
        assertEquals(new LinkedHashSet<>(EXPECTED_MIGRATIONS), versions, "已成功应用的迁移版本");
    }

    @Test
    @DisplayName("表名 / 字段名 / 字段类型与架构 §8.1–§8.7 逐条一致")
    void schemaMatchesArchitectureSection8() throws SQLException {
        Map<String, Map<String, String>> actual = actualColumns();
        Map<String, List<String[]>> expected = parse(EXPECTED_SCHEMA);

        assertEquals(expected.keySet(), actual.keySet(),
                "表集合应与 §8.1–§8.7 完全一致(多一张或少一张都算不符)");

        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, List<String[]>> e : expected.entrySet()) {
            Map<String, String> cols = actual.get(e.getKey());
            List<String> expectedNames = e.getValue().stream().map(c -> c[0]).toList();
            assertEquals(expectedNames, new ArrayList<>(cols.keySet()),
                    "表 " + e.getKey() + " 的字段名与顺序应与 §8 一致");
            for (String[] c : e.getValue()) {
                String got = cols.get(c[0]);
                if (!c[1].equalsIgnoreCase(got)) {
                    problems.add(e.getKey() + "." + c[0] + " 期望 " + c[1] + ",实际 " + got);
                }
            }
        }
        assertTrue(problems.isEmpty(), "字段类型不符:\n  " + String.join("\n  ", problems));
    }

    @Test
    @DisplayName("§8 中明确标注'唯一'的字段确实建了唯一约束")
    void documentedUniqueKeysExist() throws SQLException {
        Set<String> actual = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME)
                  FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = ? AND NON_UNIQUE = 0
                """)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    actual.add(rs.getString(1));
                }
            }
        }
        for (String required : EXPECTED_UNIQUES) {
            assertTrue(actual.contains(required),
                    "§8 声明为唯一的字段缺少唯一约束: " + required);
        }
    }

    @Test
    @DisplayName("逻辑删除列 is_deleted 只出现在 §8.4 明确列出的 t_plan 上")
    void isDeletedOnlyWhereDocumented() throws SQLException {
        Set<String> tables = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT TABLE_NAME FROM information_schema.COLUMNS"
                        + " WHERE TABLE_SCHEMA = ? AND COLUMN_NAME = 'is_deleted'")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
        }
        assertEquals(Set.of("t_plan"), tables,
                "§8 只在 t_plan 列出了 is_deleted;其它表若需逻辑删除,应先回改架构文档");
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static Flyway flyway() {
        return Flyway.configure()
                .dataSource(env("DB_URL",
                                "jdbc:mysql://localhost:3306/tile_ai?useUnicode=true&characterEncoding=utf8"
                                        + "&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"),
                        env("DB_USERNAME", "root"), env("DB_PASSWORD", ""))
                .locations("classpath:db/migration")
                .load();
    }

    /**
     * jdbc:mysql://host:3306/db?params -> jdbc:mysql://host:3306/?params
     *
     * <p><b>必须先切掉查询串再找最后一个 '/'。</b>直接在原串上找会踩坑:参数值里可能含 '/'
     * (如 {@code serverTimezone=Asia/Shanghai}),贪婪匹配会把库名解析成
     * {@code Shanghai&allowPublicKeyRetrieval=true&...} 这种垃圾,而且
     * {@code CREATE DATABASE} 会真的把它建出来 —— 报错却指向别处。
     */
    private static String serverUrl(String url) {
        String[] parts = url.split("\\?", 2);
        int slash = parts[0].lastIndexOf('/');
        return parts[0].substring(0, slash + 1) + (parts.length > 1 ? "?" + parts[1] : "");
    }

    /** 库名 = 查询串之前、最后一个 '/' 之后的片段。 */
    private static String databaseName(String url) {
        String path = url.split("\\?", 2)[0];
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private static boolean hasAnyTable(Statement st) throws SQLException {
        try (PreparedStatement ps = st.getConnection().prepareStatement(
                "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static Map<String, Map<String, String>> actualColumns() throws SQLException {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        // 排除 flyway_schema_history —— 那是 Flyway 的记账表,不属于架构 §8 的数据模型。
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE
                  FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = ?
                   AND TABLE_NAME <> 'flyway_schema_history'
                 ORDER BY TABLE_NAME, ORDINAL_POSITION
                """)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.computeIfAbsent(rs.getString(1), k -> new LinkedHashMap<>())
                            .put(rs.getString(2), rs.getString(3));
                }
            }
        }
        return out;
    }

    private static Map<String, List<String[]>> parse(String spec) {
        Map<String, List<String[]>> out = new LinkedHashMap<>();
        for (String raw : spec.strip().split("\n")) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int bar = line.indexOf('|');
            assertTrue(bar > 0, "规格行缺少 '|' 分隔符: " + line);
            String table = line.substring(0, bar).strip();
            List<String[]> cols = new ArrayList<>();
            for (String col : line.substring(bar + 1).split(";")) {
                String c = col.strip();
                if (c.isEmpty()) {
                    continue;
                }
                int sp = c.indexOf(' ');
                assertTrue(sp > 0, "字段规格缺少类型: '" + c + "'(所在表 " + table + ")");
                cols.add(new String[]{c.substring(0, sp), c.substring(sp + 1).strip()});
            }
            out.put(table, cols);
        }
        return out;
    }
}
