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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tileai.common.api.ApiResponse;
import com.tileai.plan.api.CPlanShareController;
import com.tileai.plan.api.CShareController;
import com.tileai.plan.dto.PlanDetailView;
import com.tileai.plan.dto.ShareResultView;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分享三条接口的<b>实现侧</b>契约(T-1.13)。
 *
 * <p>与 {@code PlanRecalcContractTest} 同一个理由:{@code PlanContractTest} 断言的是
 * "<b>契约文件</b>里这条没有 {@code requestBody} / 这条是公开的"。那只证明契约是这么写的。
 * 实现完全可以一边在契约里不写请求体、一边在方法上挂个 {@code @RequestBody} 把
 * "有效期""允许谁看"收进来 —— <b>契约↔实现比对工具也抓不到</b>:
 * 它比的是契约声明过的字段,契约没声明的东西不在比对范围内。唯一抓得住的是反射。
 *
 * <p>还有一处是比对工具<b>结构性</b>看不到的:实现有没有办法绕过
 * {@code SecurityConfig} 的清单去给公开页加上身份要求。这里断言公开页那个方法
 * 只看得到唯一一个 {@code @PathVariable},没有任何别的东西可注入。
 */
class PlanShareContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Method SHARE = method(CPlanShareController.class, "share", long.class);
    private static final Method REVOKE = method(CPlanShareController.class, "revoke", long.class);
    private static final Method READ = method(CShareController.class, "read", String.class);

    // ------------------------------------------------------------------
    // 两个写接口:没有请求体,只有 path 参数
    // ------------------------------------------------------------------

    @Test
    @DisplayName("share / revoke 上都不存在 @RequestBody —— 契约里这两条没有请求体")
    void writeEndpointsTakeNoRequestBody() {
        for (Method method : List.of(SHARE, REVOKE)) {
            for (Parameter parameter : method.getParameters()) {
                for (Annotation annotation : parameter.getAnnotations()) {
                    assertFalse(annotation instanceof RequestBody,
                            method.getName() + " 不得接收请求体:契约里这两条只有 path 参数。"
                                    + "凭空加一个「有效期」或「允许谁看」,就是一个没人能解释的默认值");
                }
            }
        }
    }

    @Test
    @DisplayName("share / revoke 各只收一个 @PathVariable long —— 多一个都算违约")
    void writeEndpointsTakeExactlyOnePathVariable() {
        for (Method method : List.of(SHARE, REVOKE)) {
            Parameter[] parameters = method.getParameters();
            assertEquals(1, parameters.length,
                    method.getName() + " 的参数个数不对 —— 契约里这条只有 path 参数");

            Parameter only = parameters[0];
            assertTrue(only.isAnnotationPresent(PathVariable.class),
                    method.getName() + " 唯一那个参数应当是 @PathVariable");
            assertEquals(long.class, only.getType(),
                    "路径变量 {id} 在契约里是 integer,映射成 long");
        }
    }

    @Test
    @DisplayName("share 返回 ApiResponse<ShareResultView>;revoke 返回裸 ApiResponse<Void>")
    void writeEndpointsReturnTheContractShapes() {
        assertEquals(ShareResultView.class, dataTypeOf(SHARE),
                "生成分享的 data 必须与契约 ShareResult 对齐的 VO");
        assertEquals(Void.class, dataTypeOf(REVOKE),
                "废除分享是**裸信封**(契约里没有额外声明 data)。"
                        + "顺手回一个「当前分享状态」看着更方便,但那是契约里不存在的形状");
    }

    // ------------------------------------------------------------------
    // 公开页
    // ------------------------------------------------------------------

    @Test
    @DisplayName("公开页只收一个 @PathVariable String,没有任何请求体")
    void publicPageTakesOnlyTheToken() {
        Parameter[] parameters = READ.getParameters();
        assertEquals(1, parameters.length,
                "公开页只该收一个 token。多出来的参数意味着它开始接受别的东西 —— "
                        + "而这是一个**不需要登录**的接口");

        Parameter only = parameters[0];
        assertTrue(only.isAnnotationPresent(PathVariable.class),
                "唯一那个参数应当是 @PathVariable");
        assertEquals(String.class, only.getType(), "令牌在契约里是 string");
        for (Annotation annotation : only.getAnnotations()) {
            assertFalse(annotation instanceof RequestBody, "公开页不得接收请求体");
        }

        assertEquals(PlanDetailView.class, dataTypeOf(READ),
                "分享页发的 data 与本人详情接口是同一个 VO —— "
                        + "另造一个瘦 schema 会让两边各自漂移");
    }

    // ------------------------------------------------------------------
    // 路由
    // ------------------------------------------------------------------

    @Test
    @DisplayName("三条都挂在契约写的那个路径上")
    void mappingsMatchTheContractPaths() {
        assertEquals(List.of("/api/v1/plans/{id}/share"),
                Arrays.stream(SHARE.getAnnotation(PostMapping.class).value())
                        .map(value -> prefixOf(CPlanShareController.class) + value).toList(),
                "生成分享应当在 POST /api/v1/plans/{id}/share");

        assertEquals(List.of("/api/v1/plans/{id}/share"),
                Arrays.stream(REVOKE.getAnnotation(DeleteMapping.class).value())
                        .map(value -> prefixOf(CPlanShareController.class) + value).toList(),
                "废除分享应当在 DELETE /api/v1/plans/{id}/share");

        assertEquals(List.of("/api/v1/share/{token}"),
                Arrays.stream(READ.getAnnotation(GetMapping.class).value())
                        .map(value -> prefixOf(CShareController.class) + value).toList(),
                "公开页应当在 GET /api/v1/share/{token}");
    }

    /**
     * 公开页与写接口<b>必须分属两个控制器</b>。
     *
     * <p>合在一个类里也能跑,但那样"哪个方法不需要登录"要靠逐行读注解 ——
     * 而这恰恰是最容易被改错的一处(给公开方法顺手加上身份,或者反过来)。
     * 拆开之后类的边界就是安全边界,与 {@code SecurityConfig} 的路径前缀一一对应。
     */
    @Test
    @DisplayName("公开页与两个写接口不在同一个控制器里 —— 类的边界就是安全边界")
    void publicPageLivesInItsOwnController() {
        assertFalse(CShareController.class.equals(CPlanShareController.class));
        assertEquals("/api/v1/share", prefixOf(CShareController.class),
                "公开页的类前缀应当就是 /api/v1/share —— 与 SecurityConfig 里"
                        + "「没被登记进受保护清单」这件事一一对应");
        assertEquals("/api/v1/plans", prefixOf(CPlanShareController.class));
    }

    // ------------------------------------------------------------------
    // qrCodeUrl 恒空
    // ------------------------------------------------------------------

    /**
     * 二维码由前端画(2026-10-01 用户的决定),后端不经手 —— 所以这个字段
     * <b>永远整键消失</b>,而不是输出 {@code null} 或空串。
     *
     * <p>契约里 {@code ShareResult} 三个字段都没写 {@code required},所以"不出现"
     * 是合法的。但这是一处**已知的不一致面**,登记在 {@code openapi-verification.md} §43。
     */
    @Test
    @DisplayName("ShareResultView 序列化后 qrCodeUrl 整键不出现(二维码归前端画)")
    void qrCodeUrlIsOmitted() throws Exception {
        ShareResultView view = new ShareResultView("abc123", "https://x.com/share/abc123");

        JsonNode node = MAPPER.valueToTree(view);

        assertFalse(node.has("qrCodeUrl"),
                "qrCodeUrl 恒为 null 时应当整键省略,不是输出 null: " + node);
        assertEquals("abc123", node.path("shareToken").asText());
        assertEquals("https://x.com/share/abc123", node.path("shareUrl").asText());
        assertEquals(2, node.size(), "不该有别的字段冒出来: " + node);
    }

    // ------------------------------------------------------------------

    /**
     * 剥出 {@code ApiResponse<X>} 里的那个 {@code X}。
     *
     * <p>公开页用 {@code ResponseEntity} 包了一层(为的是加 {@code Cache-Control}),
     * 写接口没有 —— 这里把两种写法都还原成同一个 {@code X} 再比,
     * 免得"换了个包法"让断言变成恒真的装饰。
     */
    private static Class<?> dataTypeOf(Method method) {
        Type generic = method.getGenericReturnType();
        assertTrue(generic instanceof ParameterizedType,
                method.getName() + " 应当返回泛型的 ApiResponse<…>/ResponseEntity<…>,实际: " + generic);

        ParameterizedType outer = (ParameterizedType) generic;
        Type inner = outer.getActualTypeArguments()[0];
        if (inner instanceof ParameterizedType nested) {
            assertEquals(ApiResponse.class, nested.getRawType(),
                    method.getName() + " 的 ResponseEntity 里包的应当是 ApiResponse");
            inner = nested.getActualTypeArguments()[0];
        } else {
            assertEquals(ApiResponse.class, outer.getRawType(),
                    method.getName() + " 应当返回 ApiResponse<…>(或它外面再包一层 ResponseEntity)");
        }

        assertTrue(inner instanceof Class<?>, "推不出 data 的类型: " + inner);
        return (Class<?>) inner;
    }

    private static String prefixOf(Class<?> controller) {
        RequestMapping mapping = controller.getAnnotation(RequestMapping.class);
        assertTrue(mapping != null, controller.getSimpleName() + " 应当有 @RequestMapping 前缀");
        assertEquals(1, mapping.value().length);
        return mapping.value()[0];
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(type.getSimpleName() + " 里找不到 " + name
                    + " —— 方法被删了或改了签名: " + e.getMessage(), e);
        }
    }
}
