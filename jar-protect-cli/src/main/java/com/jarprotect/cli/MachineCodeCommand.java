package com.jarprotect.cli;

import com.jarprotect.core.machine.HardwareCollector;
import com.jarprotect.core.machine.MachineCodeGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * 机器码生成与查看命令。
 *
 * 使用示例:
 *   jar-protect machinecode              # 生成当前机器的机器码
 *   jar-protect machinecode --detail      # 显示详细硬件信息
 *   jar-protect machinecode --verify A1B2-C3D4-E5F6-7890  # 验证机器码
 */
@Command(
        name = "machinecode",
        description = "生成、查看或验证机器码",
        mixinStandardHelpOptions = true
)
public class MachineCodeCommand implements Callable<Integer> {

    @Option(names = {"-d", "--detail"}, description = "显示详细硬件信息")
    private boolean showDetail;

    @Option(names = {"-v", "--verify"}, description = "验证给定的机器码是否匹配当前机器")
    private String verifyCode;

    @Override
    public Integer call() {
        try {
            if (verifyCode != null && !verifyCode.isEmpty()) {
                return doVerify();
            }

            return doGenerate();

        } catch (Exception e) {
            System.err.println("操作失败: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }

    private Integer doGenerate() {
        System.out.println("==============================================");
        System.out.println("  机器码生成器");
        System.out.println("==============================================");
        System.out.println();

        if (showDetail) {
            System.out.println(MachineCodeGenerator.getHardwareDetails());
            System.out.println();
        }

        String code = MachineCodeGenerator.generate();
        System.out.println("当前机器码: " + code);
        System.out.println();
        System.out.println("请将此机器码提供给软件提供商，用于生成授权的加密包。");
        System.out.println("加密时使用: jar-protect encrypt -i app.jar -p com.example.** -m " + code);

        return 0;
    }

    private Integer doVerify() {
        System.out.println("==============================================");
        System.out.println("  机器码验证");
        System.out.println("==============================================");
        System.out.println();

        String currentCode = MachineCodeGenerator.generate();
        System.out.println("当前机器码: " + currentCode);
        System.out.println("待验证码:   " + verifyCode);
        System.out.println();

        boolean match = MachineCodeGenerator.verify(verifyCode);
        if (match) {
            System.out.println("验证结果: [通过] 机器码匹配当前设备。");
            return 0;
        } else {
            System.out.println("验证结果: [失败] 机器码不匹配当前设备！");
            return 1;
        }
    }
}
