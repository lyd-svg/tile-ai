package com.tileai.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * 隐私字段的静态加密(AES-256-GCM)。架构 §13.5 / §8.3,CLAUDE.md 约定。
 *
 * <p>作用范围就是 {@code t_layout.layout_enc} / {@code t_plan.layout_enc}。
 * <b>只作用于静态存储</b> —— 对外 API 一律传解密后的明文,对外 schema 里
 * 不得出现 {@code layout_enc}(契约 T-8)。加解密只在「进库/出库」这一层发生,
 * 不越过 service 边界往外渗。
 *
 * <h2>密文格式</h2>
 *
 * <pre>
 *   nonce(12 字节) ‖ 密文(与明文等长) ‖ 认证标签(16 字节)
 * </pre>
 *
 * <p>二进制、不 Base64 —— 口径见 {@code V3__layout_and_template.sql} 的注释,
 * 列类型是 {@code VARBINARY(16384)}。列本来就是二进制,再套一层编码只会让
 * 密文白白膨胀 33%,并且让"这段 bytes 到底是什么"多一次心智负担。
 *
 * <h2>三条不变量</h2>
 *
 * <ol>
 *   <li><b>每次加密都换新 nonce。</b> GCM 在 nonce 复用时不是"安全性下降"这么轻描淡写
 *       —— 两段用同一 nonce 加密的密文异或起来就是两段明文的异或,而且攻击者能伪造
 *       通过认证的密文。这是整套算法里唯一一处不能将错的地方,所以 nonce 由本类
 *       用 {@link SecureRandom} 内部产生,<b>不接受调用方传入</b>。</li>
 *   <li><b>密钥只走环境变量</b>(§13.6):{@code LAYOUT_ENC_KEY},Base64 的 32 字节。
 *       不落代码、不落库、不打日志。</li>
 *   <li><b>密钥在首次使用时才校验</b>,缺少密钥<b>不阻止应用启动</b>。理由很实际:
 *       契约测试、纯 Web 切片测试、本地跑一半功能,都不该被迫先造一个密钥。
 *       真正用到时(保存方案)才失败,而那时失败是**正确的** —— 见下。</li>
 * </ol>
 *
 * <p><b>为什么缺密钥时不退化为明文</b>:那等于在没人注意的时候把加密悄悄关掉。
 * 更糟的是它不会显形 —— 库里两种情况下都是一段能读的字节,直到某天需要合规证明时
 * 才发现历史数据从来没加过密。宁可让写方案直接失败。
 */
@Component
public class AesGcmCipher {

    private static final Logger log = LoggerFactory.getLogger(AesGcmCipher.class);

    /** GCM 推荐的 nonce 长度(字节)。 */
    public static final int NONCE_LENGTH = 12;

    /** 认证标签长度(位)。128 位是 GCM 的推荐值,也是 Java 默认上限。 */
    public static final int TAG_LENGTH_BITS = 128;

    /** 认证标签长度(字节),用于密文长度合法性的下界判断。 */
    private static final int TAG_LENGTH_BYTES = TAG_LENGTH_BITS / 8;

    /** AES-256 的密钥字节数。 */
    private static final int KEY_BYTES = 32;

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CryptoProperties properties;

    /**
     * 解析后的密钥。{@code volatile} + 双重检查:AES 的 {@link SecretKey} 是不可变对象,
     * 解析一次即可长期复用({@link SecureRandom} 的初始化比一次 AES 运算贵得多)。
     */
    private volatile SecretKey key;

    public AesGcmCipher(CryptoProperties properties) {
        this.properties = properties;
    }

    /**
     * 启动时提醒一次,但<b>不失败</b>。
     *
     * <p>值得提醒是因为这个配置缺了以后,症状会出现在很远的地方 ——
     * 用户点了生成、等了半分钟,最后卡在"保存方案"上。提前一行 WARN 能省掉这段排查。
     */
    @PostConstruct
    void warnIfKeyMissing() {
        if (!StringUtils.hasText(properties.getLayoutKey())) {
            log.warn("未配置 LAYOUT_ENC_KEY:户型快照无法加密,凡是要保存方案的操作都会失败。"
                    + "本地联调用 `openssl rand -base64 32` 生成后设进环境变量;生产必须配置。"
                    + "刻意不退化为明文存储 —— 那等于悄悄关掉加密。");
        }
    }

