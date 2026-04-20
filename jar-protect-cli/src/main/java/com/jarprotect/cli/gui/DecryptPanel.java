package com.jarprotect.cli.gui;

import com.jarprotect.core.crypto.RSACrypto;
import com.jarprotect.core.jar.JarDecryptor;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.jarprotect.cli.gui.GuiUtils.*;

/**
 * JAR 解密面板。
 */
public class DecryptPanel extends JPanel {

    public DecryptPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(heading("解密还原 JAR 包"));
        content.add(Box.createVerticalStrut(4));
        content.add(label("将加密后的 JAR 还原为原始字节码"));
        content.add(Box.createVerticalStrut(20));

        // ── 表单 ──
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        form.setAlignmentX(LEFT_ALIGNMENT);
        GridBagConstraints g = new GridBagConstraints();

        JTextField inputF = textField();
        JTextField outputF = textField();
        JPasswordField pwdF = passwordField();

        // RSA 私钥解密选项
        JCheckBox rsaCb = checkBox("使用 RSA 私钥解密（AES+RSA 模式加密的 JAR）");
        JTextField rsaPriF = textField();
        rsaPriF.setEnabled(false);
        rsaPriF.setToolTipText("选择 RSA 私钥文件 (private.pem)");

        int r = 0;
        addRow(form, g, r++, "加密 JAR", fileRow(inputF, false));
        addRow(form, g, r++, "输出 JAR", fileRow(outputF, true));
        addRow(form, g, r++, "解密密码", pwdF);

        // RSA 私钥
        g.gridx = 0; g.gridy = r++; g.gridwidth = 2;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(0, 0, GAP, 0);
        form.add(rsaCb, g); g.gridwidth = 1;
        addRow(form, g, r++, "RSA 私钥", fileRow(rsaPriF, false));

        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, form.getPreferredSize().height + 10));
        content.add(form);
        content.add(Box.createVerticalStrut(12));

        // ── 按钮 ──
        JButton decBtn = primaryBtn("开始解密");
        JButton clrBtn = ghostBtn("清空日志");
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnRow.add(decBtn);
        btnRow.add(clrBtn);
        content.add(btnRow);
        content.add(Box.createVerticalStrut(16));

        // ── 日志 ──
        JLabel logTitle = heading("执行日志");
        logTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(logTitle);
        content.add(Box.createVerticalStrut(8));
        JTextArea logA = logArea(14);
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
                    String o = d > 0 ? n.substring(0, d) + "-decrypted" + n.substring(d) : n + "-decrypted";
                    outputF.setText(new File(f.getParentFile(), o).getAbsolutePath());
                }
            }
        });
        rsaCb.addActionListener(e -> {
            rsaPriF.setEnabled(rsaCb.isSelected());
            pwdF.setEnabled(!rsaCb.isSelected());
        });
        clrBtn.addActionListener(e -> logA.setText(""));

        decBtn.addActionListener(e -> {
            String input = inputF.getText().trim(), output = outputF.getText().trim();
            if (input.isEmpty()) { w("请选择加密 JAR"); return; }
            if (!new File(input).exists()) { w("文件不存在"); return; }
            if (output.isEmpty()) { w("请指定输出路径"); return; }

            String pwd;
            if (rsaCb.isSelected()) {
                // RSA 模式：从 manifest 中提取加密密码，用私钥解密
                String priPath = rsaPriF.getText().trim();
                if (priPath.isEmpty() || !new File(priPath).exists()) {
                    w("请选择有效的 RSA 私钥文件"); return;
                }
                try {
                    pwd = extractPasswordWithRsa(input, priPath);
                } catch (Exception ex) {
                    w("RSA 解密密码失败: " + ex.getMessage()); return;
                }
            } else {
                pwd = new String(pwdF.getPassword()).trim();
                if (pwd.isEmpty()) { w("请输入密码"); return; }
            }

            final String finalPwd = pwd;
            runAsync(decBtn, logA, () -> {
                try {
                    new JarDecryptor(input, output, finalPwd).execute();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this, "解密完成!\n" + output, "成功", JOptionPane.INFORMATION_MESSAGE));
                } catch (Exception ex) {
                    System.err.println("ERROR: " + ex.getMessage()); ex.printStackTrace();
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                            this, ex.getMessage(), "解密失败", JOptionPane.ERROR_MESSAGE));
                }
            });
        });
    }

    /**
     * 从加密 JAR 的 manifest 中提取 RSA 加密的密码并用私钥解密。
     */
    private String extractPasswordWithRsa(String jarPath, String privateKeyPath) throws Exception {
        // 读取 manifest
        try (java.util.jar.JarFile jf = new java.util.jar.JarFile(jarPath)) {
            java.util.jar.JarEntry entry = jf.getJarEntry("META-INF/protect-manifest.json");
            if (entry == null) throw new RuntimeException("JAR 中未找到 protect-manifest.json");
            try (java.io.InputStream is = jf.getInputStream(entry);
                 java.io.BufferedReader br = new java.io.BufferedReader(
                         new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                com.jarprotect.core.model.ProtectManifest manifest =
                        new com.google.gson.Gson().fromJson(sb.toString(),
                                com.jarprotect.core.model.ProtectManifest.class);
                if (!manifest.isRsaMode()) {
                    throw new RuntimeException("此 JAR 不是 AES+RSA 模式加密，请直接输入密码");
                }
                java.security.PrivateKey pk = RSACrypto.loadPrivateKey(
                        java.nio.file.Paths.get(privateKeyPath));
                return RSACrypto.decryptFromBase64(manifest.getRsaEncryptedPassword(), pk);
            }
        }
    }

    private void w(String m) {
        JOptionPane.showMessageDialog(this, m, "提示", JOptionPane.WARNING_MESSAGE);
    }
}
