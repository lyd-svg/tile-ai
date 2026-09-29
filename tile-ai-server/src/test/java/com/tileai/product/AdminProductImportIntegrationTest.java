package com.tileai.product;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.common.security.JwtService;
import com.tileai.common.security.SubjectType;
import com.tileai.product.entity.Product;
import com.tileai.product.entity.ProductImage;
import com.tileai.product.imports.ImportBatch;
import com.tileai.product.imports.ImportColumn;
import com.tileai.product.mapper.ImportBatchMapper;
import com.tileai.product.mapper.ProductImageMapper;
import com.tileai.product.mapper.ProductMapper;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * T-0.7 验收:产品库批量导入的真实行为(架构 §8.7)。
 *
 * <p><b>需要 MySQL</b>。用 {@code -Ddb.verify=true} 显式启用。
 *
 * <p>逐条对应 T-0.7 的三条判据:
 * <ol>
 *   <li>含<b>故意错误行</b>的样本:错误行不写入、错误报告<b>行号准确</b>、修正后重传可成功;</li>
 *   <li>以 {@code sku_code} 为业务主键的 upsert <b>不产生重复数据</b>;</li>
 *   <li>同一文件重复导入结果<b>幂等</b>。</li>
 * </ol>
 *
 * <p><b>不标注 {@code @Transactional}</b> —— 导入是异步的,跑在另一个线程上,
 * 测试事务里的数据它看不见;而且这里要验的正是"数据真的落库了"。
 */
@EnabledIfSystemProperty(named = "db.verify", matches = "true",
        disabledReason = "需要 MySQL;用 -Ddb.verify=true 显式启用(T-0.7 验证)")