    /**
     * 加密。
     *
     * @param plaintext 明文(通常是一份户型 JSON 的 UTF-8 字节)
     * @return {@code nonce ‖ 密文 ‖ tag},可直接写进 {@code VARBINARY} 列
     * @throws IllegalStateException 未配置密钥 —— 这是配置问题,不该被当成业务失败
     */
    public byte[] encrypt(byte[] plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("待加密内容不能为 null");
        }
        byte[] nonce = newNonce();
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            // doFinal 已经把 tag 追加在末尾,不需要再单独取
            byte[] sealed = cipher.doFinal(plaintext);
            byte[] out = new byte[NONCE_LENGTH + sealed.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_LENGTH);
            System.arraycopy(sealed, 0, out, NONCE_LENGTH, sealed.length);
            return out;
        } catch (GeneralSecurityException e) {
            // 走到这里说明 JVM/安全提供者有问题,不是数据问题
            throw new IllegalStateException("户型加密失败", e);
        }
    }

    /**
     * 解密。
     *
     * @param ciphertext {@link #encrypt} 的产物
     * @return 原明文
     * @throws IllegalStateException 密钥不匹配、密文被篡改或长度不合法。
     *         失败信息<b>不进响应体</b> —— 由 {@code GlobalExceptionHandler} 折成 90001,
     *         真实原因只进日志。
     */
    public byte[] decrypt(byte[] ciphertext) {
        // 下界 = nonce + tag(密文可以为空,虽然户型 JSON 不会)
        if (ciphertext == null || ciphertext.length <= NONCE_LENGTH + TAG_LENGTH_BYTES) {
            throw new IllegalStateException("户型密文长度不合法: "
                    + (ciphertext == null ? 0 : ciphertext.length) + " 字节");
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        System.arraycopy(ciphertext, 0, nonce, 0, NONCE_LENGTH);
        byte[] body = new byte[ciphertext.length - NONCE_LENGTH];
        System.arraycopy(ciphertext, NONCE_LENGTH, body, 0, body.length);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(body);
        } catch (AEADBadTagException e) {
            // 认证失败。两种可能:密钥换了、或密文被改过 —— 都不该把细节告诉调用方
            throw new IllegalStateException("户型解密失败:密钥不匹配或密文已损坏", e);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("户型解密失败", e);
        }
    }

    /** 文本便利方法:按 UTF-8 编码后加密。 */
    public byte[] encryptString(String plaintext) {
        return encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    /** 文本便利方法:解密后按 UTF-8 解码。 */
    public String decryptToString(byte[] ciphertext) {
        return new String(decrypt(ciphertext), StandardCharsets.UTF_8);
    }

    private static byte[] newNonce() {
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    private SecretKey key() {
        SecretKey resolved = key;
        if (resolved == null) {
            synchronized (this) {
                if (key == null) {
                    key = resolveKey();
                }
                resolved = key;
            }
        }
        return resolved;
    }

    private SecretKey resolveKey() {
        String configured = properties.getLayoutKey();
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException(
                    "未配置户型加密密钥(环境变量 LAYOUT_ENC_KEY),无法读写 layout_enc");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(configured.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("LAYOUT_ENC_KEY 不是合法的 Base64", e);
        }
        if (raw.length != KEY_BYTES) {
            // 常见错误:拿 16 字节当 AES-256 用,或把 Base64 的字符数当成了字节数
            throw new IllegalStateException("LAYOUT_ENC_KEY 解码后必须是 " + KEY_BYTES
                    + " 字节(AES-256),实际 " + raw.length + " 字节");
        }
        return new SecretKeySpec(raw, KEY_ALGORITHM);
    }
}
