package com.tileai.index;

import java.util.List;
import java.util.Map;

/**
 * 向量索引同步事件(异步契约 {@code asyncapi-internal.yaml#/components/schemas/IndexSyncPayload})。
 *
 * <p>Java 产出、Python 消费,由 Python 更新 Qdrant(架构 §6.2)。一个事件通常对应
 * 一个聚合变更,也允许批量(全量重建后的批量补发)。
 *
 * <p><b>字段名是 snake_case</b>({@code eventId} / {@code enqueuedAt} 除外)——
 * 这是<b>内部</b>契约,不遵循对外 API 的 camelCase 约定;契约怎么写就怎么来。
 */
public record IndexSyncPayload(
        String eventId,
        String collection,
        List<IndexSyncItem> items,
        String traceId,
        Integer attempt,
        String enqueuedAt) {

    /** 目标集合,对应架构 §9.2 的三张表。 */
    public static final String COLLECTION_PRODUCT = "product";
    public static final String COLLECTION_COMBO = "combo";
    public static final String COLLECTION_KB = "kb";

    /**
     * 单条索引变更。
     *
     * <p>{@code payload} 携带该集合的**检索过滤字段**(架构 §9.2),使 Python
     * <b>无需回查 MySQL</b> 即可更新索引 —— 这是"Qdrant 为派生索引、Python 不直连 MySQL"
     * 约束下的必要设计(契约里对该字段的说明写明了这一点)。
     *
     * @param id     该集合的业务主键:product 用 {@code sku_code}、combo 用 {@code combo_id}
     * @param action {@code upsert} 或 {@code delete}
     */
    public record IndexSyncItem(String id, String action, Map<String, Object> payload) {

        public static final String ACTION_UPSERT = "upsert";
        public static final String ACTION_DELETE = "delete";

        public boolean isUpsert() {
            return ACTION_UPSERT.equals(action);
        }
    }
}
