package com.tileai.product.imports;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tileai.admin.entity.DictEntry;
import com.tileai.admin.mapper.DictMapper;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.product.entity.Product;
import com.tileai.product.mapper.ImportBatchMapper;
import com.tileai.product.mapper.ProductMapper;

/**
 * 产品库批量导入(架构 §8.7;契约 {@code /admin/products/import*} 三个接口)。
 *
 * <h2>同步解析 + 异步落库</h2>
 *
 * <p>契约说导入是"异步任务"。这里的切分是:<b>文件解析同步做,逐行落库异步做</b>。
 * <ul>
 *   <li>解析同步,是为了让返回的批次<b>立刻带上 total</b> —— 否则运营刚提交就看到
 *       "总行数 0",会以为文件没传上去;</li>
 *   <li>落库异步,是因为逐行 upsert 是真正耗时的部分(几千行可能要几十秒),
 *       不能占着 HTTP 连接。</li>
 * </ul>
 * 代价:超大文件的解析仍会占用请求线程。一期的产品库规模(数千 SKU)下可忽略;
 * 若日后要导十万级,应改为"先落盘再异步解析"并给 {@code t_import_batch} 加文件路径列。
 *
 * <h2>错误行的处理(§8.7)</h2>
 *
 * <p>「错误行不写入」——单行失败只记入错误报告,不影响其它行,也不让整个请求失败。
 * 因此批次状态用 {@code success} 表示"处理完成",具体成败看
 * {@code successCount} / {@code failedCount};{@code failed} 只留给
 * "整个批次崩了"(如文件解析异常)。
 */
@Service
public class ProductImportService {

    private static final Logger log = LoggerFactory.getLogger(ProductImportService.class);

    /** 与 §8.2 列宽一致的上限,超出的值会在写库前被拦下并给出可读原因。 */
    private static final int MAX_SKU = 64;
    private static final int MAX_NAME = 200;
    private static final int MAX_SPEC = 100;
    private static final int MAX_MATERIAL = 50;
    private static final int MAX_SURFACE = 50;
    private static final int MAX_COLOR_PATTERN = 200;
    private static final int MAX_COVER_IMAGE = 500;
    private static final int MAX_MULTI_VALUE = 50;
    private static final int MAX_IMAGES = 20;

    /** {@code t_product.price_per_sqm} 是 DECIMAL(10,2)。 */
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    /** 批次级错误(而非某一行)在错误报告里的行号。 */
    private static final int BATCH_LEVEL_ROW = 0;

    private final SpreadsheetReader spreadsheetReader;
    private final ProductImportRowWriter rowWriter;
    private final ImportBatchMapper importBatchMapper;
    private final ProductMapper productMapper;
    private final DictMapper dictMapper;
    private final TaskExecutor importExecutor;

    public ProductImportService(SpreadsheetReader spreadsheetReader,
                                ProductImportRowWriter rowWriter,
                                ImportBatchMapper importBatchMapper,
                                ProductMapper productMapper,
                                DictMapper dictMapper,
                                @Qualifier(ImportAsyncConfig.EXECUTOR_BEAN) TaskExecutor importExecutor) {
        this.spreadsheetReader = spreadsheetReader;
        this.rowWriter = rowWriter;
        this.importBatchMapper = importBatchMapper;
        this.productMapper = productMapper;
        this.dictMapper = dictMapper;
        this.importExecutor = importExecutor;
    }

    // ------------------------------------------------------------------

