/**
 * 选砖·部位映射模块(架构 §4.1)。
 *
 * <p>一个空间多砖、部位分配(地面/墙面/局部);空间—部位—瓷砖映射;
 * 支持<b>部分选择</b>(不强制选全)。
 *
 * <p>部位枚举恒为 {@code floor} / {@code wall} / {@code local} —— 一期<b>不含美缝</b>
 * (CLAUDE.md 约定)。未选部位由 AI 补齐时标注 {@code source=ai}(D-4)。
 * 对应 PRD §5.2.4、§7.4。
 */
package com.tileai.selection;
