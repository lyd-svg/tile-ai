package com.tileai.plan;

/**
 * 方案名的长度口径与「另存为」的默认名 —— 全项目只此一份。
 *
 * <p>为什么值得单独一个类:{@code 200} 这个数同时被三处用到(自动保存的默认名、
 * 重命名的校验、另存为的截断),写在三个地方就一定会有一天只剩两个地方是对的。
 *
 * <p><b>截断按「码点」而不是按 {@code String.length()}。</b> Java 的 {@code length()}
 * 数的是 UTF-16 单元,而 MySQL 的 {@code VARCHAR(200)} 数码点 —— 一个 emoji 在
 * Java 里是 2、在 MySQL 里是 1。直接 {@code substring(0, 200)} 有概率把代理对
 * 劈成两半,写进库就是非法字符。{@link #fit} 用
 * {@link String#offsetByCodePoints(int, int)} 定位,不会劈开任何一个字符。
 */
public final class PlanNames {

    /** {@code t_plan.name} 的长度上限({@code VARCHAR(200)})。 */
    public static final int NAME_MAX = 200;

    /** 「另存为」复制出来的方案名后缀。口径由 T-1.12 定,契约未规定。 */
    public static final String COPY_SUFFIX = "(副本)";

    /** 原名不可用(为空 / 空白)时,副本退回的名字。 */
    private static final String FALLBACK_NAME = "方案";

    private PlanNames() {
    }

    /**
     * 「另存为」复制出来的新方案叫什么。
     *
     * <p>形如 <code>卫生间 · 现代(副本)</code> —— 一眼能看出是从哪一份复制来的。
     * 原名已经用满 200 字时,先截到「200 − 后缀长度」再拼后缀,**保证结果不超过上限**;
     * 反过来先拼再截会把后缀本身截掉,那就白拼了。
     */
    public static String copyName(String original) {
        String base = (original == null || original.isBlank()) ? FALLBACK_NAME : original;
        return fit(base, NAME_MAX - COPY_SUFFIX.length()) + COPY_SUFFIX;
    }

    /** 按码点截断到至多 {@code maxCodePoints} 个字符;本来就够短就原样返回。 */
    public static String fit(String value, int maxCodePoints) {
        if (value == null) {
            return null;
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints <= maxCodePoints) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
    }

    /** 名字是否超出上限(按码点算)。重命名用它判 10001。 */
    public static boolean isTooLong(String value) {
        return value != null && value.codePointCount(0, value.length()) > NAME_MAX;
    }
}
