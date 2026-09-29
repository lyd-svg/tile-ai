package com.tileai;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

/**
 * 定位并读取仓库根目录下的文档与契约。
 *
 * <p>让"代码 ↔ 文档"的绑定测试可以真的去读那份文档,而不是把文档内容抄进测试里 ——
 * 抄进去的话测试就变成了同义反复,文档改了它也不会红。
 */
public final class RepoDocs {

    private RepoDocs() {
    }

    /**
     * 从 {@code user.dir} 起逐级向上找文件。
     * 这样无论从仓库根还是从 {@code tile-ai-server/} 跑测试都能定位到。
     */
    public static Path locate(String fileName) {
        Path dir = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path candidate = dir.resolve(fileName);
            if (Files.exists(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "未找到 " + fileName + ",起点 = " + System.getProperty("user.dir"));
    }

    public static String text(String fileName) {
        try {
            return Files.readString(locate(fileName), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("读取失败: " + fileName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> yaml(String fileName) {
        try (InputStream in = Files.newInputStream(locate(fileName))) {
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("解析 YAML 失败: " + fileName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> child(Map<String, Object> node, String key) {
        Object value = node.get(key);
        if (!(value instanceof Map)) {
            throw new IllegalStateException("期望 " + key + " 是 mapping,实际是 " + value);
        }
        return (Map<String, Object>) value;
    }
}
