/**
 * 基础设施模块(架构 §4.1)。
 *
 * <p>统一响应、异常、拦截器、JWT 过滤、Redis/MQ/OSS 封装、审计日志。
 *
 * <p>对外约定(CLAUDE.md「容易写错的约定」):
 * <ul>
 *   <li>业务错误码统一 HTTP <b>200</b>,成败看响应体 {@code code};<b>仅</b> 401/403 用真实状态码。</li>
 *   <li>鉴权走 {@code Set-Cookie} 下发 HttpOnly Cookie,<b>不用</b> {@code Authorization} 头,
 *       令牌<b>不进</b>响应体(v0.7 起<b>无例外</b>)。</li>
 *   <li>对外 JSON 用 camelCase,DB 用 snake_case,映射在 ORM 层完成。</li>
 * </ul>
 *
 * <p>落地见 T-0.3 / T-0.4。
 */
package com.tileai.common;
