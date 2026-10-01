package com.tileai.contract;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.tileai.RepoDocs;
import com.tileai.common.api.ApiResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约 ↔ 实现的自动比对(路线图任务 T-2.1,对应校验项 T-12)。
 *
 * <p>导出两份产物:
 * <ul>
 *   <li>{@code outputs/reports/contract-impl-diff.md} —— <b>给人看的清单</b>,
 *       同时打到控制台。它回答"实现和契约对不对得上";</li>
 *   <li>{@code outputs/reports/contract-impl-diff.json} —— <b>给机器看的结论</b>。
 *       {@code check_t.py} 的 T-12 直接读它判 PASS/FAIL,<b>不拿正则去抠 md</b>。</li>
 * </ul>
 *
 * <h2>什么会让它红,什么不会</h2>
 *
 * <p>默认(<b>不</b>给 {@code -Dcontract.diff.gate=true})只有三类会红,而且都是
 * "工具自己不可信"或"清单在漂移"的信号:
 * <ul>
 *   <li><b>锚点未命中</b> —— 连必然存在的接口都没匹配上,说明路径拼接/读取写错了,
 *       这时候"没发现问题"毫无意义(最隐蔽的假绿);</li>
 *   <li><b>挂账已过期(整条)</b> —— 登记说"还没做"的接口,这次却匹配上了;</li>
 *   <li><b>挂账已过期(字段级)</b> —— 登记说不一致的那一处,这次已经一致了。
 *       不报的话,豁免表会变成漂移的藏身处。</li>
 * </ul>
 *
 * <p>真正的契约 ↔ 实现分叉(意外缺失 / 多余路由 / 字段不一致 / 类型·必填·枚举值不一致)
 * <b>只在打开门禁时阻断</b>。为什么默认关:普通 {@code mvn test} 不该被一次字段漂移打断,
 * 而"一定拦住"由 CI 承担(见 {@code contract-lint.yml} 与 {@code contract-diff.sh})——
 * 那里显式传 {@code -Dcontract.diff.gate=true},并由 {@code check_t.py} 的 T-12 复核同一份 JSON。
 *
 * <h2>为什么落在测试里,而不是 {@code ci.sh}</h2>
 *
 * <p>契约门禁的 CI 原本只有 Node + Python,<b>没有 JDK</b>;而本比对必须真的把 Spring 上下文
 * 起起来读路由表。放这里还有个好处:它和现有 {@code @SpringBootTest} 共用同一个上下文缓存。
 *
 * <p>注解与属性刻意与 {@code SecurityFilterContractTest} 保持一致 —— 上下文缓存键相同,
 * 不会为这一条测试多起一套上下文。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"
})
@AutoConfigureMockMvc
class ContractImplDiffTest {

    /** 给人看的清单(仓库根下,已 gitignore)。 */
    private static final String REPORT = "outputs/reports/contract-impl-diff.md";

    /** 给机器看的结论 —— {@code check_t.py} 的 T-12 读它。 */
    private static final String REPORT_JSON = "outputs/reports/contract-impl-diff.json";

    /** 产物格式版本。改了 JSON 的键就加一,免得旧产物被按新格式解读。 */
    private static final int SCHEMA_VERSION = 1;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("契约 ↔ 实现比对:出清单、落盘(md + json)、锚点与挂账自检")
    void diffAndReport() throws Exception {
        GapRegistry gaps = GapRegistry.load();
        List<ContractEndpoint> contract = new ContractReader().readAll();
        List<ImplEndpoint> impl = new ImplReader(objectMapper).read(mapping);

        ContractImplDiff.Result result = ContractImplDiff.diff(contract, impl, gaps);
        String report = ContractImplDiff.render(result);

        Path root = RepoDocs.locate("tile-ai-tasks.md").getParent();
        Path reportPath = root.resolve(REPORT);
        Files.createDirectories(reportPath.getParent());
        Files.writeString(reportPath, report, StandardCharsets.UTF_8);

        Path jsonPath = root.resolve(REPORT_JSON);
        Files.writeString(jsonPath, objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(machineReadable(result)), StandardCharsets.UTF_8);

        System.out.println(report);
        System.out.println("清单已写入: " + reportPath + " / " + jsonPath);

        // 工具自检一:锚点必须两侧都在(否则"没发现问题"是假绿)
        assertTrue(result.brokenCanaries().isEmpty(),
                "锚点未命中 —— 比对本身可能没跑对:\n" + String.join("\n", result.brokenCanaries()));

        // 工具自检二:挂账清单不许过期(过期的挂账会掩盖后续的真实回退)
        assertTrue(result.staleGaps().isEmpty(),
                "挂账清单已过期,请删掉这些条目:\n"
                        + result.staleGaps().stream()
                        .map(ContractImplDiff.StaleGap::brief).toList());

        // 工具自检三:字段级挂账同样不许过期 —— 已一致了就该把豁免删掉
        assertTrue(result.staleFieldGaps().isEmpty(),
                "字段级挂账已过期,请删掉这些条目:\n"
                        + result.staleFieldGaps().stream()
                        .map(ContractImplDiff.StaleFieldGap::brief).toList());

        // 两侧的恒等式由 diff() 内部断言;这里再钉一遍"确实读到了东西"
        assertTrue(result.matched() > 50,
                "匹配上的端点太少(" + result.matched() + " 条),八成是路径前缀拼接出了问题");

        if (Boolean.getBoolean("contract.diff.gate")) {
            assertFalse(result.hasFindings(),
                    "契约与实现有分叉(明细见 " + REPORT + ")");
        }
    }

