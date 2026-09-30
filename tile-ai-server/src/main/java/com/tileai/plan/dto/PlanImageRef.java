package com.tileai.plan.dto;

/**
 * 一张要落到方案里的效果图(url + 视角 + 是否封面)。
 *
 * <p>单独定义而不是复用回调 DTO 里的那个同形结构:方向必须是
 * {@code plan} 不依赖 {@code generate}。否则以后 generate 想换个字段名,
 * 改动的涟漪会漫到方案模块。
 */
public record PlanImageRef(String url, String view, boolean isCover) {
}
