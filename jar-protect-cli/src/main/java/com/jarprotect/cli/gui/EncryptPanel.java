package com.jarprotect.cli.gui;

import com.jarprotect.core.jar.JarEncryptor;
import com.jarprotect.core.machine.MachineCodeGenerator;
import com.jarprotect.core.model.EncryptConfig;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.jarprotect.cli.gui.GuiUtils.*;

/**
 * JAR 加密面板。
 */
public class EncryptPanel extends JPanel {

    public EncryptPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(heading("加密 JAR 包"));
        content.add(Box.createVerticalStrut(4));
        content.add(label("字节码擦除 + AES-256-GCM 加密，支持资源文件和依赖库"));
        content.add(Box.createVerticalStrut(20));

        // ── 表单 ──
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();

        JTextField inputF = textField();
        JTextField outputF = textField();
        JTextField pkgF = textField(); pkgF.setText("com.example.**");
        JPasswordField pwdF = passwordField();
        JPasswordField pwd2F = passwordField();
        JTextField resF = textField(); resF.setText(".yml,.yaml,.properties,.xml");
        JCheckBox libCb = checkBox("加密 BOOT-INF/lib 下的依赖");
        JTextField libF = textField(); libF.setEnabled(false);
        libF.setToolTipText("填写要加密的依赖 JAR 名称模式，如: my-company-*.jar,private-*.jar");

        // 新增：嵌入 Agent + 注册码验证
        JCheckBox embedCb = checkBox("自动嵌入解密引擎（无需 -javaagent 即可运行）");
        embedCb.setSelected(true);
        JTextField agentF = textField();
        agentF.setText(findAgentJar()); // 自动查找 agent JAR
        // 机器码绑定
        JCheckBox mcCb = checkBox("绑定机器码（加密后只能在指定机器运行）");
        JTextField mcF = textField();
        mcF.setEnabled(false);
        mcF.setToolTipText("留空则绑定当前机器；也可填写目标机器的机器码");

        // AES+RSA 混合加密
        JCheckBox rsaCb = checkBox("AES+RSA 混合加密（必须持有私钥才能解密/运行）");
        JTextField rsaPubF = textField();
        rsaPubF.setEnabled(false);
        rsaPubF.setToolTipText("选择 RSA 公钥文件 (public.pem)");

        JCheckBox licCb = checkBox("启用注册码验证（客户需在 JAR 目录下放置 license.lic）");
        JTextField licKeyF = textField();
        licKeyF.setEnabled(false);

        int r = 0;
        addRow(form, g, r++, "输入 JAR", fileRow(inputF, false));
        addRow(form, g, r++, "输出 JAR", fileRow(outputF, true));
        addRow(form, g, r++, "加密包路径", pkgF);
        addRow(form, g, r++, "加密密码", pwdF);
        addRow(form, g, r++, "确认密码", pwd2F);
        addRow(form, g, r++, "资源后缀", resF);

