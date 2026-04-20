package com.jarprotect.cli;

import com.jarprotect.cli.gui.MainWindow;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * JAR-Protect 命令行工具入口。
 */
@Command(
        name = "jar-protect",
        mixinStandardHelpOptions = true,
        version = "JAR-Protect 1.0.0",
        description = "Java应用JAR包加密与机器码绑定工具",
        subcommands = {
                EncryptCommand.class,
                DecryptCommand.class,
                MachineCodeCommand.class,
                LicenseCommand.class,
                GuiCommand.class
        }
)
public class Main implements Runnable {

    @Override
    public void run() {
        System.out.println("==============================================");
        System.out.println("  JAR-Protect v1.0.0");
        System.out.println("  Java应用JAR包加密与机器码绑定工具");
        System.out.println("==============================================");
        System.out.println();
        System.out.println("使用 --help 查看帮助信息");
        System.out.println();
        System.out.println("可用命令:");
        System.out.println("  encrypt      加密 JAR 包");
        System.out.println("  decrypt      解密还原 JAR 包");
        System.out.println("  machinecode  生成或查看当前机器码");
        System.out.println("  license      注册码的生成、验证与查看");
        System.out.println("  gui          启动图形界面");
    }

    public static void main(String[] args) {
        // 无参数时默认启动 GUI
        if (args.length == 0) {
            MainWindow.launch();
            return;
        }
        // gui 命令不调用 System.exit，防止 Swing 窗口被关闭
        if (args.length > 0 && "gui".equalsIgnoreCase(args[0])) {
            MainWindow.launch();
            return;
        }
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }
}
