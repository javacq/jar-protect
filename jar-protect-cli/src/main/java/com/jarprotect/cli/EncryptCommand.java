package com.jarprotect.cli;

import com.jarprotect.core.jar.JarEncryptor;
import com.jarprotect.core.model.EncryptConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * JAR 包加密命令。
 *
 * 使用示例:
 *   jar-protect encrypt -i app.jar -o app-encrypted.jar -p com.example.** --password mySecret
 *   jar-protect encrypt -i app.jar -p com.example.** -m A1B2-C3D4-E5F6-7890
 *   jar-protect encrypt -i app.jar -p com.myapp.** --encrypt-libs --lib-pattern mylib-*
 */
@Command(
        name = "encrypt",
        description = "加密 JAR/WAR 包",
        mixinStandardHelpOptions = true
)
public class EncryptCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "输入 JAR 文件路径（也可用 -i 指定）")
    private String inputArg;

    @Option(names = {"-i", "--input"}, description = "输入 JAR/WAR 文件路径")
    private String inputJar;

    @Option(names = {"-o", "--output"}, description = "输出加密后的 JAR 文件路径（默认: 原文件名-encrypted.jar）")
    private String outputJar;

    @Option(names = {"-p", "--packages"}, description = "需要加密的包路径模式（逗号分隔），如 com.example.**,com.myapp.**",
            required = true, split = ",")
    private List<String> packagePatterns;

    @Option(names = {"--password"}, description = "加密密码（未指定时将交互式输入）", interactive = true)
    private String password;

    @Option(names = {"-m", "--machine-code"}, description = "目标机器码（可选，绑定后只能在目标机器上运行）")
    private String machineCode;

    @Option(names = {"--resource-ext"}, description = "需要加密的资源文件扩展名（逗号分隔，默认: .yml,.yaml,.properties,.xml）",
            split = ",")
    private List<String> resourceExtensions;

    @Option(names = {"--encrypt-libs"}, description = "是否加密 BOOT-INF/lib 下的依赖 JAR", defaultValue = "false")
    private boolean encryptLibs;

    @Option(names = {"--lib-pattern"}, description = "需要加密的 lib 文件名模式（逗号分隔）", split = ",")
    private List<String> libPatterns;

    @Option(names = {"--agent-jar"}, description = "Agent JAR 路径（自动嵌入解密引擎，嵌入后无需 -javaagent）")
    private String agentJarPath;

    @Option(names = {"--license-required"}, description = "启用注册码验证", defaultValue = "false")
    private boolean licenseRequired;

    @Option(names = {"--license-key"}, description = "注册码验证密钥（可选，为空使用内置默认密钥）")
    private String licenseSecretKey;

    @Override
    public Integer call() {
        try {
            // 解析输入文件
            String input = inputJar != null ? inputJar : inputArg;
            if (input == null || input.isEmpty()) {
                System.err.println("错误: 请指定输入 JAR 文件路径 (-i 或位置参数)");
                return 1;
            }

            File inputFile = new File(input);
            if (!inputFile.exists()) {
                System.err.println("错误: 输入文件不存在: " + input);
                return 1;
            }

            // 解析输出文件
            String output = outputJar;
            if (output == null || output.isEmpty()) {
                String name = inputFile.getName();
                int dot = name.lastIndexOf('.');
                if (dot > 0) {
                    output = inputFile.getParent() + File.separator
                            + name.substring(0, dot) + "-encrypted" + name.substring(dot);
                } else {
                    output = input + "-encrypted";
                }
            }

            // 解析密码
            String pwd = password;
            if (pwd == null || pwd.isEmpty()) {
                pwd = System.getenv("JAR_PROTECT_PASSWORD");
            }
            if (pwd == null || pwd.isEmpty()) {
                Console console = System.console();
                if (console != null) {
                    char[] pwdChars = console.readPassword("请输入加密密码: ");
                    if (pwdChars != null && pwdChars.length > 0) {
                        pwd = new String(pwdChars);
                        // 确认密码
                        char[] confirmChars = console.readPassword("请再次输入密码确认: ");
                        if (!Arrays.equals(pwdChars, confirmChars)) {
                            System.err.println("错误: 两次输入的密码不一致");
                            return 1;
                        }
                    }
                }
            }
            if (pwd == null || pwd.isEmpty()) {
                System.err.println("错误: 请提供加密密码 (--password 参数、JAR_PROTECT_PASSWORD 环境变量或交互式输入)");
                return 1;
            }

            // 构建配置
            EncryptConfig config = new EncryptConfig();
            config.setInputJar(inputFile.getAbsolutePath());
            config.setOutputJar(new File(output).getAbsolutePath());
            config.setPassword(pwd);
            config.setPackagePatterns(packagePatterns);
            config.setMachineCode(machineCode);
            config.setEncryptLibs(encryptLibs);

            if (resourceExtensions != null && !resourceExtensions.isEmpty()) {
                config.setResourceExtensions(resourceExtensions);
            }
            if (libPatterns != null && !libPatterns.isEmpty()) {
                config.setLibPatterns(libPatterns);
            }
            if (agentJarPath != null && !agentJarPath.isEmpty()) {
                config.setAgentJarPath(agentJarPath);
            }
            config.setLicenseRequired(licenseRequired);
            if (licenseSecretKey != null && !licenseSecretKey.isEmpty()) {
                config.setLicenseSecretKey(licenseSecretKey);
            }
            // 执行加密
            System.out.println();
            JarEncryptor encryptor = new JarEncryptor(config);
            encryptor.execute();
            System.out.println();
            System.out.println("加密成功！输出文件: " + output);

            if (machineCode != null && !machineCode.isEmpty()) {
                System.out.println("已绑定机器码: " + machineCode);
            }

            System.out.println();
            if (agentJarPath != null && !agentJarPath.isEmpty()) {
                System.out.println("运行加密后的 JAR（已嵌入解密引擎）:");
                if (licenseRequired) {
                    System.out.println("  1. 在 JAR 同目录下创建 license.lic 文本文件");
                    System.out.println("  2. 将注册码写入 license.lic");
                    System.out.println("  3. java -jar " + new File(output).getName());
                } else {
                    System.out.println("  java -jar " + new File(output).getName());
                }
            } else {
                System.out.println("运行加密后的 JAR:");
                System.out.println("  java -javaagent:jar-protect-agent.jar=password=<密码> -jar " + new File(output).getName());
            }

            return 0;

        } catch (Exception e) {
            System.err.println("加密失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
