package com.jarprotect.cli.gui;

import com.jarprotect.core.machine.MachineCodeGenerator;

import javax.swing.*;
import java.awt.*;

import static com.jarprotect.cli.gui.GuiUtils.*;

/**
 * 机器码面板。
 */
public class MachineCodePanel extends JPanel {

    private final JTextField codeField;
    private final JTextArea detailArea;

    public MachineCodePanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // ── 标题 ──
        JLabel title = heading("硬件指纹 · 机器码");
        title.setAlignmentX(LEFT_ALIGNMENT);
        JLabel desc = label("基于 MAC / CPU / 主板 / 硬盘 序列号生成唯一标识");
        desc.setAlignmentX(LEFT_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(4));
        content.add(desc);
        content.add(Box.createVerticalStrut(24));

        // ── 机器码显示 ──
        codeField = codeDisplay();
        codeField.setAlignmentX(LEFT_ALIGNMENT);
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        content.add(codeField);
        content.add(Box.createVerticalStrut(16));

        // ── 按钮行 ──
        JButton genBtn = primaryBtn("生成机器码");
        JButton copyBtn = ghostBtn("复制到剪贴板");
        copyBtn.setEnabled(false);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        btnRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnRow.add(genBtn);
        btnRow.add(copyBtn);
        content.add(btnRow);
        content.add(Box.createVerticalStrut(20));

        // ── 分割线 ──
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_DIM);
        sep.setAlignmentX(LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        content.add(sep);
        content.add(Box.createVerticalStrut(16));

        // ── 验证区域 ──
        JLabel verifyTitle = heading("验证机器码");
        verifyTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(verifyTitle);
        content.add(Box.createVerticalStrut(12));

        JTextField verifyField = textField();
        verifyField.setAlignmentX(LEFT_ALIGNMENT);
        verifyField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JButton verifyBtn = primaryBtn("验证");
        JLabel verifyResult = label(" ");

        JPanel verifyRow = new JPanel(new BorderLayout(10, 0));
        verifyRow.setOpaque(false);
        verifyRow.setAlignmentX(LEFT_ALIGNMENT);
        verifyRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        verifyRow.add(verifyField, BorderLayout.CENTER);
        verifyRow.add(verifyBtn, BorderLayout.EAST);
        content.add(verifyRow);
        content.add(Box.createVerticalStrut(8));
        verifyResult.setAlignmentX(LEFT_ALIGNMENT);
        content.add(verifyResult);
        content.add(Box.createVerticalStrut(20));

        // ── 硬件详情 ──
        JLabel detailTitle = heading("硬件详情");
        detailTitle.setAlignmentX(LEFT_ALIGNMENT);
        content.add(detailTitle);
        content.add(Box.createVerticalStrut(8));
        detailArea = logArea(10);
        JScrollPane scroll = new JScrollPane(detailArea);
        scroll.setAlignmentX(LEFT_ALIGNMENT);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_DIM, 1));
        content.add(scroll);

        add(new JScrollPane(content, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {{
            setBorder(null); getViewport().setOpaque(false); setOpaque(false);
        }}, BorderLayout.CENTER);

        // ── 事件 ──
        genBtn.addActionListener(e -> {
            genBtn.setEnabled(false);
            genBtn.setText("生成中...");
            new Thread(() -> {
                try {
                    String code = MachineCodeGenerator.generate();
                    String details = MachineCodeGenerator.getHardwareDetails();
                    SwingUtilities.invokeLater(() -> {
                        codeField.setText(code);
                        detailArea.setText(details);
                        copyBtn.setEnabled(true);
                        genBtn.setEnabled(true);
                        genBtn.setText("生成机器码");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "失败: " + ex.getMessage(),
                                "错误", JOptionPane.ERROR_MESSAGE);
                        genBtn.setEnabled(true); genBtn.setText("生成机器码");
                    });
                }
            }).start();
        });

        copyBtn.addActionListener(e -> {
            String c = codeField.getText().trim();
            if (!c.isEmpty()) {
                copyToClipboard(c);
                copyBtn.setText("已复制!");
                Timer t = new Timer(1500, ev -> copyBtn.setText("复制到剪贴板"));
                t.setRepeats(false); t.start();
            }
        });

        verifyBtn.addActionListener(e -> {
            String c = verifyField.getText().trim();
            if (c.isEmpty()) { verifyResult.setText("请输入机器码"); verifyResult.setForeground(ACCENT_WARN); return; }
            boolean ok = MachineCodeGenerator.verify(c);
            if (ok) {
                verifyResult.setText("匹配当前设备");
                verifyResult.setForeground(ACCENT_OK);
            } else {
                verifyResult.setText("不匹配（当前: " + MachineCodeGenerator.generate() + "）");
                verifyResult.setForeground(ACCENT_ERR);
            }
        });
    }
}
