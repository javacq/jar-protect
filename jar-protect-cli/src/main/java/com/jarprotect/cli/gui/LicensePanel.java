package com.jarprotect.cli.gui;

import com.jarprotect.core.crypto.RSACrypto;
import com.jarprotect.core.license.LicenseGenerator;
import com.jarprotect.core.license.LicenseGenerator.LicenseResult;
import com.jarprotect.core.machine.MachineCodeGenerator;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static com.jarprotect.cli.gui.GuiUtils.*;

/**
 * 注册码面板 — 支持 HMAC 和 RSA 两种签名模式。
 */
public class LicensePanel extends JPanel {

    public LicensePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // ════════ 生成注册码 ════════
        content.add(heading("生成注册码"));
        content.add(Box.createVerticalStrut(4));
        content.add(label("支持 HMAC 密钥 / RSA 私钥两种签名模式"));
        content.add(Box.createVerticalStrut(16));

        JPanel genForm = new JPanel(new GridBagLayout());
        genForm.setOpaque(false);
        genForm.setAlignmentX(LEFT_ALIGNMENT);
        genForm.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        GridBagConstraints g = new GridBagConstraints();

        JTextField mcField = textField();
        JButton useCurrentBtn = ghostBtn("当前机器");

        // 签名模式选择
        JRadioButton hmacRadio = new JRadioButton("HMAC 密钥模式");
        hmacRadio.setFont(FONT_BODY); hmacRadio.setForeground(TEXT_SECONDARY);
        hmacRadio.setOpaque(false); hmacRadio.setFocusPainted(false);
        hmacRadio.setSelected(true);

        JRadioButton rsaRadio = new JRadioButton("RSA 私钥签名模式");
        rsaRadio.setFont(FONT_BODY); rsaRadio.setForeground(TEXT_SECONDARY);
        rsaRadio.setOpaque(false); rsaRadio.setFocusPainted(false);

        ButtonGroup modeGroup = new ButtonGroup();
        modeGroup.add(hmacRadio); modeGroup.add(rsaRadio);

        JPanel modeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        modeRow.setOpaque(false);
        modeRow.add(hmacRadio);
        modeRow.add(rsaRadio);

        JTextField secretField = textField();
        secretField.setToolTipText("HMAC 模式下的自定义密钥（可选）");
        JTextField rsaPriField = textField();
        rsaPriField.setEnabled(false);
        rsaPriField.setToolTipText("RSA 模式下选择私钥文件 (private.pem)");

        // 过期时间
        JRadioButton permRadio = new JRadioButton("永久有效");
        permRadio.setFont(FONT_BODY); permRadio.setForeground(TEXT_SECONDARY);
        permRadio.setOpaque(false); permRadio.setFocusPainted(false);
        permRadio.setSelected(true);

        JRadioButton dateRadio = new JRadioButton("自定义到期日");
        dateRadio.setFont(FONT_BODY); dateRadio.setForeground(TEXT_SECONDARY);
        dateRadio.setOpaque(false); dateRadio.setFocusPainted(false);

        ButtonGroup expiryGroup = new ButtonGroup();
        expiryGroup.add(permRadio); expiryGroup.add(dateRadio);

