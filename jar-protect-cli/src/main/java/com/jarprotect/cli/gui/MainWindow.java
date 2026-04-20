package com.jarprotect.cli.gui;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

import static com.jarprotect.cli.gui.GuiUtils.*;

/**
 * JAR-Protect 主窗口 — Midnight Forge 主题。
 * 侧边栏导航 + 内容区域布局。
 */
public class MainWindow extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private NavItem activeNav;

    private static final String[] NAV_KEYS   = {"machine", "license", "rsa", "encrypt", "decrypt"};
    private static final String[] NAV_LABELS = {"机器码", "注册码", "RSA 密钥", "加密 JAR", "解密 JAR"};
    private static final String[] NAV_DESCS  = {
            "生成 / 验证硬件指纹", "生成 / 验证授权码",
            "生成 RSA 密钥对", "加密保护 JAR 包", "解密还原 JAR 包"
    };

    public MainWindow() {
        setTitle("JAR-Protect");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(980, 680);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG_DEEP);

        // ========== 侧边栏 ==========
        JPanel sidebar = new JPanel();
        sidebar.setBackground(BG_CARD);
        sidebar.setPreferredSize(new Dimension(200, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_DIM));

        // Logo 区域
        JPanel logoPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 图标
                g2.setColor(ACCENT);
                g2.fill(new RoundRectangle2D.Float(20, 22, 32, 32, 8, 8));
                g2.setColor(BG_CARD);
                g2.setFont(new Font("Consolas", Font.BOLD, 18));
                g2.drawString("JP", 24, 44);
                // 标题
                g2.setColor(TEXT_PRIMARY);
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
                g2.drawString("JAR-Protect", 62, 37);
                // 版本
                g2.setColor(TEXT_DIM);
                g2.setFont(FONT_SMALL);
                g2.drawString("v1.0.0", 62, 52);
                g2.dispose();
            }
        };
        logoPanel.setOpaque(false);
        logoPanel.setPreferredSize(new Dimension(200, 76));
        logoPanel.setMaximumSize(new Dimension(200, 76));
        sidebar.add(logoPanel);
        sidebar.add(Box.createVerticalStrut(16));

        // 导航项
        NavItem[] navItems = new NavItem[NAV_KEYS.length];
        for (int i = 0; i < NAV_KEYS.length; i++) {
            navItems[i] = new NavItem(NAV_LABELS[i], NAV_DESCS[i], NAV_KEYS[i]);
            sidebar.add(navItems[i]);
        }
        sidebar.add(Box.createVerticalGlue());

        // 底部信息
        JLabel footerLabel = new JLabel("<html><center>AES-256-GCM<br>JDK 1.8+</center></html>");
        footerLabel.setFont(FONT_SMALL);
        footerLabel.setForeground(TEXT_DIM);
        footerLabel.setHorizontalAlignment(SwingConstants.CENTER);
        footerLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 16, 10));
        footerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(footerLabel);

        // ========== 内容区域 ==========
        contentPanel.setOpaque(false);
        contentPanel.add(new MachineCodePanel(), "machine");
        contentPanel.add(new LicensePanel(), "license");
        contentPanel.add(new RsaKeyPanel(), "rsa");
        contentPanel.add(new EncryptPanel(), "encrypt");
        contentPanel.add(new DecryptPanel(), "decrypt");

        root.add(sidebar, BorderLayout.WEST);
        root.add(contentPanel, BorderLayout.CENTER);
        setContentPane(root);

        // 默认选中第一项
        navItems[0].setActive(true);
        activeNav = navItems[0];
    }

    /**
     * 侧边栏导航项。
     */
    private class NavItem extends JPanel {
        private final String label;
        private final String desc;
        private final String key;
        private boolean active = false;
        private boolean hovered = false;

        NavItem(String label, String desc, String key) {
            this.label = label;
            this.desc = desc;
            this.key = key;
            setOpaque(false);
            setMaximumSize(new Dimension(200, 56));
            setPreferredSize(new Dimension(200, 56));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hovered = true; repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hovered = false; repaint(); }
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (activeNav != null) activeNav.setActive(false);
                    setActive(true);
                    activeNav = NavItem.this;
                    cardLayout.show(contentPanel, key);
                }
            });
        }

        void setActive(boolean b) { this.active = b; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            if (active) {
                g2.setColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 18));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ACCENT);
                g2.fillRect(0, 0, 3, getHeight());
            } else if (hovered) {
                g2.setColor(BG_HOVER);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            // 标签
            g2.setFont(FONT_HEADING);
            g2.setColor(active ? ACCENT : TEXT_PRIMARY);
            g2.drawString(label, 20, 26);

            // 描述
            g2.setFont(FONT_SMALL);
            g2.setColor(active ? new Color(0x00, 0xD4, 0xAA, 150) : TEXT_DIM);
            g2.drawString(desc, 20, 44);

            g2.dispose();
        }
    }

    /**
     * 启动 GUI。
     */
    public static void launch() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("Component.focusColor", ACCENT);
            UIManager.put("Component.borderColor", BORDER_DIM);
            UIManager.put("TextField.background", BG_INPUT);
            UIManager.put("TextField.foreground", TEXT_PRIMARY);
            UIManager.put("PasswordField.background", BG_INPUT);
            UIManager.put("PasswordField.foreground", TEXT_PRIMARY);
            UIManager.put("TextArea.background", BG_DEEP);
            UIManager.put("TextArea.foreground", ACCENT_OK);
            UIManager.put("ScrollBar.thumb", BORDER_DIM);
            UIManager.put("ScrollBar.track", BG_CARD);
            UIManager.put("Panel.background", BG_DEEP);
            UIManager.put("CheckBox.icon.focusColor", ACCENT);
        } catch (Exception e) {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
        }

        SwingUtilities.invokeLater(() -> {
            MainWindow w = new MainWindow();
            w.setVisible(true);
        });
    }
}
