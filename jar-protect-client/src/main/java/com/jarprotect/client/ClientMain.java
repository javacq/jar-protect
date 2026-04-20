package com.jarprotect.client;

import com.formdev.flatlaf.FlatDarkLaf;
import com.jarprotect.core.machine.MachineCodeGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;

/**
 * 机器码获取工具 - 客户端独立程序。
 * Midnight Forge 深色主题。
 */
public class ClientMain {

    private static final Color BG       = new Color(0x0D, 0x11, 0x17);
    private static final Color BG_CARD  = new Color(0x14, 0x1A, 0x22);
    private static final Color BORDER   = new Color(0x25, 0x30, 0x3D);
    private static final Color ACCENT   = new Color(0x00, 0xD4, 0xAA);
    private static final Color TXT      = new Color(0xE8, 0xEC, 0xF1);
    private static final Color TXT_DIM  = new Color(0x8B, 0x95, 0xA5);

    public static void main(String[] args) {
        if (args.length > 0 && ("--cli".equals(args[0]) || "-c".equals(args[0]))) {
            System.out.println("机器码: " + MachineCodeGenerator.generate());
            System.out.println(MachineCodeGenerator.getHardwareDetails());
            return;
        }

        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Panel.background", BG);
            UIManager.put("TextField.background", new Color(0x1B, 0x23, 0x2E));
            UIManager.put("TextField.foreground", TXT);
            UIManager.put("TextArea.background", BG);
            UIManager.put("TextArea.foreground", new Color(0x4C, 0xE6, 0xA6));
            UIManager.put("Component.borderColor", BORDER);
            UIManager.put("Component.focusColor", ACCENT);
            UIManager.put("ScrollBar.thumb", BORDER);
            UIManager.put("ScrollBar.track", BG_CARD);
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }

        SwingUtilities.invokeLater(ClientMain::buildUI);
    }

    private static void buildUI() {
        JFrame frame = new JFrame("机器码获取工具");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(560, 500);
        frame.setMinimumSize(new Dimension(460, 400));
        frame.setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(28, 32, 24, 32));

        // ── 标题 ──
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel("JP") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT);
                g2.fill(new RoundRectangle2D.Float(0, 2, 36, 36, 8, 8));
                g2.setColor(BG);
                g2.setFont(new Font("Consolas", Font.BOLD, 17));
                g2.drawString("JP", 6, 26);
                g2.dispose();
            }
            @Override public Dimension getPreferredSize() { return new Dimension(36, 40); }
        };
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("机器码获取工具");
        title.setFont(new Font("Microsoft YaHei", Font.BOLD, 20));
        title.setForeground(TXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("请将下方机器码发送给软件提供商");
        subtitle.setFont(new Font("Microsoft YaHei", Font.PLAIN, 12));
        subtitle.setForeground(TXT_DIM);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(icon);
        header.add(Box.createVerticalStrut(8));
        header.add(title);
        header.add(Box.createVerticalStrut(4));
        header.add(subtitle);
        header.add(Box.createVerticalStrut(20));

        // ── 机器码 ──
        JTextField codeField = new JTextField();
        codeField.setFont(new Font("Consolas", Font.BOLD, 26));
        codeField.setHorizontalAlignment(JTextField.CENTER);
        codeField.setEditable(false);
        codeField.setForeground(ACCENT);
        codeField.setBackground(BG);
        codeField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        // ── 按钮 ──
        JButton genBtn = new JButton("生成机器码") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? ACCENT.darker() :
                        getModel().isRollover() ? ACCENT.brighter() : ACCENT);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
                super.paintComponent(g);
            }
        };
        genBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        genBtn.setForeground(BG);
        genBtn.setContentAreaFilled(false);
        genBtn.setBorderPainted(false);
        genBtn.setFocusPainted(false);
        genBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        genBtn.setPreferredSize(new Dimension(140, 36));

        JButton copyBtn = new JButton("复制到剪贴板");
        copyBtn.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        copyBtn.setForeground(TXT_DIM);
        copyBtn.setContentAreaFilled(false);
        copyBtn.setFocusPainted(false);
        copyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(6, 16, 6, 16)));
        copyBtn.setEnabled(false);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(genBtn);
        btnPanel.add(copyBtn);

        // ── 硬件详情 ──
        JTextArea detailArea = new JTextArea(8, 40);
        detailArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        detailArea.setMargin(new Insets(8, 10, 8, 10));

        JLabel detailTitle = new JLabel("硬件详情");
        detailTitle.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        detailTitle.setForeground(TXT);

        JScrollPane detailScroll = new JScrollPane(detailArea);
        detailScroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));

        // ── 组装 ──
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        codeField.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        btnPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        detailTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailScroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        center.add(codeField);
        center.add(Box.createVerticalStrut(14));
        center.add(btnPanel);
        center.add(Box.createVerticalStrut(20));
        center.add(detailTitle);
        center.add(Box.createVerticalStrut(8));
        center.add(detailScroll);

        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        frame.setContentPane(root);

        // ── 事件 ──
        genBtn.addActionListener(e -> {
            genBtn.setEnabled(false); genBtn.setText("生成中...");
            new Thread(() -> {
                try {
                    String code = MachineCodeGenerator.generate();
                    String details = MachineCodeGenerator.getHardwareDetails();
                    SwingUtilities.invokeLater(() -> {
                        codeField.setText(code); detailArea.setText(details);
                        copyBtn.setEnabled(true); genBtn.setEnabled(true); genBtn.setText("生成机器码");
                    });
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(frame, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                        genBtn.setEnabled(true); genBtn.setText("生成机器码");
                    });
                }
            }).start();
        });

        copyBtn.addActionListener(e -> {
            String code = codeField.getText().trim();
            if (!code.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(code), null);
                copyBtn.setText("已复制!");
                Timer t = new Timer(1500, ev -> copyBtn.setText("复制到剪贴板"));
                t.setRepeats(false); t.start();
            }
        });

        frame.setVisible(true);
        genBtn.doClick();
    }
}
