package com.jarprotect.cli.gui;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Midnight Forge 设计系统 — 深色安全工具主题。
 */
public class GuiUtils {

    // ═══════════════ 色 彩 ═══════════════
    static final Color BG_DEEP       = new Color(0x0D, 0x11, 0x17);
    static final Color BG_CARD       = new Color(0x14, 0x1A, 0x22);
    static final Color BG_INPUT      = new Color(0x1B, 0x23, 0x2E);
    static final Color BG_HOVER      = new Color(0x1F, 0x2A, 0x38);
    static final Color BORDER_DIM    = new Color(0x25, 0x30, 0x3D);
    static final Color BORDER_ACTIVE = new Color(0x00, 0xD4, 0xAA, 80);

    static final Color ACCENT        = new Color(0x00, 0xD4, 0xAA);  // 电子青
    static final Color ACCENT_GLOW   = new Color(0x00, 0xD4, 0xAA, 40);
    static final Color ACCENT_WARN   = new Color(0xFF, 0xA8, 0x4C);  // 琥珀
    static final Color ACCENT_ERR    = new Color(0xFF, 0x5C, 0x5C);  // 珊瑚红
    static final Color ACCENT_OK     = new Color(0x4C, 0xE6, 0xA6);  // 薄荷绿

    static final Color TEXT_PRIMARY   = new Color(0xE8, 0xEC, 0xF1);
    static final Color TEXT_SECONDARY = new Color(0x8B, 0x95, 0xA5);
    static final Color TEXT_DIM       = new Color(0x5A, 0x63, 0x72);

    // ═══════════════ 字 体 ═══════════════
    static final Font FONT_HERO    = new Font("Consolas", Font.BOLD, 26);
    static final Font FONT_HEADING = new Font("Microsoft YaHei", Font.BOLD, 15);
    static final Font FONT_BODY    = new Font("Microsoft YaHei", Font.PLAIN, 13);
    static final Font FONT_SMALL   = new Font("Microsoft YaHei", Font.PLAIN, 11);
    static final Font FONT_INPUT   = new Font("Consolas", Font.PLAIN, 14);
    static final Font FONT_CODE    = new Font("Consolas", Font.BOLD, 18);
    static final Font FONT_LOG     = new Font("Microsoft YaHei", Font.PLAIN, 12);
    static final Font FONT_BTN     = new Font("Microsoft YaHei", Font.BOLD, 13);

    static final int GAP = 12;

    // ═══════════════ 创建组件 ═══════════════

