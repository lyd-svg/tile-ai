package com.tileai.common.storage;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 判断一个图片 URL 该不该被下载 —— <b>纯函数,不碰网络</b>,所以可以放心单测。
 *
 * <h2>为什么需要它</h2>
 *
 * <p>在引入转存之前,Java 只是把这个 URL <b>当字符串存起来</b>,存什么都不出事。
 * 现在 Java 会<b>主动去 GET 它</b> —— 这就把一个"数据"变成了一个"指令",
 * SSRF 从此成为一个真实的攻击面。
 *
 * <p>URL 的来源是内网 Python(可信),但内容源自第三方 API 的响应体。
 * 拿响应体里的地址去发起请求,必须先把"能去哪儿"框死。
 *
 * <h2>三类判定</h2>
 * <ul>
 *   <li>{@link Verdict#OWN} —— 地址本来就是我们自己的,<b>跳过转存</b>。
 *       占位图模式靠它零开销,重复回调也不会重复下载;</li>
 *   <li>{@link Verdict#FETCH} —— 放行,可以下载;</li>
 *   <li>{@link Verdict#REJECT} —— 拒。调用方应<b>静默降级</b>(保留原 URL + 告警),
 *       <b>不是</b>让整次回调失败。</li>
 * </ul>
 *
 * <h2>已知残余风险(诚实标注)</h2>
 *
 * <p>这里只做<b>字面量</b>判定,不做 DNS 解析后再判 —— 因此挡不住
 * <b>DNS rebinding</b>(校验时解析到公网 IP,真正连接时解析到内网 IP)。
 * 彻底解法是把校验过的 IP 钉死、直连 IP 并带上 Host 头。考虑到该 URL 的来源
 * 是内网服务、内容来自已签约的模型厂商,当前判定"不值得上这套复杂度",
 * 但这一条必须写在明面上,而不是假装不存在。
 *
 * <p>另外:即使能过这一关,调用方仍须<b>禁止重定向</b>(见
 * {@link ImageTransloader})。否则一个公网地址 302 到 {@code 169.254.169.254}
 * 就把这里的全部判定绕过去了。
 */
public class UrlPolicy {

    /** 判定结果。 */
    public enum Verdict {
        /** 是我们自己的地址,原样保留、跳过转存。 */
        OWN,
        /** 放行下载。 */
        FETCH,
        /** 拒绝下载,调用方降级。 */
        REJECT
    }

    /**
     * 一次判定。
     *
     * @param verdict 结论
     * @param reason  拒绝原因(仅 {@code REJECT} 时有意义,用于告警日志)
     */
    public record Decision(Verdict verdict, String reason) {

        public boolean isOwn() {
            return verdict == Verdict.OWN;
        }

        public boolean isFetchable() {
            return verdict == Verdict.FETCH;
        }
    }

    private static final Decision OWN = new Decision(Verdict.OWN, null);
    private static final Decision FETCH = new Decision(Verdict.FETCH, null);

    private final List<String> ownOrigins;

    public UrlPolicy(List<String> ownBaseUrls) {
        List<String> origins = new ArrayList<>();
        if (ownBaseUrls != null) {
            for (String base : ownBaseUrls) {
                String origin = originOfQuietly(base);
                if (origin != null) {
                    origins.add(origin);
                }
            }
        }
        this.ownOrigins = List.copyOf(origins);
    }

    /**
     * 判一个 URL。
     *
     * @param rawUrl 待判的地址(Python 回调里来的那个)
     */
    public Decision assess(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return reject("URL 为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException e) {
            return reject("URL 无法解析");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return reject("URL 缺少主机名");
        }
        // user@host 形式:用户信息会被当成主机名的一部分误判,且实际请求会带凭据
        if (uri.getUserInfo() != null) {
            return reject("URL 带 userinfo,拒绝");
        }

        // 自有域判定放在最前:占位图模式(http://localhost:8080/placeholder.svg)
        // 走的就是这一条 —— 若不先判它,下面的"拒环回"会把自家地址也拒掉,
        // 每次回调都刷一条无意义的告警。
        String origin = originOfQuietly(rawUrl);
        if (origin != null && ownOrigins.contains(origin)) {
            return OWN;
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return reject("协议不允许: " + uri.getScheme());
        }

        if (isBlockedHost(host)) {
            return reject("目标是本机/内网/链路本地地址: " + host);
        }

        return FETCH;
    }

    // ------------------------------------------------------------------
    // 主机判定
    // ------------------------------------------------------------------

    /**
     * 是否指向本机或内网。
     *
     * <p>覆盖这几类:环回、私网({@code 10/8}、{@code 172.16/12}、{@code 192.168/16})、
     * 链路本地(含云元数据端点 {@code 169.254.169.254})、运营商级 NAT
     * ({@code 100.64/10})、未指定地址、以及 IP 的<b>非点分十进制写法</b>
     * (纯整数、十六进制 —— 那是绕过点分十进制判定的老把戏)。
     *
     * <p>非 static 是为了让测试能覆写它:验下载行为的用例要打一个
     * <b>绑在 127.0.0.1 上的桩服务</b>,不覆写的话第一关就被自己的防护拒了。
     * 生产路径不做任何覆写。
     */
    protected boolean isBlockedHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        // URI.getHost() 对 IPv6 会保留方括号
        if (h.startsWith("[") && h.endsWith("]")) {
            h = h.substring(1, h.length() - 1);
        }

        if (h.equals("localhost") || h.endsWith(".localhost")) {
            return true;
        }

        if (h.contains(":")) {
            return isBlockedIpv6(h);
        }

        // 纯数字/十六进制形式:http://2130706433/ 就是 127.0.0.1
        if (h.matches("^[0-9]+$") || h.matches("^0x[0-9a-f]+$")) {
            return true;
        }

        return isBlockedIpv4(h);
    }

    private static boolean isBlockedIpv4(String h) {
        String[] parts = h.split("\\.", -1);
        if (parts.length != 4) {
            // 不是点分四段就不是本函数管的形态;非 IP 的主机名交给 DNS 那一层
            return false;
        }
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (!part.matches("^[0-9]{1,3}$")) {
                // ⚠️ 四段里只要有一段不是纯数字,它就是个主机名而不是 IP
                // (dashscope-result.oss-cn-beijing.aliyuncs.com 恰好也是四段)。
                // 这里必须放行 —— 早先写成 return true,会把正常的图片源全部拦死。
                return false;
            }
            int value = Integer.parseInt(part);
            if (value > 255) {
                // 四段全是数字却超出 255:不是合法 IP,但形态可疑,按拒绝处理
                return true;
            }
            octets[i] = value;
        }

        int a = octets[0];
        int b = octets[1];
        if (a == 0 || a == 127) {
            return true;
        }
        if (a == 10) {
            return true;
        }
        if (a == 172 && b >= 16 && b <= 31) {
            return true;
        }
        if (a == 192 && b == 168) {
            return true;
        }
        if (a == 169 && b == 254) {
            return true;
        }
        // 100.64.0.0/10 运营商级 NAT
        return a == 100 && b >= 64 && b <= 127;
    }

    private static boolean isBlockedIpv6(String h) {
        if (h.equals("::1") || h.equals("::")) {
            return true;
        }
        if (h.startsWith("fe80")) {
            return true;
        }
        // fc00::/7 唯一本地地址
        if (h.startsWith("fc") || h.startsWith("fd")) {
            return true;
        }
        // IPv4-mapped: ::ffff:127.0.0.1
        int mapped = h.indexOf("::ffff:");
        if (mapped >= 0) {
            return isBlockedIpv4(h.substring(mapped + "::ffff:".length()));
        }
        return false;
    }

    // ------------------------------------------------------------------
    // origin
    // ------------------------------------------------------------------

    /**
     * 取 {@code scheme://host:port} 形式的 origin(端口缺省时补默认端口)。
     *
     * <p>用 origin 比较而<b>不是字符串前缀</b>:后者会被
     * {@code http://localhost:8080.evil.com/...} 这种地址绕过。
     *
     * @return 解析不出 origin 时返回 {@code null}(调用方按"不匹配"处理)
     */
    static String originOfQuietly(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            host = host.toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            if (port < 0) {
                port = scheme.equals("https") ? 443 : 80;
            }
            return scheme + "://" + host + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Decision reject(String reason) {
        return new Decision(Verdict.REJECT, reason);
    }
}
