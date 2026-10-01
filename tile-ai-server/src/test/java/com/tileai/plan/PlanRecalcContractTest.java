package com.tileai.plan;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.tileai.common.api.ApiResponse;
import com.tileai.plan.api.CPlanRecalcController;
import com.tileai.quote.dto.QuoteResultView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 重算接口的<b>实现侧</b>契约(T-1.15;架构不变量 6)。
 *
 * <p>为什么要有这个文件:{@code PlanContractTest} 断言的是"<b>契约文件</b>里
 * {@code /plans/{id}/recalc} 没有 {@code requestBody}"。那只证明契约是这么写的。
 * 实现完全可以一边在契约里不写请求体、一边在方法上挂个
 * {@code @RequestBody Map<String,Object>} 把面积收进来 ——
 * <b>契约↔实现比对工具也抓不到它</b>:它比的是"契约声明的字段",契约没声明的东西
 * 不在比对范围内。唯一抓得住的是反射。
 *
 * <p>这条断言值钱的地方在于:一旦有人为了方便调试加上那个参数,
 * {@code /quote/calc} 的参数校验(面积下限、部位合法性)就被整个绕过去了 ——
 * 前端把面积填成 1 ㎡ 就能探到任意单价,而且接口、数据库、页面都不会报错。
 */
class PlanRecalcContractTest {

    private static final Method RECALC = method("recalc");

    @Test
    @DisplayName("CPlanRecalcController.recalc 上不存在 @RequestBody —— 不变量 6 的实现侧一半")
    void recalcTakesNoRequestBody() {
        for (Parameter parameter : RECALC.getParameters()) {
            for (Annotation annotation : parameter.getAnnotations()) {
                assertFalse(annotation instanceof RequestBody,
                        "重算不得接收任何请求体:部位/面积/开口一律取自方案快照。"
                                + "收了参数,前端就能把面积填成 1 ㎡ 绕过 /quote/calc 的参数校验");
            }
        }
    }

    @Test
    @DisplayName("recalc 恰好收一个参数,是 @PathVariable 的 long —— 多一个都算违约")
    void recalcTakesExactlyOnePathVariable() {
        Parameter[] parameters = RECALC.getParameters();
        assertEquals(1, parameters.length,
                "参数个数不对。契约里这条只有 path 参数 —— 多出来的参数一定是绕过校验的口子");

        Parameter only = parameters[0];
        assertTrue(only.isAnnotationPresent(PathVariable.class), "唯一那个参数应当是 @PathVariable");
        assertEquals(long.class, only.getType(), "路径变量 {id} 在契约里是 integer,映射成 long");
    }

    @Test
    @DisplayName("recalc 返回 ApiResponse<QuoteResultView> —— data 与 /quote/calc 同源")
    void recalcReturnsQuoteResult() {
        Type generic = RECALC.getGenericReturnType();
        assertTrue(generic instanceof ParameterizedType,
                "返回值应当是泛型的 ApiResponse<…>,实际:" + generic);

        ParameterizedType parameterized = (ParameterizedType) generic;
        assertEquals(ApiResponse.class, parameterized.getRawType());
        assertEquals(QuoteResultView.class, parameterized.getActualTypeArguments()[0],
                "data 必须是 QuoteResultView —— 重算与 /quote/calc 演示的是同一个形状,"
                        + "另造一个 VO 就会有两份可以各自漂移的字段集");
    }

    @Test
    @DisplayName("recalc 挂在 POST /api/v1/plans/{id}/recalc 上")
    void recalcIsMappedToTheContractPath() {
        PostMapping mapping = RECALC.getAnnotation(PostMapping.class);
        assertTrue(mapping != null, "recalc 应当是 @PostMapping");

        List<String> paths = Arrays.stream(mapping.value())
                .map(value -> controllerPrefix() + value)
                .toList();
        assertEquals(List.of("/api/v1/plans/{id}/recalc"), paths,
                "与方法上写的路径对不上 —— 契约里这条就在 /api/v1/plans/{id}/recalc");
    }

    // ------------------------------------------------------------------

    private static String controllerPrefix() {
        org.springframework.web.bind.annotation.RequestMapping mapping =
                CPlanRecalcController.class
                        .getAnnotation(org.springframework.web.bind.annotation.RequestMapping.class);
        assertTrue(mapping != null, "CPlanRecalcController 应当有 @RequestMapping 前缀");
        assertEquals(1, mapping.value().length);
        return mapping.value()[0];
    }

    private static Method method(String name) {
        try {
            return CPlanRecalcController.class.getMethod(name, long.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("CPlanRecalcController 里找不到 " + name
                    + "(long) —— 方法被删了或改了签名: " + e.getMessage(), e);
        }
    }
}
