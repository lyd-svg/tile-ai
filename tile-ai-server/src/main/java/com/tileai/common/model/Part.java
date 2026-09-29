package com.tileai.common.model;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 铺贴部位 —— <b>恒为三值</b>(契约 {@code openapi-common.yaml#/components/schemas/Part},
 * 校验表 T-9;架构 §8.2)。
 *
 * <p>{@code floor} 地面 / {@code wall} 墙面 / {@code local} 局部(腰线、花砖)。
 * <b>一期不含美缝</b> —— 美缝属施工/辅料范畴,列在二期(PRD §5.2.2)。
 *
 * <p>放在 {@code common.model} 而不是某个业务模块下,是因为它被搭配、选砖、
 * 报价、方案、生成多个模块共同引用。放进任一模块都会让其它模块反向依赖它。
 */
public enum Part {

    FLOOR("floor"),
    WALL("wall"),
    LOCAL("local");

    private static final Set<String> CODES =
            Arrays.stream(values()).map(Part::code).collect(Collectors.toUnmodifiableSet());

    private final String code;

    Part(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** 值是否合法。用于校验外部传入的部位字符串。 */
    public static boolean isValid(String code) {
        return code != null && CODES.contains(code);
    }

    public static Set<String> codes() {
        return CODES;
    }
}
