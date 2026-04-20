package com.jarprotect.agent;

import com.jarprotect.core.crypto.AESCrypto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 资源文件内存解密器（参考 ClassFinal 设计）。
 * 
 * 通过 ASM 注入到 Spring 的 ClassPathResource#getInputStream()，
 * 在 Spring 读取配置文件时拦截并返回解密后的内容，完全内存操作，无临时文件。
 * 
 * 加密的资源存储在 META-INF/encrypted/{原路径}.enc，
 * 原位文件被清空（避免框架解析加密二进制数据报错）。
 */
public class ResourceDecryptor {

    private static final String ENCRYPTED_PREFIX = "META-INF/encrypted/";

    /** 解密缓存：路径 → 解密后的字节 */
    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    /**
     * 尝试解密配置文件（由 ASM 注入到 ClassPathResource#getInputStream 调用）。
     * 
     * 当 Spring 通过 ClassPathResource 读取资源时，本方法被注入到 getInputStream() 开头：
     * - 如果资源有加密版本 → 解密并返回 ByteArrayInputStream
     * - 如果资源未加密 → 返回 null，继续原始逻辑
     *
     * @param path ClassPathResource 中的 path 字段（如 "application.yml"）
     * @return 解密后的 InputStream，或 null（表示非加密资源）
     */
    public static InputStream tryDecrypt(String path) {
        if (path == null || path.isEmpty()) return null;

        String password = System.getProperty("jarprotect.decrypt.password");
        if (password == null) return null;

        // 规范化路径
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;

        // 检查缓存
        byte[] cached = CACHE.get(cleanPath);
        if (cached != null) {
            return new ByteArrayInputStream(cached);
        }

        // 尝试从 META-INF/encrypted/ 加载加密数据
        // 资源在 JAR 中的路径可能是 BOOT-INF/classes/xxx 或 WEB-INF/classes/xxx
        String[] candidates = {
                ENCRYPTED_PREFIX + "BOOT-INF/classes/" + cleanPath + ".enc",
                ENCRYPTED_PREFIX + "WEB-INF/classes/" + cleanPath + ".enc",
                ENCRYPTED_PREFIX + cleanPath + ".enc"
        };

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = ResourceDecryptor.class.getClassLoader();

        for (String candidate : candidates) {
            try {
                InputStream is = cl.getResourceAsStream(candidate);
                if (is == null) {
                    // 尝试系统类加载器（agent 类在 JAR 根目录）
                    is = ClassLoader.getSystemResourceAsStream(candidate);
                }
                if (is != null) {
                    try {
                        byte[] encrypted = readFully(is);
                        byte[] decrypted = AESCrypto.decrypt(encrypted, password);
                        CACHE.put(cleanPath, decrypted);
                        System.out.println("[JAR-Protect] 内存解密资源: " + cleanPath);
                        return new ByteArrayInputStream(decrypted);
                    } finally {
                        is.close();
                    }
                }
            } catch (Exception e) {
                System.err.println("[JAR-Protect] 资源解密失败 [" + cleanPath + "]: " + e.getMessage());
            }
        }

        return null;
    }

    /**
     * 解密配置文件（兼容 ClassFinal 风格的调用方式）。
     * 当原始流为空时从 META-INF/encrypted/ 加载解密。
     *
     * @param path 配置文件路径
     * @param in   原始输入流
     * @return 解密后的输入流（或原始流）
     */
    public static InputStream decryptIfNeeded(String path, InputStream in) {
        if (path == null || path.endsWith(".class")) return in;

        InputStream decrypted = tryDecrypt(path);
        if (decrypted != null) return decrypted;

        return in;
    }

    private static byte[] readFully(InputStream is) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
        return baos.toByteArray();
    }
}
