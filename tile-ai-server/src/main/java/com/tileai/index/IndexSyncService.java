package com.tileai.index;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.combo.entity.RecommendCombo;
import com.tileai.common.mq.RabbitTopologyConfig;
import com.tileai.common.outbox.OutboxMessage;
import com.tileai.common.outbox.OutboxService;
import com.tileai.common.trace.TraceContext;
import com.tileai.product.entity.Product;

/**
 * 向量索引同步事件的产出(架构 §9、§15.2;异步契约 {@code queue.index.sync})。
 *
 * <p><b>调用点必须在业务事务内</b> —— 事件经 {@link OutboxService} 写入
 * {@code t_outbox},与业务数据同事务提交,这是 Outbox 模式成立的前提(§6.4)。
 *
 * <p><b>payload 里带全检索字段</b>(§9.2 的 Payload 列),让 Python 不必回查 MySQL
 * 就能更新索引 —— Python 不直连 MySQL,这是硬约束下的必要设计。
 *
 * <p><b>为什么不在 Qdrant 里更新</b>:Qdrant 的写权归 Python(§6.2)。
 * Java 只负责"把变更说清楚",怎么落到向量库是 Python 的事。
 */
@Service
public class IndexSyncService {

    /** 契约声明 {@code enqueuedAt} 为 date-time。 */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 契约声明 {@code attempt} 最小为 1。 */
    private static final int INITIAL_ATTEMPT = 1;

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    public IndexSyncService(OutboxService outboxService, ObjectMapper objectMapper) {
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 产品
    // ------------------------------------------------------------------

    /**
     * 产品新增 / 编辑 / 上下架 / 改价 → upsert。
     *
     * <p>上下架与改价也走这里:§9.3 要求"SKU 上下架/改价时,payload 的
     * {@code status}/{@code price} 同步更新"。
     */
    public void publishProductUpsert(Product product) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sku_code", product.getSkuCode());
        payload.put("name", product.getName());
        payload.put("material", product.getMaterial());
        payload.put("surface", product.getSurface());
        payload.put("style_tags", product.getStyleTags());
        payload.put("spaces", product.getApplicableSpaces());
        payload.put("price", product.getPricePerSqm());
        payload.put("status", product.getStatus());
        // §15.2 明确要求 payload 保留 updated_at,用于排查索引漂移
        payload.put("updated_at", LocalDateTime.now().format(ISO));

        publish(IndexSyncPayload.COLLECTION_PRODUCT, product.getSkuCode(),
                IndexSyncPayload.IndexSyncItem.ACTION_UPSERT, payload);
    }

    /** 产品删除 → delete(把点从索引中移除)。 */
    public void publishProductDelete(String skuCode) {
        publish(IndexSyncPayload.COLLECTION_PRODUCT, skuCode,
                IndexSyncPayload.IndexSyncItem.ACTION_DELETE, null);
    }

    // ------------------------------------------------------------------
    // 搭配
    // ------------------------------------------------------------------

    /** 搭配新增 / 编辑 / 上下架 → upsert。 */
    public void publishComboUpsert(RecommendCombo combo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("combo_id", String.valueOf(combo.getId()));
        payload.put("name", combo.getName());
        payload.put("space", combo.getSpace());
        payload.put("style", combo.getStyle());
        payload.put("status", combo.getStatus());
        payload.put("updated_at", LocalDateTime.now().format(ISO));

        publish(IndexSyncPayload.COLLECTION_COMBO, String.valueOf(combo.getId()),
                IndexSyncPayload.IndexSyncItem.ACTION_UPSERT, payload);
    }

    /** 搭配删除 → delete。 */
    public void publishComboDelete(long comboId) {
        publish(IndexSyncPayload.COLLECTION_COMBO, String.valueOf(comboId),
                IndexSyncPayload.IndexSyncItem.ACTION_DELETE, null);
    }

    // ------------------------------------------------------------------

    /**
     * 组装事件并登记到发件箱。
     *
     * <p><b>一个事件只带一个 item</b>:契约允许批量,但逐个事件更容易定位问题
     * (某条失败时能直接看出是哪条),也避免了"批量里一条坏掉整批重试"。
     * 全量重建那种场景才会用批量,走独立路径。
     */
    private void publish(String collection, String id, String action, Map<String, Object> payload) {
        IndexSyncPayload event = new IndexSyncPayload(
                UUID.randomUUID().toString().replace("-", ""),
                collection,
                List.of(new IndexSyncPayload.IndexSyncItem(id, action, payload)),
                TraceContext.getOrCreate(),
                INITIAL_ATTEMPT,
                LocalDateTime.now().format(ISO));

        outboxService.enqueue(OutboxMessage.AGGREGATE_INDEX_SYNC, event.eventId(),
                RabbitTopologyConfig.QUEUE_INDEX_SYNC, toJson(event));
    }

    private String toJson(IndexSyncPayload event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            // 序列化失败属于编码问题,不该被静默吞掉 —— 抛出去让业务事务回滚,
            // 否则会出现"数据改了但索引永远同步不了"且无人知晓。
            throw new IllegalStateException("索引同步事件无法序列化: " + event, e);
        }
    }
}
