/**
 * 后台管理模块(架构 §4.1)。
 *
 * <p>SKU(含<b>批量导入</b>)/搭配/户型模板/陈设风格/报价参数/门店/账号权限(<b>RBAC</b>)/
 * 操作日志的 B 端接口。
 *
 * <p>三角色 {@code super_admin} / {@code operator} / {@code editor},权限点标注见架构 §4.4;
 * 所有写操作记入 {@code t_operation_log}。落地见 T-0.5 ~ T-0.12。
 */
package com.tileai.admin;
