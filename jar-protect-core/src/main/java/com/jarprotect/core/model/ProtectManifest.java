package com.jarprotect.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 加密清单文件模型。
 * 序列化为 JSON 存储在 META-INF/protect-manifest.json 中。
 */
public class ProtectManifest {

    /** 工具版本号 */
    private String version = "1.0.0";

    /** 加密时间戳 */
    private long encryptTime;

    /** 机器码哈希密文（AES加密后的十六进制，为空表示不绑定机器） */
    private String machineCodeCipher;

    /** 加密的类文件条目路径列表 */
    private List<String> encryptedClasses = new ArrayList<>();

    /** 加密的资源文件条目路径列表 */
    private List<String> encryptedResources = new ArrayList<>();

    /** 加密的 lib JAR 条目路径列表 */
    private List<String> encryptedLibs = new ArrayList<>();

    /** 加密使用的包路径模式（运行时需要，用于 Agent 匹配） */
    private List<String> packagePatterns = new ArrayList<>();

    /** 加密后的解密密码（XOR 混淆存储，运行时自动提取） */
    private String encryptedPassword;

    /** 原始 Main-Class（加密前的启动类，运行时由 Launcher 调用） */
    private String originalMainClass;

    /** 注册码验证密钥（可选，为空时使用内置默认密钥） */
    private String licenseSecretKey;

    /** 是否要求注册码验证 */
    private boolean licenseRequired = false;

    /** RSA 加密后的 AES 密码（Base64，启用 RSA 模式时使用） */
    private String rsaEncryptedPassword;

    /** RSA 公钥（Base64，嵌入 manifest 用于标识加密模式） */
    private String rsaPublicKey;

    /** 是否使用 RSA 私钥模式验证注册码（与 AES+RSA 加密模式独立） */
    private boolean rsaLicenseMode = false;

    // ===== Getters & Setters =====

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public long getEncryptTime() { return encryptTime; }
    public void setEncryptTime(long encryptTime) { this.encryptTime = encryptTime; }

    public String getMachineCodeCipher() { return machineCodeCipher; }
    public void setMachineCodeCipher(String machineCodeCipher) { this.machineCodeCipher = machineCodeCipher; }

    public List<String> getEncryptedClasses() { return encryptedClasses; }
    public void setEncryptedClasses(List<String> encryptedClasses) { this.encryptedClasses = encryptedClasses; }

    public List<String> getEncryptedResources() { return encryptedResources; }
    public void setEncryptedResources(List<String> encryptedResources) { this.encryptedResources = encryptedResources; }

    public List<String> getEncryptedLibs() { return encryptedLibs; }
    public void setEncryptedLibs(List<String> encryptedLibs) { this.encryptedLibs = encryptedLibs; }

    public List<String> getPackagePatterns() { return packagePatterns; }
    public void setPackagePatterns(List<String> packagePatterns) { this.packagePatterns = packagePatterns; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }

    public String getOriginalMainClass() { return originalMainClass; }
    public void setOriginalMainClass(String originalMainClass) { this.originalMainClass = originalMainClass; }

    public String getLicenseSecretKey() { return licenseSecretKey; }
    public void setLicenseSecretKey(String licenseSecretKey) { this.licenseSecretKey = licenseSecretKey; }

    public boolean isLicenseRequired() { return licenseRequired; }
    public void setLicenseRequired(boolean licenseRequired) { this.licenseRequired = licenseRequired; }

    public String getRsaEncryptedPassword() { return rsaEncryptedPassword; }
    public void setRsaEncryptedPassword(String rsaEncryptedPassword) { this.rsaEncryptedPassword = rsaEncryptedPassword; }

    public String getRsaPublicKey() { return rsaPublicKey; }
    public void setRsaPublicKey(String rsaPublicKey) { this.rsaPublicKey = rsaPublicKey; }

    public boolean isRsaLicenseMode() { return rsaLicenseMode; }
    public void setRsaLicenseMode(boolean rsaLicenseMode) { this.rsaLicenseMode = rsaLicenseMode; }

    /** 判断是否启用了 RSA 混合加密模式。 */
    public boolean isRsaMode() {
        return rsaEncryptedPassword != null && !rsaEncryptedPassword.isEmpty();
    }

}
