package com.jarprotect.cli;

import com.jarprotect.core.license.LicenseGenerator;
import com.jarprotect.core.license.LicenseGenerator.LicenseResult;
import com.jarprotect.core.machine.MachineCodeGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.util.Date;
import java.util.concurrent.Callable;

/**
 * 注册码（License）生成与验证命令。
 *
 * 过期时间编码在注册码内部，验证时自动提取。
 *
 * 使用示例:
 *   # 生成永久注册码
 *   jar-protect license --generate -m A1B2-C3D4-E5F6-7890 --password mySecret
 *
 *   # 生成限期注册码
 *   jar-protect license --generate -m A1B2-C3D4-E5F6-7890 --password mySecret --expiry 2026-12-31
 *
 *   # 验证注册码（过期时间自动从注册码中提取）
 *   jar-protect license --verify -l F9E8-D7C6-B5A4-3210 --password mySecret
 */
@Command(
        name = "license",
        description = "注册码的生成与验证",
        mixinStandardHelpOptions = true
)
public class LicenseCommand implements Callable<Integer> {

    @Option(names = {"-g", "--generate"}, description = "生成注册码模式")
    private boolean generateMode;

    @Option(names = {"-v", "--verify"}, description = "验证注册码模式")
    private boolean verifyMode;

    @Option(names = {"-m", "--machine-code"}, description = "目标机器码（不指定则使用当前机器）")
    private String machineCode;

    @Option(names = {"-l", "--license"}, description = "注册码字符串（验证时使用，如 F9E8-D7C6-B5A4-3210）")
    private String licenseCode;

    @Option(names = {"--password"}, description = "主密码（与加密 JAR 时使用的密码一致）", interactive = true)
    private String password;

    @Option(names = {"--expiry"}, description = "生成时指定过期时间（yyyy-MM-dd 或 PERMANENT，默认永久）", defaultValue = "PERMANENT")
    private String expiry;

    @Override
    public Integer call() {
        try {
            if (!generateMode && !verifyMode) {
                System.err.println("错误: 请选择操作模式: --generate / --verify");
                System.err.println("使用 --help 查看帮助");
                return 1;
            }

            // 解析密码
            String pwd = resolvePassword();
            if (pwd == null || pwd.isEmpty()) {
                System.err.println("错误: 请提供主密码 (--password 参数、JAR_PROTECT_PASSWORD 环境变量或交互式输入)");
                return 1;
            }

            if (generateMode) {
                return doGenerate(pwd);
            } else {
                return doVerify(pwd);
            }

        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    /**
     * 生成注册码。
     */
    private Integer doGenerate(String pwd) throws Exception {
        String targetCode = machineCode;
        if (targetCode == null || targetCode.isEmpty()) {
            targetCode = MachineCodeGenerator.generate();
            System.out.println("未指定目标机器码，使用当前机器码: " + targetCode);
        }

        System.out.println();
        System.out.println("==============================================");
        System.out.println("  注册码生成");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("目标机器码: " + targetCode);

        Date expiryDate = LicenseGenerator.parseExpiry(expiry);
        String license = LicenseGenerator.generate(targetCode, pwd, expiryDate);

        // 从生成的注册码中提取过期时间（验证编码正确性）
        String extractedExpiry = LicenseGenerator.extractExpiry(license);

        System.out.println("有效期:     " + extractedExpiry);
        System.out.println();
        System.out.println("注册码:");
        System.out.println("----------------------------------------------");
        System.out.println(license);
        System.out.println("----------------------------------------------");
        System.out.println();
        System.out.println("请将此注册码发送给客户。");
        System.out.println("客户只需将注册码写入 license.lic 文件即可（仅一行）。");
        System.out.println("过期时间已编码在注册码内部，无需额外配置。");

        return 0;
    }

    /**
     * 验证注册码（过期时间自动从注册码中提取）。
     */
    private Integer doVerify(String pwd) {
        if (licenseCode == null || licenseCode.isEmpty()) {
            System.err.println("错误: 请提供注册码 (-l 参数)");
            return 1;
        }

        String targetCode = machineCode;
        if (targetCode == null || targetCode.isEmpty()) {
            targetCode = MachineCodeGenerator.generate();
            System.out.println("未指定目标机器码，使用当前机器码: " + targetCode);
        }

        System.out.println();
        System.out.println("==============================================");
        System.out.println("  注册码验证");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("目标机器码: " + targetCode);

        // 从注册码中提取过期时间
        String extractedExpiry = LicenseGenerator.extractExpiry(licenseCode);
        System.out.println("注册码有效期: " + (extractedExpiry != null ? extractedExpiry : "无法解析"));
        System.out.println();

        LicenseResult result = LicenseGenerator.verify(licenseCode, targetCode, pwd);

        System.out.println("验证结果: " + result);

        return result.isValid() ? 0 : 1;
    }

    /**
     * 解析密码。
     */
    private String resolvePassword() {
        if (password != null && !password.isEmpty()) {
            return password;
        }
        String envPassword = System.getenv("JAR_PROTECT_PASSWORD");
        if (envPassword != null && !envPassword.isEmpty()) {
            return envPassword;
        }
        Console console = System.console();
        if (console != null) {
            char[] pwd = console.readPassword("请输入主密码: ");
            if (pwd != null && pwd.length > 0) {
                return new String(pwd);
            }
        }
        return null;
    }
}
