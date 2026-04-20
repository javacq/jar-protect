package com.jarprotect.core.license;

import com.jarprotect.core.crypto.CryptoUtils;
import com.jarprotect.core.crypto.RSACrypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * 注册码（License）生成与验证工具。
 *
 * 注册码格式：XXXX-XXXX-XXXX-XXXX （16 个十六进制字符，共 8 字节）
 *
 * 字节布局：
 *   [0..1]  过期天数（2 字节，大端序无符号短整型，自 2000-01-01 起的天数，0 = 永久）
 *   [2..7]  HMAC-SHA256(机器码 + "|" + 过期天数, 密钥) 截取前 6 字节
 *
 * 过期时间编码在注册码内部，验证时自动提取，无需外部传入。
 * license.lic 只需存放注册码一行。
 */
public class LicenseGenerator {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /** 内置默认密钥（服务商不提供密码时使用） */
    private static final String DEFAULT_SECRET = "JP#2026!Secure@KeyForLicense";

    /** 永久有效标记 */
    public static final String PERMANENT = "PERMANENT";

    /** 日期格式 */
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    /** 纪元起点：2000-01-01 */
    private static final long EPOCH_MILLIS;
    static {
        Calendar cal = Calendar.getInstance();
        cal.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);
        EPOCH_MILLIS = cal.getTimeInMillis();
    }

    /** 天数 0 = 永久有效 */
    private static final int PERMANENT_DAYS = 0;

    private static final int EXPIRY_BYTES = 2;   // 过期天数占 2 字节
    private static final int HMAC_BYTES = 6;      // HMAC 截取 6 字节
    private static final int TOTAL_BYTES = EXPIRY_BYTES + HMAC_BYTES; // 8 字节 = 16 hex = XXXX-XXXX-XXXX-XXXX

    // ═══════════════ 生成 ═══════════════

    /**
     * 生成永久注册码（默认密钥）。
     */
    public static String generate(String machineCode) throws Exception {
        return generate(machineCode, null, (Date) null);
    }

    /**
     * 生成永久注册码（自定义密钥）。
     */
    public static String generate(String machineCode, String secretKey) throws Exception {
        return generate(machineCode, secretKey, (Date) null);
    }

    /**
     * 生成注册码。
     *
     * @param machineCode 目标机器码
     * @param secretKey   自定义密钥（null/空 → 使用内置默认密钥）
     * @param expiry      过期日期（null → 永久有效）
     * @return 注册码字符串（如 F9E8-D7C6-B5A4-3210）
     */
    public static String generate(String machineCode, String secretKey, Date expiry) throws Exception {
        if (machineCode == null || machineCode.isEmpty()) {
            throw new IllegalArgumentException("机器码不能为空");
        }
        String key = resolveKey(secretKey);
        int days = dateToDays(expiry);

        // HMAC(机器码 + "|" + 天数, 密钥)
        byte[] hmacFull = computeHmac(machineCode, key, days);

        // 组装: [2字节天数][6字节HMAC]
        byte[] code = new byte[TOTAL_BYTES];
        code[0] = (byte) ((days >> 8) & 0xFF);
        code[1] = (byte) (days & 0xFF);
        System.arraycopy(hmacFull, 0, code, EXPIRY_BYTES, HMAC_BYTES);

        return formatBytes(code);
    }

    /**
     * 生成注册码（过期时间字符串版本）。
     *
     * @param machineCode 目标机器码
     * @param secretKey   自定义密钥
     * @param expiryStr   过期时间字符串（yyyy-MM-dd 或 PERMANENT）
     */
    public static String generate(String machineCode, String secretKey, String expiryStr) throws Exception {
        return generate(machineCode, secretKey, parseExpiry(expiryStr));
    }

    // ═══════════════ 验证 ═══════════════

    /**
     * 验证注册码（默认密钥）。
     * 过期时间从注册码内部自动提取。
     */
    public static LicenseResult verify(String licenseCode, String machineCode) {
        return verify(licenseCode, machineCode, null);
    }

    /**
     * 验证注册码。
     * 过期时间从注册码内部自动提取，无需外部传入。
     *
     * @param licenseCode 注册码
     * @param machineCode 当前机器码
     * @param secretKey   自定义密钥（null/空 → 内置默认）
     * @return 验证结果
     */
    public static LicenseResult verify(String licenseCode, String machineCode, String secretKey) {
        if (licenseCode == null || licenseCode.isEmpty()) {
            return LicenseResult.fail("注册码为空");
        }
        if (machineCode == null || machineCode.isEmpty()) {
            return LicenseResult.fail("机器码为空");
        }

        try {
            // 解析注册码字节
            byte[] code = parseCode(licenseCode);
            if (code == null || code.length != TOTAL_BYTES) {
                return LicenseResult.fail("注册码格式无效");
            }

            // 提取过期天数
            int days = ((code[0] & 0xFF) << 8) | (code[1] & 0xFF);

            // 重新计算 HMAC
            String key = resolveKey(secretKey);
            byte[] hmacFull = computeHmac(machineCode, key, days);

            // 比对 HMAC 部分（6 字节）
            for (int i = 0; i < HMAC_BYTES; i++) {
                if (code[EXPIRY_BYTES + i] != hmacFull[i]) {
                    return LicenseResult.fail("注册码不匹配");
                }
            }

            // 检查过期
            if (days != PERMANENT_DAYS) {
                Date expiryDate = daysToDate(days);
                // 比较到天（忽略时分秒）
                Calendar today = Calendar.getInstance();
                today.set(Calendar.HOUR_OF_DAY, 0);
                today.set(Calendar.MINUTE, 0);
                today.set(Calendar.SECOND, 0);
                today.set(Calendar.MILLISECOND, 0);

                if (expiryDate.before(today.getTime())) {
                    SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
                    return LicenseResult.fail("注册码已过期（过期日期: " + sdf.format(expiryDate) + "）");
                }
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
                return LicenseResult.success("注册码验证通过（有效期至 " + sdf.format(expiryDate) + "）");
            }

            return LicenseResult.success("注册码验证通过（永久有效）");

        } catch (Exception e) {
            return LicenseResult.fail("验证异常: " + e.getMessage());
        }
    }

    // ═══════════════ RSA 模式（私钥推导 HMAC） ═══════════════

    /**
     * 使用 RSA 私钥生成注册码。
     * 格式与 HMAC 模式相同：XXXX-XXXX-XXXX-XXXX
     * 原理：SHA256(privateKey.encoded) 作为 HMAC 密钥，只有持有私钥才能生成/验证。
     *
     * @param machineCode 目标机器码
     * @param privateKey  RSA 私钥
     * @param expiry      过期日期（null → 永久）
     * @return 注册码字符串（XXXX-XXXX-XXXX-XXXX）
     */
    public static String generateRsa(String machineCode, PrivateKey privateKey, Date expiry) throws Exception {
        String hmacKey = RSACrypto.deriveHmacKeyFromPrivateKey(privateKey);
        return generate(machineCode, hmacKey, expiry);
    }

    /**
     * 使用 RSA 私钥生成注册码（过期时间字符串版本）。
     */
    public static String generateRsa(String machineCode, PrivateKey privateKey, String expiryStr) throws Exception {
        return generateRsa(machineCode, privateKey, parseExpiry(expiryStr));
    }

    /**
     * 使用 RSA 私钥验证注册码。
     * 从私钥推导 HMAC 密钥后按 HMAC 模式验证。
     *
     * @param licenseCode 注册码（XXXX-XXXX-XXXX-XXXX）
     * @param machineCode 当前机器码
     * @param privateKey  RSA 私钥
     * @return 验证结果
     */
    public static LicenseResult verifyRsa(String licenseCode, String machineCode, PrivateKey privateKey) {
        try {
            String hmacKey = RSACrypto.deriveHmacKeyFromPrivateKey(privateKey);
            return verify(licenseCode, machineCode, hmacKey);
        } catch (Exception e) {
            return LicenseResult.fail("RSA 验证异常: " + e.getMessage());
        }
    }

    // ═══════════════ 辅助方法 ═══════════════

    /**
     * 从注册码中提取过期时间（供外部展示使用）。
     * @return 过期时间字符串，如 "2026-12-31" 或 "PERMANENT"
     */
    public static String extractExpiry(String licenseCode) {
        try {
            byte[] code = parseCode(licenseCode);
            if (code == null || code.length != TOTAL_BYTES) return null;
            int days = ((code[0] & 0xFF) << 8) | (code[1] & 0xFF);
            if (days == PERMANENT_DAYS) return PERMANENT;
            return new SimpleDateFormat(DATE_FORMAT).format(daysToDate(days));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取过期时间字符串。
     */
    public static String formatExpiry(Date expiry) {
        return expiry != null ? new SimpleDateFormat(DATE_FORMAT).format(expiry) : PERMANENT;
    }

    /**
     * 解析过期时间字符串。
     * @return null 表示永久
     */
    public static Date parseExpiry(String expiryStr) throws Exception {
        if (expiryStr == null || expiryStr.trim().isEmpty() || PERMANENT.equalsIgnoreCase(expiryStr.trim())) {
            return null;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
        sdf.setLenient(false);
        return sdf.parse(expiryStr.trim());
    }

    // ═══════════════ 内部方法 ═══════════════

    private static String resolveKey(String secretKey) {
        return (secretKey != null && !secretKey.trim().isEmpty()) ? secretKey.trim() : DEFAULT_SECRET;
    }

    /**
     * 日期 → 天数（自 2000-01-01 起）。null → 0（永久）。
     */
    private static int dateToDays(Date date) {
        if (date == null) return PERMANENT_DAYS;
        long diff = date.getTime() - EPOCH_MILLIS;
        int days = (int) (diff / (24L * 60 * 60 * 1000));
        return Math.max(1, Math.min(days, 0xFFFF)); // 1 ~ 65535
    }

    /**
     * 天数 → 日期。
     */
    private static Date daysToDate(int days) {
        return new Date(EPOCH_MILLIS + (long) days * 24L * 60 * 60 * 1000);
    }

    /**
     * HMAC-SHA256(机器码 + "|" + 过期天数, 密钥)
     */
    private static byte[] computeHmac(String machineCode, String key, int days) throws Exception {
        String normalizedCode = machineCode.replace("-", "").replace(" ", "").toUpperCase();
        String data = normalizedCode + "|" + days;
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
        mac.init(keySpec);
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 8 字节 → XXXX-XXXX-XXXX-XXXX
     */
    private static String formatBytes(byte[] bytes) {
        String hex = CryptoUtils.bytesToHex(bytes);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hex.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(hex.charAt(i));
        }
        return sb.toString();
    }

    /**
     * XXXX-XXXX-XXXX-XXXX → 8 字节
     */
    private static byte[] parseCode(String code) {
        String hex = code.replace("-", "").replace(" ", "").toUpperCase();
        if (hex.length() != TOTAL_BYTES * 2) return null;
        byte[] bytes = new byte[TOTAL_BYTES];
        for (int i = 0; i < TOTAL_BYTES; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    /**
     * 注册码验证结果。
     */
    public static class LicenseResult {
        private final boolean valid;
        private final String message;

        private LicenseResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public static LicenseResult success() {
            return new LicenseResult(true, "验证通过");
        }

        public static LicenseResult success(String msg) {
            return new LicenseResult(true, msg);
        }

        public static LicenseResult fail(String reason) {
            return new LicenseResult(false, reason);
        }

        public boolean isValid() { return valid; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return (valid ? "[通过] " : "[失败] ") + message;
        }
    }
}
