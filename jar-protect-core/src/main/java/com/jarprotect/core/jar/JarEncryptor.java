package com.jarprotect.core.jar;

import com.jarprotect.core.bytecode.ClassProcessor;
import com.jarprotect.core.crypto.AESCrypto;
import com.jarprotect.core.crypto.CryptoUtils;
import com.jarprotect.core.crypto.RSACrypto;
import com.jarprotect.core.model.EncryptConfig;
import com.jarprotect.core.model.ProtectManifest;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * JAR 包加密处理器。
 * 负责读取原始 JAR，对匹配的类文件和资源文件进行加密，输出加密后的 JAR。
 */
public class JarEncryptor {

    /** 加密数据存储的前缀路径 */
    private static final String ENCRYPTED_PREFIX = "META-INF/encrypted/";

    /** 清单文件路径 */
    public static final String MANIFEST_PATH = "META-INF/protect-manifest.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final EncryptConfig config;
    private final ProtectManifest manifest;
    private int classCount = 0;
    private int resourceCount = 0;
    private int libCount = 0;

    public JarEncryptor(EncryptConfig config) {
        this.config = config;
        this.manifest = new ProtectManifest();
    }

    /** ProtectLauncher 的全限定名（嵌入后作为新 Main-Class） */
    private static final String LAUNCHER_CLASS = "com.jarprotect.agent.ProtectLauncher";

