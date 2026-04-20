package com.jarprotect.agent;

import com.jarprotect.core.crypto.AESCrypto;
import com.jarprotect.core.crypto.CryptoUtils;
import com.jarprotect.core.crypto.RSACrypto;
import com.jarprotect.core.license.LicenseGenerator;
import com.jarprotect.core.machine.MachineCodeGenerator;
import com.jarprotect.core.model.ProtectManifest;
import com.google.gson.Gson;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 加密 JAR 的启动入口。
 * 
 * 运行流程：
 * 1. 加载 protect-manifest.json
 * 2. 从 manifest 中提取混淆密码
 * 3. 如果启用了注册码验证 → 读取 license.lic → 验证机器码 + 注册码
 * 4. 如果启用了机器码绑定（无注册码）→ 验证机器码
 * 5. 等待 Instrumentation（JDK 9+ 通过 Launcher-Agent-Class 自动注入）
 * 6. 注册 DecryptTransformer
 * 7. 调用原始 Main-Class.main()
 *
 * 客户使用方式：
 *   在 JAR 同目录下创建 license.lic 文本文件，写入注册码即可。
 */
public class ProtectLauncher {

    private static final String MANIFEST_PATH = "META-INF/protect-manifest.json";
    private static final String LICENSE_FILE_NAME = "license.lic";
    private static final String LICENSE_DIR_NAME = ".jarprotect";

