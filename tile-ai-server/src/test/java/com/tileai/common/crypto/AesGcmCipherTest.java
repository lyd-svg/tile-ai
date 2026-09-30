package com.tileai.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T-1.11:户型快照静态加密的单元测试(纯单测,不依赖数据库)。
 *
 * <p>断言的重点不是"能加能解"(那只证明 API 没写错),而是三件出错时<b>不会报错、
 * 只会悄悄出错</b>的事:nonce 有没有每次换、密文里能不能读出明文、密钥不对时
 * 是拒绝还是返回垃圾。
 */
class AesGcmCipherTest {

    /** 测试密钥。Base64 的 32 字节 —— 形状必须与生产一致,否则测的是另一套解析。 */
    private static final String KEY = Base64.getEncoder()
            .encodeToString(randomBytes(32));

    private static final String LAYOUT_JSON =
            "{\"spaces\":[{\"type\":\"bathroom\",\"length\":2.5,\"width\":2.0,\"height\":2.8}]}";

    private static AesGcmCipher cipher(String base64Key) {
        CryptoProperties props = new CryptoProperties();
        props.setLayoutKey(base64Key);
        return new AesGcmCipher(props);
    }

    private static byte[] randomBytes(int n) {
        byte[] bytes = new byte[n];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    // ------------------------------------------------------------------
    // 正路
    // ------------------------------------------------------------------

    @Test
    @DisplayName("加密后能解回原文")
    void roundTrip() {
        AesGcmCipher subject = cipher(KEY);
        byte[] sealed = subject.encryptString(LAYOUT_JSON);

        assertEquals(LAYOUT_JSON, subject.decryptToString(sealed));
    }

    @Test
    @DisplayName("密文长度 = nonce(12) + 明文 + tag(16) —— 与迁移文件声明的口径一致")
    void sealedLengthMatchesTheDeclaredLayout() {
        AesGcmCipher subject = cipher(KEY);
        byte[] plain = LAYOUT_JSON.getBytes(StandardCharsets.UTF_8);
        byte[] sealed = subject.encrypt(plain);

        assertEquals(AesGcmCipher.NONCE_LENGTH + plain.length + 16, sealed.length,
                "密文格式是 nonce ‖ 密文 ‖ tag,列类型 VARBINARY 按这个口径建");
    }

    // ------------------------------------------------------------------
    // 三条不变量
    // ------------------------------------------------------------------

    @Test
    @DisplayName("同一明文两次加密得到不同密文 —— 证明每次换了新 nonce")
    void everyEncryptionUsesAFreshNonce() {
        AesGcmCipher subject = cipher(KEY);
        byte[] first = subject.encryptString(LAYOUT_JSON);
        byte[] second = subject.encryptString(LAYOUT_JSON);

        // 前 12 字节就是 nonce。GCM 复用 nonce 会直接泄露明文异或值并让认证失效,
        // 这是整套算法唯一不能将错的地方,所以单列一条断言。
        assertNotEquals(Base64.getEncoder().encodeToString(first),
                Base64.getEncoder().encodeToString(second),
                "两次加密的密文相同 ⇒ nonce 没换,等于把加密废了");
        assertEquals(LAYOUT_JSON, subject.decryptToString(first));
        assertEquals(LAYOUT_JSON, subject.decryptToString(second));
    }

    @Test
    @DisplayName("密文里搜不到明文 —— 规格与空间类型都不该裸奔在库里")
    void ciphertextDoesNotLeakThePlaintext() {
        AesGcmCipher subject = cipher(KEY);
        byte[] sealed = subject.encryptString(LAYOUT_JSON);
        String asLatin1 = new String(sealed, StandardCharsets.ISO_8859_1);

        assertFalse(asLatin1.contains("spaces"), "明文里的字段名出现在密文里");
        assertFalse(asLatin1.contains("bathroom"), "明文里的空间类型出现在密文里");
    }

    @Test
    @DisplayName("密钥不对 ⇒ 拒绝,而不是解出一段垃圾")
    void wrongKeyIsRejectedRatherThanReturningGarbage() {
        byte[] sealed = cipher(KEY).encryptString(LAYOUT_JSON);
        AesGcmCipher other = cipher(Base64.getEncoder().encodeToString(randomBytes(32)));

        // GCM 是带认证的:解不开就是解不开,不会安静地吐出一串无意义字节,
        // 也不会被当成"这份户型就是这么长"。这条是选用 GCM 而非 CBC 的主要理由。
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> other.decryptToString(sealed));
        assertTrue(error.getMessage().contains("解密失败"), error.getMessage());
    }

    @Test
    @DisplayName("密文被改一个字节 ⇒ 认证失败")
    void tamperedCiphertextIsRejected() {
        AesGcmCipher subject = cipher(KEY);
        byte[] sealed = subject.encryptString(LAYOUT_JSON);
        sealed[sealed.length - 1] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> subject.decryptToString(sealed));
    }

    @Test
    @DisplayName("密文太短(连 nonce+tag 都不够)⇒ 明确报长度,不走进 Cipher")
    void tooShortCiphertextIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cipher(KEY).decrypt(new byte[]{1, 2, 3}));
        assertTrue(error.getMessage().contains("长度不合法"), error.getMessage());
    }

    // ------------------------------------------------------------------
    // 密钥来源:缺失与格式错
    // ------------------------------------------------------------------

    @Test
    @DisplayName("未配置密钥 ⇒ 抛错,绝不退化为明文存储")
    void missingKeyDoesNotSilentlyFallBackToPlaintext() {
        AesGcmCipher subject = cipher("");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> subject.encryptString(LAYOUT_JSON));
        assertTrue(error.getMessage().contains("LAYOUT_ENC_KEY"), error.getMessage());
        // 话要说死:退化为明文等于在没人注意时把加密关掉,而库里两种情况下
        // 看起来都是"一段能读的字节",发现不了。
    }

    @Test
    @DisplayName("密钥不是 Base64 ⇒ 报错并说清是编码问题")
    void nonBase64KeyIsRejected() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cipher("这不是base64!!").encryptString(LAYOUT_JSON));
        assertTrue(error.getMessage().contains("Base64"), error.getMessage());
    }

    @Test
    @DisplayName("AES-128 的密钥长度要挡下来 —— 写死 AES-256 就不能默默降级")
    void wrongKeyLengthIsRejected() {
        String sixteenBytes = Base64.getEncoder().encodeToString(randomBytes(16));
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> cipher(sixteenBytes).encryptString(LAYOUT_JSON));
        assertTrue(error.getMessage().contains("32 字节"), error.getMessage());
    }

    @Test
    @DisplayName("缺密钥不影响构造 —— 不碰方案的场景不该被迫先造一个密钥")
    void constructionDoesNotRequireAKey() {
        // 契约测试、纯 Web 切片测试、本地只跑一半功能,都不该被加密配置挡住。
        // 所以密钥是**懒解析**的:构造(以及 Spring 启动)都不碰它,
        // 真正用到时(保存方案)才失败,而那时失败是正确的。
        assertThrows(IllegalStateException.class, () -> cipher("").encryptString("x"));
    }

    @Test
    @DisplayName("二进制内容也能原样往返(不假设明文一定是 UTF-8 文本)")
    void binaryPayloadRoundTrips() {
        AesGcmCipher subject = cipher(KEY);
        byte[] payload = randomBytes(1024);

        assertArrayEquals(payload, subject.decrypt(subject.encrypt(payload)));
    }
}
