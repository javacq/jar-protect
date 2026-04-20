package com.jarprotect.core.machine;

import com.jarprotect.core.crypto.CryptoUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 机器码生成器。
 * 采集硬件信息，通过 SHA-256 哈希生成格式化的短码（如 A1B2-C3D4-E5F6-7890）。
 */
public class MachineCodeGenerator {

    /** 默认分组数量 */
    private static final int DEFAULT_GROUPS = 4;
    /** 每组字符数 */
    private static final int DEFAULT_GROUP_SIZE = 4;

    /**
     * 生成当前机器的机器码。
     *
     * @return 格式化的机器码字符串，如 A1B2-C3D4-E5F6-7890
     */
    public static String generate() {
        try {
            List<String> components = HardwareCollector.collectAll();
            StringBuilder combined = new StringBuilder();
            for (String c : components) {
                combined.append(c).append("|");
            }

            byte[] hash = CryptoUtils.sha256(combined.toString());
            return CryptoUtils.formatAsCode(hash, DEFAULT_GROUPS, DEFAULT_GROUP_SIZE);
        } catch (Exception e) {
            throw new RuntimeException("生成机器码失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成机器码的完整哈希值（用于内部比对）。
     *
     * @return SHA-256 十六进制字符串
     */
    public static String generateHash() {
        try {
            List<String> components = HardwareCollector.collectAll();
            StringBuilder combined = new StringBuilder();
            for (String c : components) {
                combined.append(c).append("|");
            }

            byte[] hash = CryptoUtils.sha256(combined.toString());
            return CryptoUtils.bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("生成机器码哈希失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证给定的机器码是否与当前环境匹配。
     *
     * @param expectedCode 预期的机器码（短格式或完整哈希）
     * @return true 匹配
     */
    public static boolean verify(String expectedCode) {
        if (expectedCode == null || expectedCode.isEmpty()) {
            return true; // 未设置机器码绑定，直接通过
        }
        // 去除分隔符进行比较
        String normalizedExpected = expectedCode.replace("-", "").toUpperCase();

        String currentCode = generate().replace("-", "").toUpperCase();
        String currentHash = generateHash().toUpperCase();

        return normalizedExpected.equals(currentCode) || normalizedExpected.equals(currentHash);
    }

    /**
     * 获取详细的硬件信息摘要（用于调试和展示）。
     */
    public static String getHardwareDetails() {
        List<String> components = HardwareCollector.collectAll();
        StringBuilder sb = new StringBuilder();
        sb.append("========== 硬件信息摘要 ==========\n");
        for (String c : components) {
            String[] parts = c.split(":", 2);
            if (parts.length == 2) {
                sb.append(String.format("  %-8s: %s%n", parts[0], parts[1]));
            } else {
                sb.append("  ").append(c).append("\n");
            }
        }
        sb.append("==================================");
        return sb.toString();
    }
}
