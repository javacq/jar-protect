package com.jarprotect.core.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 加密辅助工具类。
 */
public class CryptoUtils {

    private static final char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    /**
     * SHA-256 哈希。
     */
    public static byte[] sha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(data);
    }

    /**
     * SHA-256 哈希（字符串输入）。
     */
    public static byte[] sha256(String data) throws Exception {
        return sha256(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 字节数组转十六进制字符串。
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0F]);
            sb.append(HEX_CHARS[b & 0x0F]);
        }
        return sb.toString();
    }

    /**
     * 十六进制字符串转字节数组。
     */
    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    /**
     * 密码混淆：XOR + Base64 编码，用于安全存储到 manifest。
     * 不是加密级安全，但防止明文泄露，真正安全靠机器码 + 注册码校验拦截。
     */
    private static final byte[] OBFUSCATE_KEY = {
            0x4A, 0x41, 0x52, 0x2D, 0x50, 0x72, 0x6F, 0x74,
            0x65, 0x63, 0x74, 0x2D, 0x4B, 0x65, 0x79, 0x21
    }; // "JAR-Protect-Key!"

    public static String obfuscatePassword(String password) {
        byte[] raw = password.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[raw.length];
        for (int i = 0; i < raw.length; i++) {
            result[i] = (byte) (raw[i] ^ OBFUSCATE_KEY[i % OBFUSCATE_KEY.length]);
        }
        return bytesToHex(result);
    }

    public static String deobfuscatePassword(String hex) {
        byte[] data = hexToBytes(hex);
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ OBFUSCATE_KEY[i % OBFUSCATE_KEY.length]);
        }
        return new String(result, StandardCharsets.UTF_8);
    }

    /**
     * 将哈希值格式化为易读的机器码格式（如 A1B2-C3D4-E5F6-G7H8）。
     */
    public static String formatAsCode(byte[] hash, int groups, int size) {
        String hex = bytesToHex(hash);
        StringBuilder sb = new StringBuilder();
        int totalChars = groups * size;
        String source = hex.length() >= totalChars ? hex.substring(0, totalChars) : hex;
        for (int i = 0; i < source.length(); i++) {
            if (i > 0 && i % size == 0) {
                sb.append('-');
            }
            sb.append(source.charAt(i));
        }
        return sb.toString();
    }
}
