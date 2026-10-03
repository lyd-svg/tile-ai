package com.tileai.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.admin.dto.OperationLogView;
import com.tileai.admin.entity.AdminUser;
import com.tileai.admin.entity.OperationLog;
import com.tileai.admin.mapper.AdminUserMapper;
import com.tileai.admin.mapper.OperationLogMapper;
import com.tileai.common.api.PageData;
import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

/**
 * 操作日志的查询(契约 {@code GET /admin/logs};架构 §8.1)。
 *
 * <p>日志<b>只读</b> —— 没有写接口,写入由 {@code AdminAuditAspect} 在带
 * {@code @AdminOperation} 的方法成功返回后完成。
 *
 * <p><b>这个类只做三件读时的事</b>,每件都值得说清楚:
 *
 * <ol>
 *   <li><b>把 {@code detail} 从 JSON 文本转成对象。</b>实体里是 {@code String},
 *       原样透出去会 JSON 被二次转义,前端解不出 {@code method}/{@code path}。
 *       刻意<b>不给实体加 typeHandler</b>:项目里已记录过 typeHandler 在
 *       {@code UpdateWrapper} 上不生效的坑,而写入方是切面 —— 改类型要连它一起动,
 *       收益却只是省一次反序列化。</li>
 *   <li><b>把操作人的用户名富化出来。</b>表里只有 {@code admin_id}。放服务端做是必须的:
 *       {@code log:read} 属于运营,而运营<b>没有</b> {@code account:manage},
 *       前端查不到账号列表(§8.1 的裁决)。已删除的账号查不到就给 {@code null},
 *       前端显示"已删除账号 #N"。</li>
 *   <li><b>纯日期补边界时刻。</b>{@code from=2026-10-03} 要按当日 00:00:00 起算,
 *       {@code to=2026-10-03} 要按当日 23:59:59 止 —— 否则"查到今天"会漏掉一整天,
 *       因为库里存的是带时刻的 DATETIME。</li>
 * </ol>
 */
@Service
public class AdminLogService {

    private static final Logger log = LoggerFactory.getLogger(AdminLogService.class);

    private final OperationLogMapper operationLogMapper;
    private final AdminUserMapper adminUserMapper;
    private final ObjectMapper objectMapper;

    public AdminLogService(OperationLogMapper operationLogMapper, AdminUserMapper adminUserMapper,
                           ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.adminUserMapper = adminUserMapper;
        this.objectMapper = objectMapper;
    }

    /** 分页查询(契约的 6 个 query 参数:adminId / action / from / to / page / size)。 */
    @Transactional(readOnly = true)
    public PageData<OperationLogView> page(Long adminId, String action, String from, String to,
                                           int page, int size) {
        LambdaQueryWrapper<OperationLog> query = Wrappers.lambdaQuery();
        if (adminId != null) {
            query.eq(OperationLog::getAdminId, adminId);
        }
        if (StringUtils.hasText(action)) {
            query.eq(OperationLog::getAction, action.trim());
        }
        LocalDateTime fromAt = parseBoundary(from, false);
        LocalDateTime toAt = parseBoundary(to, true);
        if (fromAt != null) {
            query.ge(OperationLog::getCreatedAt, fromAt);
        }
        if (toAt != null) {
            query.le(OperationLog::getCreatedAt, toAt);
        }
        // 按 id 倒序而不是 created_at:同一秒内的多条也能稳定排序,翻页不会跳行
        query.orderByDesc(OperationLog::getId);

        Page<OperationLog> result = operationLogMapper.selectPage(new Page<>(page, size), query);

        List<OperationLog> records = result.getRecords();
        Map<Long, String> usernames = usernamesOf(records);
        List<OperationLogView> list = records.stream()
                .map(row -> OperationLogView.of(row, usernameOf(usernames, row.getAdminId()),
                        parseDetail(row.getDetail())))
                .toList();

        return PageData.of(list, result.getTotal(), page, size);
    }

    /**
     * 本页出现的操作人 → 用户名,**一次查完**。
     *
     * <p>逐条去查就是一页 20 条 21 次查询(架构 §13.1 的 P95 撑不住)。
     * 查不到的 id(账号已被删除)不会出现在返回的 map 里 —— 调用方按"没有用户名"处理。
     */
    private Map<Long, String> usernamesOf(List<OperationLog> records) {
        List<Long> ids = records.stream().map(OperationLog::getAdminId)
                .filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return adminUserMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AdminUser::getId, AdminUser::getUsername));
    }

    /**
     * 取用户名。{@code adminId} 为空、或账号已删时返回 {@code null}。
     *
     * <p>这里的判空不是多余的:`Map.of()` 返回的不可变 map <b>对 {@code get(null)}
     * 直接抛 NPE</b>,而不是返回 null。日志里历史数据有 {@code admin_id} 为空的行。
     */
    private static String usernameOf(Map<Long, String> usernames, Long adminId) {
        return adminId == null ? null : usernames.get(adminId);
    }

    /**
     * 把 {@code from} / {@code to} 解析成时刻。
     *
     * <p>接受两种写法:**纯日期**({@code yyyy-MM-dd},按当日 00:00:00 / 23:59:59 解释)
     * 与 **ISO 时间**({@code yyyy-MM-ddTHH:mm[:ss]})。
     *
     * <p>解析不了就抛 {@code 10001} 而不是当成"没传":静默忽略一个写错的日期,
     * 会让用户看到一份"过滤了、但过滤条件其实没生效"的列表 —— 比报错更误导人。
     */
    private static LocalDateTime parseBoundary(String raw, boolean endOfDay) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        try {
            if (value.length() == 10) {
                LocalDate date = LocalDate.parse(value);
                return endOfDay
                        ? LocalDateTime.of(date, LocalTime.of(23, 59, 59))
                        : date.atStartOfDay();
            }
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException error) {
            throw new BusinessException(ErrorCode.PARAM_INVALID,
                    (endOfDay ? "to" : "from") + " 不是合法的时间:" + value);
        }
    }

    /**
     * 详情 JSON 文本 → 对象。
     *
     * <p>空的返回 {@code null};<b>坏的 JSON 也返回 {@code null} 并记一条警告</b>,
     * 不抛异常 —— 一行日志的详情坏了,不该让整页列表 500。审计记录本身(谁/何时/做了什么)
     * 还在,详情只是附加信息。
     */
    private Map<String, Object> parseDetail(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException error) {
            log.warn("操作日志的 detail 不是合法 JSON,本次按「无详情」返回:{}", json, error);
            return null;
        }
    }
}
