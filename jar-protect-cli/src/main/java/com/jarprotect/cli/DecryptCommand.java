package com.jarprotect.cli;

import com.jarprotect.core.jar.JarDecryptor;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.Console;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * JAR 包解密还原命令。
 * 将加密后的 JAR 还原为原始 JAR（包含完整字节码和明文配置文件）。
 *
 * 使用示例:
 *   jar-protect decrypt -i app-encrypted.jar -o app-original.jar --password mySecret
 *   jar-protect decrypt app-encrypted.jar --password mySecret
 */
@Command(
        name = "decrypt",
        description = "解密还原加密后的 JAR/WAR 包",
        mixinStandardHelpOptions = true
)
public class DecryptCommand implements Callable<Integer> {

    @Parameters(index = "0", arity = "0..1", description = "输入加密 JAR 文件路径（也可用 -i 指定）")
    private String inputArg;

    @Option(names = {"-i", "--input"}, description = "输入加密后的 JAR/WAR 文件路径")
    private String inputJar;

    @Option(names = {"-o", "--output"}, description = "输出还原后的 JAR 文件路径（默认: 原文件名-decrypted.jar）")
    private String outputJar;

    @Option(names = {"--password"}, description = "解密密码（未指定时将交互式输入）", interactive = true)
    private String password;

    @Override
    public Integer call() {
        try {
            // 解析输入文件
            String input = inputJar != null ? inputJar : inputArg;
            if (input == null || input.isEmpty()) {
                System.err.println("错误: 请指定输入加密 JAR 文件路径 (-i 或位置参数)");
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
                            + name.substring(0, dot) + "-decrypted" + name.substring(dot);
                } else {
                    output = input + "-decrypted";
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
                    char[] pwdChars = console.readPassword("请输入解密密码: ");
                    if (pwdChars != null && pwdChars.length > 0) {
                        pwd = new String(pwdChars);
                    }
                }
            }
            if (pwd == null || pwd.isEmpty()) {
                System.err.println("错误: 请提供解密密码 (--password 参数、JAR_PROTECT_PASSWORD 环境变量或交互式输入)");
                return 1;
            }

            // 执行解密
            System.out.println();
            JarDecryptor decryptor = new JarDecryptor(
                    inputFile.getAbsolutePath(),
                    new File(output).getAbsolutePath(),
                    pwd
            );
            decryptor.execute();
            System.out.println();
            System.out.println("解密还原成功！输出文件: " + output);

            return 0;

        } catch (SecurityException e) {
            System.err.println("解密失败: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("解密失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
}