    static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_BODY);
        l.setForeground(TEXT_SECONDARY);
        return l;
    }

    static JLabel heading(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_HEADING);
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    static JTextField textField() {
        JTextField f = new JTextField();
        f.setFont(FONT_INPUT);
        f.setCaretColor(ACCENT);
        return f;
    }

    static JPasswordField passwordField() {
        JPasswordField f = new JPasswordField();
        f.setFont(FONT_INPUT);
        f.setCaretColor(ACCENT);
        return f;
    }

    static JTextField codeDisplay() {
        JTextField f = new JTextField();
        f.setFont(FONT_HERO);
        f.setHorizontalAlignment(JTextField.CENTER);
        f.setEditable(false);
        f.setForeground(ACCENT);
        f.setBackground(BG_DEEP);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return f;
    }

    static JButton primaryBtn(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(ACCENT.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(ACCENT.brighter());
                } else {
                    g2.setColor(ACCENT);
                }
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BTN);
        btn.setForeground(BG_DEEP);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 32, 36));
        return btn;
    }

    static JButton ghostBtn(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BTN);
        btn.setForeground(TEXT_SECONDARY);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_DIM, 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        return btn;
    }

    static JTextArea logArea(int rows) {
        JTextArea a = new JTextArea(rows, 50);
        a.setFont(FONT_LOG);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setForeground(ACCENT_OK);
        a.setBackground(BG_DEEP);
        a.setCaretColor(ACCENT);
        a.setMargin(new Insets(8, 10, 8, 10));
        return a;
    }

    static JCheckBox checkBox(String text) {
        JCheckBox cb = new JCheckBox(text);
        cb.setFont(FONT_BODY);
        cb.setForeground(TEXT_SECONDARY);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        return cb;
    }

    // ═══════════════ 卡片面板 ═══════════════

    static JPanel card(String title) {
        JPanel p = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 12, 12));
                g2.setColor(BORDER_DIM);
                g2.draw(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
                g2.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        return p;
    }

    // ═══════════════ 表单行 ═══════════════

    static void addRow(JPanel panel, GridBagConstraints gbc, int row,
                       String labelText, JComponent comp) {
        gbc.gridx = 0; gbc.gridy = row;
        gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.EAST;
        gbc.insets = new Insets(0, 0, GAP, GAP);
        panel.add(label(labelText), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(0, 0, GAP, 0);
        panel.add(comp, gbc);
    }

    // ═══════════════ 文件选择行 ═══════════════

    static JPanel fileRow(JTextField field, boolean save) {
        return fileRow(field, save, "JAR/WAR", "jar", "war");
    }

    static JPanel fileRow(JTextField field, boolean save, String desc, String... exts) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JButton browse = ghostBtn("...");
        browse.setPreferredSize(new Dimension(44, 30));
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(desc, exts));
            String cur = field.getText().trim();
            if (!cur.isEmpty()) {
                File f = new File(cur);
                if (f.getParentFile() != null && f.getParentFile().exists())
                    fc.setCurrentDirectory(f.getParentFile());
            }
            int r = save ? fc.showSaveDialog(row) : fc.showOpenDialog(row);
            if (r == JFileChooser.APPROVE_OPTION)
                field.setText(fc.getSelectedFile().getAbsolutePath());
        });
        row.add(field, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);
        return row;
    }

    // ═══════════════ 辅助方法 ═══════════════

    static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
    }

    static PrintStream redirectStream(JTextArea area) {
        OutputStream dummy = new OutputStream() {
            @Override public void write(int b) {}
        };
        try {
            return new PrintStream(dummy, true, "UTF-8") {
                private void appendText(String s) {
                    SwingUtilities.invokeLater(() -> {
                        area.append(s);
                        area.setCaretPosition(area.getDocument().getLength());
                    });
                }
                @Override public void print(String s)   { appendText(s == null ? "null" : s); }
                @Override public void println(String s)  { appendText((s == null ? "null" : s) + "\n"); }
                @Override public void print(Object obj)  { print(String.valueOf(obj)); }
                @Override public void println(Object obj){ println(String.valueOf(obj)); }
                @Override public void println()          { appendText("\n"); }
                @Override public void print(boolean b)   { print(String.valueOf(b)); }
                @Override public void println(boolean b) { println(String.valueOf(b)); }
                @Override public void print(char c)      { appendText(String.valueOf(c)); }
                @Override public void println(char c)    { appendText(c + "\n"); }
                @Override public void print(int i)       { print(String.valueOf(i)); }
                @Override public void println(int i)     { println(String.valueOf(i)); }
                @Override public void print(long l)      { print(String.valueOf(l)); }
                @Override public void println(long l)    { println(String.valueOf(l)); }
                @Override public void write(byte[] b, int off, int len) {
                    appendText(new String(b, off, len, StandardCharsets.UTF_8));
                }
                @Override public void write(int b) { appendText(String.valueOf((char) b)); }
            };
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    static void runAsync(JButton btn, JTextArea log, Runnable task) {
        btn.setEnabled(false);
        log.setText("");
        new Thread(() -> {
            PrintStream oOut = System.out, oErr = System.err;
            PrintStream redir = redirectStream(log);
            System.setOut(redir); System.setErr(redir);
            try { task.run(); }
            catch (Exception e) { System.err.println("ERROR: " + e.getMessage()); e.printStackTrace(); }
            finally { System.setOut(oOut); System.setErr(oErr); SwingUtilities.invokeLater(() -> btn.setEnabled(true)); }
        }).start();
    }

    static JPanel rowWithBtn(JTextField field, JButton btn) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.add(field, BorderLayout.CENTER);
        row.add(btn, BorderLayout.EAST);
        return row;
    }
}
