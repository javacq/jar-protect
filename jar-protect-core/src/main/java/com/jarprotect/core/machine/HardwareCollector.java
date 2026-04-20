package com.jarprotect.core.machine;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

/**
 * 跨平台硬件信息采集器。
 * 支持 Windows / Linux / macOS，并对 Docker/VMware 等虚拟化环境做降级处理。
 */
public class HardwareCollector {

    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

    /**
     * 采集所有可用的硬件信息组件。
     */
    public static List<String> collectAll() {
        List<String> components = new ArrayList<>();

        // 1. MAC 地址（始终可获取）
        String mac = getMacAddresses();
        if (!mac.isEmpty()) {
            components.add("MAC:" + mac);
        }

        // 2. CPU 序列号 / 型号
        String cpuId = getCpuId();
        if (!cpuId.isEmpty()) {
            components.add("CPU:" + cpuId);
        }

        // 3. 主板序列号
        String boardId = getMotherboardId();
        if (!boardId.isEmpty()) {
            components.add("BOARD:" + boardId);
        }

        // 4. 硬盘序列号
        String diskId = getDiskSerial();
        if (!diskId.isEmpty()) {
            components.add("DISK:" + diskId);
        }

        // 5. 降级策略：如果硬件信息过少（虚拟机环境），补充 OS + hostname
        if (components.size() < 2) {
            components.add("OS:" + System.getProperty("os.name", "unknown") + "_"
                    + System.getProperty("os.arch", "unknown"));
            String hostname = getHostname();
            if (!hostname.isEmpty()) {
                components.add("HOST:" + hostname);
            }
        }

        return components;
    }

    /**
     * 获取所有物理网卡的 MAC 地址（排序拼接）。
     */
    public static String getMacAddresses() {
        try {
            List<String> macs = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                    continue;
                }
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X", mac[i]));
                        if (i < mac.length - 1) sb.append(":");
                    }
                    macs.add(sb.toString());
                }
            }
            Collections.sort(macs);
            StringBuilder result = new StringBuilder();
            for (String m : macs) {
                if (result.length() > 0) result.append(";");
                result.append(m);
            }
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取 CPU 标识信息。
     */
    public static String getCpuId() {
        try {
            if (isWindows()) {
                return execCommand("wmic", "cpu", "get", "ProcessorId").trim();
            } else if (isLinux()) {
                // 尝试从 /proc/cpuinfo 获取
                String result = execCommand("sh", "-c",
                        "cat /proc/cpuinfo | grep -i 'model name' | head -1 | cut -d: -f2");
                if (result.isEmpty()) {
                    result = execCommand("sh", "-c",
                            "cat /proc/cpuinfo | grep -i 'serial' | head -1 | cut -d: -f2");
                }
                return result.trim();
            } else if (isMac()) {
                return execCommand("sh", "-c",
                        "sysctl -n machdep.cpu.brand_string").trim();
            }
        } catch (Exception e) {
            // ignored
        }
        return "";
    }

    /**
     * 获取主板序列号。
     */
    public static String getMotherboardId() {
        try {
            if (isWindows()) {
                return execCommand("wmic", "baseboard", "get", "SerialNumber").trim();
            } else if (isLinux()) {
                String result = execCommand("sh", "-c",
                        "cat /sys/class/dmi/id/board_serial 2>/dev/null || "
                                + "sudo dmidecode -s baseboard-serial-number 2>/dev/null");
                return result.trim();
            } else if (isMac()) {
                return execCommand("sh", "-c",
                        "ioreg -rd1 -c IOPlatformExpertDevice | grep IOPlatformSerialNumber | cut -d'\"' -f4").trim();
            }
        } catch (Exception e) {
            // ignored
        }
        return "";
    }

    /**
     * 获取硬盘序列号。
     */
    public static String getDiskSerial() {
        try {
            if (isWindows()) {
                return execCommand("wmic", "diskdrive", "get", "SerialNumber").trim();
            } else if (isLinux()) {
                String result = execCommand("sh", "-c",
                        "lsblk -dno SERIAL /dev/sda 2>/dev/null || "
                                + "hdparm -I /dev/sda 2>/dev/null | grep 'Serial Number' | cut -d: -f2");
                return result.trim();
            } else if (isMac()) {
                return execCommand("sh", "-c",
                        "system_profiler SPSerialATADataType 2>/dev/null | grep 'Serial Number' | head -1 | cut -d: -f2").trim();
            }
        } catch (Exception e) {
            // ignored
        }
        return "";
    }

    /**
     * 获取主机名。
     */
    private static String getHostname() {
        try {
            if (isWindows()) {
                return execCommand("hostname").trim();
            } else {
                return execCommand("hostname").trim();
            }
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 执行系统命令并返回输出（过滤掉标题行）。
     */
    private static String execCommand(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 跳过 wmic 命令的标题行和空行
                if (line.isEmpty()
                        || line.equalsIgnoreCase("ProcessorId")
                        || line.equalsIgnoreCase("SerialNumber")
                        || line.equalsIgnoreCase("Serial Number")) {
                    continue;
                }
                if (output.length() > 0) output.append(";");
                output.append(line);
            }
            process.waitFor();
            return output.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean isWindows() {
        return OS.contains("win");
    }

    private static boolean isLinux() {
        return OS.contains("linux") || OS.contains("nux");
    }

    private static boolean isMac() {
        return OS.contains("mac") || OS.contains("darwin");
    }
}
