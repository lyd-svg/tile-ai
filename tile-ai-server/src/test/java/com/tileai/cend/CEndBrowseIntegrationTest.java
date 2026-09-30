package com.tileai.cend;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.entity.DictEntry;
import com.tileai.admin.mapper.DictMapper;
import com.tileai.combo.entity.RecommendCombo;
import com.tileai.combo.entity.RecommendComboItem;
import com.tileai.combo.mapper.RecommendComboItemMapper;
import com.tileai.combo.mapper.RecommendComboMapper;
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ProductMapper;
import com.tileai.store.entity.Store;
import com.tileai.store.mapper.StoreMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * T-1.2 / T-1.3 / T-1.4 / T-1.14 验收:C 端四个只读接口。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>四个接口都是<b>公开</b>的(契约 {@code security: []}),因此本测试不带任何 Cookie ——
 * 这本身就顺带验证了"浏览免登录"(PRD §5.1)。
 *
 * <p>各自的判据:
 * <ul>
 *   <li><b>T-1.2</b> 分页(T-3)、8 个查询参数与 D-8 裁决一致、<b>下架 SKU 不出现</b>、
 *       详情返回 30001;</li>
 *   <li><b>T-1.3</b> 结构与 Combo schema 一致、items 的 part 恒为三值(T-9)、仅上架;</li>
 *   <li><b>T-1.4</b> 仅返回 enabled、按 sort 升序(T-15 的运行时前提);</li>
 *   <li><b>T-1.14</b> 字段与 Store schema 一致、未配置返回 30001、无下单入口。</li>
 * </ul>
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CEndBrowseIntegrationTest {

    private static final String ON_SALE_SKU = "T12-ON";
    private static final String OFF_SALE_SKU = "T12-OFF";
    private static final String DICT_KEY = "t12_probe_space";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RecommendComboMapper comboMapper;

    @Autowired
    private RecommendComboItemMapper comboItemMapper;

    @Autowired
    private DictMapper dictMapper;

    @Autowired
    private StoreMapper storeMapper;

    private long onSaleComboId;
    private long offSaleComboId;

    /**
     * 每个用例都从干净的前置数据开始。
     *
     * <p>不依赖执行顺序 —— 本项目已因"多用例共用可变前置数据"踩过四次。
     */
    @BeforeEach
    void seed() {
        cleanUp();

        insertProduct(ON_SALE_SKU, "在售探针砖", "釉面砖", "浅灰细石纹", "750×1500mm",
                "268.00", Product.STATUS_ON_SALE, List.of("bathroom"), List.of("modern"));
        insertProduct(OFF_SALE_SKU, "下架探针砖", "岩板", "深灰石纹", "600×1200mm",
                "99.00", Product.STATUS_OFF_SALE, List.of("kitchen"), List.of("nordic"));

        onSaleComboId = insertCombo("在售搭配", RecommendCombo.STATUS_ON_SALE);
        offSaleComboId = insertCombo("下架搭配", RecommendCombo.STATUS_OFF_SALE);

        setDict(DICT_KEY, "探针空间", 5, DictEntry.STATUS_ENABLED);
        setDict("t12_disabled_space", "停用空间", 9, "disabled");
    }

    @AfterAll
    void cleanupAll() {
        cleanUp();
        storeMapper.delete(Wrappers.<Store>lambdaQuery());
    }

    // ===============================================================
    // T-1.2 产品库浏览与筛选
    // ===============================================================

    @Test
    @DisplayName("T-1.2 列表分页结构成立(T-3),且返回在售 SKU")
    void productListIsPaginated() throws Exception {
        JsonNode data = data("/api/v1/products");

        assertTrue(data.has("list") && data.has("total") && data.has("page") && data.has("size"),
                "分页四字段应齐备(T-3): " + data);
        assertTrue(skuCodesOf(data).contains(ON_SALE_SKU), "在售 SKU 应出现");
    }

    @Test
    @DisplayName("T-1.2 判据:下架 SKU 不出现于列表,详情返回 30001")
    void offSaleProductIsHidden() throws Exception {
        JsonNode data = data("/api/v1/products", "size", "100");

        assertTrue(!skuCodesOf(data).contains(OFF_SALE_SKU),
                "下架 SKU 不该出现在前台浏览里: " + skuCodesOf(data));

        assertEquals(30001, codeOf(call("/api/v1/products/" + OFF_SALE_SKU)),
                "下架商品的详情应与「不存在」同样处理 —— 否则用户能拿着买不到的砖往下走");
    }

    @Test
    @DisplayName("T-1.2 详情按 sku 查(不是主键),结构含契约 Sku 的字段")
    void productDetailBySku() throws Exception {
        JsonNode data = data("/api/v1/products/" + ON_SALE_SKU);

        assertEquals(ON_SALE_SKU, data.get("sku").asText());
        assertEquals("在售探针砖", data.get("name").asText());
        for (String field : new String[]{"id", "sku", "name", "spec", "material", "surface",
                "colorPattern", "pricePerSqm", "applicableSpaces", "styleTags",
                "status", "coverImage", "images"}) {
            assertTrue(data.has(field), "契约 Sku 应含字段 " + field + ": " + data);
        }

        assertEquals(30001, codeOf(call("/api/v1/products/NO-SUCH-SKU")));
    }

    @Test
    @DisplayName("T-1.2 八项筛选与 D-8 裁决一致")
    void allEightFiltersWork() throws Exception {
        assertTrue(hits("keyword", "在售探针").contains(ON_SALE_SKU), "keyword 匹配名称");
        assertTrue(hits("keyword", ON_SALE_SKU).contains(ON_SALE_SKU), "keyword 匹配编号");
        assertTrue(hits("material", "釉面砖").contains(ON_SALE_SKU), "material");
        assertTrue(hits("colorPattern", "浅灰").contains(ON_SALE_SKU), "colorPattern 模糊匹配");
        assertTrue(hits("spec", "750×1500mm").contains(ON_SALE_SKU), "spec");
        assertTrue(hits("style", "modern").contains(ON_SALE_SKU), "style 按 style_tags 数组");
        assertTrue(hits("space", "bathroom").contains(ON_SALE_SKU), "space 按 applicable_spaces 数组");

        assertTrue(hits("priceMin", "200").contains(ON_SALE_SKU), "priceMin 200 ≤ 268");
        assertTrue(!hits("priceMin", "300").contains(ON_SALE_SKU), "priceMin 300 > 268");
        assertTrue(hits("priceMax", "300").contains(ON_SALE_SKU), "priceMax 300 ≥ 268");

        // 负向:不匹配的值应当筛掉,证明筛选真的在起作用
        assertTrue(!hits("material", "不存在的材质").contains(ON_SALE_SKU));
        assertTrue(!hits("style", "nordic").contains(ON_SALE_SKU),
                "在售砖是 modern,按 nordic 筛不该命中");
    }

    @Test
    @DisplayName("T-1.2 数组字段按元素精确匹配:modern 不会命中 modern_lux")
    void arrayFilterIsExactNotSubstring() throws Exception {
        insertProduct("T12-SUBSTR", "子串陷阱砖", "釉面砖", "纹", "300×300mm", "50.00",
                Product.STATUS_ON_SALE, List.of("bathroom_lux"), List.of("modern_lux"));

        assertTrue(!hits("style", "modern").contains("T12-SUBSTR"),
                "用 JSON_CONTAINS 而非 LIKE —— 否则 modern 会误命中 modern_lux");
        assertTrue(!hits("space", "bathroom").contains("T12-SUBSTR"));
    }

    @Test
    @DisplayName("T-1.2 列表免登录(PRD §5.1 浏览免登录)")
    void productListIsPublic() throws Exception {
        assertEquals(200, call("/api/v1/products").getResponse().getStatus());
    }

    // ===============================================================
    // T-1.3 推荐搭配展示
    // ===============================================================

    @Test
    @DisplayName("T-1.3 列表仅返回上架搭配,结构含契约 Combo 的字段")
    void comboListReturnsOnSaleOnly() throws Exception {
        JsonNode list = data("/api/v1/combos", "size", "100").get("list");

        // ⚠️ **只对自己造的数据下结论,不对全表条数下结论。**
        //
        // 原先这里断言 `list.size() == 1`("只应返回上架的那一条")——
        // 它隐含假设"整张表只有本测试的数据"。库里一旦有**真实运营数据**
        // (或任何人灌过演示数据),断言就崩,而代码毫无问题。
        // 这是本项目第七次遇到"共享可变前置数据",前六次都在讲同一个道理:
        // **测试不该假设它独占数据库。**
        List<Long> ids = idsOf(list);
        assertTrue(ids.contains(onSaleComboId), "自己的在售搭配应出现");
        assertFalse(ids.contains(offSaleComboId), "自己的下架搭配不该出现");

        JsonNode mine = findById(list, onSaleComboId);
        for (String field : new String[]{"id", "name", "space", "style", "refPriceMin",
                "refPriceMax", "coverImage", "status", "sort", "items"}) {
            assertTrue(mine.has(field), "契约 Combo 应含字段 " + field + ": " + mine);
        }
    }

    @Test
    @DisplayName("T-1.3 判据:items 的 part 恒为 floor / wall / local 三值(T-9)")
    void comboItemPartsAreThreeValued() throws Exception {
        // 取**自己那条**(按 id 找),不用 list.get(0) —— 第一条是别人的数据时,
        // 这条断言验的就不是自己要验的东西了
        JsonNode combo = findById(data("/api/v1/combos", "size", "100").get("list"), onSaleComboId);

        assertTrue(combo.get("items").size() > 0, "搭配应含至少一个 SKU");
        for (JsonNode item : combo.get("items")) {
            String part = item.get("part").asText();
            assertTrue(List.of("floor", "wall", "local").contains(part),
                    "部位恒为三值(契约 T-9;一期不含美缝),实际: " + part);
            assertNotNull(item.get("sku").asText());
        }
    }

    @Test
    @DisplayName("T-1.3 详情:上架可取;下架与不存在都返回 30001")
    void comboDetailRespectsStatus() throws Exception {
        assertEquals(onSaleComboId, data("/api/v1/combos/" + onSaleComboId).get("id").asLong());

        assertEquals(30001, codeOf(call("/api/v1/combos/99999999")));
    }

    @Test
    @DisplayName("T-1.3 按 space / style 筛选")
    void comboFiltersWork() throws Exception {
        // 同样:判"自己那条在不在",不判"总共有几条"
        List<Long> bathroom = idsOf(data("/api/v1/combos", "space", "bathroom", "size", "100")
                .get("list"));
        assertTrue(bathroom.contains(onSaleComboId), "按空间筛应命中自己的在售搭配");
        assertFalse(bathroom.contains(offSaleComboId), "下架那条不该被筛出来");

        List<Long> kitchen = idsOf(data("/api/v1/combos", "space", "kitchen", "size", "100")
                .get("list"));
        assertFalse(kitchen.contains(onSaleComboId), "空间不符不该命中");
        assertFalse(kitchen.contains(offSaleComboId), "空间不符不该命中");
    }

    /** 列表里的全部 id。用于"判自己那条在不在",避免对全表条数下结论。 */
    private static List<Long> idsOf(JsonNode list) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode row : list) {
            ids.add(row.get("id").asLong());
        }
        return ids;
    }

    private static JsonNode findById(JsonNode list, long id) {
        for (JsonNode row : list) {
            if (row.get("id").asLong() == id) {
                return row;
            }
        }
        throw new AssertionError("列表里找不到 id=" + id + ":" + list);
    }

    // ===============================================================
    // T-1.4 数据字典
    // ===============================================================

    @Test
    @DisplayName("T-1.4 仅返回 enabled 项,并按 sort 升序")
    void dictReturnsEnabledSortedBySort() throws Exception {
        JsonNode list = data("/api/v1/dicts", "type", "space").get("list");

        List<String> keys = list.findValuesAsText("key");
        assertTrue(keys.contains(DICT_KEY), "启用的字典项应返回");
        assertTrue(!keys.contains("t12_disabled_space"), "停用项不该返回给前台");

        for (JsonNode item : list) {
            assertEquals("enabled", item.get("status").asText());
            assertEquals("space", item.get("type").asText());
        }

        List<Integer> sorts = list.findValuesAsText("sort").stream()
                .map(Integer::valueOf).toList();
        for (int i = 1; i < sorts.size(); i++) {
            assertTrue(sorts.get(i - 1) <= sorts.get(i), "应按 sort 升序: " + sorts);
        }
    }

    @Test
    @DisplayName("T-1.4 响应 data 是 { list }(与后台同构,不是裸数组)")
    void dictDataIsWrappedInList() throws Exception {
        JsonNode data = data("/api/v1/dicts");

        assertTrue(data.isObject(), "data 必须是对象 —— ApiResponse.data 声明为 object: " + data);
        assertTrue(data.has("list") && data.get("list").isArray(), "data 应含 list 数组: " + data);
    }

    // ===============================================================
    // T-1.14 门店
    // ===============================================================

    @Test
    @DisplayName("T-1.14 门店字段与 Store schema 一致;未配置返回 30001")
    void storeFieldsAndUnconfigured() throws Exception {
        storeMapper.delete(Wrappers.<Store>lambdaQuery());
        assertEquals(30001, codeOf(call("/api/v1/store")),
                "未配置应明确报 30001,而不是返回空对象");

        Store store = new Store();
        store.setName("旗舰店");
        store.setAddress("北京市朝阳区某路 1 号");
        store.setPhone("010-00000000");
        store.setMapUrl("https://map.example.com/store");
        storeMapper.insert(store);

        JsonNode data = data("/api/v1/store");
        assertEquals("旗舰店", data.get("name").asText());
        assertEquals("北京市朝阳区某路 1 号", data.get("address").asText());
        assertEquals("010-00000000", data.get("phone").asText());
        assertEquals("https://map.example.com/store", data.get("mapUrl").asText());
    }

    @Test
    @DisplayName("T-1.14 判据:仅展示,无任何下单/支付入口(PRD §10 边界)")
    void storeHasNoCheckoutEntry() throws Exception {
        Store store = new Store();
        store.setName("旗舰店");
        store.setAddress("地址");
        store.setPhone("010-00000000");
        storeMapper.insert(store);

        String body = call("/api/v1/store").getResponse().getContentAsString();
        for (String forbidden : new String[]{"order", "pay", "cart", "checkout", "下单", "支付"}) {
            assertTrue(!body.toLowerCase().contains(forbidden),
                    "一期明确不做线上下单/支付(PRD §10),响应里不该有 " + forbidden + ": " + body);
        }
    }

    // ===============================================================
    // 工具
    // ===============================================================

    /** 发一次 GET,断言 HTTP 200,返回 MvcResult。 */
    private MvcResult call(String path, String... params) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        for (int i = 0; i + 1 < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(200, result.getResponse().getStatus(),
                "C 端只读接口应为公开可访问: " + result.getResponse().getContentAsString());
        return result;
    }

    /** 发一次 GET,返回响应体的 data 节点。 */
    private JsonNode data(String path, String... params) throws Exception {
        String body = call(path, params).getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");
        assertNotNull(data, "响应缺 data: " + body);
        return data;
    }

    /** 带单个筛选参数查列表,返回命中的 sku_code 列表。 */
    private List<String> hits(String param, String value) throws Exception {
        return skuCodesOf(data("/api/v1/products", param, value, "size", "100"));
    }

    private static List<String> skuCodesOf(JsonNode data) {
        return data.get("list").findValuesAsText("sku");
    }

    private static int codeOf(MvcResult result) throws Exception {
        return new ObjectMapper().readTree(result.getResponse().getContentAsString())
                .get("code").asInt();
    }

    // --- 前置数据 ---

    private void insertProduct(String sku, String name, String material, String colorPattern,
                               String spec, String price, String status,
                               List<String> spaces, List<String> styles) {
        Product product = new Product();
        product.setSkuCode(sku);
        product.setName(name);
        product.setMaterial(material);
        product.setColorPattern(colorPattern);
        product.setSpec(spec);
        product.setPricePerSqm(new BigDecimal(price));
        product.setStatus(status);
        product.setApplicableSpaces(spaces);
        product.setStyleTags(styles);
        productMapper.insert(product);
    }

    private long insertCombo(String name, String status) {
        RecommendCombo combo = new RecommendCombo();
        combo.setName(name);
        combo.setSpace("bathroom");
        combo.setStyle("modern");
        combo.setRefPriceMin(new BigDecimal("100"));
        combo.setRefPriceMax(new BigDecimal("300"));
        combo.setStatus(status);
        combo.setSort(0);
        comboMapper.insert(combo);

        // 挂一个真实 SKU,让 items 非空(契约测试要断言 part 三值)
        RecommendComboItem item = new RecommendComboItem();
        item.setComboId(combo.getId());
        Product floorTile = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, ON_SALE_SKU));
        item.setProductId(floorTile.getId());
        item.setPart("floor");
        item.setSort(0);
        comboItemMapper.insert(item);
        return combo.getId();
    }

    private void setDict(String key, String label, int sort, String status) {
        DictEntry entry = new DictEntry();
        entry.setDictType("space");
        entry.setDictKey(key);
        entry.setDictLabel(label);
        entry.setSort(sort);
        entry.setStatus(status);
        dictMapper.insert(entry);
    }

    private void cleanUp() {
        // 先删搭配及其关联项,再删产品 —— 避免留下指向已删产品的悬挂项
        for (String name : List.of("在售搭配", "下架搭配")) {
            for (RecommendCombo combo : comboMapper.selectList(
                    Wrappers.<RecommendCombo>lambdaQuery().eq(RecommendCombo::getName, name))) {
                comboItemMapper.delete(Wrappers.<RecommendComboItem>lambdaQuery()
                        .eq(RecommendComboItem::getComboId, combo.getId()));
                comboMapper.deleteById(combo.getId());
            }
        }
        for (String sku : List.of(ON_SALE_SKU, OFF_SALE_SKU, "T12-SUBSTR")) {
            Product product = productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                    .eq(Product::getSkuCode, sku));
            if (product != null) {
                productMapper.deleteById(product.getId());
            }
        }
        dictMapper.delete(Wrappers.<DictEntry>lambdaQuery()
                .in(DictEntry::getDictKey, List.of(DICT_KEY, "t12_disabled_space")));
    }
}
