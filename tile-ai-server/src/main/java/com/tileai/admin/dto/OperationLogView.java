package com.tileai.admin.dto;

import java.time.LocalDateTime;
import java.util.Map;

import com.tileai.admin.entity.OperationLog;

/**
 * 操作日志的对外视图(契约 {@code openapi-common.yaml#/components/schemas/OperationLogView},
 * {@code required: [id, action, createdAt]})。
 *
 * <p><b>两个字段值得单独说</b>:
 *
 * <p>① {@link #adminUsername} —— <b>表里不存</b>,是读时按 {@code adminId} 富化的
 * (架构 §8.1)。富化必须放在服务端:看日志要的是 {@code log:read},而运营有这个权限、
 * <b>却没有</b> {@code account:manage},所以前端根本查不出 id 对应谁。
 * 账号已被物理删除时这里为 {@code null},前端兜底显示"已删除账号 #N"。
 *
 * <p>② {@link #detail} 是 <b>对象</b>而不是字符串。实体里 {@code detail} 存的是 JSON 文本,
 * 若原样透出去,Jackson 会把它序列化成一个<b>转义后的字符串</b>(契约要求 {@code type: object}),
 * 前端也就解不出 {@code method}/{@code path}。转换在
 * {@link com.tileai.admin.AdminLogService} 里做。
 */
public record OperationLogView(
        Long id,
        Long adminId,
        String adminUsername,
        String action,
        String targetType,
        String targetId,
        Map<String, Object> detail,
        String ip,
        LocalDateTime createdAt) {

    /**
     * 由实体 + 富化出来的用户名 + 已解析的详情组装。
     *
     * <p>映射只此一处:实体以后再长字段,也不会"哪里顺手漏了一个"。
     */
    public static OperationLogView of(OperationLog log, String adminUsername,
                                      Map<String, Object> detail) {
        return new OperationLogView(log.getId(), log.getAdminId(), adminUsername, log.getAction(),
                log.getTargetType(), log.getTargetId(), detail, log.getIp(), log.getCreatedAt());
    }
}
