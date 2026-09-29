/**
 * 报价计算模块(架构 §4.1)。
 *
 * <p>规则引擎:按部位计算材料费 + 辅料及损耗;开口面积扣减;
 * 同部位同砖<b>先合并面积再乘单价</b>;读取参数(辅料单价/损耗率);生成报价明细。
 * 规则细化见架构 §8.8。
 *
 * <p><b>报价只归本模块</b>(CLAUDE.md 架构不变量 1):Python 不参与报价,
 * 契约中<b>不存在</b> {@code /internal/quote/*}。触发点仅两处 ——
 * 生成流程 Java 内 in-process Facade,与 C 端 {@code /quote/calc} 预览(一致性评审 A1)。
 * 落地见 T-1.8。
 */
package com.tileai.quote;
