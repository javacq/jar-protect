package com.jarprotect.core.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 加密配置参数。
 */
public class EncryptConfig {

    /** 输入 JAR 文件路径 */
    private String inputJar;

    /** 输出 JAR 文件路径 */
    private String outputJar;

    /** 加密密码 */
    private String password;

    /** 目标机器码（可选，为空则不绑定机器） */
    private String machineCode;

    /** 需要加密的包路径模式列表，如 ["com.example.**", "com.myapp.**"] */
    private List<String> packagePatterns = new ArrayList<>();

    /** 需要加密的资源文件扩展名 */
    private List<String> resourceExtensions = Arrays.asList(".yml", ".yaml", ".properties", ".xml");

    /** 是否递归加密 BOOT-INF/lib 下的依赖 JAR */
    private boolean encryptLibs = false;

    /** 需要加密的 lib 包名模式（用于过滤私有依赖） */
    private List<String> libPatterns = new ArrayList<>();

    /** Agent JAR 路径（自动嵌入到加密 JAR 中，为空则不嵌入） */
    private String agentJarPath;

    /** 注册码验证密钥（可选，为空时使用内置默认密钥） */
    private String licenseSecretKey;

    /** 是否要求注册码验证 */
    private boolean licenseRequired = false;

    /** RSA 公钥文件路径（可选，启用 AES+RSA 混合加密） */
    private String rsaPublicKeyPath;

    /** 是否使用 RSA 私钥模式验证注册码 */
    private boolean rsaLicenseMode = false;

    // ===== Getters & Setters =====

    public String getInputJar() { return inputJar; }
    public void setInputJar(String inputJar) { this.inputJar = inputJar; }

    public String getOutputJar() { return outputJar; }
    public void setOutputJar(String outputJar) { this.outputJar = outputJar; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public List<String> getPackagePatterns() { return packagePatterns; }
    public void setPackagePatterns(List<String> packagePatterns) { this.packagePatterns = packagePatterns; }

    public List<String> getResourceExtensions() { return resourceExtensions; }
    public void setResourceExtensions(List<String> resourceExtensions) { this.resourceExtensions = resourceExtensions; }

    public boolean isEncryptLibs() { return encryptLibs; }
    public void setEncryptLibs(boolean encryptLibs) { this.encryptLibs = encryptLibs; }

    public List<String> getLibPatterns() { return libPatterns; }
    public void setLibPatterns(List<String> libPatterns) { this.libPatterns = libPatterns; }

    public String getAgentJarPath() { return agentJarPath; }
    public void setAgentJarPath(String agentJarPath) { this.agentJarPath = agentJarPath; }

    public String getLicenseSecretKey() { return licenseSecretKey; }
    public void setLicenseSecretKey(String licenseSecretKey) { this.licenseSecretKey = licenseSecretKey; }

    public boolean isLicenseRequired() { return licenseRequired; }
    public void setLicenseRequired(boolean licenseRequired) { this.licenseRequired = licenseRequired; }

    public String getRsaPublicKeyPath() { return rsaPublicKeyPath; }
    public void setRsaPublicKeyPath(String rsaPublicKeyPath) { this.rsaPublicKeyPath = rsaPublicKeyPath; }

    public boolean isRsaLicenseMode() { return rsaLicenseMode; }
    public void setRsaLicenseMode(boolean rsaLicenseMode) { this.rsaLicenseMode = rsaLicenseMode; }

    /**
     * 验证配置有效性。
     */
    public void validate() {
        if (inputJar == null || inputJar.isEmpty()) {
            throw new IllegalArgumentException("输入 JAR 文件路径不能为空");
        }
        if (outputJar == null || outputJar.isEmpty()) {
            throw new IllegalArgumentException("输出 JAR 文件路径不能为空");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("加密密码不能为空");
        }
        if (packagePatterns.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一个加密包路径模式");
        }
    }
}