    /**
     * 执行 JAR 加密操作。
     */
    public void execute() throws Exception {
        config.validate();

        File inputFile = new File(config.getInputJar());
        if (!inputFile.exists()) {
            throw new FileNotFoundException("输入文件不存在: " + config.getInputJar());
        }

        System.out.println("[JAR-Protect] 开始加密处理...");
        System.out.println("[JAR-Protect] 输入: " + config.getInputJar());
        System.out.println("[JAR-Protect] 输出: " + config.getOutputJar());

        // 准备清单
        manifest.setEncryptTime(System.currentTimeMillis());
        manifest.setPackagePatterns(config.getPackagePatterns());

        // 密码存储策略
        if (config.getRsaPublicKeyPath() != null && !config.getRsaPublicKeyPath().isEmpty()) {
            // AES+RSA 混合模式：RSA 加密 AES 密码
            PublicKey rsaPubKey = RSACrypto.loadPublicKey(Paths.get(config.getRsaPublicKeyPath()));
            String rsaEncPwd = RSACrypto.encryptToBase64(config.getPassword(), rsaPubKey);
            manifest.setRsaEncryptedPassword(rsaEncPwd);
            manifest.setRsaPublicKey(RSACrypto.publicKeyToBase64(rsaPubKey));
            // RSA 模式不存储混淆密码（必须用私钥解密）
            manifest.setEncryptedPassword(null);
            System.out.println("[JAR-Protect] 已启用 AES+RSA 混合加密模式");
        } else {
            // 纯 AES 模式：混淆存储密码（运行时自动提取）
            manifest.setEncryptedPassword(CryptoUtils.obfuscatePassword(config.getPassword()));
        }

        // 注册码验证配置
        manifest.setLicenseRequired(config.isLicenseRequired());
        manifest.setRsaLicenseMode(config.isRsaLicenseMode());
        if (config.getLicenseSecretKey() != null && !config.getLicenseSecretKey().isEmpty()) {
            manifest.setLicenseSecretKey(config.getLicenseSecretKey());
        }

        // 处理机器码绑定
        if (config.getMachineCode() != null && !config.getMachineCode().isEmpty()) {
            String machineCodeHash = CryptoUtils.bytesToHex(
                    CryptoUtils.sha256(config.getMachineCode().replace("-", "").toUpperCase()));
            byte[] encryptedMC = AESCrypto.encrypt(
                    machineCodeHash.getBytes(StandardCharsets.UTF_8), config.getPassword());
            manifest.setMachineCodeCipher(CryptoUtils.bytesToHex(encryptedMC));
            System.out.println("[JAR-Protect] 已绑定机器码: " + config.getMachineCode());
        }

        // 处理 JAR 文件
        try (JarFile jarFile = new JarFile(inputFile)) {

            // 读取原始 MANIFEST，提取并保存原始 Main-Class
            java.util.jar.Manifest originalManifest = jarFile.getManifest();
            java.util.jar.Manifest newManifest = originalManifest != null
                    ? new java.util.jar.Manifest(originalManifest)
                    : new java.util.jar.Manifest();
            Attributes mainAttrs = newManifest.getMainAttributes();

            // 保存原始 Main-Class
            String originalMainClass = mainAttrs.getValue(Attributes.Name.MAIN_CLASS);
            if (originalMainClass != null && !originalMainClass.isEmpty()) {
                manifest.setOriginalMainClass(originalMainClass);
                System.out.println("[JAR-Protect] 原始 Main-Class: " + originalMainClass);
            }

            // 如果提供了 agent JAR，修改 MANIFEST
            boolean embedAgent = config.getAgentJarPath() != null
                    && !config.getAgentJarPath().isEmpty()
                    && new File(config.getAgentJarPath()).exists();

            if (embedAgent) {
                // 设置新的 Main-Class 为 ProtectLauncher
                mainAttrs.put(Attributes.Name.MAIN_CLASS, LAUNCHER_CLASS);
                // JDK 9+ 自动加载 agent
                mainAttrs.putValue("Launcher-Agent-Class", "com.jarprotect.agent.ProtectAgent");
                mainAttrs.putValue("Premain-Class", "com.jarprotect.agent.ProtectAgent");
                mainAttrs.putValue("Agent-Class", "com.jarprotect.agent.ProtectAgent");
                mainAttrs.putValue("Can-Redefine-Classes", "true");
                mainAttrs.putValue("Can-Retransform-Classes", "true");
                System.out.println("[JAR-Protect] 已嵌入 Agent，新 Main-Class: " + LAUNCHER_CLASS);
            }

            try (JarOutputStream jos = createOutputStream(config.getOutputJar(), newManifest)) {
                Set<String> writtenEntries = new HashSet<>();
                writtenEntries.add("META-INF/MANIFEST.MF"); // 已由 JarOutputStream 写入

                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    if ("META-INF/MANIFEST.MF".equalsIgnoreCase(entryName)) continue;
                    if (writtenEntries.contains(entryName)) continue;

                    byte[] data = readEntryBytes(jarFile, entry);

                    if (entryName.endsWith(".class") && shouldEncryptClass(entryName)) {
                        processClassEntry(jos, entryName, data, writtenEntries);
                    } else if (shouldEncryptResource(entryName)) {
                        processResourceEntry(jos, entryName, data, writtenEntries);
                    } else if (config.isEncryptLibs() && isTargetLib(entryName)) {
                        processLibEntry(jos, entryName, data, writtenEntries);
                    } else {
                        writeEntry(jos, entryName, data);
                        writtenEntries.add(entryName);
                    }
                }

                // 嵌入 agent JAR 中的所有 class 文件
                if (embedAgent) {
                    embedAgentClasses(jos, config.getAgentJarPath(), writtenEntries);
                }

                // 写入加密清单
                String manifestJson = GSON.toJson(manifest);
                writeEntry(jos, MANIFEST_PATH, manifestJson.getBytes(StandardCharsets.UTF_8));
                writtenEntries.add(MANIFEST_PATH);
            }
        }