    public static void main(String[] args) {
        try {
            System.out.println("[JAR-Protect] 启动保护模式...");

            // 1. 加载清单
            ProtectManifest manifest = loadManifest();
            if (manifest == null) {
                System.err.println("[JAR-Protect] 未检测到加密清单，直接启动。");
                launchOriginal(null, args);
                return;
            }

            // 2. 提取密码
            String password;
            if (manifest.isRsaMode()) {
                // AES+RSA 混合模式：从私钥文件解密 AES 密码
                password = decryptPasswordWithRsa(manifest);
                if (password == null) {
                    System.err.println("[JAR-Protect] RSA 解密失败，无法启动！");
                    System.err.println("[JAR-Protect] 请将 RSA 私钥文件 (private.pem) 放置在 JAR 同目录。");
                    System.exit(1);
                    return;
                }
                System.out.println("[JAR-Protect] AES+RSA 混合解密已就绪");
            } else {
                // 纯 AES 模式：从混淆密码提取
                password = CryptoUtils.deobfuscatePassword(manifest.getEncryptedPassword());
            }

            // 3. 注册码验证（如果启用）
            if (manifest.isLicenseRequired()) {
                verifyLicense(manifest);
            }

            // 4. 机器码验证（如果绑定了机器码）
            if (manifest.getMachineCodeCipher() != null && !manifest.getMachineCodeCipher().isEmpty()) {
                verifyMachineCode(manifest, password);
            }

            // 5. 获取 Instrumentation 并注册解密器
            Instrumentation inst = ProtectAgent.getInstrumentation();
            if (inst != null) {
                DecryptTransformer transformer = new DecryptTransformer(manifest, password);
                inst.addTransformer(transformer, true);
                System.out.println("[JAR-Protect] 解密引擎已就绪（" 
                        + manifest.getEncryptedClasses().size() + " 个加密类）");
            } else {
                System.err.println("[JAR-Protect] 警告: 未获取到 Instrumentation，尝试 self-attach...");
                // JDK 8 回退：通过 Attach API 自我附加
                if (!trySelfAttach(password, manifest)) {
                    System.err.println("[JAR-Protect] ================================================");
                    System.err.println("[JAR-Protect] 无法初始化解密引擎！");
                    System.err.println("[JAR-Protect] 如果是 JDK 8，请使用: java -javaagent:<当前jar> -jar <当前jar>");
                    System.err.println("[JAR-Protect] JDK 9+ 应自动工作。");
                    System.err.println("[JAR-Protect] ================================================");
                    System.exit(1);
                    return;
                }
            }

            // 6. 设置资源解密密码（由 DecryptTransformer 注入到 ClassPathResource 实现内存解密）
            if (!manifest.getEncryptedResources().isEmpty()) {
                System.setProperty("jarprotect.decrypt.password", password);
                System.out.println("[JAR-Protect] 已注册 " + manifest.getEncryptedResources().size()
                        + " 个加密资源（内存解密模式）");
            }

            // 7. 启动原始应用
            String originalMain = manifest.getOriginalMainClass();
            if (originalMain == null || originalMain.isEmpty()) {
                System.err.println("[JAR-Protect] 错误: 未找到原始 Main-Class");
                System.exit(1);
                return;
            }

            System.out.println("[JAR-Protect] 启动应用: " + originalMain);
            launchOriginal(originalMain, args);

        } catch (Exception e) {
            System.err.println("[JAR-Protect] 启动失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * 验证注册码。
     * license.lic 只包含注册码一行，过期时间编码在注册码内部，自动提取。
     */
    private static void verifyLicense(ProtectManifest manifest) throws Exception {
        String licenseCode = readLicenseFile();
        if (licenseCode == null || licenseCode.isEmpty()) {
            System.err.println("[JAR-Protect] ================================================");
            System.err.println("[JAR-Protect] 未找到注册码文件！");
            System.err.println("[JAR-Protect] 请在以下任一位置创建 license.lic 文本文件，");
            System.err.println("[JAR-Protect] 并将注册码写入其中（仅一行注册码即可）：");
            System.err.println("[JAR-Protect]   1. JAR 所在目录/" + LICENSE_FILE_NAME);
            System.err.println("[JAR-Protect]   2. 用户目录/" + LICENSE_DIR_NAME + "/" + LICENSE_FILE_NAME);
            System.err.println("[JAR-Protect] ");
            System.err.println("[JAR-Protect] 当前机器码: " + MachineCodeGenerator.generate());
            System.err.println("[JAR-Protect] ================================================");
            System.exit(3);
            return;
        }

        String machineCode = MachineCodeGenerator.generate();

        LicenseGenerator.LicenseResult result;
        if (manifest.isRsaLicenseMode()) {
            // RSA 注册码模式：从私钥推导 HMAC 密钥验证注册码
            java.security.PrivateKey privateKey = loadPrivateKeyForLicense();
            if (privateKey == null) {
                System.err.println("[JAR-Protect] RSA 模式需要私钥文件 (private.pem) 用于验证注册码");
                System.exit(3);
                return;
            }
            result = LicenseGenerator.verifyRsa(licenseCode, machineCode, privateKey);
        } else {
            // HMAC 模式
            String secretKey = manifest.getLicenseSecretKey();
            result = LicenseGenerator.verify(
                    licenseCode, machineCode,
                    (secretKey != null && !secretKey.isEmpty()) ? secretKey : null);
        }

        if (!result.isValid()) {
            System.err.println("[JAR-Protect] ================================================");
            System.err.println("[JAR-Protect] 注册码验证失败！");
            System.err.println("[JAR-Protect] " + result.getMessage());
            System.err.println("[JAR-Protect] 当前机器码: " + machineCode);
            System.err.println("[JAR-Protect] 读取注册码: " + licenseCode);
            System.err.println("[JAR-Protect] ================================================");
            System.exit(3);
            return;
        }

        System.out.println("[JAR-Protect] " + result.getMessage());
    }

    /**
     * AES+RSA 混合模式：从私钥文件解密 AES 密码。
     * 查找 private.pem 顺序：
     *   1. JAR 所在目录/private.pem
     *   2. 用户主目录/.jarprotect/private.pem
     *   3. 系统属性 jarprotect.rsa.privatekey（文件路径）
     */
    private static String decryptPasswordWithRsa(ProtectManifest manifest) {
        String rsaEncPwd = manifest.getRsaEncryptedPassword();
        if (rsaEncPwd == null || rsaEncPwd.isEmpty()) return null;

        String[] searchPaths = buildPrivateKeySearchPaths();

        for (String pkPath : searchPaths) {
            if (pkPath == null) continue;
            try {
                Path keyFile = Paths.get(pkPath);
                if (Files.exists(keyFile)) {
                    java.security.PrivateKey privateKey = RSACrypto.loadPrivateKey(keyFile);
                    String password = RSACrypto.decryptFromBase64(rsaEncPwd, privateKey);
                    System.out.println("[JAR-Protect] 从 RSA 私钥解密密码: " + keyFile);
                    return password;
                }
            } catch (Exception e) {
                System.err.println("[JAR-Protect] RSA 私钥解密失败 [" + pkPath + "]: " + e.getMessage());
            }
        }

        System.err.println("[JAR-Protect] 未找到 RSA 私钥文件 (private.pem)");
        return null;
    }

    /**
     * 加载 RSA 私钥（用于注册码验证）。
     */
    private static java.security.PrivateKey loadPrivateKeyForLicense() {
        String[] searchPaths = buildPrivateKeySearchPaths();
        for (String pkPath : searchPaths) {
            if (pkPath == null) continue;
            try {
                Path keyFile = Paths.get(pkPath);
                if (Files.exists(keyFile)) {
                    return RSACrypto.loadPrivateKey(keyFile);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static String[] buildPrivateKeySearchPaths() {
        String[] paths = new String[3];
        try {
            Path jarDir = getJarDirectory();
            if (jarDir != null) paths[0] = jarDir.resolve("private.pem").toString();
        } catch (Exception ignored) {}
        paths[1] = Paths.get(System.getProperty("user.home"), LICENSE_DIR_NAME, "private.pem").toString();
        paths[2] = System.getProperty("jarprotect.rsa.privatekey");
        return paths;
    }

    /**
     * 从文件系统读取注册码（只读第一行）。
     * 查找顺序：
     *   1. JAR 所在目录/license.lic
     *   2. 用户主目录/.jarprotect/license.lic
     *   3. 环境变量 JAR_PROTECT_LICENSE
     */
    private static String readLicenseFile() {
        // 1. JAR 所在目录
        try {
            Path jarDir = getJarDirectory();
            if (jarDir != null) {
                Path licFile = jarDir.resolve(LICENSE_FILE_NAME);
                String code = readFirstLine(licFile);
                if (code != null) {
                    System.out.println("[JAR-Protect] 读取注册码: " + licFile);
                    return code;
                }
            }
        } catch (Exception ignored) {}

        // 2. 用户主目录
        try {
            Path homeDir = Paths.get(System.getProperty("user.home"), LICENSE_DIR_NAME, LICENSE_FILE_NAME);
            String code = readFirstLine(homeDir);
            if (code != null) {
                System.out.println("[JAR-Protect] 读取注册码: " + homeDir);
                return code;
            }
        } catch (Exception ignored) {}

        // 3. 环境变量
        String envLicense = System.getenv("JAR_PROTECT_LICENSE");
        if (envLicense != null && !envLicense.trim().isEmpty()) {
            System.out.println("[JAR-Protect] 读取注册码: 环境变量 JAR_PROTECT_LICENSE");
            return envLicense.trim();
        }

        return null;
    }

    /**
     * 读取文件第一个非空行（即注册码）。
     */
    private static String readFirstLine(Path path) {
        try {
            if (!Files.exists(path)) return null;
            java.util.List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) return trimmed;
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 获取当前 JAR 文件所在目录。
     */
    private static Path getJarDirectory() {
        try {
            File jarFile = new File(ProtectLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return jarFile.getParentFile().toPath();
        } catch (Exception e) {
            // 回退到工作目录
            return Paths.get(System.getProperty("user.dir"));
        }
    }

    /**
     * 验证机器码（不依赖注册码的传统模式）。
     */
    private static void verifyMachineCode(ProtectManifest manifest, String password) throws Exception {
        String machineCodeCipher = manifest.getMachineCodeCipher();

        byte[] encryptedMC = CryptoUtils.hexToBytes(machineCodeCipher);
        byte[] decryptedMC = com.jarprotect.core.crypto.AESCrypto.decrypt(encryptedMC, password);
        String expectedHash = new String(decryptedMC, StandardCharsets.UTF_8);

        String currentCode = MachineCodeGenerator.generate().replace("-", "").toUpperCase();
        String currentHash = CryptoUtils.bytesToHex(CryptoUtils.sha256(currentCode));

        if (!expectedHash.equalsIgnoreCase(currentHash)) {
            // 检查是否存在注册码且已过期，优先提示过期信息
            String licenseCode = readLicenseFile();
            String licenseExpiry = (licenseCode != null && !licenseCode.isEmpty())
                    ? LicenseGenerator.extractExpiry(licenseCode) : null;
            boolean licenseExpired = false;
            if (licenseExpiry != null && !LicenseGenerator.PERMANENT.equals(licenseExpiry)) {
                try {
                    java.util.Date expiryDate = new java.text.SimpleDateFormat(LicenseGenerator.DATE_FORMAT).parse(licenseExpiry);
                    java.util.Calendar today = java.util.Calendar.getInstance();
                    today.set(java.util.Calendar.HOUR_OF_DAY, 0);
                    today.set(java.util.Calendar.MINUTE, 0);
                    today.set(java.util.Calendar.SECOND, 0);
                    today.set(java.util.Calendar.MILLISECOND, 0);
                    licenseExpired = expiryDate.before(today.getTime());
                } catch (Exception ignored) {}
            }

            System.err.println("[JAR-Protect] ================================================");
            if (licenseExpired) {
                System.err.println("[JAR-Protect] 注册码已过期！");
                System.err.println("[JAR-Protect] 过期日期: " + licenseExpiry);
                System.err.println("[JAR-Protect] 当前机器码: " + MachineCodeGenerator.generate());
                System.err.println("[JAR-Protect] 请联系供应商获取新的注册码。");
            } else {
                System.err.println("[JAR-Protect] 机器码验证失败！");
                System.err.println("[JAR-Protect] 当前机器码: " + MachineCodeGenerator.generate());
                System.err.println("[JAR-Protect] 此应用未授权在当前设备上运行。");
            }
            System.err.println("[JAR-Protect] ================================================");
            System.exit(2);
        }
        System.out.println("[JAR-Protect] 机器码验证通过。");
    }

    /**
     * 尝试通过 Attach API 自我附加（JDK 8 兼容方案）。
     */
    private static boolean trySelfAttach(String password, ProtectManifest manifest) {
        try {
            // 把密码传给 ProtectAgent，让 agentmain 处理
            ProtectAgent.initFromLauncher(password, manifest);

            String pid = getProcessId();
            File jarFile = new File(ProtectLauncher.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());

            // 通过反射调用 Attach API
            Class<?> vmClass = Class.forName("com.sun.tools.attach.VirtualMachine");
            Method attach = vmClass.getMethod("attach", String.class);
            Object vm = attach.invoke(null, pid);
            Method loadAgent = vmClass.getMethod("loadAgent", String.class, String.class);
            loadAgent.invoke(vm, jarFile.getAbsolutePath(), "password=" + password);
            Method detach = vmClass.getMethod("detach");
            detach.invoke(vm);

            return ProtectAgent.isInitialized();
        } catch (Exception e) {
            return false;
        }
    }

    private static String getProcessId() {
        String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        return name.split("@")[0];
    }

    /**
     * 反射调用原始 Main-Class.main(args)。
     */
    private static void launchOriginal(String className, String[] args) throws Exception {
        if (className == null) return;
        Class<?> mainClass = Class.forName(className);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) args);
    }

    /**
     * 加载加密清单。
     */
    private static ProtectManifest loadManifest() {
        try {
            InputStream is = ProtectLauncher.class.getClassLoader().getResourceAsStream(MANIFEST_PATH);
            if (is == null) {
                is = ClassLoader.getSystemResourceAsStream(MANIFEST_PATH);
            }
            if (is == null) return null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                return new Gson().fromJson(sb.toString(), ProtectManifest.class);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
