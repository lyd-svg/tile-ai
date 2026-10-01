package com.tileai.contract;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 端点某一处的「结构形态」。
 *
 * <p>契约侧与实现侧各自产出一棵树,再交给 {@link ContractImplDiff} 比。两侧归一到同一个
 * 形状,是为了让比较器<b>完全不认识 YAML,也不认识 Spring</b> —— 它只吃两个值对象,
 * 于是可以用合成输入做负向对照(见 {@code ContractImplDiffComparerTest})。
 *
 * <h2>它回答三个问题(2026-10-01 由「只回答字段名」扩成三个维度)</h2>
 *
 * <ul>
 *   <li>{@link Kind} —— 这一处是什么形状(对象 / 数组 / 标量 / Map / 不是 JSON);</li>
 *   <li>{@link #type()} —— 值是<b>什么 JSON 类型</b>(string / integer / number / …);</li>
 *   <li>{@link #enumValues()} —— 只允许填哪几个值。<b>这一条是被真实事故逼出来的</b>:
 *       §31.2 那次 Python 侧多出 {@code bay_window} 的分叉,当时没有任何门禁会因此变红。
 *       空集 = 契约没声明,或实现侧推断不出(如走后台字典的字段)。</li>
 *   <li>{@link #requiredFields()} —— 该对象的哪些子字段是必填(仅请求体侧参与比对)。</li>
 * </ul>
 *
 * <p>刻意<b>不比</b>的仍然不比:format、约束(minLength/minimum)、nullable 细节 ——
 * 框架自动生成的描述与手写契约在这些细节上天然有差异,比下去会淹掉真信号。
 */
final class Shape {

    /** 形态种类。只有 {@link #OBJECT} 与 {@link #ARRAY} 会被真的拿去比。 */
    enum Kind {
        /** 具名对象:有明确的字段集合 —— 主战场之一。 */
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

    /**
     * 这一处的 JSON 类型。
     *
     * <p>{@link Kind} 说"是什么形状",这里说"值是什么类型" —— 两者大部分时候一致,
     * 但 {@code Kind.SCALAR} 之下还要再分 string / integer / number / boolean。
     */
    enum JsonType {
        STRING,
        INTEGER,
        NUMBER,
        BOOLEAN,
        OBJECT,
        ARRAY,
        /** 推不出(如 {@code Map}、非 JSON、超深)—— <b>不参与类型比对</b>。 */
        UNKNOWN
    }

    static final Shape NONE = new Shape(Kind.NONE, JsonType.UNKNOWN, null, Map.of(), Set.of(), Set.of());
    static final Shape MAP = new Shape(Kind.MAP, JsonType.UNKNOWN, null, Map.of(), Set.of(), Set.of());
    static final Shape BINARY = new Shape(Kind.BINARY, JsonType.UNKNOWN, null, Map.of(), Set.of(), Set.of());
    /** 类型未知的裸标量 —— 合成输入与「说不出类型」的场合用它。 */
    static final Shape SCALAR = new Shape(Kind.SCALAR, JsonType.UNKNOWN, null, Map.of(), Set.of(), Set.of());
    static final Shape UNKNOWN = new Shape(Kind.UNKNOWN, JsonType.UNKNOWN, null, Map.of(), Set.of(), Set.of());
    static final Shape TRUNCATED = new Shape(Kind.TRUNCATED, JsonType.UNKNOWN, null, Map.of(), Set.of(), Set.of());

    private final Kind kind;
    private final JsonType type;
    private final Shape item;
    private final Map<String, Shape> fields;
    private final Set<String> enumValues;
    private final Set<String> requiredFields;

    private Shape(Kind kind,
                  JsonType type,
                  Shape item,
                  Map<String, Shape> fields,
                  Set<String> enumValues,
                  Set<String> requiredFields) {
        this.kind = kind;
        this.type = type;
        this.item = item;
        this.fields = Map.copyOf(fields);
        this.enumValues = Set.copyOf(enumValues);
        this.requiredFields = Set.copyOf(requiredFields);
    }

    static Shape object(Map<String, Shape> fields) {
        return object(fields, Set.of());
    }

    /**
     * @param requiredFields 该对象里<b>必填</b>的子字段名。只有请求体侧会比它 ——
     *                       Java 的响应没有"必填"概念(见 {@code ContractImplDiff} 的口径)。
     */
    static Shape object(Map<String, Shape> fields, Set<String> requiredFields) {
        return new Shape(Kind.OBJECT, JsonType.OBJECT, null, fields, Set.of(), requiredFields);
    }

    static Shape array(Shape item) {
        return new Shape(Kind.ARRAY, JsonType.ARRAY, item, Map.of(), Set.of(), Set.of());
    }

    /**
     * @param type       值的 JSON 类型;推不出时给 {@link JsonType#UNKNOWN}
     * @param enumValues 只允许填哪几个值;空集 = 没声明 / 推不出
     */
    static Shape scalar(JsonType type, Set<String> enumValues) {
        return new Shape(Kind.SCALAR, type, null, Map.of(), enumValues, Set.of());
    }

    Kind kind() {
        return kind;
    }

    JsonType type() {
        return type;
    }

    Shape item() {
        return item;
    }

    /** 字段名 → 该字段的形态。仅 {@link Kind#OBJECT} 非空。 */
    Map<String, Shape> fields() {
        return fields;
    }

    /** 只允许填哪几个值。空集 = 契约没声明,或实现侧推断不出。 */
    Set<String> enumValues() {
        return enumValues;
    }

    boolean hasEnumValues() {
        return !enumValues.isEmpty();
    }

    /** 该对象里必填的子字段名。仅 {@link Kind#OBJECT} 可能非空。 */
    Set<String> requiredFields() {
        return requiredFields;
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

    /** 类型能否参与比对 —— 两侧都得说出自己是什么类型。 */
    boolean hasTypeInfo() {
        return type != JsonType.UNKNOWN;
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
            case SCALAR -> type == JsonType.UNKNOWN ? "SCALAR"
                    : "SCALAR(" + type + (hasEnumValues() ? " " + enumValues : "") + ")";
            default -> kind.name();
        };
    }
}
