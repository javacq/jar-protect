package com.jarprotect.cli;

import com.jarprotect.cli.gui.MainWindow;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * 启动图形界面命令。
 */
@Command(
        name = "gui",
        description = "启动图形界面（GUI）",
        mixinStandardHelpOptions = true
)
public class GuiCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        MainWindow.launch();
        return 0;
    }
}
