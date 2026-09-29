package com.tileai.common.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 的签发与校验(架构 §10.1 · D1 定稿)。
 *
 * <p>令牌<b>一律不进响应体</b> —— 本类只负责产出字符串,由
 * {@link AuthCookies} 写入 {@code Set-Cookie}。唯一的例外是注册临时令牌,
 * 它按契约走 {@code Authorization: Bearer} 返回给前端。
 *
 * <p>每个令牌都带 {@code jti}(唯一 ID),它是两件事的键:
 * 登出后进黑名单、临时令牌的一次性消费。
 */
@Service
public class JwtService {

    /** HS256 要求密钥至少 32 字节。 */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_SUBJECT_TYPE = "styp";

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props, Environment environment) {
        this.props = props;
        this.key = buildKey(props.getSecret(), environment);
    }

    public String issueAccess(long subjectId, SubjectType subjectType) {
        return issue(subjectId, subjectType, TokenType.ACCESS);
    }

    public String issueRefresh(long subjectId, SubjectType subjectType) {
        return issue(subjectId, subjectType, TokenType.REFRESH);
    }

    /**
     * 解析并校验令牌。
     *
     * <p>失败时抛业务异常而非返回 null —— 调用方不该有机会"忘了判空"而放行。
     * <b>过期</b>与<b>无效</b>要分开:前者引导前端去刷新,后者直接视为未登录。
     *
     * @throws BusinessException {@code 10003} 已过期;{@code 10002} 签名/格式/签发方不符
     */
    public ParsedToken parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(props.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return new ParsedToken(
                    Long.parseLong(claims.getSubject()),
                    SubjectType.valueOf(claims.get(CLAIM_SUBJECT_TYPE, String.class)),
                    TokenType.valueOf(claims.get(CLAIM_TYPE, String.class)),
                    claims.getId(),
                    claims.getExpiration().toInstant());
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // 不把底层原因透给前端(签名不符、格式错误等);排查靠 traceId + 日志。
            log.debug("令牌校验失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }

    public Duration ttlOf(TokenType type) {
        return switch (type) {
            case ACCESS -> props.getAccessTtl();
            case REFRESH -> props.getRefreshTtl();
        };
    }

    /** 令牌剩余有效期 —— 黑名单项只需存活到令牌自然过期,不必更久。 */
    public Duration remaining(ParsedToken token) {
        Duration left = Duration.between(Instant.now(), token.expiresAt());
        return left.isNegative() ? Duration.ZERO : left;
    }

    // ------------------------------------------------------------------

    private String issue(long subjectId, SubjectType subjectType, TokenType type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(props.getIssuer())
                .subject(Long.toString(subjectId))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TYPE, type.name())
                .claim(CLAIM_SUBJECT_TYPE, subjectType.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttlOf(type))))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 构建签名密钥。
     *
     * <p>密钥缺失时的行为<b>刻意分环境</b>:prod 直接启动失败,其余环境生成随机密钥
     * 并告警。不能给一个"看起来能用"的默认值 —— 那种默认值迟早会被带到生产。
     */
    private static SecretKey buildKey(String secret, Environment environment) {
        if (secret == null || secret.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException(
                        "生产环境必须提供 JWT_SECRET(环境变量),不得使用随机密钥 —— 见架构 §13.6");
            }
            log.warn("未配置 JWT_SECRET,已生成临时随机密钥。进程重启后所有令牌失效"
                    + "(仅适用于本地开发;生产环境会拒绝启动)");
            byte[] random = new byte[MIN_SECRET_BYTES];
            new SecureRandom().nextBytes(random);
            return Keys.hmacShaKeyFor(random);
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 至少需要 " + MIN_SECRET_BYTES + " 字节(HS256),当前 " + bytes.length + " 字节");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    /**
     * 解析结果。
     *
     * @param subjectId  主体 ID
     * @param subjectType 业主还是后台账号
     * @param tokenType  令牌类型
     * @param jti        唯一 ID —— 黑名单与一次性消费的键
     * @param expiresAt  过期时刻
     */
    public record ParsedToken(long subjectId, SubjectType subjectType, TokenType tokenType,
                              String jti, Instant expiresAt) {

        public AuthPrincipal toPrincipal() {
            return subjectType == SubjectType.ADMIN
                    ? AuthPrincipal.admin(subjectId)
                    : AuthPrincipal.user(subjectId);
        }
    }
}