        // Lib 加密
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, GAP, 0);
        form.add(libCb, g); g.gridwidth = 1;
        addRow(form, g, r++, "Lib 匹配规则", libF);

        // 分隔
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(4, 0, 4, 0);
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_DIM);
        form.add(sep, g); g.gridwidth = 1;

        // 嵌入 Agent
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, GAP, 0);
        form.add(embedCb, g); g.gridwidth = 1;
        addRow(form, g, r++, "Agent JAR", fileRow(agentF, false));

        // 机器码绑定
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, GAP, 0);
        form.add(mcCb, g); g.gridwidth = 1;
        addRow(form, g, r++, "目标机器码", mcF);

        // AES+RSA 混合加密
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, GAP, 0);
        form.add(rsaCb, g); g.gridwidth = 1;
        addRow(form, g, r++, "RSA 公钥", fileRow(rsaPubF, false, "PEM 密钥文件", "pem"));

        // 注册码验证
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, GAP, 0);
        form.add(licCb, g); g.gridwidth = 1;
        addRow(form, g, r++, "自定义密钥（可选）", licKeyF);

        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, form.getPreferredSize().height + 10));
        content.add(form);
        content.add(Box.createVerticalStrut(12));

        // ── 按钮 ──
        JButton encBtn = primaryBtn("开始加密");
        JButton clrBtn = ghostBtn("清空日志");
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnRow.add(encBtn);
        btnRow.add(clrBtn);
        content.add(btnRow);
        content.add(Box.createVerticalStrut(16));

        // ── 日志 ──
        JLabel logTitle = heading("执行日志");
        logTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(logTitle);
        content.add(Box.createVerticalStrut(8));
        JTextArea logA = logArea(10);
        JScrollPane logScroll = new JScrollPane(logA);
        logScroll.setAlignmentX(LEFT_ALIGNMENT);
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER_DIM, 1));
        content.add(logScroll);

        add(new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {{
            setBorder(null); getViewport().setOpaque(false); setOpaque(false);
        }}, BorderLayout.CENTER);

        // ── 事件 ──
        inputF.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) {
                String in = inputF.getText().trim();
                if (!in.isEmpty() && outputF.getText().trim().isEmpty()) {
                    File f = new File(in); String n = f.getName();
                    int d = n.lastIndexOf('.');
                    String o = d > 0 ? n.substring(0, d) + "-encrypted" + n.substring(d) : n + "-encrypted";
                    outputF.setText(new File(f.getParentFile(), o).getAbsolutePath());
                }
            }
        });
        libCb.addActionListener(e -> libF.setEnabled(libCb.isSelected()));
        embedCb.addActionListener(e -> agentF.setEnabled(embedCb.isSelected()));
        mcCb.addActionListener(e -> {
            mcF.setEnabled(mcCb.isSelected());
            if (mcCb.isSelected() && mcF.getText().trim().isEmpty()) {
                // 自动填充当前机器码
                mcF.setText(MachineCodeGenerator.generate());
            }
        });
        rsaCb.addActionListener(e -> {
            boolean rsa = rsaCb.isSelected();
            rsaPubF.setEnabled(rsa);
            pwdF.setEnabled(!rsa);
            pwd2F.setEnabled(!rsa);
            // RSA 模式下注册码也用 RSA 签名，无需单独密钥
            licKeyF.setEnabled(!rsa && licCb.isSelected());
        });
        licCb.addActionListener(e -> licKeyF.setEnabled(licCb.isSelected() && !rsaCb.isSelected()));
        clrBtn.addActionListener(e -> logA.setText(""));

        encBtn.addActionListener(e -> {
            String input = inputF.getText().trim(), output = outputF.getText().trim();
            String pkg = pkgF.getText().trim();
            if (input.isEmpty()) { w("请选择输入 JAR"); return; }
            if (!new File(input).exists()) { w("输入文件不存在"); return; }
            if (output.isEmpty()) { w("请指定输出路径"); return; }
            if (pkg.isEmpty()) { w("请输入加密包路径"); return; }

            // RSA 模式：自动生成随机密码；纯 AES 模式：手动输入
            String pwd, pwd2;
            if (rsaCb.isSelected()) {
                pwd = java.util.UUID.randomUUID().toString().replace("-", "")
                        + java.util.UUID.randomUUID().toString().replace("-", "");
                pwd2 = pwd;
            } else {
                pwd = new String(pwdF.getPassword()).trim();
                pwd2 = new String(pwd2F.getPassword()).trim();
                if (pwd.isEmpty()) { w("请输入密码"); return; }
            }
            if (!pwd.equals(pwd2)) { w("两次密码不一致"); return; }

            if (embedCb.isSelected()) {
                String ap = agentF.getText().trim();
                if (ap.isEmpty() || !new File(ap).exists()) {
                    w("请指定有效的 Agent JAR 路径"); return;
                }
            }

            EncryptConfig cfg = new EncryptConfig();
            cfg.setInputJar(input); cfg.setOutputJar(output); cfg.setPassword(pwd);
            cfg.setPackagePatterns(split(pkg));
            String ext = resF.getText().trim();
            if (!ext.isEmpty()) cfg.setResourceExtensions(split(ext));
            cfg.setEncryptLibs(libCb.isSelected());
            if (libCb.isSelected()) {
                String lp = libF.getText().trim();
                if (!lp.isEmpty()) cfg.setLibPatterns(split(lp));
            }
            // Agent 嵌入
            if (embedCb.isSelected()) {
                cfg.setAgentJarPath(agentF.getText().trim());
            }
            // 机器码绑定
            if (mcCb.isSelected()) {
                String mc = mcF.getText().trim();
                if (mc.isEmpty()) {
                    mc = MachineCodeGenerator.generate();
                }
                cfg.setMachineCode(mc);
            }
            // 注册码验证
            if (licCb.isSelected()) {
                cfg.setLicenseRequired(true);
                if (rsaCb.isSelected()) {
                    // AES+RSA 模式下，注册码也使用 RSA 私钥模式验证
                    cfg.setRsaLicenseMode(true);
                } else {
                    String lk = licKeyF.getText().trim();
                    if (!lk.isEmpty()) cfg.setLicenseSecretKey(lk);
                }
            }
            // AES+RSA 混合加密
            if (rsaCb.isSelected()) {
                String rp = rsaPubF.getText().trim();
                if (rp.isEmpty() || !new File(rp).exists()) {
                    w("请选择有效的 RSA 公钥文件"); return;
                }
                cfg.setRsaPublicKeyPath(rp);
            }

            runAsync(encBtn, logA, () -> {
                try {
                    new JarEncryptor(cfg).execute();
                    String msg = "加密完成!\n" + output;
                    if (licCb.isSelected()) {
                        msg += "\n\n客户使用方式:\n1. 在 JAR 同目录下创建 license.lic 文本文件\n2. 将注册码写入 license.lic\n3. java -jar " + new File(output).getName();
                    } else if (embedCb.isSelected()) {
                        msg += "\n\n客户直接运行: java -jar " + new File(output).getName();
                    }
                    String finalMsg = msg;
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this, finalMsg, "成功", JOptionPane.INFORMATION_MESSAGE));
                } catch (Exception ex) {
                    System.err.println("ERROR: " + ex.getMessage()); ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this, ex.getMessage(), "加密失败", JOptionPane.ERROR_MESSAGE));
                }
            });
        });
    }

    /**
     * 自动查找 agent JAR：在当前 JAR 同级目录下搜索。
     */
    private static String findAgentJar() {
        try {
            // 尝试从当前 JAR 所在目录查找
            File self = new File(EncryptPanel.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            File dir = self.isFile() ? self.getParentFile() : self;
            // 搜索同级目录
            File[] candidates = dir.listFiles((d, n) ->
                    n.startsWith("jar-protect-agent") && n.endsWith(".jar"));
            if (candidates != null && candidates.length > 0) {
                return candidates[0].getAbsolutePath();
            }
            // 搜索上级 target 目录
            File parent = dir.getParentFile();
            if (parent != null) {
                File agentTarget = new File(parent, "jar-protect-agent/target");
                if (agentTarget.exists()) {
                    File[] found = agentTarget.listFiles((d, n) ->
                            n.startsWith("jar-protect-agent") && n.endsWith(".jar")
                                    && !n.contains("original"));
                    if (found != null && found.length > 0) {
                        return found[0].getAbsolutePath();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    private List<String> split(String t) {
        List<String> l = new ArrayList<>();
        for (String s : t.split(",")) { String x = s.trim(); if (!x.isEmpty()) l.add(x); }
        return l;
    }

    private void w(String m) { JOptionPane.showMessageDialog(this, m, "提示", JOptionPane.WARNING_MESSAGE); }
}
