package com.jarprotect.core.crypto;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

/**
 * RSA 加密工具。
 * 用于 AES+RSA 混合加密模式：RSA 保护 AES 密码，AES 加密实际数据。
 *
 * 密钥格式：Base64 编码的 PKCS#8 (私钥) / X.509 (公钥)，带 PEM 标记。
 * 默认密钥长度：2048 位。
 */
public class RSACrypto {

    private static final String ALGORITHM = "RSA";
    private static final String CIPHER_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int KEY_SIZE = 2048;

    /**
     * 生成 RSA 密钥对（随机种子）。
     *
     * @return KeyPair (publicKey, privateKey)
     */
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        generator.initialize(KEY_SIZE, new SecureRandom());
        return generator.generateKeyPair();
    }

    /**
     * 生成 RSA 密钥对（确定性种子）。
     * 相同的种子始终生成相同的密钥对。
     *
     * @param seed 密钥种子/密码短语
     * @return KeyPair (publicKey, privateKey)
     */
    public static KeyPair generateKeyPair(String seed) throws Exception {
        if (seed == null || seed.trim().isEmpty()) {
            return generateKeyPair();
        }
        byte[] seedBytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(seed.getBytes(StandardCharsets.UTF_8));
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seedBytes);
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        generator.initialize(KEY_SIZE, sr);
        return generator.generateKeyPair();
    }

    /**
     * 从 RSA 私钥推导 HMAC 密钥。
     * 用于 RSA 模式注册码：SHA256(privateKey.encoded) → Base64 作为 HMAC 密钥。
     */
    public static String deriveHmacKeyFromPrivateKey(PrivateKey privateKey) throws Exception {
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(privateKey.getEncoded());
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * 生成密钥对并保存为 PEM 文件。
     *
     * @param publicKeyPath  公钥文件路径
     * @param privateKeyPath 私钥文件路径
     */
    public static void generateKeyPairToFiles(Path publicKeyPath, Path privateKeyPath) throws Exception {
        KeyPair keyPair = generateKeyPair();
        savePublicKey(keyPair.getPublic(), publicKeyPath);
        savePrivateKey(keyPair.getPrivate(), privateKeyPath);
    }

    /**
     * 使用 RSA 公钥加密数据（适合加密 AES 密码等短数据）。
     */
    public static byte[] encrypt(byte[] data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    /**
     * 使用 RSA 私钥解密数据。
     */
    public static byte[] decrypt(byte[] encryptedData, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(encryptedData);
    }

    /**
     * 使用 RSA 公钥加密字符串（返回 Base64）。
     */
    public static String encryptToBase64(String plainText, PublicKey publicKey) throws Exception {
        byte[] encrypted = encrypt(plainText.getBytes(StandardCharsets.UTF_8), publicKey);
        return Base64.getEncoder().encodeToString(encrypted);
    }

    /**
     * 使用 RSA 私钥解密 Base64 密文。
     */
    public static String decryptFromBase64(String base64Cipher, PrivateKey privateKey) throws Exception {
        byte[] encrypted = Base64.getDecoder().decode(base64Cipher);
        byte[] decrypted = decrypt(encrypted, privateKey);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    // ===== RSA 签名/验签（用于注册码系统） =====

    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    /**
     * 使用 RSA 私钥签名数据。
     *
     * @param data       待签名数据
     * @param privateKey RSA 私钥
     * @return 签名（字节数组）
     */
    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        java.security.Signature sig = java.security.Signature.getInstance(SIGN_ALGORITHM);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    /**
     * 使用 RSA 公钥验证签名。
     *
     * @param data      原始数据
     * @param signature 签名
     * @param publicKey RSA 公钥
     * @return 签名是否有效
     */
    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        java.security.Signature sig = java.security.Signature.getInstance(SIGN_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    /**
     * 签名字符串，返回 Base64。
     */
    public static String signToBase64(String content, PrivateKey privateKey) throws Exception {
        byte[] signature = sign(content.getBytes(StandardCharsets.UTF_8), privateKey);
        return Base64.getEncoder().encodeToString(signature);
    }

    /**
     * 验证 Base64 签名。
     */
    public static boolean verifyFromBase64(String content, String base64Signature, PublicKey publicKey) throws Exception {
        byte[] signature = Base64.getDecoder().decode(base64Signature);
        return verify(content.getBytes(StandardCharsets.UTF_8), signature, publicKey);
    }

    // ===== PEM 文件读写 =====

    /**
     * 保存公钥到 PEM 文件。
     */
    public static void savePublicKey(PublicKey key, Path path) throws Exception {
        String pem = "-----BEGIN PUBLIC KEY-----\n"
                + formatBase64(Base64.getEncoder().encodeToString(key.getEncoded()))
                + "-----END PUBLIC KEY-----\n";
        Files.write(path, pem.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 保存私钥到 PEM 文件。
     */
    public static void savePrivateKey(PrivateKey key, Path path) throws Exception {
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + formatBase64(Base64.getEncoder().encodeToString(key.getEncoded()))
                + "-----END PRIVATE KEY-----\n";
        Files.write(path, pem.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 从 PEM 文件加载公钥。
     */
    public static PublicKey loadPublicKey(Path path) throws Exception {
        String pem = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return loadPublicKeyFromPem(pem);
    }

    /**
     * 从 PEM 字符串加载公钥。
     */
    public static PublicKey loadPublicKeyFromPem(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        return factory.generatePublic(spec);
    }

    /**
     * 从 PEM 文件加载私钥。
     */
    public static PrivateKey loadPrivateKey(Path path) throws Exception {
        String pem = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return loadPrivateKeyFromPem(pem);
    }

    /**
     * 从 PEM 字符串加载私钥。
     */
    public static PrivateKey loadPrivateKeyFromPem(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        return factory.generatePrivate(spec);
    }

    /**
     * 将公钥转为 Base64 字符串（不含 PEM 标记）。
     */
    public static String publicKeyToBase64(PublicKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 将私钥转为 Base64 字符串（不含 PEM 标记）。
     */
    public static String privateKeyToBase64(PrivateKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * 格式化 Base64 为每行 64 字符。
     */
    private static String formatBase64(String base64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length()));
            sb.append('\n');
        }
        return sb.toString();
    }
}
