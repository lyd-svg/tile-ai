package com.tileai.plan.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 生成分享链接的结果(契约 {@code openapi-c.yaml#/components/schemas/ShareResult};T-1.13)。
 *
 * <p>契约里三个字段<b>都没有 {@code required}</b>,也没有 {@code nullable} —— 全可选。
 *
 * <h2>{@code qrCodeUrl} 恒为空,这是刻意的</h2>
 *
 * <p>契约在 {@code ShareResult} 上带着 {@code x-todo}("§10.2 仅'生成分享链接/二维码',
 * 返回字段结构未定义")—— 也就是说上游从没定义过这个字段到底该返回什么。
 *
 * <p>2026-10-01 用户拍板:**二维码由前端用库自己画**,后端只给 {@code shareUrl}。
 * 所以这个字段在实现里**永远为 null**,靠 {@code @JsonInclude(NON_NULL)} 整个从响应里
 * 消失。这不违约(契约没写它必填),但属于**已知的不一致面**,与
 * {@code §41.6} 的 {@code materialFee} 同类,登记在 {@code openapi-verification.md} §43。
 *
 * <p><b>不要顺手把它填成"二维码图片的地址"</b> —— 那需要后端生成并托管一张图
 * (多一个对象存储依赖、多一份要清理的产物),而前端画是一行代码的事。
 *
 * @param shareToken 分享令牌;它是不登录就能换到一份方案的全部凭据
 * @param shareUrl   完整分享链接,**指向前端页面**(不是 API 地址)
 * @param qrCodeUrl  二维码图片地址;恒为 null(见类注释)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShareResultView(
        String shareToken,
        String shareUrl,
        String qrCodeUrl) {

    /** 不带二维码地址的构造 —— 后端不经手二维码(见类注释)。 */
    public ShareResultView(String shareToken, String shareUrl) {
        this(shareToken, shareUrl, null);
    }
}
