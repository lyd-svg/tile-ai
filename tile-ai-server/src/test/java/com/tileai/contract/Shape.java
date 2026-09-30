package com.tileai.contract;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 端点某一处的「结构形态」 —— <b>只回答"这里有哪些字段",不回答字段是什么类型</b>。
 *
 * <p>契约侧与实现侧各自产出一棵树,再交给 {@link ContractImplDiff} 比。两侧归一到同一个
 * 形状,是为了让比较器<b>完全不认识 YAML,也不认识 Spring</b> —— 它只吃两个值对象,
 * 于是可以用合成输入做负向对照(见 {@code ContractImplDiffComparerTest})。
 *
 * <p>刻意<b>不比类型</b>:框架从代码自动生成的类型描述与手写契约在细节上天然有差异
 * (nullable、format、open 占位),比下去会淹掉真信号。范围由用户拍板,见计划。
 */
final class Shape {

    /** 形态种类。只有 {@link #OBJECT} 与 {@link #ARRAY} 会被真的拿去比。 */
    enum Kind {
        /** 具名对象:有明确的字段集合 —— 本骨架的主战场。 */
        OBJECT,
        /** 数组:比较其元素的形态(数组本身不消耗深度)。 */
        ARRAY,
        /** 键不可枚举的对象(如 {@code Map<String,?>}) —— 真实盲区,记「无法比对」。 */
        MAP,
        /** 二进制 / 流(文件下载、SSE) —— 不是 JSON,记「无法比对」。 */
        BINARY,
        /** 标量(字符串 / 数字 / 布尔 / 枚举)。 */
        SCALAR,
        /** 没有响应体。 */
        NONE,
        /** 契约没声明、或声明成开放占位(如 {@code items: {}}) —— 无从比。 */
        UNKNOWN,
        /** 超出深度上限,不再展开。 */
        TRUNCATED
    }

    static final Shape NONE = new Shape(Kind.NONE, null, Map.of());
    static final Shape MAP = new Shape(Kind.MAP, null, Map.of());
    static final Shape BINARY = new Shape(Kind.BINARY, null, Map.of());
    static final Shape SCALAR = new Shape(Kind.SCALAR, null, Map.of());
    static final Shape UNKNOWN = new Shape(Kind.UNKNOWN, null, Map.of());
    static final Shape TRUNCATED = new Shape(Kind.TRUNCATED, null, Map.of());

    private final Kind kind;
    private final Shape item;
    private final Map<String, Shape> fields;

    private Shape(Kind kind, Shape item, Map<String, Shape> fields) {
        this.kind = kind;
        this.item = item;
        this.fields = Map.copyOf(fields);
    }

    static Shape object(Map<String, Shape> fields) {
        return new Shape(Kind.OBJECT, null, fields);
    }

    static Shape array(Shape item) {
        return new Shape(Kind.ARRAY, item, Map.of());
    }

    Kind kind() {
        return kind;
    }

    Shape item() {
        return item;
    }

    /** 字段名 → 该字段的形态。仅 {@link Kind#OBJECT} 非空。 */
    Map<String, Shape> fields() {
        return fields;
    }

    /** 字段名集合(排序,便于报告里稳定输出)。 */
    Set<String> fieldNames() {
        return new TreeSet<>(fields.keySet());
    }

    /**
     * 能否参与"字段名"比对。只有具名对象与数组可以 ——
     * 其余形态要么是真实盲区,要么是"契约没声明",<b>一律记「无法比对」而不是「一致」</b>。
     */
    boolean isComparable() {
        return kind == Kind.OBJECT || kind == Kind.ARRAY;
    }

    /** 报告里用的短标签。 */
    String label() {
        return switch (kind) {
            case OBJECT -> "对象";
            case ARRAY -> "数组";
            case MAP -> "键不可枚举的对象";
            case BINARY -> "二进制/流";
            case SCALAR -> "标量";
            case NONE -> "无响应体";
            case UNKNOWN -> "契约未声明字段";
            case TRUNCATED -> "超出深度上限";
        };
    }

    @Override
    public String toString() {
        return switch (kind) {
            case OBJECT -> "OBJECT" + fieldNames();
            case ARRAY -> "ARRAY[" + (item == null ? "?" : item) + "]";
            default -> kind.name();
        };
    }
}
