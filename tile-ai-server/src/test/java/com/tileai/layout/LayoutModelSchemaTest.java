package com.tileai.layout;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.tileai.ContractSchema;
import com.tileai.layout.model.LayoutAreas;
import com.tileai.layout.model.LayoutModel;
import com.tileai.layout.model.LayoutSpace;
import com.tileai.layout.model.Opening;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-0.9 判据:「存入的 layout <b>用 LayoutModel schema 校验通过</b>(T-11 同类断言)」。
 *
 * <p>与其他任务不同,这条判据要的是<b>字面意义</b>的 schema 校验,而不是"字段名对得上"。
 * 所以这里把契约 {@code openapi-common.yaml} 里的 {@code LayoutModel}
 * (连同它引用的 {@code LayoutSpace} / {@code Opening} / {@code LayoutAreas} / 枚举)
 * 抽出来,内联 {@code $ref} 后交给 JSON Schema 校验器,校验<b>实际会落库的那份 JSON</b>。
 *
 * <p>这能抓到字段名比对抓不到的东西:类型不符、必填缺失、枚举越界、嵌套层级错误。
 */
class LayoutModelSchemaTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final JsonSchema LAYOUT_MODEL_SCHEMA =
            JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(ContractSchema.resolved("openapi-common.yaml", "LayoutModel"));

    private static Set<ValidationMessage> validate(JsonNode instance) {
        return LAYOUT_MODEL_SCHEMA.validate(instance);
    }

    // ------------------------------------------------------------------

    @Test
    @DisplayName("推导过面积的户型通过契约 LayoutModel schema 校验")
    void derivedLayoutPassesContractSchema() throws Exception {
        LayoutModel model = LayoutAreaCalculator.withDerivedAreas(new LayoutModel(List.of(
                new LayoutSpace("bathroom", "卫生间",
                        new BigDecimal("2.0"), new BigDecimal("1.8"), new BigDecimal("2.7"), 4,
                        List.of(new Opening("door", new BigDecimal("0.8"), new BigDecimal("2.1")),
                                new Opening("window", new BigDecimal("1.0"), new BigDecimal("1.2"))),
                        null),
                new LayoutSpace("kitchen", "厨房",
                        new BigDecimal("3.5"), new BigDecimal("2.4"), new BigDecimal("2.8"), 4,
                        List.of(), null))));

        Set<ValidationMessage> errors = validate(MAPPER.valueToTree(model));
        assertTrue(errors.isEmpty(), "户型未通过契约 schema 校验: " + errors);
    }

    @Test
    @DisplayName("带显式 areas 的户型同样通过校验")
    void explicitAreasAlsoPass() throws Exception {
        LayoutModel model = new LayoutModel(List.of(
                new LayoutSpace("bedroom", "卧室",
                        new BigDecimal("4.0"), new BigDecimal("3.2"), new BigDecimal("2.9"), 4,
                        List.of(),
                        new LayoutAreas(new BigDecimal("12.8"), new BigDecimal("41.76"),
                                new BigDecimal("0.0")))));

        assertTrue(validate(MAPPER.valueToTree(model)).isEmpty());
    }

    @Test
    @DisplayName("省略 name / wallCount / openings / areas 也能通过(它们都非必填)")
    void minimalSpacePasses() throws Exception {
        LayoutModel model = new LayoutModel(List.of(
                new LayoutSpace("living_room", null,
                        new BigDecimal("5.0"), new BigDecimal("4.0"), new BigDecimal("2.8"),
                        null, null, null)));

        Set<ValidationMessage> errors = validate(MAPPER.valueToTree(model));
        assertTrue(errors.isEmpty(), "最小可用空间应通过校验: " + errors);
    }

    // ------------------------------------------------------------------
    // 负向对照:证明校验确实在起作用
    // ------------------------------------------------------------------

    @Test
    @DisplayName("负向对照:缺必填的 length 会被 schema 拦下(证明校验不是空转)")
    void missingRequiredFieldIsCaught() throws Exception {
        JsonNode missingLength = MAPPER.readTree("""
                {"spaces":[{"type":"bathroom","width":1.8,"height":2.7}]}
                """);

        Set<ValidationMessage> errors = validate(missingLength);
        assertFalse(errors.isEmpty(),
                "契约声明 LayoutSpace.required = [type, length, width, height];"
                        + "缺 length 却没被拦下,说明校验根本没生效");
    }

    @Test
    @DisplayName("负向对照:空 spaces 会被拦下(契约 minItems: 1)")
    void emptySpacesIsCaught() throws Exception {
        assertFalse(validate(MAPPER.readTree("{\"spaces\":[]}")).isEmpty());
    }

    @Test
    @DisplayName("负向对照:非法开口类型会被拦下(契约 enum: door / window)")
    void invalidOpeningTypeIsCaught() throws Exception {
        JsonNode badOpening = MAPPER.readTree("""
                {"spaces":[{"type":"bathroom","length":2.0,"width":1.8,"height":2.7,
                            "openings":[{"type":"ceiling","w":1.0,"h":1.0}]}]}
                """);

        assertFalse(validate(badOpening).isEmpty(),
                "开口类型不在 door/window 内应被拦下");
    }

    @Test
    @DisplayName("负向对照:类型不符会被拦下(length 传字符串)")
    void wrongTypeIsCaught() throws Exception {
        JsonNode wrongType = MAPPER.readTree("""
                {"spaces":[{"type":"bathroom","length":"两米","width":1.8,"height":2.7}]}
                """);

        assertFalse(validate(wrongType).isEmpty());
    }
}