    /**
     * 受理一次导入,返回批次。
     *
     * <p><b>刻意不加 {@code @Transactional}</b>:批次记录必须<b>先提交</b>再派发异步任务,
     * 否则异步线程可能在事务提交前就去查批次,查不到 —— 表现为"任务凭空消失"。
     * 单条 insert 本身自带提交,不需要外层事务。
     */
    public ImportBatch accept(MultipartFile file, String modeCode) {
        ImportMode mode = ImportMode.of(modeCode);
        if (mode == null) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    "导入模式只能是 upsert 或 insert_only");
        }

        SpreadsheetTable table = spreadsheetReader.read(file);

        ImportBatch batch = new ImportBatch();
        batch.setBatchId(UUID.randomUUID().toString().replace("-", ""));
        batch.setBizType(ImportBatch.BIZ_PRODUCT);
        batch.setMode(mode.code());
        batch.setStatus(ImportBatch.STATUS_PENDING);
        batch.setTotalCount(table.rows().size());
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        importBatchMapper.insert(batch);

        String batchId = batch.getBatchId();
        importExecutor.execute(() -> process(batchId, table, mode));

        return batch;
    }

    /** 查询批次(契约 {@code GET /admin/products/import/{batchId}};失败码 30001)。 */
    public ImportBatch find(String batchId) {
        ImportBatch batch = importBatchMapper.selectOne(
                Wrappers.<ImportBatch>lambdaQuery().eq(ImportBatch::getBatchId, batchId));
        if (batch == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "导入批次不存在: " + batchId);
        }
        return batch;
    }

    // ------------------------------------------------------------------

    private void process(String batchId, SpreadsheetTable table, ImportMode mode) {
        try {
            updateBatch(batchId, ImportBatch.STATUS_RUNNING, null);
            DictIndex dict = loadDictIndex();
            Outcome outcome = applyRows(table, mode, dict);
            updateBatch(batchId, ImportBatch.STATUS_SUCCESS, outcome);
            log.info("批量导入完成 batchId={} 成功={} 失败={}",
                    batchId, outcome.successCount(), outcome.errors().size());
        } catch (Exception e) {
            // 整批崩了(如字典查询失败)。异常消息只截断后对外,细节进日志。
            log.error("批量导入异常终止 batchId={}", batchId, e);
            safeUpdateBatch(batchId, ImportBatch.STATUS_FAILED,
                    new Outcome(0, List.of(new ImportRowError(BATCH_LEVEL_ROW,
                            "导入过程异常:" + rootMessage(e)))));
        }
    }

    private Outcome applyRows(SpreadsheetTable table, ImportMode mode, DictIndex dict) {
        Map<ImportColumn, Integer> columns = table.columnIndexes();

        // 一次性把文件里出现过的编号对应的既有记录捞出来 —— 避免逐行查库。
        // 文件有几千行时,这是"1 次查询"与"几千次查询"的差别。
        Set<String> skusInFile = table.rows().stream()
                .map(row -> row.value(columns, ImportColumn.SKU))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, Long> existingIds = skusInFile.isEmpty() ? Map.of()
                : productMapper.selectList(Wrappers.<Product>lambdaQuery()
                                .in(Product::getSkuCode, skusInFile))
                        .stream()
                        .collect(Collectors.toMap(Product::getSkuCode, Product::getId, (a, b) -> a));

        Set<String> seenInFile = new HashSet<>();
        List<ImportRowError> errors = new ArrayList<>();
        int successCount = 0;

        for (SpreadsheetTable.Row row : table.rows()) {
            try {
                ProductImportData data = parseRow(row, columns, dict);

                if (!seenInFile.add(data.sku())) {
                    throw new ImportRowException("文件内产品编号重复:" + data.sku());
                }

                Long existingId = existingIds.get(data.sku());
                if (mode == ImportMode.INSERT_ONLY && existingId != null) {
                    throw new ImportRowException("产品编号已存在,仅新增模式不覆盖");
                }

                rowWriter.apply(data, existingId);
                successCount++;
            } catch (ImportRowException e) {
                // 预期内的行级失败:记入报告,继续处理下一行
                errors.add(new ImportRowError(row.rowNumber(), e.getMessage()));
            } catch (Exception e) {
                // 意外失败(如数据库约束)也按行隔离,不能让一行拖垮整批
                log.warn("导入行 {} 落库失败", row.rowNumber(), e);
                errors.add(new ImportRowError(row.rowNumber(), "写入失败:" + rootMessage(e)));
            }
        }
        return new Outcome(successCount, errors);
    }

    /** 解析并校验一行。任何问题都抛 {@link ImportRowException},由调用方转成错误报告。 */
    private ProductImportData parseRow(SpreadsheetTable.Row row,
                                       Map<ImportColumn, Integer> columns,
                                       DictIndex dict) {
        String sku = required(row, columns, ImportColumn.SKU, MAX_SKU);
        String name = required(row, columns, ImportColumn.NAME, MAX_NAME);
        BigDecimal price = parsePrice(required(row, columns, ImportColumn.PRICE_PER_SQM, 32));

        String spec = optional(row, columns, ImportColumn.SPEC, MAX_SPEC);
        String material = optional(row, columns, ImportColumn.MATERIAL, MAX_MATERIAL);
        String surface = optional(row, columns, ImportColumn.SURFACE, MAX_SURFACE);
        String colorPattern = optional(row, columns, ImportColumn.COLOR_PATTERN, MAX_COLOR_PATTERN);
        String coverImage = optional(row, columns, ImportColumn.COVER_IMAGE, MAX_COVER_IMAGE);

        List<String> spaces = multiValues(row, columns, ImportColumn.APPLICABLE_SPACES);
        dict.validate(spaces, DictEntry.TYPE_SPACE, ImportColumn.APPLICABLE_SPACES);

        List<String> styles = multiValues(row, columns, ImportColumn.STYLE_TAGS);
        dict.validate(styles, DictEntry.TYPE_STYLE, ImportColumn.STYLE_TAGS);

        List<String> imageUrls = multiValues(row, columns, ImportColumn.IMAGE_URLS);
        for (String url : imageUrls) {
            if (url.length() > MAX_COVER_IMAGE) {
                throw new ImportRowException(ImportColumn.IMAGE_URLS.header() + " 中存在过长的 URL");
            }
        }
        if (imageUrls.size() > MAX_IMAGES) {
            throw new ImportRowException(ImportColumn.IMAGE_URLS.header() + " 最多 " + MAX_IMAGES + " 条");
        }

        return new ProductImportData(sku, name, spec, material, surface, colorPattern,
                price, spaces, styles, coverImage, imageUrls);
    }

    private static String required(SpreadsheetTable.Row row, Map<ImportColumn, Integer> columns,
                                   ImportColumn column, int maxLength) {
        String value = row.value(columns, column);
        if (value == null) {
            throw new ImportRowException("缺少必填项:" + column.header());
        }
        if (value.length() > maxLength) {
            throw new ImportRowException(column.header() + " 超过 " + maxLength + " 字符");
        }
        return value;
    }

    private static String optional(SpreadsheetTable.Row row, Map<ImportColumn, Integer> columns,
                                   ImportColumn column, int maxLength) {
        String value = row.value(columns, column);
        if (value != null && value.length() > maxLength) {
            throw new ImportRowException(column.header() + " 超过 " + maxLength + " 字符");
        }
        return value;
    }

    private static BigDecimal parsePrice(String raw) {
        BigDecimal price;
        try {
            price = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new ImportRowException("单价不是合法数字:" + raw);
        }
        if (price.signum() < 0) {
            throw new ImportRowException("单价不能为负:" + raw);
        }
        if (price.compareTo(MAX_PRICE) > 0) {
            throw new ImportRowException("单价超出上限:" + raw);
        }
        return price.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /** 拆多值单元格;空白项被丢弃。 */
    private static List<String> multiValues(SpreadsheetTable.Row row,
                                            Map<ImportColumn, Integer> columns,
                                            ImportColumn column) {
        String raw = row.value(columns, column);
        if (raw == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String part : raw.split(ImportColumn.MULTI_VALUE_SEPARATOR)) {
            String value = part.trim();
            if (value.isEmpty()) {
                continue;
            }
            if (value.length() > MAX_MULTI_VALUE) {
                throw new ImportRowException(column.header() + " 中的值过长:" + value);
            }
            values.add(value);
        }
        return values;
    }

    /**
     * 载入字典索引。
     *
     * <p><b>字典为空时跳过该项校验</b> —— 这与"静默放过"不同,是刻意的:
     * 字典由 T-0.11 的后台维护,一期上线初期完全可能是空的;
     * 若在空字典上执行严格校验,整个导入会全行失败,而运营根本无法自行解决
     * (他们还没地方配字典)。一旦字典配好,校验立即生效。
     */
    private DictIndex loadDictIndex() {
        List<DictEntry> entries = dictMapper.selectList(Wrappers.<DictEntry>lambdaQuery()
                .eq(DictEntry::getStatus, DictEntry.STATUS_ENABLED)
                .in(DictEntry::getDictType, List.of(DictEntry.TYPE_SPACE, DictEntry.TYPE_STYLE)));

        Set<String> spaces = entries.stream()
                .filter(e -> DictEntry.TYPE_SPACE.equals(e.getDictType()))
                .map(DictEntry::getDictKey).collect(Collectors.toSet());
        Set<String> styles = entries.stream()
                .filter(e -> DictEntry.TYPE_STYLE.equals(e.getDictType()))
                .map(DictEntry::getDictKey).collect(Collectors.toSet());
        return new DictIndex(spaces, styles);
    }

    /**
     * 更新批次状态。
     *
     * <p><b>必须走实体更新,不能用 {@code LambdaUpdateWrapper.set(...)}。</b>
     * {@code t_import_batch.errors} 是 JSON 列,靠 {@code @TableField(typeHandler=...)}
     * 完成 {@code List<ImportRowError>} ↔ JSON 的转换 —— 而这个 typeHandler
     * <b>只在实体字段参与时生效</b>。用 wrapper 的 {@code .set()} 会把 List
     * 原样交给 JDBC 驱动,抛 {@code NotSerializableException: ImportRowError}。
     *
     * <p>这个坑的代价尤其大:失败路径上也要写 {@code errors},所以一旦踩中,
     * <b>连"把批次标记为 failed"都做不到</b>,批次会永远停在 {@code running},
     * 前端无限轮询。下面的 {@link #safeUpdateBatch} 就是为这种情况兜底。
     */
    private void updateBatch(String batchId, String status, Outcome outcome) {
        ImportBatch batch = importBatchMapper.selectOne(
                Wrappers.<ImportBatch>lambdaQuery().eq(ImportBatch::getBatchId, batchId));
        if (batch == null) {
            log.warn("批次已不存在,无法更新状态 batchId={} status={}", batchId, status);
            return;
        }
        batch.setStatus(status);
        if (outcome != null) {
            batch.setSuccessCount(outcome.successCount());
            batch.setFailedCount(outcome.errors().size());
            batch.setErrors(outcome.errors());
        }
        importBatchMapper.updateById(batch);
    }

    /**
     * 兜底更新:更新状态本身失败时,至少留下一条日志。
     * 不能让"更新失败"把异步线程直接抛死 —— 那样批次状态永远停在 running,
     * 调用方无从判断到底发生了什么。
     */
    private void safeUpdateBatch(String batchId, String status, Outcome outcome) {
        try {
            updateBatch(batchId, status, outcome);
        } catch (Exception e) {
            log.error("更新批次状态失败 batchId={} status={} —— 批次可能停留在中间状态", batchId, status, e);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName()
                : message.substring(0, Math.min(message.length(), 200));
    }

    private record Outcome(int successCount, List<ImportRowError> errors) {
    }

    /** 字典索引。集合为空表示"该类型尚未配置",此时不做校验。 */
    private record DictIndex(Set<String> spaces, Set<String> styles) {

        void validate(List<String> values, String dictType, ImportColumn column) {
            Set<String> allowed = DictEntry.TYPE_SPACE.equals(dictType) ? spaces : styles;
            if (allowed.isEmpty()) {
                return;
            }
            for (String value : values) {
                if (!allowed.contains(value)) {
                    throw new ImportRowException(
                            column.header() + " 含字典中不存在的值:" + value);
                }
            }
        }
    }
}
