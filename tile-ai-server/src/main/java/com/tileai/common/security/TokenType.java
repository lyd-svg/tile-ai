package com.tileai.common.security;

/**
 * 令牌类型,写入 JWT 的 {@code typ} 声明。
 *
 * <p>必须区分:否则一个 <b>refresh 令牌</b>就能当 access 令牌去访问业务接口 ——
 * 那会让"14 天有效期"和"2 小时有效期"的隔离形同虚设。
 */
public enum TokenType {

    /** 访问令牌,2 小时。请求时从 Cookie {@code access_token} 读取。 */
    ACCESS,

    /** 刷新令牌,14 天。只在对 {@code /api/v1/auth} 的请求中携带(Path 已收窄)。 */
    REFRESH
}
