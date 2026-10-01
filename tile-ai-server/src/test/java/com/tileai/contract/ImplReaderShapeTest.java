package com.tileai.contract;

import java.math.BigDecimal;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.generate.dto.GenerateRequest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 实现侧<b>形态提取</b>的负向对照 —— 证明枚举取值与必填确实被读到了。
 *
 * <p>与 {@code ContractImplDiffComparerTest} 分工不同:那个证明"比较器会红",
 * 这个证明"喂给比较器的输入不是空的"。两者缺一不可 —— 比较器再灵敏,
 * 只要提取器静默失灵(注解挂在哪一处没看全、正则切错),报告照样全绿。
 *
 * <p>不启 Spring:{@link ImplReader#describe} 是专为这个测试开的接缝。
 */
class ImplReaderShapeTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ImplReader reader = new ImplReader(MAPPER);

    // ------------------------------------------------------------ 合成 DTO

    enum OpeningKind { DOOR, WINDOW }

    record Patterned(@Pattern(regexp = "door|window") String type) {
    }

    record Formatted(@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$") String date) {
    }

    record HasJavaEnum(OpeningKind kind) {
    }

    record HasRequired(@NotNull String sku,
                       @NotBlank String name,
                       @Size(max = 8) String memo,
                       String optional) {
    }

    record JsonNamed(@JsonProperty(required = true) String pinned) {
    }

    record Nested(Patterned outer) {
    }

    record Deep(Deep next) {
    }

    private Shape describe(Class<?> type) {
        return reader.describe(MAPPER.constructType(type));
    }

    // ------------------------------------------------------------ 枚举取值

    @Test
    @DisplayName("@Pattern(\"a|b|c\") 被读成取值集合 —— 本仓库的枚举主力形态")
    void simpleAlternationBecomesEnumValues() {
        assertEquals(Set.of("door", "window"),
                describe(Patterned.class).fields().get("type").enumValues());
    }

    @Test
    @DisplayName("格式校验类正则**不许**被当成枚举(误判比漏掉更难查)")
    void nonAlternationPatternIsNotMisreadAsEnum() {
        assertTrue(describe(Formatted.class).fields().get("date").enumValues().isEmpty(),
                "^\\d{4}-\\d{2}-\\d{2}$ 是格式约束,不是枚举 —— 切开会造出假分叉");
    }

    @Test
    @DisplayName("Java enum 常量按 JSON 名读出")
    void javaEnumConstantsAreRead() {
        assertEquals(Set.of("DOOR", "WINDOW"),
                describe(HasJavaEnum.class).fields().get("kind").enumValues());
    }

    @Test
    @DisplayName("没有 @Pattern 的普通 String 不留取值集合(空 = 推不出,交给比较器记跳过)")
    void plainStringHasNoEnumValues() {
        assertTrue(describe(HasRequired.class).fields().get("optional").enumValues().isEmpty());
    }

    @Test
    @DisplayName("嵌套里的 @Pattern 也要读到")
    void nestedPatternIsRead() {
        assertEquals(Set.of("door", "window"),
                describe(Nested.class).fields().get("outer").fields().get("type").enumValues());
    }

    // ------------------------------------------------------------ 必填

    @Test
    @DisplayName("必填:只认 @NotNull / @NotBlank;@Size 不拒绝 null,不算必填")
    void requiredAnnotationsAreRead() {
        assertEquals(Set.of("sku", "name"), describe(HasRequired.class).requiredFields(),
                "@Size 只约束长度、不拒绝 null —— 算成必填会天天假红");
    }

    @Test
    @DisplayName("必填:@JsonProperty(required=true) 同样算")
    void jsonPropertyRequiredCounts() {
        assertEquals(Set.of("pinned"), describe(JsonNamed.class).requiredFields());
    }

    @Test
    @DisplayName("必填:真 DTO 上确实被读到(否则这一维度会静默失灵)")
    void requiredIsReadOnRealDto() {
        // GenerateRequest 契约 required: [space, layout, parts];@Valid 不算必填,故不含任何 "valid" 痕迹
        assertEquals(Set.of("space", "layout", "parts"),
                describe(GenerateRequest.class).requiredFields());
    }

    // ------------------------------------------------------------ 类型

    @Test
    @DisplayName("类型:标量给出 JSON 类型,枚举字段一律按 string 记")
    void scalarTypesAreRead() {
        assertEquals(Shape.JsonType.STRING, describe(String.class).type());
        assertEquals(Shape.JsonType.INTEGER, describe(Integer.class).type());
        assertEquals(Shape.JsonType.INTEGER, describe(Long.class).type());
        assertEquals(Shape.JsonType.NUMBER, describe(Double.class).type());
        assertEquals(Shape.JsonType.NUMBER, describe(BigDecimal.class).type());
        assertEquals(Shape.JsonType.BOOLEAN, describe(Boolean.class).type());
        assertEquals(Shape.JsonType.STRING, describe(OpeningKind.class).type(),
                "枚举在 JSON 里就是字符串");
    }

    // ------------------------------------------------------------ 深度

    @Test
    @DisplayName("深度 5 可达:契约的 layout.spaces[].openings[].type 就落在这里(F2)")
    void depthFiveIsReachable() {
        Shape shape = describe(GenerateRequest.class);

        Shape spaces = shape.fields().get("layout").fields().get("spaces");
        Shape openingType = spaces.item().fields().get("openings").item().fields().get("type");

        assertEquals(Shape.Kind.SCALAR, openingType.kind(),
                "第 5 层的标量应被展开,而不是 TRUNCATED");
        assertEquals(Set.of("door", "window"), openingType.enumValues(),
                "§31.2 的 Java 镜像就堵在这里 —— 深度 2 够不到它,门禁就是摆设");
    }

    @Test
    @DisplayName("超过深度上限记 TRUNCATED,不静默当成「没字段」")
    void beyondDepthIsTruncatedNotSilent() {
        Shape walk = describe(Deep.class);
        // Deep 无限自引用:一路往里走,应当撞上显式的截断标记而不是 null / UNKNOWN
        for (int i = 0; i < 8 && walk.kind() == Shape.Kind.OBJECT; i++) {
            walk = walk.fields().get("next");
        }
        assertEquals(Shape.Kind.TRUNCATED, walk.kind(),
                "截断必须显式标记 —— 截断不是「一致」");
    }

    @Test
    @DisplayName("接缝自身:describe(null) 记 UNKNOWN,不抛")
    void describeNullIsUnknown() {
        assertEquals(Shape.Kind.UNKNOWN, reader.describe(null).kind());
    }
}
