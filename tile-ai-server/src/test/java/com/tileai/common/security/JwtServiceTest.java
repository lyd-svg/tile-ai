package com.tileai.common.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.security.JwtService.ParsedToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.4:JWT 签发与校验的单元测试。
 *
 * <p>重点是两条<b>隔离</b>,它们一旦破了,整个会话方案就形同虚设:
 * <ul>
 *   <li>令牌<b>类型</b>隔离 —— refresh(14 天)不能当 access(2 小时)用;</li>
 *   <li>令牌<b>主体</b>隔离 —— 业主令牌不能变成后台令牌。</li>
 * </ul>
 */
class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static JwtService service(JwtProperties props) {
        return new JwtService(props, new StandardEnvironment());
    }

    private static JwtProperties props() {
        JwtProperties p = new JwtProperties();
        p.setSecret(SECRET);
        return p;
    }

    @Test
    @DisplayName("签发后能解析回同样的主体与类型")
    void roundTrip() {
        JwtService jwt = service(props());

        ParsedToken access = jwt.parse(jwt.issueAccess(42L, SubjectType.USER));
        assertEquals(42L, access.subjectId());
        assertEquals(SubjectType.USER, access.subjectType());
        assertEquals(TokenType.ACCESS, access.tokenType());

        ParsedToken admin = jwt.parse(jwt.issueAccess(7L, SubjectType.ADMIN));
        assertEquals(SubjectType.ADMIN, admin.subjectType());
    }

    @Test
    @DisplayName("令牌类型互不混淆(refresh 不能冒充 access)")
    void tokenTypesAreDistinct() {
        JwtService jwt = service(props());

        assertEquals(TokenType.ACCESS, jwt.parse(jwt.issueAccess(1L, SubjectType.USER)).tokenType());
        assertEquals(TokenType.REFRESH, jwt.parse(jwt.issueRefresh(1L, SubjectType.USER)).tokenType());
    }

    @Test
    @DisplayName("每个令牌的 jti 唯一 —— 它是黑名单与一次性消费的键")
    void jtiIsUnique() {
        JwtService jwt = service(props());

        String a = jwt.parse(jwt.issueRefresh(1L, SubjectType.USER)).jti();
        String b = jwt.parse(jwt.issueRefresh(1L, SubjectType.USER)).jti();

        assertNotEquals(a, b, "同一主体两次签发的 jti 必须不同,否则拉黑一个会误伤另一个");
        assertTrue(a != null && !a.isBlank());
    }

    @Test
    @DisplayName("有效期符合契约:access 2 小时 / refresh 14 天")
    void ttlMatchesContract() {
        JwtProperties p = props();
        JwtService jwt = service(p);

        assertEquals(Duration.ofHours(2), p.getAccessTtl());
        assertEquals(Duration.ofDays(14), p.getRefreshTtl());

        // 实际签发的 exp 也要对得上(容忍几秒执行误差)
        Duration left = jwt.remaining(jwt.parse(jwt.issueAccess(1L, SubjectType.USER)));
        assertTrue(left.compareTo(Duration.ofHours(2).minusMinutes(1)) > 0
                        && left.compareTo(Duration.ofHours(2).plusMinutes(1)) < 0,
                "access 剩余有效期应约为 2 小时,实际 " + left);
    }

    @Test
    @DisplayName("被篡改的令牌 → 10002,不是 10003(无效 ≠ 过期)")
    void tamperedTokenIsUnauthenticated() {
        JwtService jwt = service(props());
        String token = jwt.issueAccess(1L, SubjectType.USER);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        BusinessException ex = assertThrows(BusinessException.class, () -> jwt.parse(tampered));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.errorCode());
    }

    @Test
    @DisplayName("用另一把密钥签的令牌不被接受")
    void tokenFromAnotherKeyIsRejected() {
        JwtProperties other = new JwtProperties();
        other.setSecret("ffffffffffffffffffffffffffffffff");
        String foreign = service(other).issueAccess(1L, SubjectType.USER);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service(props()).parse(foreign));
        assertEquals(ErrorCode.UNAUTHENTICATED, ex.errorCode());
    }

    @Test
    @DisplayName("过期令牌 → 10003(引导前端去刷新,而非直接判未登录)")
    void expiredTokenIsTokenExpired() throws InterruptedException {
        JwtProperties p = props();
        p.setAccessTtl(Duration.ofMillis(1));
        JwtService jwt = service(p);

        String token = jwt.issueAccess(1L, SubjectType.USER);
        Thread.sleep(30);

        BusinessException ex = assertThrows(BusinessException.class, () -> jwt.parse(token));
        assertEquals(ErrorCode.TOKEN_EXPIRED, ex.errorCode());
    }

    @Test
    @DisplayName("生产环境未配置密钥 → 拒绝启动(宁可起不来,也不用可预测的密钥)")
    void prodWithoutSecretRefusesToStart() {
        StandardEnvironment prod = new StandardEnvironment();
        prod.setActiveProfiles("prod");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtService(new JwtProperties(), prod));
        assertTrue(ex.getMessage().contains("JWT_SECRET"), ex.getMessage());
    }

    @Test
    @DisplayName("非生产环境未配置密钥 → 生成随机密钥并告警(便于本地开发)")
    void devWithoutSecretGeneratesRandomKey() {
        JwtService a = service(new JwtProperties());
        JwtService b = service(new JwtProperties());

        String token = a.issueAccess(1L, SubjectType.USER);
        assertThrows(BusinessException.class, () -> b.parse(token),
                "两次启动的随机密钥必须不同,否则说明用了固定默认值");
    }

    @Test
    @DisplayName("密钥短于 32 字节 → 拒绝(HS256 的安全下限)")
    void shortSecretIsRejected() {
        JwtProperties p = new JwtProperties();
        p.setSecret("too-short");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service(p));
        assertTrue(ex.getMessage().contains("32"), ex.getMessage());
    }

    @Test
    @DisplayName("UTF-8 密钥按字节计算长度(中文密钥不会被误判)")
    void secretLengthCountsBytesNotChars() {
        JwtProperties p = new JwtProperties();
        // 13 个汉字 × 3 字节 = 39 字节 > 32,应通过
        // (若按字符数算只有 13,会被误判为过短而拒绝 —— 这正是本用例要钉住的差异)
        p.setSecret("瓷砖智能体项目密钥测试一下");
        assertEquals(39, "瓷砖智能体项目密钥测试一下".getBytes(StandardCharsets.UTF_8).length);

        JwtService jwt = service(p);
        assertEquals(1L, jwt.parse(jwt.issueAccess(1L, SubjectType.USER)).subjectId());
    }
}
