package com.jarprotect.cli.gui;

import com.jarprotect.core.crypto.RSACrypto;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Path;
import java.security.KeyPair;

import static com.jarprotect.cli.gui.GuiUtils.*;

/**
 * RSA 密钥管理面板 — 生成密钥对、查看密钥内容、导出 PEM 文件。
 */
public class RsaKeyPanel extends JPanel {

    private KeyPair currentKeyPair;

    public RsaKeyPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // ── 标题 ──
        content.add(heading("RSA 密钥管理"));
        content.add(Box.createVerticalStrut(4));
        content.add(label("生成 RSA-2048 密钥对，用于 AES+RSA 混合加密 / RSA 注册码签名"));
        content.add(Box.createVerticalStrut(20));

        // ── 密钥种子 ──
        JPanel seedForm = new JPanel(new GridBagLayout());
        seedForm.setOpaque(false);
        seedForm.setAlignmentX(LEFT_ALIGNMENT);
        seedForm.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        GridBagConstraints sg = new GridBagConstraints();
        JTextField seedField = textField();
        seedField.setToolTipText("输入密钥种子（相同种子生成相同密钥对）。留空则随机生成。");
        addRow(seedForm, sg, 0, "密钥种子（可选）", seedField);
        content.add(seedForm);
        content.add(Box.createVerticalStrut(12));

        // ── 按钮行 ──
        JButton genBtn = primaryBtn("生成 RSA 密钥对");
        JButton saveBtn = ghostBtn("保存到文件");
        saveBtn.setEnabled(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnRow.add(genBtn);
        btnRow.add(saveBtn);
        content.add(btnRow);
        content.add(Box.createVerticalStrut(16));

        // ── 公钥区域 ──
        JLabel pubTitle = heading("公钥 (Public Key)");
        pubTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(pubTitle);
        content.add(Box.createVerticalStrut(4));

        JLabel pubTip = label("加密时使用 — 嵌入到加密 JAR 中");
        pubTip.setAlignmentX(LEFT_ALIGNMENT);
        content.add(pubTip);
        content.add(Box.createVerticalStrut(8));

        JTextArea pubArea = logArea(6);
        pubArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        pubArea.setForeground(ACCENT);
        JScrollPane pubScroll = new JScrollPane(pubArea);
        pubScroll.setAlignmentX(LEFT_ALIGNMENT);
        pubScroll.setBorder(BorderFactory.createLineBorder(BORDER_DIM, 1));
        content.add(pubScroll);
        content.add(Box.createVerticalStrut(4));

        JButton copyPubBtn = ghostBtn("复制公钥");
        copyPubBtn.setEnabled(false);
        JPanel pubBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        pubBtnRow.setOpaque(false);
        pubBtnRow.setAlignmentX(LEFT_ALIGNMENT);
        pubBtnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        pubBtnRow.add(copyPubBtn);
        content.add(pubBtnRow);
        content.add(Box.createVerticalStrut(16));

        // ── 私钥区域 ──
        JLabel priTitle = heading("私钥 (Private Key)");
        priTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(priTitle);
        content.add(Box.createVerticalStrut(4));

        JLabel priTip = label("解密时使用 — 需妥善保管，放在 JAR 同目录或生成注册码时使用");
        priTip.setForeground(ACCENT_WARN);
        priTip.setAlignmentX(LEFT_ALIGNMENT);
        content.add(priTip);
        content.add(Box.createVerticalStrut(8));

        JTextArea priArea = logArea(6);
        priArea.setFont(new Font("Consolas", Font.PLAIN, 11));
        priArea.setForeground(ACCENT_WARN);
        JScrollPane priScroll = new JScrollPane(priArea);
        priScroll.setAlignmentX(LEFT_ALIGNMENT);
        priScroll.setBorder(BorderFactory.createLineBorder(BORDER_DIM, 1));
        content.add(priScroll);
        content.add(Box.createVerticalStrut(4));

        JButton copyPriBtn = ghostBtn("复制私钥");
        copyPriBtn.setEnabled(false);
        JPanel priBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        priBtnRow.setOpaque(false);
        priBtnRow.setAlignmentX(LEFT_ALIGNMENT);
        priBtnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        priBtnRow.add(copyPriBtn);
        content.add(priBtnRow);
        content.add(Box.createVerticalStrut(16));

        // ── 安全提示 ──
        JLabel warnLabel = label("! 私钥是最高机密，泄露私钥等于丧失全部保护。请勿上传至公共仓库或发送给客户。");
        warnLabel.setForeground(ACCENT_ERR);
        warnLabel.setFont(FONT_SMALL);
        warnLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(warnLabel);

        add(new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {{
            setBorder(null); getViewport().setOpaque(false); setOpaque(false);
        }}, BorderLayout.CENTER);

        // ══════ 事件 ══════
        genBtn.addActionListener(e -> {
            genBtn.setEnabled(false);
            genBtn.setText("生成中...");
            new Thread(() -> {
                try {
                    String seed = seedField.getText().trim();
                    KeyPair kp = seed.isEmpty() ? RSACrypto.generateKeyPair() : RSACrypto.generateKeyPair(seed);
                    String pubPem = toPem("PUBLIC KEY", RSACrypto.publicKeyToBase64(kp.getPublic()));
                    String priPem = toPem("PRIVATE KEY", RSACrypto.privateKeyToBase64(kp.getPrivate()));
                    SwingUtilities.invokeLater(() -> {
                        currentKeyPair = kp;
                        pubArea.setText(pubPem);
                        priArea.setText(priPem);
                        saveBtn.setEnabled(true);
                        copyPubBtn.setEnabled(true);
                        copyPriBtn.setEnabled(true);
                        genBtn.setEnabled(true);
                        genBtn.setText("生成 RSA 密钥对");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "生成失败: " + ex.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE);
                        genBtn.setEnabled(true);
                        genBtn.setText("生成 RSA 密钥对");
                    });
                }
            }).start();
        });

