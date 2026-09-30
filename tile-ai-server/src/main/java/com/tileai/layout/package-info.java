/**
 * 户型/模板模块(架构 §4.1)。
 *
 * <p>户型模板维护与筛选;接收"结构化户型数据模型";自然语言解析的<b>落库与校验</b>
 * —— 解析本身在 Python(智能体),本模块不做解析。
 *
 * <p>户型以加密快照 {@code layout_enc}(AES-256-GCM)静态存储,<b>对外 API 传明文</b>
 * (架构 §13.5;CLAUDE.md 约定)。对应 PRD §5.3、§7.3。
 *
 * <p>加密实现是 {@code common.crypto.AesGcmCipher}(T-1.11 落地)。此处原本写着
 * "落地见 T-0.9 / T-1.7" —— <b>那是错的</b>:T-0.9 只建表、T-1.7 只做了模板 CRUD
 * 与解析,加密一直没人写。订正记录见 {@code openapi-verification.md} §35.6。
 */
package com.tileai.layout;
