package com.jarprotect.agent;

import com.jarprotect.core.crypto.AESCrypto;
import com.jarprotect.core.crypto.CryptoUtils;
import com.jarprotect.core.machine.MachineCodeGenerator;
import com.jarprotect.core.model.ProtectManifest;
import com.google.gson.Gson;

import java.io.*;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;

/**
 * Java Agent 入口。
 * 支持三种加载方式：
 * 1. Launcher-Agent-Class（JDK 9+，自动注入 Instrumentation）
 * 2. -javaagent 传统模式
 * 3. agentmain（通过 Attach API 动态附加）
 *
 * 当通过 ProtectLauncher 启动时，Agent 仅保存 Instrumentation 实例，
 * 验证和 Transformer 注册由 Launcher 完成。
 */
public class ProtectAgent {

    private static final String MANIFEST_PATH = "META-INF/protect-manifest.json";
    private static volatile Instrumentation instrumentation;
    private static volatile String decryptPassword;
    private static volatile ProtectManifest manifest;
    private static volatile boolean initialized = false;
    /** 标记是否由 ProtectLauncher 主导初始化 */
    private static volatile boolean launcherMode = false;

    /**
     * premain — 作为 Launcher-Agent-Class 或 -javaagent 加载时调用。
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;

        // 如果由 Launcher 主导，只保存 Instrumentation，其余由 Launcher 处理
        if (launcherMode) {
            return;
        }

        // 检查是否为嵌入引擎模式（ProtectLauncher 作为 Main-Class）
        // 如果是，仅保存 Instrumentation，由 ProtectLauncher 完成全部初始化
        ProtectManifest mf = loadManifest();
        if (mf != null && mf.getOriginalMainClass() != null && !mf.getOriginalMainClass().isEmpty()) {
            manifest = mf;
            launcherMode = true;
            return;
        }

        // 传统 -javaagent 模式：需要自行完成全部初始化
        try {
            System.out.println("[JAR-Protect Agent] 初始化中...");

            decryptPassword = resolvePassword(agentArgs);
            if (decryptPassword == null || decryptPassword.isEmpty()) {
                // 尝试从 manifest 中提取混淆密码
                if (mf == null) mf = loadManifest();
                manifest = mf;
                if (manifest != null && manifest.getEncryptedPassword() != null) {
                    decryptPassword = CryptoUtils.deobfuscatePassword(manifest.getEncryptedPassword());
                }
            }
            if (decryptPassword == null || decryptPassword.isEmpty()) {
                System.err.println("[JAR-Protect Agent] 错误: 未提供解密密码!");
                System.exit(1);
                return;
            }

            if (manifest == null) {
                manifest = mf != null ? mf : loadManifest();
            }
            if (manifest == null) {
                System.out.println("[JAR-Protect Agent] 未检测到加密清单，Agent 跳过。");
                return;
            }

            System.out.println("[JAR-Protect Agent] 检测到 " + manifest.getEncryptedClasses().size()
                    + " 个加密类, " + manifest.getEncryptedResources().size() + " 个加密资源");

            verifyMachineCode();

            DecryptTransformer transformer = new DecryptTransformer(manifest, decryptPassword);
            inst.addTransformer(transformer, true);

            initialized = true;
            System.out.println("[JAR-Protect Agent] 初始化完成，解密引擎已就绪。");

        } catch (Exception e) {
            System.err.println("[JAR-Protect Agent] 初始化失败: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * agentmain — 通过 Attach API 动态加载时调用。
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        instrumentation = inst;

        if (launcherMode && manifest != null && decryptPassword != null) {
            // Launcher 已预设好密码和 manifest，直接注册 Transformer
            try {
                DecryptTransformer transformer = new DecryptTransformer(manifest, decryptPassword);
                inst.addTransformer(transformer, true);
                initialized = true;
                System.out.println("[JAR-Protect Agent] agentmain 解密引擎已就绪。");
            } catch (Exception e) {
                System.err.println("[JAR-Protect Agent] agentmain 初始化失败: " + e.getMessage());
            }
            return;
        }

        // 作为独立 agent 加载
        premain(agentArgs, inst);
    }

    /**
     * 由 ProtectLauncher 调用：预设密码和 manifest，标记 launcher 模式。
     */
    public static void initFromLauncher(String password, ProtectManifest mf) {
        launcherMode = true;
        decryptPassword = password;
        manifest = mf;
    }

    // ===== 密码解析（传统 -javaagent 模式使用）=====

    private static String resolvePassword(String agentArgs) {
        if (agentArgs != null && !agentArgs.isEmpty()) {
            if (agentArgs.startsWith("password=")) {
                return agentArgs.substring("password=".length());
            }
            String[] params = agentArgs.split(",");
            for (String param : params) {
                param = param.trim();
                if (param.startsWith("password=")) {
                    return param.substring("password=".length());
                }
            }
            return agentArgs;
        }

        String envPassword = System.getenv("JAR_PROTECT_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) return envPassword;

        String propPassword = System.getProperty("jar.protect.password");
        if (propPassword != null && !propPassword.isEmpty()) return propPassword;

        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword("[JAR-Protect Agent] 请输入解密密码: ");
            if (pwd != null && pwd.length > 0) return new String(pwd);
        }

        return null;
    }

    private static ProtectManifest loadManifest() {
        try {
            InputStream is = ProtectAgent.class.getClassLoader().getResourceAsStream(MANIFEST_PATH);
            if (is == null) is = ClassLoader.getSystemResourceAsStream(MANIFEST_PATH);
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

    private static void verifyMachineCode() throws Exception {
        String machineCodeCipher = manifest.getMachineCodeCipher();
        if (machineCodeCipher == null || machineCodeCipher.isEmpty()) {
            if (manifest.isLicenseRequired()) {
                System.out.println("[JAR-Protect Agent] 机器码已通过注册码绑定。");
            } else {
                System.out.println("[JAR-Protect Agent] 未启用机器码绑定。");
            }
            return;
        }
        System.out.println("[JAR-Protect Agent] 正在验证机器码...");
        byte[] encryptedMC = CryptoUtils.hexToBytes(machineCodeCipher);
        byte[] decryptedMC = AESCrypto.decrypt(encryptedMC, decryptPassword);
        String expectedHash = new String(decryptedMC, StandardCharsets.UTF_8);
        String currentCode = MachineCodeGenerator.generate().replace("-", "").toUpperCase();
        String currentHash = CryptoUtils.bytesToHex(CryptoUtils.sha256(currentCode));

        if (!expectedHash.equalsIgnoreCase(currentHash)) {
            System.err.println("[JAR-Protect Agent] 机器码验证失败！当前: " + MachineCodeGenerator.generate());
            System.exit(2);
        }
        System.out.println("[JAR-Protect Agent] 机器码验证通过。");
    }

    // ===== 公开访问方法 =====

    public static Instrumentation getInstrumentation() { return instrumentation; }
    public static String getDecryptPassword() { return decryptPassword; }
    public static ProtectManifest getManifest() { return manifest; }
    public static boolean isInitialized() { return initialized; }
}