        saveBtn.addActionListener(e -> {
            if (currentKeyPair == null) return;
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("选择密钥保存目录");
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File dir = fc.getSelectedFile();
                try {
                    Path pubPath = dir.toPath().resolve("public.pem");
                    Path priPath = dir.toPath().resolve("private.pem");
                    RSACrypto.savePublicKey(currentKeyPair.getPublic(), pubPath);
                    RSACrypto.savePrivateKey(currentKeyPair.getPrivate(), priPath);
                    JOptionPane.showMessageDialog(this,
                            "密钥文件已保存：\n\n"
                                    + "公钥: " + pubPath + "\n"
                                    + "私钥: " + priPath + "\n\n"
                                    + "使用说明：\n"
                                    + "- 加密面板选择 public.pem 作为 RSA 公钥\n"
                                    + "- 运行加密 JAR 时将 private.pem 放在 JAR 同目录\n"
                                    + "- 生成注册码时选择 private.pem 签名",
                            "保存成功", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "保存失败: " + ex.getMessage(),
                            "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        copyPubBtn.addActionListener(e -> {
            copyToClipboard(pubArea.getText());
            copyPubBtn.setText("已复制!");
            Timer t = new Timer(1500, ev -> copyPubBtn.setText("复制公钥"));
            t.setRepeats(false); t.start();
        });

        copyPriBtn.addActionListener(e -> {
            copyToClipboard(priArea.getText());
            copyPriBtn.setText("已复制!");
            Timer t = new Timer(1500, ev -> copyPriBtn.setText("复制私钥"));
            t.setRepeats(false); t.start();
        });
    }

    private static String toPem(String type, String base64) {
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            sb.append(base64, i, Math.min(i + 64, base64.length()));
            sb.append('\n');
        }
        sb.append("-----END ").append(type).append("-----");
        return sb.toString();
    }
}
