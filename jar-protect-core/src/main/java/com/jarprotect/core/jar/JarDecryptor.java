package com.jarprotect.core.jar;

import com.jarprotect.core.crypto.AESCrypto;
import com.jarprotect.core.crypto.CryptoUtils;
import com.jarprotect.core.model.ProtectManifest;
import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.*;

/**
 * JAR 包解密处理器。
 * 从加密后的 JAR 中还原出原始 JAR：
 * 1. 读取加密清单
 * 2. 验证密码正确性
 * 3. 用加密数据(.enc)还原原始 .class 文件
 * 4. 解密资源文件
 * 5. 移除加密元数据，输出还原后的 JAR
 */
public class JarDecryptor {

    private static final String ENCRYPTED_PREFIX = "META-INF/encrypted/";
    private static final Gson GSON = new Gson();

    private final String inputJar;
    private final String outputJar;
    private final String password;

    private int classCount = 0;
    private int resourceCount = 0;

    public JarDecryptor(String inputJar, String outputJar, String password) {
        this.inputJar = inputJar;
        this.outputJar = outputJar;
        this.password = password;
    }

    /**
     * 执行 JAR 解密还原操作。
     */
    public void execute() throws Exception {
        File inputFile = new File(inputJar);
        if (!inputFile.exists()) {
            throw new FileNotFoundException("输入文件不存在: " + inputJar);
        }

        System.out.println("[JAR-Protect] 开始解密还原...");
        System.out.println("[JAR-Protect] 输入: " + inputJar);
        System.out.println("[JAR-Protect] 输出: " + outputJar);

        // 1. 加载加密清单
        ProtectManifest manifest = loadManifest(inputFile);
        if (manifest == null) {
            throw new IllegalStateException("找不到加密清单 (META-INF/protect-manifest.json)，该 JAR 可能未被加密");
        }

        // 2. 验证密码：尝试解密机器码字段（如有）
        verifyPassword(manifest);

        // 构建加密类和资源的集合
        Set<String> encryptedClasses = new HashSet<>(manifest.getEncryptedClasses());
        Set<String> encryptedResources = new HashSet<>(manifest.getEncryptedResources());

        System.out.println("[JAR-Protect] 检测到 " + encryptedClasses.size()
                + " 个加密类, " + encryptedResources.size() + " 个加密资源");

        // 3. 处理 JAR 文件
        try (JarFile jarFile = new JarFile(inputFile);
             JarOutputStream jos = createOutputStream(outputJar, jarFile.getManifest())) {

            Set<String> writtenEntries = new HashSet<>();
            Enumeration<JarEntry> entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // 跳过 manifest
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entryName)) {
                    continue;
                }

                // 跳过加密元数据目录和清单文件（不复制到输出）
                if (entryName.startsWith(ENCRYPTED_PREFIX) || entryName.equals(JarEncryptor.MANIFEST_PATH)) {
                    continue;
                }

                // 跳过已写入的条目
                if (writtenEntries.contains(entryName)) {
                    continue;
                }

                byte[] data = readEntryBytes(jarFile, entry);

                if (encryptedClasses.contains(entryName)) {
                    // 还原加密类：从 .enc 文件读取并解密
                    byte[] restored = restoreClassEntry(jarFile, entryName);
                    if (restored != null) {
                        writeEntry(jos, entryName, restored);
                        classCount++;
                    } else {
                        // .enc 找不到，保留空壳
                        System.err.println("[JAR-Protect] 警告: 找不到加密数据，保留空壳 - " + entryName);
                        writeEntry(jos, entryName, data);
                    }
                } else if (encryptedResources.contains(entryName)) {
                    // 解密资源文件
                    byte[] decrypted = AESCrypto.decrypt(data, password);
                    writeEntry(jos, entryName, decrypted);
                    resourceCount++;
                } else {
                    // 原样复制
                    writeEntry(jos, entryName, data);
                }

                writtenEntries.add(entryName);
            }
        }

        System.out.println("[JAR-Protect] 解密还原完成!");
        System.out.println("[JAR-Protect] 还原类文件: " + classCount + " 个");
        System.out.println("[JAR-Protect] 还原资源文件: " + resourceCount + " 个");
    }

    /**
     * 从加密 JAR 中还原一个类文件。
     */
    private byte[] restoreClassEntry(JarFile jarFile, String classEntryName) throws Exception {
        // 查找对应的 .enc 条目
        String encPath = ENCRYPTED_PREFIX + classEntryName + ".enc";
        JarEntry encEntry = jarFile.getJarEntry(encPath);

        if (encEntry == null) {
            // 尝试其他路径格式
            return null;
        }

        byte[] encryptedData = readEntryBytes(jarFile, encEntry);
        return AESCrypto.decrypt(encryptedData, password);
    }

    /**
     * 加载加密清单。
     */
    private ProtectManifest loadManifest(File jarFile) throws Exception {
        try (JarFile jf = new JarFile(jarFile)) {
            JarEntry manifestEntry = jf.getJarEntry(JarEncryptor.MANIFEST_PATH);
            if (manifestEntry == null) {
                return null;
            }
            try (InputStream is = jf.getInputStream(manifestEntry);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return GSON.fromJson(sb.toString(), ProtectManifest.class);
            }
        }
    }

    /**
     * 验证密码正确性。
     * 如果清单中有机器码密文，尝试解密来验证密码；
     * 否则尝试解密第一个加密类来验证。
     */
    private void verifyPassword(ProtectManifest manifest) throws Exception {
        String mcCipher = manifest.getMachineCodeCipher();
        if (mcCipher != null && !mcCipher.isEmpty()) {
            try {
                byte[] encryptedMC = CryptoUtils.hexToBytes(mcCipher);
                AESCrypto.decrypt(encryptedMC, password);
                System.out.println("[JAR-Protect] 密码验证通过。");
                return;
            } catch (Exception e) {
                throw new SecurityException("密码错误：无法解密，请检查密码是否正确。", e);
            }
        }

        // 无机器码字段时，尝试解密第一个加密类来验证
        if (!manifest.getEncryptedClasses().isEmpty()) {
            String firstClass = manifest.getEncryptedClasses().get(0);
            String encPath = ENCRYPTED_PREFIX + firstClass + ".enc";
            try (JarFile jf = new JarFile(new File(inputJar))) {
                JarEntry entry = jf.getJarEntry(encPath);
                if (entry != null) {
                    byte[] encData = readEntryBytes(jf, entry);
                    try {
                        AESCrypto.decrypt(encData, password);
                        System.out.println("[JAR-Protect] 密码验证通过。");
                        return;
                    } catch (Exception e) {
                        throw new SecurityException("密码错误：无法解密，请检查密码是否正确。", e);
                    }
                }
            }
        }

        System.out.println("[JAR-Protect] 警告: 无法预验证密码，将继续尝试解密。");
    }

    // ===== 辅助方法 =====

    private JarOutputStream createOutputStream(String path, java.util.jar.Manifest manifest) throws IOException {
        File outFile = new File(path);
        File parentDir = outFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        FileOutputStream fos = new FileOutputStream(outFile);
        if (manifest != null) {
            return new JarOutputStream(fos, manifest);
        }
        return new JarOutputStream(fos);
    }

    private byte[] readEntryBytes(JarFile jarFile, JarEntry entry) throws IOException {
        try (InputStream is = jarFile.getInputStream(entry)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        }
    }

    private void writeEntry(JarOutputStream jos, String name, byte[] data) throws IOException {
        JarEntry entry = new JarEntry(name);
        jos.putNextEntry(entry);
        jos.write(data);
        jos.closeEntry();
    }
}