        JTextField expiryField = textField();
        expiryField.setEnabled(false);
        SimpleDateFormat sdf = new SimpleDateFormat(LicenseGenerator.DATE_FORMAT);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.YEAR, 1);
        expiryField.setText(sdf.format(cal.getTime()));

        JPanel expiryRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        expiryRow.setOpaque(false);
        expiryRow.add(permRadio);
        expiryRow.add(dateRadio);
        expiryRow.add(expiryField);
        expiryField.setPreferredSize(new Dimension(120, 28));
        expiryField.setColumns(10);

        JTextArea licenseOut = logArea(3);
        licenseOut.setFont(FONT_CODE);
        JScrollPane licenseScroll = new JScrollPane(licenseOut);
        licenseScroll.setBorder(BorderFactory.createLineBorder(BORDER_DIM, 1));
        licenseScroll.setPreferredSize(new Dimension(600, 60));

        JLabel expiryDisplay = label(" ");
        expiryDisplay.setFont(FONT_SMALL);
        expiryDisplay.setForeground(TEXT_DIM);

        int r = 0;
        addRow(genForm, g, r++, "机器码", rowWithBtn(mcField, useCurrentBtn));
        addRow(genForm, g, r++, "签名模式", modeRow);
        addRow(genForm, g, r++, "HMAC 密钥（可选）", secretField);
        addRow(genForm, g, r++, "RSA \u79c1\u94a5", fileRow(rsaPriField, false, "PEM \u5bc6\u94a5\u6587\u4ef6", "pem"));
        addRow(genForm, g, r++, "有效期", expiryRow);

        content.add(genForm);
        content.add(Box.createVerticalStrut(4));

        // 注册码输出
        JLabel licLabel = label("生成的注册码：");
        licLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(licLabel);
        content.add(Box.createVerticalStrut(4));
        licenseScroll.setAlignmentX(LEFT_ALIGNMENT);
        licenseScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        content.add(licenseScroll);
        content.add(Box.createVerticalStrut(4));
        expiryDisplay.setAlignmentX(LEFT_ALIGNMENT);
        content.add(expiryDisplay);
        content.add(Box.createVerticalStrut(8));

        JButton genBtn = primaryBtn("生成注册码");
        JButton copyBtn = ghostBtn("复制注册码");
        copyBtn.setEnabled(false);
        JPanel genBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        genBtnRow.setOpaque(false);
        genBtnRow.setAlignmentX(LEFT_ALIGNMENT);
        genBtnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        genBtnRow.add(genBtn);
        genBtnRow.add(copyBtn);
        content.add(genBtnRow);
        content.add(Box.createVerticalStrut(10));

        JLabel tipLabel = label("▸ 客户使用：在 JAR 同目录创建 license.lic，写入注册码即可");
        tipLabel.setForeground(TEXT_DIM);
        tipLabel.setFont(FONT_SMALL);
        tipLabel.setAlignmentX(LEFT_ALIGNMENT);
        content.add(tipLabel);
        content.add(Box.createVerticalStrut(20));

        // ── 分割线 ──
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_DIM);
        sep.setAlignmentX(LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(sep);
        content.add(Box.createVerticalStrut(20));

        // ════════ 验证注册码 ════════
        content.add(heading("验证注册码"));
        content.add(Box.createVerticalStrut(4));
        content.add(label("选择与生成时相同的模式进行验证"));
        content.add(Box.createVerticalStrut(16));

        JPanel vForm = new JPanel(new GridBagLayout());
        vForm.setOpaque(false);
        vForm.setAlignmentX(LEFT_ALIGNMENT);
        vForm.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        GridBagConstraints g2 = new GridBagConstraints();

        JTextArea vLicense = logArea(2);
        vLicense.setFont(FONT_CODE);
        JScrollPane vLicScroll = new JScrollPane(vLicense);
        vLicScroll.setBorder(BorderFactory.createLineBorder(BORDER_DIM, 1));
        vLicScroll.setPreferredSize(new Dimension(600, 46));

        JTextField vMc = textField();
        JButton vUseCurrent = ghostBtn("当前机器");
        JTextField vSecret = textField();
        vSecret.setToolTipText("HMAC 模式下的自定义密钥");
        JTextField vRsaPri = textField();
        vRsaPri.setEnabled(false);
        vRsaPri.setToolTipText("RSA 模式下选择私钥文件 (private.pem)");

        JRadioButton vHmacRadio = new JRadioButton("HMAC 模式");
        vHmacRadio.setFont(FONT_BODY); vHmacRadio.setForeground(TEXT_SECONDARY);
        vHmacRadio.setOpaque(false); vHmacRadio.setFocusPainted(false);
        vHmacRadio.setSelected(true);
        JRadioButton vRsaRadio = new JRadioButton("RSA 模式");
        vRsaRadio.setFont(FONT_BODY); vRsaRadio.setForeground(TEXT_SECONDARY);
        vRsaRadio.setOpaque(false); vRsaRadio.setFocusPainted(false);
        ButtonGroup vModeGroup = new ButtonGroup();
        vModeGroup.add(vHmacRadio); vModeGroup.add(vRsaRadio);
        JPanel vModeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        vModeRow.setOpaque(false);
        vModeRow.add(vHmacRadio); vModeRow.add(vRsaRadio);

        int vr = 0;
        addRow(vForm, g2, vr++, "注册码", vLicScroll);
        addRow(vForm, g2, vr++, "机器码", rowWithBtn(vMc, vUseCurrent));
        addRow(vForm, g2, vr++, "验证模式", vModeRow);
        addRow(vForm, g2, vr++, "HMAC 密钥（可选）", vSecret);
        addRow(vForm, g2, vr++, "RSA 私钥", fileRow(vRsaPri, false, "PEM 密钥文件", "pem"));
        content.add(vForm);
        content.add(Box.createVerticalStrut(12));

        JButton vBtn = primaryBtn("验证");
        JLabel vResult = label(" ");
        JPanel vBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        vBtnRow.setOpaque(false);
        vBtnRow.setAlignmentX(LEFT_ALIGNMENT);
        vBtnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        vBtnRow.add(vBtn);
        content.add(vBtnRow);
        content.add(Box.createVerticalStrut(8));
        vResult.setAlignmentX(LEFT_ALIGNMENT);
        content.add(vResult);
        content.add(Box.createVerticalGlue());

        add(new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {{
            setBorder(null); getViewport().setOpaque(false); setOpaque(false);
        }}, BorderLayout.CENTER);

        // ══════ 事件 ══════
        permRadio.addActionListener(e -> expiryField.setEnabled(false));
        dateRadio.addActionListener(e -> expiryField.setEnabled(true));

        vHmacRadio.addActionListener(e -> { vSecret.setEnabled(true); vRsaPri.setEnabled(false); });
        vRsaRadio.addActionListener(e -> { vSecret.setEnabled(false); vRsaPri.setEnabled(true); });

        hmacRadio.addActionListener(e -> {
            secretField.setEnabled(true);
            rsaPriField.setEnabled(false);
        });
        rsaRadio.addActionListener(e -> {
            secretField.setEnabled(false);
            rsaPriField.setEnabled(true);
        });

        useCurrentBtn.addActionListener(e -> {
            try { mcField.setText(MachineCodeGenerator.generate()); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE); }
        });
        vUseCurrent.addActionListener(e -> {
            try { vMc.setText(MachineCodeGenerator.generate()); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE); }
        });

        genBtn.addActionListener(e -> {
            String mc = mcField.getText().trim();
            if (mc.isEmpty()) { mc = MachineCodeGenerator.generate(); mcField.setText(mc); }
            try {
                Date expiry = null;
                if (dateRadio.isSelected()) {
                    String dateStr = expiryField.getText().trim();
                    if (dateStr.isEmpty()) {
                        JOptionPane.showMessageDialog(this, "请输入过期日期（格式 yyyy-MM-dd）", "提示", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    expiry = LicenseGenerator.parseExpiry(dateStr);
                    if (expiry == null) {
                        JOptionPane.showMessageDialog(this, "日期格式无效，请使用 yyyy-MM-dd", "提示", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                }

                String lic;
                if (rsaRadio.isSelected()) {
                    // RSA 签名模式
                    String priPath = rsaPriField.getText().trim();
                    if (priPath.isEmpty() || !new File(priPath).exists()) {
                        JOptionPane.showMessageDialog(this, "请选择有效的 RSA 私钥文件", "提示", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    java.security.PrivateKey pk = RSACrypto.loadPrivateKey(Paths.get(priPath));
                    lic = LicenseGenerator.generateRsa(mc, pk, expiry);
                    expiryDisplay.setText("模式: RSA 签名 | 有效期: "
                            + (expiry != null ? sdf.format(expiry) : "永久"));
                } else {
                    // HMAC 模式
                    String key = secretField.getText().trim();
                    lic = LicenseGenerator.generate(mc, key.isEmpty() ? null : key, expiry);
                    String extractedExpiry = LicenseGenerator.extractExpiry(lic);
                    expiryDisplay.setText("模式: HMAC | 有效期: " + extractedExpiry);
                }

                licenseOut.setText(lic);
                expiryDisplay.setForeground(ACCENT);
                copyBtn.setEnabled(true);
            } catch (Exception ex) {
                licenseOut.setText("");
                expiryDisplay.setText(" ");
                JOptionPane.showMessageDialog(this, "生成失败: " + ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
            }
        });

        copyBtn.addActionListener(e -> {
            String lic = licenseOut.getText().trim();
            if (!lic.isEmpty()) {
                copyToClipboard(lic);
                copyBtn.setText("已复制!");
                Timer timer = new Timer(1500, ev -> copyBtn.setText("复制注册码"));
                timer.setRepeats(false); timer.start();
            }
        });

        vBtn.addActionListener(e -> {
            String lic = vLicense.getText().trim();
            String mc = vMc.getText().trim();
            if (lic.isEmpty()) { vResult.setText("请输入注册码"); vResult.setForeground(ACCENT_WARN); return; }
            if (mc.isEmpty()) { mc = MachineCodeGenerator.generate(); vMc.setText(mc); }

            LicenseResult result;
            if (vRsaRadio.isSelected()) {
                // RSA 模式验证
                String priPath = vRsaPri.getText().trim();
                if (priPath.isEmpty() || !new File(priPath).exists()) {
                    vResult.setText("RSA 模式需要提供私钥文件验证");
                    vResult.setForeground(ACCENT_WARN);
                    return;
                }
                try {
                    java.security.PrivateKey pk = RSACrypto.loadPrivateKey(Paths.get(priPath));
                    result = LicenseGenerator.verifyRsa(lic, mc, pk);
                } catch (Exception ex) {
                    result = LicenseResult.fail("RSA 验证失败: " + ex.getMessage());
                }
            } else {
                // HMAC 模式验证
                String key = vSecret.getText().trim();
                result = LicenseGenerator.verify(lic, mc, key.isEmpty() ? null : key);
            }
            vResult.setText(result.toString());
            vResult.setForeground(result.isValid() ? ACCENT_OK : ACCENT_ERR);
        });
    }
}
