package com.tileai.common.security;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import com.tileai.common.error.BusinessException;
import com.tileai.common.error.ErrorCode;
import com.tileai.common.security.JwtService.ParsedToken;

/**
 * 会话机制 —— 下发 / 刷新 / 登出。
 *
 * <p>本类只提供<b>能力</b>,不提供 HTTP 端点。端点在 T-1.1
 * ({@code /auth/login/sms}、{@code /auth/refresh}、{@code /auth/logout} 等),
 * 那时只需调用这里并把返回的 Cookie 塞进响应。
 *
 * <p>分成两层的原因:登出/刷新涉及的<b>黑名单语义</b>是安全性的关键,
 * 不应埋在某个 controller 里被各写一遍。
 */
@Service
public class AuthSessionService {

    private final JwtService jwtService;
    private final TokenBlacklist blacklist;
    private final AuthCookies cookies;

    public AuthSessionService(JwtService jwtService, TokenBlacklist blacklist, AuthCookies cookies) {
        this.jwtService = jwtService;
        this.blacklist = blacklist;
        this.cookies = cookies;
    }

    /** 登录成功后下发的一对 Cookie。 */
    public record SessionCookies(ResponseCookie access, ResponseCookie refresh) {
    }

    /**
     * 建立会话 —— 注册成功与登录成功都调用它,两者各是一次请求(v0.7 起)。
     */
    public SessionCookies start(long subjectId, SubjectType subjectType) {
        return new SessionCookies(
                cookies.access(jwtService.issueAccess(subjectId, subjectType)),
                cookies.refresh(jwtService.issueRefresh(subjectId, subjectType)));
    }

    /**
     * 用 refresh_token 换一个新的 access_token。
     *
     * <p>契约把该接口描述为"刷新<b>访问</b>令牌",因此这里<b>不轮换</b>
     * refresh_token 本身 —— 它保持 14 天有效期,直到登出或过期。
     *
     * <p>三道校验缺一不可:签名有效、类型必须是 REFRESH、且不在黑名单里。
     * 少了第二道,一个 access 令牌就能当 refresh 用,2 小时的有效期会被无限续期。
     */
    public ResponseCookie refreshAccess(String refreshToken) {
        ParsedToken parsed = jwtService.parse(refreshToken);

        if (parsed.tokenType() != TokenType.REFRESH) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "刷新令牌类型不正确");
        }
        if (blacklist.isBlacklisted(parsed.jti())) {
            // 已登出的令牌被重放 —— 是安全事件,值得单独文案与日志关注。
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "该令牌已失效,请重新登录");
        }
        return cookies.access(jwtService.issueAccess(parsed.subjectId(), parsed.subjectType()));
    }

    /**
     * 登出 —— 把 refresh_token 拉黑,并清除两个 Cookie。
     *
     * <p>幂等:令牌无效或已经登出时<b>不报错</b>,照样清 Cookie。登出必须"总能成功",
     * 否则用户会卡在登录态里 —— 而失败的登出正是最不该出现的体验。
     *
     * <p>注意:access_token 不进黑名单(契约只要求 refresh 黑名单),因此登出后
     * 它最多还能再用 2 小时。这是 §10.1 的设计取舍(access 短寿命 + 无状态),
     * 不是遗漏。若需立即失效,得改为校验 access 的 jti,那要先回改架构。
     */
    public SessionCookies logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            try {
                ParsedToken parsed = jwtService.parse(refreshToken);
                Duration remaining = jwtService.remaining(parsed);
                blacklist.blacklist(parsed.jti(), remaining);
            } catch (BusinessException ignored) {
                // 签名不对 / 已过期 —— 本来就用不了,无需入名单。
            }
        }
        return new SessionCookies(cookies.clearAccess(), cookies.clearRefresh());
    }
}
