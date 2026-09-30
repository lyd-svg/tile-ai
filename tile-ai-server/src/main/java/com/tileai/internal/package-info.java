/**
 * 内部接口({@code /internal/*})—— 契约 {@code openapi-internal.yaml}。
 *
 * <p><b>调用方只有 Python 智能体,不是前端。</b>这些路径<b>不带 {@code /api/v1}</b>:
 * 它们不属于对外 API 的版本体系,对外由网关阻断(不变量 3 —— Java 是唯一对外 API)。
 *
 * <p>方向分两种,不要混:
 * <ul>
 *   <li><b>Python → Java</b>(本包实现):{@code GET /internal/products/{sku}}、
 *       {@code GET /internal/furnish/prompts}、{@code POST /internal/generate/callback}。
 *       前两个是 Python 取权威数据 —— Python 不持有业务数据副本(不变量 4),
 *       所以它拿不到的东西必须由 Java 给。</li>
 *   <li><b>Java → Python</b>(不在这里,见 {@code common.agent.AgentClient}):
 *       {@code /internal/agent/parse-layout} 等由 Python 提供。</li>
 * </ul>
 *
 * <p><b>鉴权</b>:服务令牌 {@code X-Service-Token},由
 * {@code common.security.ServiceTokenInterceptor} 统一挂在本包路径上 ——
 * 所以本包的控制器<b>不要再各自写一遍令牌校验</b>。
 *
 * <p><b>响应体口径与前台一致</b>({@code ApiResponse},业务错误码 HTTP 200)——
 * 契约里这组接口的 schema 复用的就是 {@code common.ApiResponse}。§10.4 约定:
 * Java 只透传<b>已定义</b>的业务码,其余折 90001。
 */
package com.tileai.internal;
