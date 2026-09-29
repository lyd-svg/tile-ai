package com.tileai.common;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.tileai.common.error.ErrorCode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 把 {@link ErrorCode} 与契约 {@code openapi-common.yaml} 的 enum <b>逐值绑住</b>。
 *
 * <p>这是 T-12(契约 ↔ 实现双向 diff)在本模块的<b>最小可用切片</b>:
 * 真实 T-12 要导出实现侧 OpenAPI 再整体 diff(见任务 T-2.1),而这里只钉死
 * 最容易悄悄漂移的一处 —— 错误码集合。改契约没改代码、或改代码没改契约,
 * 这个测试都会红,不会像 G1–G7 那样"只校验契约自身、两边分叉仍全绿"。
 *
 * <p>不依赖 Spring 上下文,也不需要数据库。
 */
class ErrorCodeContractTest {

    private static final Path CONTRACT = locate("openapi-common.yaml");

    /**
     * 契约里的 enum 必须恰好这么多个码(架构 §10.5)。
     *
     * <p>v0.7 起为 **17** —— 原 19 个码中,验证码相关的两个已随短信通道取消
     * (架构 §15.6「短信通道 —— 已取消」)。
     */
    private static final int EXPECTED_SIZE = 17;

    @Test
    @DisplayName("Java ErrorCode 与契约 enum 逐值一致(多一个少一个都算不符)")
    void javaEnumMatchesContract() {
        Set<Integer> contract = contractErrorCodes();
        Set<Integer> java = javaErrorCodes();

        assertEquals(EXPECTED_SIZE, contract.size(), "契约 enum 数量与 §10.5 不符: " + contract);
        assertEquals(contract, java,
                "实现与契约的错误码集合不一致 —— "
                        + "契约独有: " + diff(contract, java)
                        + ";实现独有: " + diff(java, contract));
    }

    @Test
    @DisplayName("每个错误码的默认文案非空(避免前端拿到空 message)")
    void everyCodeHasMessage() {
        for (ErrorCode code : ErrorCode.values()) {
            assertNotNull(code.defaultMessage(), code + " 缺少默认文案");
            assertTrue(!code.defaultMessage().isBlank(), code + " 的默认文案为空");
        }
    }

    // ------------------------------------------------------------------

    private static Set<Integer> javaErrorCodes() {
        return java.util.Arrays.stream(ErrorCode.values())
                .map(ErrorCode::code)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @SuppressWarnings("unchecked")
    private static Set<Integer> contractErrorCodes() {
        try (InputStream in = Files.newInputStream(CONTRACT)) {
            Map<String, Object> root = new Yaml().load(in);
            Map<String, Object> components = (Map<String, Object>) root.get("components");
            Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
            Map<String, Object> errorCode = (Map<String, Object>) schemas.get("ErrorCode");
            List<Object> values = (List<Object>) errorCode.get("enum");
            assertNotNull(values, "契约中未找到 components.schemas.ErrorCode.enum");
            return values.stream()
                    .map(v -> ((Number) v).intValue())
                    .collect(Collectors.toCollection(TreeSet::new));
        } catch (Exception e) {
            throw new IllegalStateException("读取契约失败: " + CONTRACT, e);
        }
    }

    private static Set<Integer> diff(Set<Integer> a, Set<Integer> b) {
        Set<Integer> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    /**
     * 从 {@code user.dir} 起逐级向上找契约文件。
     * 这样无论从仓库根还是从 {@code tile-ai-server/} 跑测试都能定位到。
     */
    private static Path locate(String fileName) {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path candidate = dir.resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "未找到 " + fileName + ",起点 = " + System.getProperty("user.dir")
                        + " —— 契约文件应在仓库根目录");
    }
}