        System.out.println("[JAR-Protect] 加密完成!");
        System.out.println("[JAR-Protect] 加密类文件: " + classCount + " 个");
        System.out.println("[JAR-Protect] 加密资源文件: " + resourceCount + " 个");
        System.out.println("[JAR-Protect] 加密依赖库: " + libCount + " 个");
        if (config.isLicenseRequired()) {
            System.out.println("[JAR-Protect] 注册码验证: 已启用（客户需在 JAR 同目录下创建 license.lic 文件）");
        }
    }

    /**
     * 将 agent fat-jar 中的所有 .class 文件嵌入到输出 JAR。
     */
    private void embedAgentClasses(JarOutputStream jos, String agentJarPath,
                                   Set<String> writtenEntries) throws IOException {
        File agentFile = new File(agentJarPath);
        int count = 0;
        try (ZipFile agentZip = new ZipFile(agentFile)) {
            Enumeration<? extends ZipEntry> entries = agentZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                // 跳过 MANIFEST 和已存在的条目
                if (name.startsWith("META-INF/MANIFEST.MF") || name.startsWith("META-INF/maven/")) continue;
                if (writtenEntries.contains(name)) continue;
                if (entry.isDirectory()) {
                    writeEntry(jos, name, new byte[0]);
                    writtenEntries.add(name);
                    continue;
                }
                // 复制所有 .class、.properties、.json 等资源
                try (InputStream is = agentZip.getInputStream(entry)) {
                    byte[] data = readStreamBytes(is);
                    writeEntry(jos, name, data);
                    writtenEntries.add(name);
                    if (name.endsWith(".class")) count++;
                }
            }
        }
        System.out.println("[JAR-Protect] 已嵌入 Agent 类: " + count + " 个");
    }

    /**
     * 处理类文件：擦除方法体 + 加密原始字节码。
     */
    private void processClassEntry(JarOutputStream jos, String entryName, byte[] data,
                                   Set<String> writtenEntries) throws Exception {
        if (!ClassProcessor.isValidClass(data)) {
            writeEntry(jos, entryName, data);
            writtenEntries.add(entryName);
            return;
        }

        // 1. 擦除方法体，生成空壳类
        byte[] erasedClass = ClassProcessor.eraseMethodBodies(data);

        // 2. 加密原始完整字节码
        byte[] encryptedData = AESCrypto.encrypt(data, config.getPassword());

        // 3. 写入空壳类（替代原始位置）
        writeEntry(jos, entryName, erasedClass);
        writtenEntries.add(entryName);

        // 4. 写入加密数据
        String encryptedPath = ENCRYPTED_PREFIX + entryName + ".enc";
        writeEntry(jos, encryptedPath, encryptedData);
        writtenEntries.add(encryptedPath);

        manifest.getEncryptedClasses().add(entryName);
        classCount++;

        if (classCount % 50 == 0) {
            System.out.println("[JAR-Protect] 已处理 " + classCount + " 个类文件...");
        }
    }

    /**
     * 处理资源文件：原位放空壳，加密数据存到 META-INF/encrypted/。
     * 这样 Spring Boot 等框架读到原位文件时不会解析失败（空文件），
     * 真实内容由 ProtectLauncher 从 META-INF/encrypted/ 解密后通过临时目录提供。
     */
    private void processResourceEntry(JarOutputStream jos, String entryName, byte[] data,
                                      Set<String> writtenEntries) throws Exception {
        // 1. 加密原始资源
        byte[] encryptedData = AESCrypto.encrypt(data, config.getPassword());

        // 2. 原位写入空文件（避免框架解析加密二进制数据报错）
        writeEntry(jos, entryName, new byte[0]);
        writtenEntries.add(entryName);

        // 3. 加密数据存到 META-INF/encrypted/ 目录
        String encryptedPath = ENCRYPTED_PREFIX + entryName + ".enc";
        writeEntry(jos, encryptedPath, encryptedData);
        writtenEntries.add(encryptedPath);

        // 标记为已加密
        manifest.getEncryptedResources().add(entryName);
        resourceCount++;
    }

    /**
     * 处理依赖 JAR：递归加密其中的匹配类文件。
     */
    private void processLibEntry(JarOutputStream jos, String entryName, byte[] data,
                                 Set<String> writtenEntries) throws Exception {
        // 将 lib JAR 内容读入临时缓冲，递归处理
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (JarInputStream jis = new JarInputStream(bais);
             JarOutputStream innerJos = new JarOutputStream(baos)) {

            JarEntry innerEntry;
            while ((innerEntry = jis.getNextJarEntry()) != null) {
                byte[] innerData = readStreamBytes(jis);
                String innerName = innerEntry.getName();

                if (innerName.endsWith(".class") && shouldEncryptClass(innerName)) {
                    if (ClassProcessor.isValidClass(innerData)) {
                        byte[] erasedClass = ClassProcessor.eraseMethodBodies(innerData);
                        byte[] encryptedData = AESCrypto.encrypt(innerData, config.getPassword());

                        writeEntry(innerJos, innerName, erasedClass);
                        writeEntry(innerJos, ENCRYPTED_PREFIX + innerName + ".enc", encryptedData);

                        manifest.getEncryptedClasses().add(entryName + "!/" + innerName);
                    } else {
                        writeEntry(innerJos, innerName, innerData);
                    }
                } else {
                    writeEntry(innerJos, innerName, innerData);
                }
            }
        }

        // 写入处理后的 lib JAR
        writeEntry(jos, entryName, baos.toByteArray());
        writtenEntries.add(entryName);

        manifest.getEncryptedLibs().add(entryName);
        libCount++;
    }

    /**
     * 判断类文件是否匹配加密规则。
     */
    private boolean shouldEncryptClass(String entryName) {
        // 处理 Spring Boot 的 BOOT-INF/classes/ 前缀
        String className = entryName;
        if (className.startsWith("BOOT-INF/classes/")) {
            className = className.substring("BOOT-INF/classes/".length());
        }
        if (className.startsWith("WEB-INF/classes/")) {
            className = className.substring("WEB-INF/classes/".length());
        }

        // 转换为包路径格式
        String packagePath = className.replace('/', '.').replace('\\', '.');
        if (packagePath.endsWith(".class")) {
            packagePath = packagePath.substring(0, packagePath.length() - 6);
        }

        for (String pattern : config.getPackagePatterns()) {
            if (matchPattern(packagePath, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断资源文件是否需要加密。
     */
    private boolean shouldEncryptResource(String entryName) {
        // 不加密 META-INF 下的签名文件和清单
        if (entryName.startsWith("META-INF/")) {
            return false;
        }

        String lowerName = entryName.toLowerCase();
        for (String ext : config.getResourceExtensions()) {
            if (lowerName.endsWith(ext.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断 lib 是否为需要加密的目标。
     */
    private boolean isTargetLib(String entryName) {
        if (!entryName.startsWith("BOOT-INF/lib/") || !entryName.endsWith(".jar")) {
            return false;
        }
        if (config.getLibPatterns().isEmpty()) {
            return false;
        }
        String libName = entryName.substring(entryName.lastIndexOf('/') + 1);
        for (String pattern : config.getLibPatterns()) {
            if (matchPattern(libName, pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单的通配符匹配（支持 ** 和 *）。
     */
    static boolean matchPattern(String text, String pattern) {
        // 将 ** 替换为特殊标记，* 替换为另一个标记
        String regex = pattern
                .replace(".", "\\.")
                .replace("**", "##DOUBLESTAR##")
                .replace("*", "[^.]*")
                .replace("##DOUBLESTAR##", ".*");
        return text.matches(regex);
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
            return readStreamBytes(is);
        }
    }

    private static byte[] readStreamBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    private void writeEntry(JarOutputStream jos, String name, byte[] data) throws IOException {
        JarEntry entry = new JarEntry(name);

        // Spring Boot 要求 BOOT-INF/lib/ 下的嵌套 JAR 以 STORED 方式存储
        if (name.startsWith("BOOT-INF/lib/") && name.endsWith(".jar")) {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(data.length);
            entry.setCompressedSize(data.length);
            java.util.zip.CRC32 crc = new java.util.zip.CRC32();
            crc.update(data);
            entry.setCrc(crc.getValue());
        }

        jos.putNextEntry(entry);
        jos.write(data);
        jos.closeEntry();
    }

    /**
     * 获取加密统计信息。
     */
    public String getStatistics() {
        return String.format("类文件: %d, 资源文件: %d, 依赖库: %d", classCount, resourceCount, libCount);
    }
}
