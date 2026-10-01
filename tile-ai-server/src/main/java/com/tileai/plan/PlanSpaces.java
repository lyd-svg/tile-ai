package com.tileai.plan;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;

/**
 * 「这个方案算的是哪个空间」—— 全项目<b>唯一</b>的一份判定(T-1.15)。
 *
 * <p>面积快照取自单个空间,而一次生成 / 一次重算都必须落到<b>同一个</b>空间上,
 * 否则两次算出来的金额会在多空间户型的边界上分叉,而两边各自的测试都可能是绿的。
 * 所以这段逻辑从 {@code PlanAutoSaveService} 里原样搬出来,由生成路径与重算路径
 * <b>静态调用同一个方法</b> —— 不是复制一份,是同一份。
 *
 * <p>规则上游没写,由 T-1.11 定,记在 {@code openapi-verification.md} §35.8:
 * <ol>
 *   <li>取户型里 {@code type} 与请求 {@code space} 相同的第一个空间;</li>
 *   <li>没有匹配的、且户型只有一个空间时就用它 —— 用户录了个卫生间却把空间
 *       选成"其他",这种不一致不值得让整个方案保存失败;</li>
 *   <li>都没有则取第一个并记 WARN。<b>不报错</b>:面积定不准是报价问题,
 *       而出图已经成功了 —— 不该因为定不出面积就把方案整个丢掉。</li>
 * </ol>
 *
 * <p>⚠️ 注意第 2、3 条:<b>只要户型里至少有一个空间,这里就一定返回一个空间</b>。
 * 多空间户型并没有"保守不报价"的短路分支 —— {@code openapi-verification.md}
 * §41.2 曾如此声称,已按事实订正(见 §42)。
 */
final class PlanSpaces {

    private static final Logger log = LoggerFactory.getLogger(PlanSpaces.class);

    private PlanSpaces() {
    }

    /**
     * 定出"这次算的是哪个空间"(面积快照取它的)。
     *
     * @param spaceKey 调用方给的空间键;为 null 时走第 2/3 条兜底
     * @return 命中的空间;户型为 null / 没有空间时返回 {@code null}
     *         —— 那种情况下面积推不出来,由调用方决定怎么处置
     */
    static LayoutSpace resolve(LayoutModel layout, String spaceKey) {
        if (layout == null || layout.spaces() == null || layout.spaces().isEmpty()) {
            return null;
        }
        if (layout.spaces().size() == 1) {
            return layout.spaces().get(0);
        }
        for (LayoutSpace space : layout.spaces()) {
            if (space.type() != null && space.type().equals(spaceKey)) {
                return space;
            }
        }
        log.warn("户型含 {} 个空间,其中没有 type={} 的,无法唯一确定生成空间"
                        + "(一期为单空间出图),已取第一个空间用于面积快照",
                layout.spaces().size(), spaceKey);
        return layout.spaces().get(0);
    }
}
