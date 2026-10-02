package com.tileai.common.storage;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.tileai.common.storage.UrlPolicy.Verdict;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 图片 URL 判定的单测。
 *
 * <p>这些用例守的是<b>新增的攻击面</b>:在引入转存之前,Java 只是把 URL 当字符串存,
 * 存什么都不出事;现在它会主动去 GET —— 于是"能不能指向内网"成了必须钉死的事。
 */
@DisplayName("UrlPolicy —— 图片地址能不能下载")
class UrlPolicyTest {

    private static final String OUR_BASE = "http://localhost:8080";

    private final UrlPolicy policy = new UrlPolicy(List.of(OUR_BASE));

    // ------------------------------------------------------------------
    // 自有域
    // ------------------------------------------------------------------

    @Test
    @DisplayName("自己域下的地址判为 OWN(占位图模式走这条,跳过转存)")
    void ownAddressIsSkipped() {
        UrlPolicy.Decision decision = policy.assess("http://localhost:8080/placeholder.svg?v=default");

        assertEquals(Verdict.OWN, decision.verdict());
        assertTrue(decision.isOwn());
    }

    @Test
    @DisplayName("端口不同就不算自己域 —— 免得把别人的 8080 当成自家")
    void ownRequiresSamePort() {
        assertNotEquals(Verdict.OWN, policy.assess("http://localhost:8081/placeholder.svg").verdict());
    }

    /**
     * 用<b>origin 比较</b>而不是字符串前缀,防的就是这一类:前缀匹配会把
     * {@code localhost:8080.evil.com} 当成自家地址。
     */
    @Test
    @DisplayName("自家域后缀被做手脚不算自己域")
    void originConfusionIsNotOwn() {
        assertNotEquals(Verdict.OWN, policy.assess("http://localhost:8080.evil.com/x.png").verdict());
    }

    @Test
    @DisplayName("配了多个自有域时逐个匹配")
    void multipleOwnBaseUrls() {
        UrlPolicy multi = new UrlPolicy(List.of(OUR_BASE, "https://cdn.example.com"));

        assertEquals(Verdict.OWN, multi.assess("https://cdn.example.com/a/b.png").verdict());
        assertEquals(Verdict.OWN, multi.assess("http://localhost:8080/x.png").verdict());
    }

    // ------------------------------------------------------------------
    // 放行
    // ------------------------------------------------------------------

    @ParameterizedTest
    @DisplayName("公网 http(s) 放行")
    @ValueSource(strings = {
            "https://dashscope-result.oss-cn-beijing.aliyuncs.com/a/b.png?Expires=1",
            "http://example.com/image.jpg",
            "https://1.1.1.1/img.png",
            "https://172.32.0.1/img.png",   // 172.16/12 的上界之外,应当放行
            "https://100.128.0.1/img.png",  // 100.64/10 的上界之外,应当放行
            // ⚠️ 回归:四段的主机名不是 IP。这一条曾经被误判成"可疑 IP"而拒掉,
            // 而 OSS 域名恰好几乎都是四段 —— 会静默拦死全部正常图源。
            "https://img.cdn.example.com/x.png",
            "https://dashscope-result.oss-cn-beijing.aliyuncs.com/x.png"})
    void publicAddressesAreFetchable(String url) {
        assertEquals(Verdict.FETCH, policy.assess(url).verdict(), url);
    }

    // ------------------------------------------------------------------
    // 拒绝:内网与走私形态
    // ------------------------------------------------------------------

    @ParameterizedTest
    @DisplayName("环回 / 私网 / 链路本地 / 云元数据端点一律拒")
    @ValueSource(strings = {
            "http://127.0.0.1/x.png",
            "http://127.1.2.3/x.png",
            "http://10.0.0.5/x.png",
            "http://172.16.0.1/x.png",
            "http://172.31.255.254/x.png",
            "http://192.168.1.1/x.png",
            "http://169.254.169.254/latest/meta-data/",   // 云元数据:SSRF 的经典目标
            "http://100.64.0.1/x.png",
            "http://0.0.0.0/x.png",
            "http://localhost/x.png",
            "http://foo.localhost/x.png",
            "http://[::1]/x.png",
            "http://[fe80::1]/x.png",
            "http://[fd00::1]/x.png"})
    void internalAddressesAreRejected(String url) {
        UrlPolicy.Decision decision = policy.assess(url);

        assertEquals(Verdict.REJECT, decision.verdict(), url);
        assertTrue(decision.reason() != null && !decision.reason().isBlank(), "拒绝必须给得出原因");
    }

    @ParameterizedTest
    @DisplayName("IP 的非点分十进制写法也拒(绕点分判定的老把戏)")
    @ValueSource(strings = {
            "http://2130706433/x.png",   // = 127.0.0.1
            "http://0x7f000001/x.png"})
    void obfuscatedLoopbackIsRejected(String url) {
        assertEquals(Verdict.REJECT, policy.assess(url).verdict(), url);
    }

    @ParameterizedTest
    @DisplayName("非 http(s) 协议一律拒(file:// 读到就是任意文件读取)")
    @ValueSource(strings = {
            "file:///etc/passwd",
            "ftp://example.com/x.png",
            "jar:file:/a.jar!/b.png",
            "gopher://example.com/x"})
    void nonHttpSchemesAreRejected(String url) {
        assertEquals(Verdict.REJECT, policy.assess(url).verdict(), url);
    }

    @Test
    @DisplayName("带 userinfo 的地址拒(user@host 会干扰主机判定,且会带上凭据)")
    void userInfoIsRejected() {
        assertEquals(Verdict.REJECT, policy.assess("http://user:pass@example.com/x.png").verdict());
    }

    @ParameterizedTest
    @DisplayName("空值 / 畸形 / 缺主机名一律拒")
    @ValueSource(strings = {"", "   ", "not a url", "http:///x.png"})
    void malformedUrlsAreRejected(String url) {
        assertEquals(Verdict.REJECT, policy.assess(url).verdict(), "'" + url + "'");
    }

    @Test
    @DisplayName("null 拒")
    void nullIsRejected() {
        assertEquals(Verdict.REJECT, policy.assess(null).verdict());
    }
}