    /**
     * 机器可读的结论。
     *
     * <p>{@code runId} 与 {@code gitSha} 是<b>新鲜度锚点</b>:CI 上由环境变量传进来,
     * {@code check_t.py} 会核对它们与本次运行一致,免得拿上一轮的产物当成本轮结果。
     */
    private static Map<String, Object> machineReadable(ContractImplDiff.Result result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("schemaVersion", SCHEMA_VERSION);
        out.put("generatedAt", Instant.now().toString());
        out.put("runId", env("CONTRACT_DIFF_RUN_ID"));
        out.put("gitSha", env("GITHUB_SHA"));
        out.put("hasFindings", result.hasFindings());

        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("contractJava", result.contractJava());
        counts.put("contractPython", result.contractPython());
        counts.put("implTotal", result.implTotal());
        counts.put("matched", result.matched());
        counts.put("matchedImpl", result.matchedImpl());
        counts.put("unexpectedMissing", result.unexpectedMissing().size());
        counts.put("knownMissing", result.knownMissing().size());
        counts.put("extraImpl", result.extraImpl().size());
        counts.put("fieldDiffs", result.fieldDiffs().size());
        counts.put("typeMismatches", result.mismatchesOf(ContractImplDiff.Dimension.TYPE));
        counts.put("enumMismatches", result.mismatchesOf(ContractImplDiff.Dimension.ENUM));
        counts.put("requiredMismatches", result.mismatchesOf(ContractImplDiff.Dimension.REQUIRED));
        counts.put("fieldGapHits", result.fieldGapHits().size());
        counts.put("staleGaps", result.staleGaps().size());
        counts.put("staleFieldGaps", result.staleFieldGaps().size());
        counts.put("unevaluatedFieldGaps", result.unevaluatedFieldGaps().size());
        counts.put("brokenCanaries", result.brokenCanaries().size());
        counts.put("skipped", result.skipped().size());
        // 叶子分两类,别只报一个数 —— 「没判过」的那类才是要人看的(见 ContractImplDiff.LeafCounts)
        counts.put("comparedLeaves", result.comparedLeaves());
        counts.put("uncomparedLeaves", result.uncomparedLeaves());
        out.put("counts", counts);

        out.put("staleGaps", result.staleGaps().stream()
                .map(ContractImplDiff.StaleGap::brief).toList());
        out.put("staleFieldGaps", result.staleFieldGaps().stream()
                .map(ContractImplDiff.StaleFieldGap::brief).toList());
        out.put("brokenCanaries", result.brokenCanaries());
        out.put("unexpectedMissing", result.unexpectedMissing().stream()
                .map(ContractEndpoint::brief).toList());
        out.put("extraImpl", result.extraImpl().stream()
                .map(ImplEndpoint::brief).toList());
        out.put("fieldMismatches", result.fieldMismatches().stream()
                .map(ContractImplDiff.FieldMismatch::brief).toList());
        return out;
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? "local" : value;
    }

    @Test
    @DisplayName("提取器自检:两侧对 ApiResponse 信封的字段理解一致")
    void envelopeFieldsAgreeOnBothSides() {
        Map<String, Object> common = RepoDocs.yaml("openapi-common.yaml");
        Map<String, Object> schema = RepoDocs.child(
                RepoDocs.child(RepoDocs.child(common, "components"), "schemas"), "ApiResponse");
        Set<String> contractSide = new TreeSet<>(
                RepoDocs.child(schema, "properties").keySet());

        BeanDescription description = objectMapper.getSerializationConfig()
                .introspect(objectMapper.constructType(ApiResponse.class));
        Set<String> implSide = new TreeSet<>();
        for (BeanPropertyDefinition property : description.findProperties()) {
            implSide.add(property.getName());
        }

        // 若这条红了:说明"剥信封取 data"的前提变了,比对结果整体不可信 ——
        // 而不是"实现和契约分叉了"。
        assertEquals(Set.of("code", "message", "data", "traceId"), contractSide);
        assertEquals(contractSide, implSide,
                "契约与实现对外层信封的字段理解必须一致,否则两侧的 data 比对无从谈起");
    }
}
