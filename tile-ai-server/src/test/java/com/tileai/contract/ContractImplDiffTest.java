package com.tileai.contract;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 契约 ↔ 实现的自动比对(路线图里排在 T-2.1 的 T-12 的<b>轻量落地</b>)。
 *
 * <p>导出的那份清单落 {@code outputs/reports/contract-impl-diff.md},同时打到控制台。
 * <b>清单本身就是要交给人的东西</b> —— 它回答"实现和契约对不对得上"。
 *
 * <h2>什么会让它红,什么不会</h2>
 *
 * <p>默认(<b>不</b>给 {@code -Dcontract.diff.gate=true</b>)只有两类会红,而且都是
 * "工具自己不可信"的信号:
 * <ul>
 *   <li><b>锚点未命中</b> —— 连必然存在的接口都没匹配上,说明路径拼接/读取写错了,
 *       这时候"没发现问题"毫无意义(最隐蔽的假绿);</li>
 *   <li><b>挂账已过期</b> —— 登记说"还没做"的接口,这次却匹配上了。不报的话,清单会
 *       慢慢变成漂移的藏身处:一个已实现的接口若哪天又没了,会被当成"已知未开工"放过去。</li>
 * </ul>
 *
 * <p>真正的契约 ↔ 实现分叉(意外缺失 / 多余路由 / 字段不一致)<b>只进清单,不阻断构建</b> ——
 * 这是用户拍板的处置:先看清单再定逐条怎么办。要把它变成门禁,加
 * {@code -Dcontract.diff.gate=true} 即可。
 *
 * <h2>为什么落在测试里,而不是 {@code ci.sh}</h2>
 *
 * <p>契约门禁的 CI 只有 Node + Python,<b>没有 JDK</b>;而本比对必须真的把 Spring 上下文
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

    /** 报告落点(仓库根下,已 gitignore)。 */
    private static final String REPORT = "outputs/reports/contract-impl-diff.md";

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("契约 ↔ 实现比对:出清单、落盘、锚点与挂账自检")
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

        System.out.println(report);
        System.out.println("清单已写入: " + reportPath);

        // 工具自检一:锚点必须两侧都在(否则"没发现问题"是假绿)
        assertTrue(result.brokenCanaries().isEmpty(),
                "锚点未命中 —— 比对本身可能没跑对:\n" + String.join("\n", result.brokenCanaries()));

        // 工具自检二:挂账清单不许过期(过期的挂账会掩盖后续的真实回退)
        assertTrue(result.staleGaps().isEmpty(),
                "挂账清单已过期,请删掉这些条目:\n"
                        + result.staleGaps().stream()
                        .map(ContractImplDiff.StaleGap::brief).toList());

        // 两侧的恒等式由 diff() 内部断言;这里再钉一遍"确实读到了东西"
        assertTrue(result.matched() > 50,
                "匹配上的端点太少(" + result.matched() + " 条),八成是路径前缀拼接出了问题");

        if (Boolean.getBoolean("contract.diff.gate")) {
            assertFalse(result.hasFindings(),
                    "契约与实现有分叉(明细见 " + REPORT + ")");
        }
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
