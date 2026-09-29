package com.tileai.quote;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;
import com.tileai.product.mapper.ProductImageMapper;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.quote.entity.QuoteParam;
import com.tileai.quote.mapper.QuoteParamMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-1.8 验收:<b>用架构 §10.6⑤ 的示例数值做回归断言</b>。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>该示例的算术已在 {@code openapi-verification.md} §3 第 3 条核对自洽,
 * 任务清单也点名"可直接复用为测试用例"。它是本项目**唯一一组端到端定死的数值** ——
 * 公式、边界、舍入口径全部可以据此钉住。
 *
 * <pre>
 *   floor 3.6 × 1.06 × 268        = 1022.69
 *   wall  有效面积 13.9 − 2.88    = 11.02 → 11.02 × 1.06 × 68 = 794.32
 *   材料合计                        = 1817.01
 *   辅料 (3.6 + 11.02) × 20        = 292.40
 *   总价                            = 2109.41
 * </pre>
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-1.8 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuoteEngineRegressionTest {

    private static final String CALC = "/api/v1/quote/calc";

    /** 示例中的两块砖(编号取自 §10.6⑤)。 */
    private static final String SKU_FLOOR = "TL-2043";
    private static final String SKU_WALL = "TL-3012";
    private static final String SKU_EXTRA = "T18-EXTRA";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QuoteParamMapper quoteParamMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductImageMapper productImageMapper;

    @BeforeAll
    void seed() {
        deleteProducts();
        insertProduct(SKU_FLOOR, "雅灰石纹", "268.00");
        insertProduct(SKU_WALL, "雪瓷白", "68.00");
        insertProduct(SKU_EXTRA, "备用砖", "100.00");
    }

    /** 每个用例都从标准参数开始 —— 有的用例会故意把参数删掉。 */
    @BeforeEach
    void resetParams() {
        setParam("aux_unit_price", "20.00");
        setParam("loss_rate", "0.06");
        setParam("loss_rate_min", "0.05");
        setParam("loss_rate_max", "0.08");
    }

    @AfterAll
    void cleanup() {
        deleteProducts();
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .in(QuoteParam::getParamKey, List.of("aux_unit_price", "loss_rate")));
    }

    // ------------------------------------------------------------------
    // 判据:§10.6⑤ 示例数值回归
    // ------------------------------------------------------------------

    @Test
    @DisplayName("§10.6⑤ 示例:金额逐项与合计完全吻合")
    void architectureExampleReproducesExactly() throws Exception {
        JsonNode data = calc(exampleRequest());

        assertDecimal("1817.01", data.get("materialTotal"), "材料费合计");
        assertDecimal("292.40", data.get("auxTotal"), "辅料费");
        assertDecimal("2109.41", data.get("total"), "单空间总价");
    }

    @Test
    @DisplayName("§10.6⑤ 示例:两个分项的面积、损耗率、单价、材料费逐项吻合")
    void exampleItemsMatchExactly() throws Exception {
        JsonNode items = calc(exampleRequest()).get("items");
        assertEquals(2, items.size(), "示例只有地面与墙面两个分项");

        JsonNode floor = items.get(0);
        assertEquals("floor", floor.get("part").asText(), "输出顺序应为 地面 → 墙面 → 局部");
        assertEquals(SKU_FLOOR, floor.get("sku").asText());
        assertDecimal("3.6", floor.get("grossArea"), "地面毛面积");
        assertDecimal("3.6", floor.get("effectiveArea"), "地面不扣开口");
        assertDecimal("0.06", floor.get("lossRate"), "损耗率");
        assertDecimal("268.00", floor.get("pricePerSqm"), "地砖单价");
        assertDecimal("1022.69", floor.get("materialFee"), "3.6 × 1.06 × 268");

        JsonNode wall = items.get(1);
        assertEquals("wall", wall.get("part").asText());
        assertEquals(SKU_WALL, wall.get("sku").asText());
        assertDecimal("13.9", wall.get("grossArea"), "墙面毛面积");
        assertDecimal("11.02", wall.get("effectiveArea"), "13.9 − (0.8×2.1 + 1.0×1.2)");
        assertDecimal("68.00", wall.get("pricePerSqm"), "墙砖单价");
        assertDecimal("794.32", wall.get("materialFee"), "11.02 × 1.06 × 68");
    }

    @Test
    @DisplayName("§10.6⑤ 示例:rulesSnapshot 记录生成时的参数")
    void exampleRulesSnapshotMatches() throws Exception {
        JsonNode snapshot = calc(exampleRequest()).get("rulesSnapshot");

        assertDecimal("20.00", snapshot.get("auxUnitPrice"), "辅料单价快照");
        assertDecimal("0.06", snapshot.get("lossRate"), "损耗率快照");
    }

    // ------------------------------------------------------------------
    // 判据:开口扣减与合并
    // ------------------------------------------------------------------

    @Test
    @DisplayName("开口扣减只作用于墙面:门洞 + 窗洞,地面与局部不扣")
    void onlyWallDeductsOpenings() throws Exception {
        // 同一块砖同时铺在地面与局部,并给两个部位都挂上开口
        String request = """
                {"space":"bathroom",
                 "parts":[{"part":"floor","sku":"%s","area":10.0},
                          {"part":"wall","sku":"%s","area":10.0},
                          {"part":"local","sku":"%s","area":10.0}],
                 "openings":[{"part":"floor","openings":[{"type":"door","w":0.8,"h":2.1}]},
                             {"part":"wall","openings":[{"type":"door","w":0.8,"h":2.1}]},
                             {"part":"local","openings":[{"type":"door","w":0.8,"h":2.1}]}]}
                """.formatted(SKU_EXTRA, SKU_EXTRA, SKU_EXTRA);

        JsonNode items = calc(request).get("items");
        assertEquals(3, items.size());
        assertDecimal("10.0000", items.get(0).get("effectiveArea"), "地面不扣开口");
        assertDecimal("8.3200", items.get(1).get("effectiveArea"), "墙面扣 0.8×2.1 = 1.68");
        assertDecimal("10.0000", items.get(2).get("effectiveArea"), "局部不扣开口");
    }

    @Test
    @DisplayName("同部位同砖先合并面积,只出一个分项")
    void samePartSameSkuIsMerged() throws Exception {
        String request = """
                {"space":"bathroom",
                 "parts":[{"part":"floor","sku":"%s","area":1.5},
                          {"part":"floor","sku":"%s","area":2.1}]}
                """.formatted(SKU_EXTRA, SKU_EXTRA);

        JsonNode items = calc(request).get("items");
        assertEquals(1, items.size(), "同部位同砖应合并为一个分项(§8.8)");
        assertDecimal("3.6000", items.get(0).get("grossArea"), "1.5 + 2.1");
    }

    @Test
    @DisplayName("同部位不同砖分别计价,各自承担按面积占比分摊的开口扣减")
    void differentSkusInSamePartArePricedSeparately() throws Exception {
        String request = """
                {"space":"bathroom",
                 "parts":[{"part":"wall","sku":"%s","area":9.0},
                          {"part":"wall","sku":"%s","area":3.0}],
                 "openings":[{"part":"wall","openings":[{"type":"window","w":1.0,"h":2.0}]}]}
                """.formatted(SKU_EXTRA, SKU_FLOOR);

        JsonNode items = calc(request).get("items");
        assertEquals(2, items.size(), "不同砖应分别出分项");

        // 部位毛面积 12,扣 2 → 有效 10,占比 10/12
        assertDecimal("7.5000", items.get(0).get("effectiveArea"), "9 × 10/12");
        assertDecimal("2.5000", items.get(1).get("effectiveArea"), "3 × 10/12");

        // 保证"分摊后之和 == 部位有效面积",不会因为分摊丢面积
        BigDecimal sum = items.get(0).get("effectiveArea").decimalValue()
                .add(items.get(1).get("effectiveArea").decimalValue());
        assertDecimal("10.0000", sum, "各分项有效面积之和应等于部位有效面积");
    }

    @Test
    @DisplayName("明细逐条相加等于合计 —— 对账时不会差一分钱")
    void itemSumEqualsMaterialTotal() throws Exception {
        JsonNode data = calc(exampleRequest());

        BigDecimal sum = BigDecimal.ZERO;
        for (JsonNode item : data.get("items")) {
            sum = sum.add(item.get("materialFee").decimalValue());
        }
        assertDecimal("1817.01", sum, "分项材料费之和");
        assertEquals(0, sum.compareTo(data.get("materialTotal").decimalValue()),
                "合计必须等于明细之和");
    }

    // ------------------------------------------------------------------
    // 边界
    // ------------------------------------------------------------------

    @Test
    @DisplayName("参数缺失 → 30001,并明确指出缺哪个")
    void missingParamsFailLoudly() throws Exception {
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .eq(QuoteParam::getParamKey, "loss_rate"));

        MvcResult result = asPost(exampleRequest());
        assertEquals(30001, codeOf(result));
        assertTrue(result.getResponse().getContentAsString().contains("loss_rate"),
                "错误信息应指出缺的是哪个参数: " + result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("瓷砖编号不存在 → 10001")
    void unknownSkuIsRejected() throws Exception {
        String request = """
                {"space":"bathroom","parts":[{"part":"floor","sku":"NO-SUCH","area":1.0}]}
                """;
        assertEquals(10001, codeOf(asPost(request)));
    }

    @Test
    @DisplayName("缺面积 → 10001(/quote/calc 请求里没有户型,服务端推不出来)")
    void missingAreaIsRejected() throws Exception {
        String request = """
                {"space":"bathroom","parts":[{"part":"floor","sku":"%s"}]}
                """.formatted(SKU_EXTRA);
        MvcResult result = asPost(request);
        assertEquals(10001, codeOf(result));
        // 字段路径是嵌套的,完整写法为 parts[0].area —— 不能只找 "area",
        // 那个子串前后带着引号,在这里并不出现。
        assertTrue(result.getResponse().getContentAsString().contains("parts[0].area"),
                result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("空 parts → 10001(契约 minItems: 1)")
    void emptyPartsIsRejected() throws Exception {
        assertEquals(10001, codeOf(asPost("{\"space\":\"bathroom\",\"parts\":[]}")));
    }

    @Test
    @DisplayName("非法部位 → 10001")
    void invalidPartIsRejected() throws Exception {
        String request = """
                {"space":"bathroom","parts":[{"part":"ceiling","sku":"%s","area":1.0}]}
                """.formatted(SKU_EXTRA);
        assertEquals(10001, codeOf(asPost(request)));
    }

    @Test
    @DisplayName("不是报价参数的那个 loss_rate_min 不参与计算 —— 改它不影响金额")
    void minMaxDoNotAffectCalculation() throws Exception {
        BigDecimal before = calc(exampleRequest()).get("total").decimalValue();

        setParam("loss_rate_min", "0.05");
        setParam("loss_rate_max", "0.09");

        assertDecimal(before.toPlainString(), calc(exampleRequest()).get("total"),
                "loss_rate_min/max 是允许范围,只用于校验与提示(架构 §8.8)");
    }

    // ------------------------------------------------------------------

    /** §10.6⑤ 的原始示例请求。 */
    private String exampleRequest() {
        return """
                {"space":"bathroom",
                 "parts":[{"part":"floor","sku":"%s","area":3.6},
                          {"part":"wall","sku":"%s","area":13.9}],
                 "openings":[{"part":"wall","openings":[{"type":"door","w":0.8,"h":2.1},
                                                        {"type":"window","w":1.0,"h":1.2}]}]}
                """.formatted(SKU_FLOOR, SKU_WALL);
    }

    private JsonNode calc(String requestJson) throws Exception {
        MvcResult result = asPost(requestJson);
        assertEquals(0, codeOf(result), "计算应成功: " + result.getResponse().getContentAsString());
        return dataOf(result);
    }

    private MvcResult asPost(String requestJson) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post(CALC).contentType(MediaType.APPLICATION_JSON).content(requestJson)).andReturn();
    }

    private void setParam(String key, String value) {
        quoteParamMapper.delete(Wrappers.<QuoteParam>lambdaQuery()
                .eq(QuoteParam::getParamKey, key));
        QuoteParam param = new QuoteParam();
        param.setParamKey(key);
        param.setParamValue(new BigDecimal(value));
        quoteParamMapper.insert(param);
    }

    private void insertProduct(String sku, String name, String price) {
        Product product = new Product();
        product.setSkuCode(sku);
        product.setName(name);
        product.setPricePerSqm(new BigDecimal(price));
        product.setStatus(Product.STATUS_OFF_SALE);
        productMapper.insert(product);
    }

    private void deleteProducts() {
        for (String sku : List.of(SKU_FLOOR, SKU_WALL, SKU_EXTRA)) {
            Product product = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                    .eq(Product::getSkuCode, sku));
            if (product != null) {
                productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                        .eq(ProductImage::getProductId, product.getId()));
                productMapper.deleteById(product.getId());
            }
        }
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("code").asInt();
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertNotNull(data, "响应缺 data: " + body);
        return data;
    }

    private static void assertDecimal(String expected, JsonNode actual, String message) {
        assertNotNull(actual, message + " —— 字段缺失");
        assertDecimal(expected, actual.decimalValue(), message);
    }

    private static void assertDecimal(String expected, BigDecimal actual, String message) {
        assertNotNull(actual, message);
        assertEquals(0, new BigDecimal(expected).compareTo(actual),
                message + ";期望 " + expected + ",实际 " + actual.toPlainString());
    }
}