@SpringBootTest(properties = {"spring.flyway.enabled=false",
        "tile.auth.secret=0123456789abcdef0123456789abcdef"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminProductImportIntegrationTest {

    private static final String BASE = "/api/v1/admin/products/import";
    private static final String SKU_PREFIX = "T07-";
    /** 异步处理是本地线程池,几秒足够;超过就是真出问题了。 */
    private static final long IMPORT_TIMEOUT_MS = 15_000;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private ProductImageMapper productImageMapper;

    @Autowired
    private ImportBatchMapper importBatchMapper;

    private long adminId;
    private String token;
    private final List<String> createdBatchIds = new ArrayList<>();

    @BeforeAll
    void seedAdmin() {
        AdminUser admin = new AdminUser();
        admin.setUsername("t07_admin");
        admin.setPasswordHash("$2a$10$placeholder-not-used-because-no-login-here");
        admin.setRole("operator");
        admin.setStatus(AdminUser.STATUS_ENABLED);
        adminUserMapper.insert(admin);
        adminId = admin.getId();
        token = jwtService.issueAccess(adminId, SubjectType.ADMIN);
    }

    @AfterAll
    void cleanup() {
        List<Long> ids = productIds();
        if (!ids.isEmpty()) {
            productImageMapper.delete(Wrappers.<ProductImage>lambdaQuery()
                    .in(ProductImage::getProductId, ids));
            productMapper.deleteByIds(ids);
        }
        if (!createdBatchIds.isEmpty()) {
            importBatchMapper.delete(Wrappers.<ImportBatch>lambdaQuery()
                    .in(ImportBatch::getBatchId, createdBatchIds));
        }
        adminUserMapper.deleteById(adminId);
    }

    // ---------------------------------------------------------------
    // 判据一:错误行不写入、行号准确、修正后可成功
    // ---------------------------------------------------------------

    @Test
    @DisplayName("含错误行的样本:错误行不写入,错误报告行号与文件行号一致")
    void badRowsAreReportedWithAccurateLineNumbers() throws Exception {
        // 文件行号:1=表头, 2=T07-A, 3=缺名称, 4=T07-B, 5=单价非法
        byte[] csv = csv(
                row("T07-A", "第一款", "100.00"),
                rowMissingName("T07-BAD-NONAME"),
                row("T07-B", "第二款", "200.00"),
                row("T07-BAD-PRICE", "坏单价", "abc"));

        JsonNode status = runImport(csv, "upsert");

        assertEquals(4, status.get("total").asInt());
        assertEquals(2, status.get("successCount").asInt(), "两行应成功");
        assertEquals(2, status.get("failedCount").asInt(), "两行应失败");

        List<Integer> errorRows = errorRows(status);
        assertEquals(List.of(3, 5), errorRows,
                "错误行号必须与文件里的真实行号一致(含表头),否则运营照着找不到那一行");

        assertTrue(reasonOf(status, 3).contains("产品名称"), "行 3 应报缺名称: " + status);
        assertTrue(reasonOf(status, 5).contains("单价"), "行 5 应报单价非法: " + status);

        // 错误行绝不写入
        assertNotNull(findProduct("T07-A"));
        assertNotNull(findProduct("T07-B"));
        assertEquals(null, findProduct("T07-BAD-NONAME"), "错误行不得写入");
        assertEquals(null, findProduct("T07-BAD-PRICE"), "错误行不得写入");
    }

    @Test
    @DisplayName("修正后重传:原先失败的行变为成功")
    void fixedFileCanBeReimported() throws Exception {
        byte[] broken = csv(
                row("T07-FIX", "待修款", "100.00"),
                rowMissingName("T07-FIX2"));
        JsonNode first = runImport(broken, "upsert");
        assertEquals(1, first.get("failedCount").asInt());
        assertEquals(null, findProduct("T07-FIX2"));

        byte[] fixed = csv(
                row("T07-FIX", "待修款", "100.00"),
                row("T07-FIX2", "修好了", "200.00"));
        JsonNode second = runImport(fixed, "upsert");

        assertEquals(0, second.get("failedCount").asInt(), "修正后应无失败行");
        assertNotNull(findProduct("T07-FIX2"), "修正后的行应写入");
    }

    // ---------------------------------------------------------------
    // 判据二:upsert 不产生重复数据
    // ---------------------------------------------------------------

    @Test
    @DisplayName("upsert:同编号再导入是更新而非新增,不产生重复行")
    void upsertUpdatesInsteadOfDuplicating() throws Exception {
        runImport(csv(row("T07-UPSERT", "原名称", "100.00")), "upsert");
        Long firstId = idOf("T07-UPSERT");
        assertNotNull(firstId);

        runImport(csv(row("T07-UPSERT", "改后名称", "188.88")), "upsert");

        assertEquals(1, countBySku("T07-UPSERT"), "同编号必须只有一条记录");
        assertEquals(firstId, idOf("T07-UPSERT"), "upsert 应是更新同一条,而不是删了重建");
        assertEquals("改后名称", findProduct("T07-UPSERT").getName());
    }

    @Test
    @DisplayName("insert_only:已存在的编号记为错误行,不覆盖既有数据")
    void insertOnlyRejectsExistingSku() throws Exception {
        runImport(csv(row("T07-IO", "原有款", "100.00")), "upsert");

        JsonNode status = runImport(csv(row("T07-IO", "想覆盖", "999.00")), "insert_only");

        assertEquals(1, status.get("failedCount").asInt());
        assertTrue(reasonOf(status, 2).contains("已存在"), status.toString());
        assertEquals("原有款", findProduct("T07-IO").getName(),
                "仅新增模式下绝不能改动既有数据");
    }

    // ---------------------------------------------------------------
    // 判据三:重复导入幂等
    // ---------------------------------------------------------------

    @Test
    @DisplayName("同一文件导入两次,结果完全一致(幂等)")
    void repeatedImportIsIdempotent() throws Exception {
        byte[] content = csv(
                row("T07-IDEM-A", "幂等款A", "100.00"),
                row("T07-IDEM-B", "幂等款B", "200.00"));

        JsonNode first = runImport(content, "upsert");
        long countAfterFirst = countBySkuPrefix("T07-IDEM-");

        JsonNode second = runImport(content, "upsert");

        assertEquals(first.get("successCount").asInt(), second.get("successCount").asInt());
        assertEquals(first.get("failedCount").asInt(), second.get("failedCount").asInt());
        assertEquals(countAfterFirst, countBySkuPrefix("T07-IDEM-"),
                "重复导入不得产生重复记录");
    }

    // ---------------------------------------------------------------
    // 其它
    // ---------------------------------------------------------------

    @Test
    @DisplayName("文件内编号自身重复:后一条记为错误行")
    void duplicateSkuWithinFileIsReported() throws Exception {
        byte[] csv = csv(
                row("T07-DUP", "第一条", "100.00"),
                row("T07-DUP", "第二条", "200.00"));

        JsonNode status = runImport(csv, "upsert");

        assertEquals(1, status.get("successCount").asInt());
        assertEquals(1, status.get("failedCount").asInt());
        assertTrue(reasonOf(status, 3).contains("重复"), status.toString());
    }

    @Test
    @DisplayName("批次不存在 → 30001")
    void unknownBatchIsNotFound() throws Exception {
        MvcResult result = mvc.perform(get(BASE + "/no-such-batch")
                .cookie(new Cookie("access_token", token))).andReturn();

        assertEquals(200, result.getResponse().getStatus());
        assertEquals(30001, codeOf(result));
    }

    @Test
    @DisplayName("非法 mode → 10001")
    void invalidModeIsRejected() throws Exception {
        MvcResult result = mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", "p.csv", "text/csv",
                                csv(row("T07-MODE", "x", "1.00"))))
                        .param("mode", "whatever")
                        .cookie(new Cookie("access_token", token)))
                .andReturn();

        assertEquals(10001, codeOf(result));
    }

    @Test
    @DisplayName("模板可下载:xlsx 与 CSV 两种内容类型都能取到")
    void templateIsDownloadable() throws Exception {
        MvcResult xlsx = mvc.perform(get(BASE + "/template")
                .cookie(new Cookie("access_token", token))).andReturn();
        assertEquals(200, xlsx.getResponse().getStatus());
        assertTrue(xlsx.getResponse().getContentAsByteArray().length > 0);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsx.getResponse().getContentType());

        MvcResult csvResult = mvc.perform(get(BASE + "/template")
                .header("Accept", "text/csv")
                .cookie(new Cookie("access_token", token))).andReturn();
        assertEquals(200, csvResult.getResponse().getStatus());
        assertTrue(csvResult.getResponse().getContentType().contains("text/csv"),
                "Accept: text/csv 时应返回 CSV: " + csvResult.getResponse().getContentType());
    }

    @Test
    @DisplayName("导出的模板可以直接被导入(往返闭环)")
    void exportedTemplateCanBeImported() throws Exception {
        MvcResult template = mvc.perform(get(BASE + "/template")
                .cookie(new Cookie("access_token", token))).andReturn();

        // 模板自带一行示例(TL-2043),直接导它应当成功而不是报"缺列"
        JsonNode status = runImport(template.getResponse().getContentAsByteArray(), "upsert",
                "template.xlsx");
        assertEquals(0, status.get("failedCount").asInt(),
                "模板自身应能被导入: " + status);

        productMapper.delete(Wrappers.<Product>lambdaQuery().eq(Product::getSkuCode, "TL-2043"));
    }

    // ---------------------------------------------------------------
    // 工具
    // ---------------------------------------------------------------

    /** 上传并轮询到批次结束。返回 ImportStatus 的 data 节点。 */
    private JsonNode runImport(byte[] content, String mode) throws Exception {
        return runImport(content, mode, "products.csv");
    }

    private JsonNode runImport(byte[] content, String mode, String fileName) throws Exception {
        MvcResult accepted = mvc.perform(multipart(BASE)
                        .file(new MockMultipartFile("file", fileName, "application/octet-stream", content))
                        .param("mode", mode)
                        .cookie(new Cookie("access_token", token)))
                .andReturn();

        assertEquals(200, accepted.getResponse().getStatus(),
                accepted.getResponse().getContentAsString());
        JsonNode data = dataOf(accepted);
        String batchId = data.get("batchId").asText();
        createdBatchIds.add(batchId);

        return awaitDone(batchId);
    }

    private JsonNode awaitDone(String batchId) throws Exception {
        long deadline = System.currentTimeMillis() + IMPORT_TIMEOUT_MS;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = dataOf(mvc.perform(get(BASE + "/" + batchId)
                    .cookie(new Cookie("access_token", token))).andReturn());
            String status = last.get("status").asText();
            if (!"pending".equals(status) && !"running".equals(status)) {
                assertEquals("success", status, "批次应以 success 结束: " + last);
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("导入未在 " + IMPORT_TIMEOUT_MS + "ms 内完成,最后状态: " + last);
    }

    private static List<Integer> errorRows(JsonNode status) {
        List<Integer> rows = new ArrayList<>();
        status.get("errors").forEach(e -> rows.add(e.get("row").asInt()));
        return rows;
    }

    private static String reasonOf(JsonNode status, int rowNumber) {
        for (JsonNode e : status.get("errors")) {
            if (e.get("row").asInt() == rowNumber) {
                return e.get("reason").asText();
            }
        }
        return "";
    }

    private byte[] csv(String[]... dataRows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", ImportColumn.headers())).append("\r\n");
        for (String[] dataRow : dataRows) {
            sb.append(String.join(",", dataRow)).append("\r\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** 按列顺序拼一行;未给的列留空。 */
    private String[] row(Map<ImportColumn, String> values) {
        return Arrays.stream(ImportColumn.values())
                .map(column -> values.getOrDefault(column, ""))
                .toArray(String[]::new);
    }

    private String[] row(String sku, String name, String price) {
        Map<ImportColumn, String> values = new LinkedHashMap<>();
        values.put(ImportColumn.SKU, sku);
        values.put(ImportColumn.NAME, name);
        values.put(ImportColumn.PRICE_PER_SQM, price);
        return row(values);
    }

    private String[] rowMissingName(String sku) {
        Map<ImportColumn, String> values = new LinkedHashMap<>();
        values.put(ImportColumn.SKU, sku);
        values.put(ImportColumn.PRICE_PER_SQM, "100.00");
        return row(values);
    }

    private Product findProduct(String skuCode) {
        return productMapper.selectOne(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, skuCode));
    }

    private Long idOf(String skuCode) {
        Product product = findProduct(skuCode);
        return product == null ? null : product.getId();
    }

    private long countBySku(String skuCode) {
        return productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .eq(Product::getSkuCode, skuCode));
    }

    private long countBySkuPrefix(String prefix) {
        return productMapper.selectCount(Wrappers.<Product>lambdaQuery()
                .likeRight(Product::getSkuCode, prefix));
    }

    private List<Long> productIds() {
        return productMapper.selectList(Wrappers.<Product>lambdaQuery()
                        .likeRight(Product::getSkuCode, SKU_PREFIX))
                .stream().map(Product::getId).toList();
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
}
