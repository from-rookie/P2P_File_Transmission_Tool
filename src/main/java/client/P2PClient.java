package client;

import model.Message;
import model.MessageType;
import util.NetworkUtil;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.geom.RoundRectangle2D;
import java.awt.*;
import java.io.File;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.dnd.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Vector;

public class P2PClient extends JFrame {
    // 自定义圆角按钮类
    private static class RoundedButton extends JButton {
        private Color backgroundColor;
        private int cornerRadius;
        
        public RoundedButton(String text) {
            super(text);
            this.backgroundColor = new Color(30, 144, 255); 
            this.cornerRadius = 10; // 圆角半径
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
        }
        
        public RoundedButton(String text, Color backgroundColor) {
            this(text);
            this.backgroundColor = backgroundColor;
        }
        
        public RoundedButton(String text, Color backgroundColor, int cornerRadius) {
            this(text, backgroundColor);
            this.cornerRadius = cornerRadius;
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 绘制圆角矩形背景
            g2.setColor(backgroundColor);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), cornerRadius, cornerRadius);
            
            // 绘制文字
            super.paintComponent(g2);
            
            g2.dispose();
        }
        
        @Override
        protected void paintBorder(Graphics g) {
            // 不绘制边框
        }
        
        @Override
        public void setContentAreaFilled(boolean b) {
            
        }
    }
    private static String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 8080;
    
    // 带备注的历史服务器IP
    private static Map<String, String> serverIPRemarks = new HashMap<>();
    private static String defaultServerIP = null;
    
    // 登录凭据历史
    private static Map<String, String> loginCredentials = new HashMap<>();
    private static boolean autoLoginEnabled = false;
    private static String defaultLoginUsername = null;
    
    // 好友备注
    private static Map<String, String> friendRemarks = new HashMap<>();
    
    // 状态栏引用
    private JLabel statusBar;
    
    static {
        loadServerHistory();
        loadLoginHistory();
        loadFriendRemarks();
        // 如果设置了默认IP，则使用它
        if (defaultServerIP != null && !defaultServerIP.trim().isEmpty()) {
            SERVER_HOST = defaultServerIP.trim();
        }
        // 否则，保持SERVER_HOST为默认值（localhost），并让用户在UI中更改它
    }
    
    /**
     * 从文件加载服务器IP历史
     */
    private static void loadServerHistory() {
        try {
            File historyFile = new File("server_history.properties");
            if (historyFile.exists()) {
                Properties props = new Properties();
                props.load(new FileInputStream(historyFile));
                
                // 加载带备注的服务器IP
                for (String key : props.stringPropertyNames()) {
                    if (key.startsWith("server.")) {
                        String ip = key.substring(7); // Remove "server." prefix
                        String remark = props.getProperty(key);
                        serverIPRemarks.put(ip, remark);
                    } else if (key.equals("default")) {
                        defaultServerIP = props.getProperty(key);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading server history: " + e.getMessage());
        }
    }
    
    /**
     * 将服务器IP历史保存到文件
     */
    private static void saveServerHistory() {
        try {
            Properties props = new Properties();
            
            // 保存带备注的服务器IP
            for (Map.Entry<String, String> entry : serverIPRemarks.entrySet()) {
                props.setProperty("server." + entry.getKey(), entry.getValue());
            }
            
            // 保存默认服务器IP
            if (defaultServerIP != null) {
                props.setProperty("default", defaultServerIP);
            }
            
            props.store(new FileOutputStream("server_history.properties"), "Server IP History");
        } catch (Exception e) {
            System.err.println("Error saving server history: " + e.getMessage());
        }
    }
    
    /**
     * 从文件加载登录凭据历史
     */
    private static void loadLoginHistory() {
        try {
            File historyFile = new File("login_history.properties");
            if (historyFile.exists()) {
                Properties props = new Properties();
                props.load(new FileInputStream(historyFile));
                
                // 加载凭据（排除特殊键）
                for (String key : props.stringPropertyNames()) {
                    if (!key.equals("auto_login") && !key.equals("default_username")) {
                        loginCredentials.put(key, props.getProperty(key));
                    }
                }
                
                // 检查是否启用了自动登录
                autoLoginEnabled = "true".equals(props.getProperty("auto_login", "false"));
                
                // 加载默认登录用户名
                defaultLoginUsername = props.getProperty("default_username");
            }
        } catch (Exception e) {
            System.err.println("Error loading login history: " + e.getMessage());
        }
    }
    
    /**
     * 将登录凭据历史保存到文件
     */
    private static void saveLoginHistory() {
        try {
            Properties props = new Properties();
            
            // 保存凭据
            for (Map.Entry<String, String> entry : loginCredentials.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue());
            }
            
            // 保存自动登录设置
            props.setProperty("auto_login", String.valueOf(autoLoginEnabled));
            
            // 保存默认登录用户名
            if (defaultLoginUsername != null) {
                props.setProperty("default_username", defaultLoginUsername);
            }
            
            props.store(new FileOutputStream("login_history.properties"), "Login Credentials History");
        } catch (Exception e) {
            System.err.println("Error saving login history: " + e.getMessage());
        }
    }
    
    /**
     * 显示对话框以从历史记录中选择服务器IP或输入新IP
     * @return 选择的服务器IP，如果取消则返回null
     */
    private static String selectServerIP() {
        // 创建对话框
        JDialog dialog = new JDialog((JFrame)null, "Select Server IP", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 400);
        dialog.setLocationRelativeTo(null);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        
        // 创建IP选择面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // 创建服务器IP列表
        DefaultListModel<String> listModel = new DefaultListModel<>();
        
        // 加入默认IP
        listModel.addElement("[Enter New IP Address]");
        
        // 加入历史IP
        for (Map.Entry<String, String> entry : serverIPRemarks.entrySet()) {
            String displayText = entry.getKey();
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                displayText += " (" + entry.getValue() + ")";
            }
            listModel.addElement(displayText);
        }
        
        JList<String> ipList = new JList<>(listModel);
        ipList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(ipList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Select Server IP"));
        
        // 加输入框
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("New Server IP"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Server IP:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField ipField = new JTextField(20);
        inputPanel.add(ipField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Remark:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField remarkField = new JTextField(20);
        inputPanel.add(remarkField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2;
        JButton addIPButton = new JButton("Add to History");
        inputPanel.add(addIPButton, gbc);
        
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JCheckBox defaultCheckBox = new JCheckBox("Set as default server");
        inputPanel.add(defaultCheckBox, gbc);
        
        // 创建按钮
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton connectButton = new JButton("Connect");
        JButton cancelButton = new JButton("Cancel");
        
        // 结果变量
        final String[] result = {null};
        
        // 添加IP到历史记录按钮操作
        addIPButton.addActionListener(e -> {
            String ip = ipField.getText().trim();
            if (!ip.isEmpty()) {
                String remark = remarkField.getText().trim();
                serverIPRemarks.put(ip, remark);
                saveServerHistory();
                
                // 刷新IP列表
                listModel.clear();
                listModel.addElement("[Enter New IP Address]");
                for (Map.Entry<String, String> entry : serverIPRemarks.entrySet()) {
                    String displayText = entry.getKey();
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        displayText += " (" + entry.getValue() + ")";
                    }
                    listModel.addElement(displayText);
                }
                
                JOptionPane.showMessageDialog(dialog, "IP address added to history successfully.");
            } else {
                JOptionPane.showMessageDialog(dialog, "Please enter a valid IP address.", "Invalid IP", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        connectButton.addActionListener(e -> { // 连接按钮操作  
            String selectedIP = null;
            
            // 检查是否从列表中选择了一个IP
            if (ipList.getSelectedIndex() >= 0) {
                String selectedText = ipList.getSelectedValue();
                
                // 检查是否选择了“Enter New IP”选项
                if (selectedText.equals("[Enter New IP Address]")) {
                    selectedIP = ipField.getText().trim();
                    
                    // 保存提供过的IP
                    if (!selectedIP.isEmpty()) {
                        String remark = remarkField.getText().trim();
                        serverIPRemarks.put(selectedIP, remark);
                        
                        // 如果复选框被选中，则设置为默认服务器
                        if (defaultCheckBox.isSelected()) {
                            defaultServerIP = selectedIP;
                        }
                        
                        // 保存历史记录
                        saveServerHistory();
                    }
                } else {
                    //      提取IP地址（如果显示文本包含括号）          
                    if (selectedText.contains(" (")) {
                        selectedIP = selectedText.substring(0, selectedText.indexOf(" ("));
                    } else {
                        selectedIP = selectedText;
                    }
                    
                    // 如果复选框被选中，则设置为默认服务器
                    if (defaultCheckBox.isSelected()) {
                        defaultServerIP = selectedIP;
                        saveServerHistory();
                    }
                }
            } else {
                // 使用手动输入的IP如果未选择项目但IP字段有内容
                String ipFromField = ipField.getText().trim();
                if (!ipFromField.isEmpty()) {
                    selectedIP = ipFromField;
                    
                    // 保存到历史记录
                    String remark = remarkField.getText().trim();
                    serverIPRemarks.put(selectedIP, remark);
                    
                    // 如果复选框被选中，则设置为默认服务器
                    if (defaultCheckBox.isSelected()) {
                        defaultServerIP = selectedIP;
                    }
                    
                    // 保存历史记录
                    saveServerHistory();
                }
            }
            
            if (selectedIP != null && !selectedIP.isEmpty()) {
                result[0] = selectedIP;
                dialog.dispose();
            } else {
                // 显示错误消息如果没有提供IP
                JOptionPane.showMessageDialog(dialog, "Please enter or select a valid IP address.", "Invalid IP", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        cancelButton.addActionListener(e -> {
            dialog.dispose();
        });
        
        buttonPanel.add(connectButton);
        buttonPanel.add(cancelButton);
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
        
        return result[0];
    }
    
    // 客户端IP地址存储映射
    private Map<String, String> clientIPs = new HashMap<>();
    
        /**
         * 接收客户端IP
         * @param username 
         * @return 客户端IP
         */
    private String getClientIP(String username) {
        return clientIPs.get(username);
    }
    
    /**
     * 设置客户端IP
     * @param username 客户端的用户名
     * @param ipAddress 客户端的IP地址
     */
    private void setClientIP(String username, String ipAddress) {
        if (username != null && ipAddress != null) {
            clientIPs.put(username, ipAddress);
        }
    }
    
    private static String getServerHost() { // 获取服务器主机
        try {
            return NetworkUtil.getLocalIPAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }
    
    // 定义一个内部类来表示列表中的朋友项
    private static class FriendItem {
        private String username;
        private boolean online;
        
        public FriendItem(String username, boolean online) {
            this.username = username;
            this.online = online;
        }
        
        public String getUsername() {
            return username;
        }
        
        public boolean isOnline() {
            return online;
        }
        
        @Override
        public String toString() {
            // 检查是否有此朋友的备注
            String displayName = username;
            if (friendRemarks.containsKey(username)) {
                String remark = friendRemarks.get(username);
                if (remark != null && !remark.isEmpty()) {
                    displayName = remark + " (" + username + ")";
                }
            }
            return displayName + (online ? " [ONLINE]" : " [OFFLINE]");
        }
    }
    
    // 自定义列表单元渲染器
    private static class FriendListCellRenderer extends JPanel implements ListCellRenderer<FriendItem> {
        private JLabel nameLabel;
        
        public FriendListCellRenderer() {
            setLayout(new BorderLayout());
            
            nameLabel = new JLabel();
            nameLabel.setFont(new Font("Arial", Font.PLAIN, 14));
            
            // 创建按钮面板，固定标签以指示按钮位置
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
            JLabel selectLabel = new JLabel("[Select]");
            JLabel remarkLabel = new JLabel("[Remark]");
            JLabel removeLabel = new JLabel("[Remove]");
        
            selectLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 144, 255)), 
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
            ));
            selectLabel.setBackground(new Color(30, 144, 255)); 
            selectLabel.setForeground(Color.WHITE);
            selectLabel.setOpaque(true);
            selectLabel.setHorizontalAlignment(JLabel.CENTER);
            selectLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 144, 255)), 
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
            ));
            
            remarkLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 144, 255)), 
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
            ));
            remarkLabel.setBackground(new Color(30, 144, 255)); 
            remarkLabel.setForeground(Color.WHITE);
            remarkLabel.setOpaque(true);
            remarkLabel.setHorizontalAlignment(JLabel.CENTER);
            remarkLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 144, 255)), 
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
            ));
            
            removeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 144, 255)), 
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
            ));
            removeLabel.setBackground(new Color(30, 144, 255));
            removeLabel.setForeground(Color.WHITE);
            removeLabel.setOpaque(true);
            removeLabel.setHorizontalAlignment(JLabel.CENTER);
            removeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 144, 255)), 
                BorderFactory.createEmptyBorder(2, 5, 2, 5)
            ));
            
            selectLabel.setPreferredSize(new Dimension(70, 22));
            remarkLabel.setPreferredSize(new Dimension(70, 22));
            removeLabel.setPreferredSize(new Dimension(70, 22));
            
            buttonPanel.add(selectLabel);
            buttonPanel.add(remarkLabel);
            buttonPanel.add(removeLabel);
            
            add(nameLabel, BorderLayout.CENTER);
            add(buttonPanel, BorderLayout.EAST);
            
            setPreferredSize(new Dimension(300, 35));
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)));
        }
        
        @Override
        public Component getListCellRendererComponent(JList<? extends FriendItem> list,
                FriendItem value, int index, boolean isSelected, boolean cellHasFocus) {
            
            if (value != null) {
                nameLabel.setText(value.toString());
                
                // 根据在线状态设置颜色
                if (value.isOnline()) {
                    nameLabel.setForeground(Color.GREEN);
                } else {
                    nameLabel.setForeground(Color.GRAY);
                }
                
                // 根据选择设置背景颜色
                if (isSelected) {
                    setBackground(list.getSelectionBackground());
                    nameLabel.setForeground(list.getSelectionForeground());
                } else {
                    setBackground(list.getBackground());
                }
            }
            
            return this;
        }
    }
    
    private Socket socket;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    
    private String username;
    private boolean loggedIn = false;
    private transfer.FileTransferHandler fileTransferHandler;
    
    // GUI组件
    private JTextField usernameField;
    private JPasswordField passwordField;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton loginButton;
    private JButton registerButton;
    private JButton logoutButton;
    private JTextField friendField;
    private JButton addFriendButton;
    private JButton removeFriendButton;
    private JList<FriendItem> friendList;
    private DefaultListModel<FriendItem> friendListModel;
    private JTextField fileRecipientField;
    private JTextField filePathField;
    private JButton sendFileButton;
    private JTextField downloadPathField;
    private JButton browseDownloadButton;
    
    // 多文件传输支持
    private List<File> fileList;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileListView;
    
    public P2PClient() {
        // 初始化文件传输处理程序，默认下载路径
        fileTransferHandler = new transfer.FileTransferHandler();
        
        // 初始化多文件传输支持
        fileList = new ArrayList<>();
        fileListModel = new DefaultListModel<>();
        
        initializeGUI(); // 初始化GUI
        connectToServer(); // 连接到服务器
    }
    
    private void initializeGUI() {
        setTitle("P2P File Transfer Client");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        
        // 设置应用程序外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Use default look and feel if system L&F is not available
        }
        
        // 设置颜色方案
        getContentPane().setBackground(Color.WHITE);
        
        // 处理窗口关闭
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
                // 保存记录
                saveServerHistory();
                saveLoginHistory();
                saveFriendRemarks();
                System.exit(0);
            }
        });
        
        // 主选项卡面板，改进样式
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Arial", Font.PLAIN, 12));
        
        // 认证选项卡
        JPanel authPanel = createAuthPanel();
        tabbedPane.addTab("Authentication", authPanel);
        
        // 朋友选项卡   
        JPanel friendsPanel = createFriendsPanel();
        tabbedPane.addTab("Friends", friendsPanel);
        
        // 文件传输选项卡
        JPanel fileTransferPanel = createFileTransferPanel();
        tabbedPane.addTab("File Transfer", fileTransferPanel);
        
        // 消息/日志选项卡
        JPanel messagesPanel = createMessagesPanel();
        tabbedPane.addTab("Messages", messagesPanel);
        
        // 设置选项卡   
        JPanel settingsPanel = createSettingsPanel();
        tabbedPane.addTab("Settings", settingsPanel);
        
        add(tabbedPane, BorderLayout.CENTER);
        
        // 扩展状态栏
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createLoweredBevelBorder());
        
        statusBar = new JLabel("Not connected");
        statusBar.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        statusBar.setFont(new Font("Arial", Font.PLAIN, 11));
        
        JLabel versionLabel = new JLabel("v1.9");
        versionLabel.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
        versionLabel.setHorizontalAlignment(JLabel.RIGHT);
        versionLabel.setFont(new Font("Arial", Font.PLAIN, 11));
        
        statusPanel.add(statusBar, BorderLayout.CENTER);
        statusPanel.add(versionLabel, BorderLayout.EAST);
        
        add(statusPanel, BorderLayout.SOUTH);
        
        setVisible(true);
    }
    
    private JPanel createAuthPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.setBackground(Color.WHITE);
        
        // Title panel
        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        titlePanel.setBackground(Color.WHITE);
        JLabel titleLabel = new JLabel("User Authentication");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(new Color(0, 102, 204)); // Blue color
        titlePanel.add(titleLabel);
        
        // 中心表单面板 
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Login Credentials"
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        formPanel.setBackground(Color.WHITE);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // 用户名
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        usernameField = new JTextField(20);
        usernameField.setPreferredSize(new Dimension(200, 25));
        formPanel.add(usernameField, gbc);
        
        // 密码
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        formPanel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        passwordField = new JPasswordField(20);
        passwordField.setPreferredSize(new Dimension(200, 25));
        formPanel.add(passwordField, gbc);
        
        // 自动登录复选框 
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JCheckBox autoLoginCheckBox = new JCheckBox("Auto-login with saved credentials");
        autoLoginCheckBox.setSelected(autoLoginEnabled);
        formPanel.add(autoLoginCheckBox, gbc);
        
        // 默认凭证管理
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.anchor = GridBagConstraints.WEST;
        formPanel.add(new JLabel("Default Credentials:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.WEST;
        JButton manageCredentialsButton = new JButton("Manage Saved Credentials");
        manageCredentialsButton.addActionListener(e -> {
            showCredentialManager();
        });
        manageCredentialsButton.setPreferredSize(new Dimension(200, 25));
        formPanel.add(manageCredentialsButton, gbc);
        
        // 按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        loginButton = new RoundedButton("Login");
        registerButton = new RoundedButton("Register");
        logoutButton = new RoundedButton("Logout");
        logoutButton.setEnabled(false);
        
        // 按钮样式
        Dimension buttonSize = new Dimension(110, 35);
        loginButton.setPreferredSize(buttonSize);
        loginButton.setForeground(Color.WHITE);
        
        registerButton.setPreferredSize(buttonSize);
        registerButton.setForeground(Color.WHITE);
        
        logoutButton.setPreferredSize(buttonSize);
        logoutButton.setForeground(Color.WHITE);
        
        buttonPanel.add(loginButton);
        buttonPanel.add(registerButton);
        buttonPanel.add(logoutButton);
        
        //  主布局
        panel.add(titlePanel, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 添加动作监听器
        loginButton.addActionListener(e -> {
            // 保存自动登录偏好
            autoLoginEnabled = autoLoginCheckBox.isSelected();
            login();
        });
        registerButton.addActionListener(e -> register());
        logoutButton.addActionListener(e -> logout());
        
        return panel;
    }
    
    private JPanel createFriendsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // 朋友管理面板，改进布局
        JPanel friendManagementPanel = new JPanel(new BorderLayout());
        friendManagementPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Manage Friends"
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        friendManagementPanel.setBackground(Color.WHITE);
        
        // 输入面板
        JPanel inputPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        inputPanel.add(new JLabel("Friend Username:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        friendField = new JTextField(20);
        friendField.setPreferredSize(new Dimension(150, 25));
        inputPanel.add(friendField, gbc);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        addFriendButton = new RoundedButton("Add Friend");
        addFriendButton.setEnabled(false);
        addFriendButton.setPreferredSize(new Dimension(100, 30));
        addFriendButton.setForeground(Color.WHITE);
        removeFriendButton = new RoundedButton("Remove Friend");
        removeFriendButton.setEnabled(false);
        removeFriendButton.setPreferredSize(new Dimension(120, 30));
        removeFriendButton.setForeground(Color.WHITE);
        
        buttonPanel.add(addFriendButton);
        buttonPanel.add(removeFriendButton);
        
        friendManagementPanel.add(inputPanel, BorderLayout.CENTER);
        friendManagementPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 朋友列表带按钮
        friendListModel = new DefaultListModel<>();
        friendList = new JList<>(friendListModel);
        friendList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        friendList.setCellRenderer(new FriendListCellRenderer());
        
        // 添加鼠标监听器以处理自定义渲染器中的按钮点击
        friendList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = friendList.locationToIndex(e.getPoint());
                if (index >= 0) {
                    FriendItem item = friendListModel.getElementAt(index);
                    String username = item.getUsername();
                    
                    // 获取单元格的边界
                    Rectangle cellBounds = friendList.getCellBounds(index, index);
                    if (cellBounds != null) {
                        int relX = e.getX() - cellBounds.x;
                        int relY = e.getY() - cellBounds.y;
                        
                        int cellWidth = cellBounds.width;
                        int cellHeight = cellBounds.height;
                        
                        if (relX > cellWidth - 250) {
                            int buttonAreaStart = cellWidth - 250;
                            int buttonWidth = 80;
                            int buttonSpacing = 5;
                            
                            int relativeButtonX = relX - buttonAreaStart;
                            
                            if (relativeButtonX < buttonWidth) {

                                setFileRecipient(username);
                            } else if (relativeButtonX < buttonWidth * 2 + buttonSpacing) {
                               
                                String currentRemark = friendRemarks.getOrDefault(username, "");
                                Object input = JOptionPane.showInputDialog(P2PClient.this, 
                                    "Enter remark for " + username + ":", "Friend Remark", 
                                    JOptionPane.QUESTION_MESSAGE, null, null, currentRemark);
                                if (input != null) {
                                    String newRemark = input.toString();
                                    setFriendRemark(username, newRemark);
                                }
                            } else {
                        
                                removeFriendByUsername(username);
                            }
                        }
                    }
                }
            }
        });
        
        JScrollPane friendListScrollPane = new JScrollPane(friendList);
        friendListScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Friend List"
            ),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        friendListScrollPane.setBackground(new Color(250, 250, 250));
        
        // 添加动作监听器
        addFriendButton.addActionListener(e -> sendFriendRequest());
        removeFriendButton.addActionListener(e -> removeFriend());
        
        panel.add(friendManagementPanel, BorderLayout.NORTH);
        panel.add(friendListScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createFileTransferPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // 文件选择面板，改进布局
        JPanel fileSelectionPanel = new JPanel(new BorderLayout());
        fileSelectionPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "File Transfer"
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        fileSelectionPanel.setBackground(Color.WHITE);
        
        // 添加拖放
        fileSelectionPanel.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        fileList.clear();
                        fileListModel.clear();
                        
                        // 添加所有拖放的文件到列表
                        for (File file : droppedFiles) {
                            fileList.add(file);
                            fileListModel.addElement(file.getName() + " (" + file.length() + " bytes)");
                        }
                        
                        // 为向后兼容性设置第一个文件路径在文本字段中
                        File firstFile = droppedFiles.get(0);
                        filePathField.setText(firstFile.getAbsolutePath());
                        
                        updateSendButtonState();
                        appendMessage("Multiple files selected via drag and drop: " + droppedFiles.size() + " files");
                    }
                    evt.dropComplete(true);
                } catch (Exception e) {
                    evt.dropComplete(false);
                    JOptionPane.showMessageDialog(P2PClient.this, "Error processing dropped file: " + e.getMessage(), "Drag & Drop Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        // 创建一个面板用于文件选择网格和拖放区域
        JPanel fileMainPanel = new JPanel(new BorderLayout(10, 10));
        fileMainPanel.setBackground(Color.WHITE);
        
        // 文件选择网格
        JPanel fileGridPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        
        // 选择文件
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        fileGridPanel.add(new JLabel("Selected File:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        filePathField = new JTextField(30);
        filePathField.setEditable(false);
        filePathField.setPreferredSize(new Dimension(200, 25));
        filePathField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateSendButtonState(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateSendButtonState(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateSendButtonState(); }
        });
        fileGridPanel.add(filePathField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JButton browseButton = new RoundedButton("Browse...");
        browseButton.setPreferredSize(new Dimension(100, 25));
        browseButton.setForeground(Color.WHITE);
        browseButton.addActionListener(e -> browseFile());
        fileGridPanel.add(browseButton, gbc);
        
        // 拖放区域
        JPanel dragDropPanel = new JPanel(new BorderLayout());
        dragDropPanel.setBackground(new Color(240, 248, 255)); // Light blue background
        dragDropPanel.setBorder(BorderFactory.createDashedBorder(new Color(30, 144, 255), 2, 5, 5, false));
        dragDropPanel.setPreferredSize(new Dimension(400, 100));
        
        JLabel dragDropLabel = new JLabel("Drag & Drop Files Here", JLabel.CENTER);
        dragDropLabel.setFont(new Font("Arial", Font.BOLD, 14));
        dragDropLabel.setForeground(new Color(30, 144, 255));
        dragDropPanel.add(dragDropLabel, BorderLayout.CENTER);
        
        // 添加拖放支持到dragDropPanel
        dragDropPanel.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!droppedFiles.isEmpty()) {
                        // 清除现有文件列表
                        fileList.clear();
                        fileListModel.clear();
                        
                        // 添加所有拖放的文件到列表
                        for (File file : droppedFiles) {
                            fileList.add(file);
                            fileListModel.addElement(file.getName() + " (" + file.length() + " bytes)");
                        }
                        
                        // 为向后兼容性设置第一个文件路径在文本字段中
                        File firstFile = droppedFiles.get(0);
                        filePathField.setText(firstFile.getAbsolutePath());
                        
                        updateSendButtonState();
                        appendMessage("Multiple files selected via drag and drop: " + droppedFiles.size() + " files");
                    }
                    evt.dropComplete(true);
                } catch (Exception e) {
                    evt.dropComplete(false);
                    JOptionPane.showMessageDialog(P2PClient.this, "Error processing dropped file: " + e.getMessage(), "Drag & Drop Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        
        fileMainPanel.add(fileGridPanel, BorderLayout.NORTH);
        fileMainPanel.add(dragDropPanel, BorderLayout.CENTER);
        
        // 添加文件列表面板
        JPanel fileListPanel = new JPanel(new BorderLayout());
        fileListPanel.setBorder(BorderFactory.createTitledBorder("Selected Files"));
        fileListPanel.setPreferredSize(new Dimension(400, 150));
        
        fileListView = new JList<>(fileListModel);
        fileListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane fileListScrollPane = new JScrollPane(fileListView);
        fileListPanel.add(fileListScrollPane, BorderLayout.CENTER);
        
        // 添加文件列表控件
        JPanel fileListControls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton removeFileButton = new RoundedButton("Remove Selected");
        removeFileButton.setPreferredSize(new Dimension(120, 25));
        removeFileButton.setForeground(Color.WHITE);
        removeFileButton.addActionListener(e -> removeSelectedFile());
        fileListControls.add(removeFileButton);
        
        JButton clearAllButton = new RoundedButton("Clear All");
        clearAllButton.setPreferredSize(new Dimension(100, 25));
        clearAllButton.setForeground(Color.WHITE);
        clearAllButton.addActionListener(e -> clearAllFiles());
        fileListControls.add(clearAllButton);
        
        fileListPanel.add(fileListControls, BorderLayout.SOUTH);
        
        fileMainPanel.add(fileListPanel, BorderLayout.SOUTH);
        
        // 选择目标
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        fileGridPanel.add(new JLabel("Recipient:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        fileRecipientField = new JTextField(30);
        fileRecipientField.setPreferredSize(new Dimension(200, 25));
        // 添加鼠标监听器以在点击时显示朋友选择对话框
        fileRecipientField.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showFriendSelectionDialog();
            }
        });
        fileGridPanel.add(fileRecipientField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        JButton selectFriendButton = new RoundedButton("Select...");
        selectFriendButton.setPreferredSize(new Dimension(100, 25));
        selectFriendButton.setForeground(Color.WHITE);
        selectFriendButton.addActionListener(e -> showFriendSelectionDialog());
        fileGridPanel.add(selectFriendButton, gbc);
        
        // 发送按钮
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 3; gbc.anchor = GridBagConstraints.CENTER;
        sendFileButton = new RoundedButton("Send File");
        sendFileButton.setPreferredSize(new Dimension(120, 35));
        sendFileButton.setFont(new Font("Arial", Font.BOLD, 12));
        sendFileButton.setForeground(Color.WHITE);
        sendFileButton.setEnabled(false);
        sendFileButton.addActionListener(e -> sendFile());
        fileGridPanel.add(sendFileButton, gbc);
        
        fileSelectionPanel.add(fileMainPanel, BorderLayout.CENTER);
        
        // 进度面板，改进样式
        JPanel progressPanel = new JPanel(new BorderLayout());
        progressPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Transfer Progress"
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        progressPanel.setBackground(Color.WHITE);
        
        JProgressBar progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(300, 25));
        progressPanel.add(progressBar, BorderLayout.CENTER);
        
        panel.add(fileSelectionPanel, BorderLayout.NORTH);
        panel.add(progressPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createMessagesPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(BorderFactory.createTitledBorder("Messages & Logs"));
        
        messageField = new JTextField();
        messageField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage();
            }
        });
        
        panel.add(chatScrollPane, BorderLayout.CENTER);
        panel.add(messageField, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createSettingsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        // 主设置容器
        JPanel settingsContainer = new JPanel(new GridLayout(2, 1, 10, 10));
        
        // 下载设置面板，改进布局
        JPanel downloadSettingsPanel = new JPanel(new BorderLayout());
        downloadSettingsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Download Settings"
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        downloadSettingsPanel.setBackground(Color.WHITE);
        
        // 下载设置网格
        JPanel downloadGridPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        downloadGridPanel.add(new JLabel("Download Location:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        downloadPathField = new JTextField(30);
        downloadPathField.setPreferredSize(new Dimension(200, 25));
        String defaultDownloadPath = System.getProperty("user.home") + File.separator + "Downloads";
        downloadPathField.setText(defaultDownloadPath);
        downloadPathField.setEditable(false);
        downloadGridPanel.add(downloadPathField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 0; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        browseDownloadButton = new RoundedButton("Browse...");
        browseDownloadButton.setPreferredSize(new Dimension(100, 25));
        browseDownloadButton.setForeground(Color.WHITE);
        browseDownloadButton.addActionListener(e -> browseDownloadFolder());
        downloadGridPanel.add(browseDownloadButton, gbc);
        
        downloadSettingsPanel.add(downloadGridPanel, BorderLayout.CENTER);
        
        // 服务器IP设置面板，改进布局
        JPanel serverSettingsPanel = new JPanel(new BorderLayout());
        serverSettingsPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)), 
                "Server IP Settings"
            ),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        serverSettingsPanel.setBackground(Color.WHITE);
        
        // 服务器设置网格
        JPanel serverGridPanel = new JPanel(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST;
        serverGridPanel.add(new JLabel("Current Server IP:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField currentServerIPField = new JTextField(SERVER_HOST);
        currentServerIPField.setPreferredSize(new Dimension(200, 25));
        currentServerIPField.setEditable(false);
        serverGridPanel.add(currentServerIPField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        serverGridPanel.add(new JLabel("Manage Server IPs:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.anchor = GridBagConstraints.WEST;
        JButton manageServerIPsButton = new RoundedButton("Manage Server IPs");
        manageServerIPsButton.setPreferredSize(new Dimension(150, 25));
        manageServerIPsButton.setForeground(Color.WHITE);
        manageServerIPsButton.addActionListener(e -> showServerIPManager());
        serverGridPanel.add(manageServerIPsButton, gbc);
        
        // Auto-connect option
        gbc.gridx = 0; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST;
        serverGridPanel.add(new JLabel("Auto-connect:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST;
        JCheckBox autoConnectCheckBox = new JCheckBox("Automatically use default IP");
        autoConnectCheckBox.setSelected(defaultServerIP != null && !defaultServerIP.isEmpty());
        serverGridPanel.add(autoConnectCheckBox, gbc);
        
        // IP switching option
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.WEST;
        serverGridPanel.add(new JLabel("Switch Connection IP:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.WEST;
        JButton switchIPButton = new RoundedButton("Change Server IP");
        switchIPButton.setPreferredSize(new Dimension(150, 25));
        switchIPButton.setForeground(Color.WHITE);
        switchIPButton.addActionListener(e -> {
            String newIP = selectServerIP();
            if (newIP != null && !newIP.isEmpty()) {
                // Update current server IP field
                currentServerIPField.setText(newIP);
                JOptionPane.showMessageDialog(panel, "Server IP changed to: " + newIP + "\nPlease restart the client to apply changes.");
            }
        });
        serverGridPanel.add(switchIPButton, gbc);
        
        serverSettingsPanel.add(serverGridPanel, BorderLayout.CENTER);
        
        settingsContainer.add(downloadSettingsPanel);
        settingsContainer.add(serverSettingsPanel);
        
        panel.add(settingsContainer, BorderLayout.NORTH);
        
        return panel;
    }
    
    /**
     * 显示对话框来管理服务器IP历史记录
     */
    private void showServerIPManager() {
        // 创建对话框
        JDialog dialog = new JDialog(this, "Manage Server IPs", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(600, 400);
        dialog.setLocationRelativeTo(this);
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // 创建服务器IP列表
        DefaultListModel<String> listModel = new DefaultListModel<>();
        
        // 添加历史IP
        for (Map.Entry<String, String> entry : serverIPRemarks.entrySet()) {
            String displayText = entry.getKey();
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                displayText += " (" + entry.getValue() + ")";
            }
            listModel.addElement(displayText);
        }
        
        JList<String> ipList = new JList<>(listModel);
        ipList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(ipList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Saved Server IPs"));
        
        // 创建输入字段
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Add/Edit Server IP"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Server IP:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField ipField = new JTextField(20);
        inputPanel.add(ipField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Remark:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField remarkField = new JTextField(20);
        inputPanel.add(remarkField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        inputPanel.add(new JLabel("Options:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 2; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JCheckBox defaultCheckBox = new JCheckBox("Set as default server");
        inputPanel.add(defaultCheckBox, gbc);
        
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton addButton = new JButton("Add/Update");
        JButton removeButton = new JButton("Remove");
        JButton setDefaultButton = new JButton("Set as Default");
        JButton closeButton = new JButton("Close");
        
        addButton.addActionListener(e -> {
            String ip = ipField.getText().trim();
            if (!ip.isEmpty()) {
                String remark = remarkField.getText().trim();
                serverIPRemarks.put(ip, remark);
                
                // 更新默认IP如果复选框被选中
                if (defaultCheckBox.isSelected()) {
                    defaultServerIP = ip;
                }
        
                saveServerHistory();
                refreshServerIPList(listModel);
                
                ipField.setText("");
                remarkField.setText("");
                defaultCheckBox.setSelected(false);
            }
        });
        
        removeButton.addActionListener(e -> {
            int selectedIndex = ipList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String selectedText = listModel.getElementAt(selectedIndex);
                String ip;
                if (selectedText.contains(" (")) {
                    ip = selectedText.substring(0, selectedText.indexOf(" ("));
                } else {
                    ip = selectedText;
                }
                
                // 从映射中移除
                serverIPRemarks.remove(ip);
                if (defaultServerIP != null && defaultServerIP.equals(ip)) {
                    defaultServerIP = null;
                }
                
                // 保存并刷新列表
                saveServerHistory();
                refreshServerIPList(listModel);
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select an IP to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 设置默认按钮操作
        setDefaultButton.addActionListener(e -> {
            int selectedIndex = ipList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String selectedText = listModel.getElementAt(selectedIndex);
                // 从显示文本中提取IP
                String ip;
                if (selectedText.contains(" (")) {
                    ip = selectedText.substring(0, selectedText.indexOf(" ("));
                } else {
                    ip = selectedText;
                }
                
                // 设置为默认IP
                defaultServerIP = ip;
                saveServerHistory();
                
                JOptionPane.showMessageDialog(dialog, "Default server IP set to: " + ip, "Default Set", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select an IP to set as default.", "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 关闭按钮操作
        closeButton.addActionListener(e -> {
            dialog.dispose();
        });
        
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(setDefaultButton);
        buttonPanel.add(closeButton);
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }
    
    /**
     * 显示对话框来管理保存的凭证
     */
    private void showCredentialManager() {
        // 创建对话框
        JDialog dialog = new JDialog(this, "Manage Saved Credentials", true);
        dialog.setLayout(new BorderLayout());
        dialog.setSize(500, 300);
        dialog.setLocationRelativeTo(this);
        
        // 创建主面板
        JPanel mainPanel = new JPanel(new BorderLayout());
        
        // 创建保存凭证列表
        DefaultListModel<String> listModel = new DefaultListModel<>();
        
        // 添加保存的凭证  
        for (Map.Entry<String, String> entry : loginCredentials.entrySet()) {
            listModel.addElement(entry.getKey());
        }
        
        JList<String> credentialList = new JList<>(listModel);
        credentialList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(credentialList);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Saved Credentials"));
        
        // 创建输入字段
        JPanel inputPanel = new JPanel(new GridBagLayout());
        inputPanel.setBorder(BorderFactory.createTitledBorder("Add/Edit Credential"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0;
        inputPanel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JTextField usernameField = new JTextField(20);
        inputPanel.add(usernameField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 1;
        inputPanel.add(new JLabel("Password:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        JPasswordField passwordField = new JPasswordField(20);
        inputPanel.add(passwordField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2;
        inputPanel.add(new JLabel("Options:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 2; gbc.anchor = GridBagConstraints.WEST;
        JCheckBox defaultUserCheckBox = new JCheckBox("Set as default user");
        inputPanel.add(defaultUserCheckBox, gbc);
        
        // 创建按钮
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton addButton = new JButton("Add/Update");
        JButton removeButton = new JButton("Remove");
        JButton setDefaultButton = new JButton("Set as Default");
        JButton closeButton = new JButton("Close");
        
        // 添加按钮操作
        addButton.addActionListener(e -> {
            String username = usernameField.getText().trim();
            String password = new String(passwordField.getPassword()).trim();
            if (!username.isEmpty() && !password.isEmpty()) {
                loginCredentials.put(username, password);
                
                if (defaultUserCheckBox.isSelected()) {
                    defaultLoginUsername = username;
                }
                
                saveLoginHistory();
                
                listModel.clear();
                for (Map.Entry<String, String> entry : loginCredentials.entrySet()) {
                    listModel.addElement(entry.getKey());
                }
                
                usernameField.setText("");
                passwordField.setText("");
                defaultUserCheckBox.setSelected(false);
                
                JOptionPane.showMessageDialog(dialog, "Credential saved successfully.");
            } else {
                JOptionPane.showMessageDialog(dialog, "Please enter both username and password.", "Missing Information", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 移除按钮操作
        removeButton.addActionListener(e -> {
            int selectedIndex = credentialList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String username = listModel.getElementAt(selectedIndex);
                loginCredentials.remove(username);
                saveLoginHistory();
                
                listModel.clear();
                for (Map.Entry<String, String> entry : loginCredentials.entrySet()) {
                    listModel.addElement(entry.getKey());
                }
                
                JOptionPane.showMessageDialog(dialog, "Credential removed successfully.");
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select a credential to remove.", "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 设置默认按钮操作
        setDefaultButton.addActionListener(e -> {
            int selectedIndex = credentialList.getSelectedIndex();
            if (selectedIndex >= 0) {
                String username = listModel.getElementAt(selectedIndex);
                defaultLoginUsername = username;
                saveLoginHistory();
                JOptionPane.showMessageDialog(dialog, "Default login user set to: " + username, "Default Set", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(dialog, "Please select a credential to set as default.", "No Selection", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        // 关闭按钮操作
        closeButton.addActionListener(e -> {
            dialog.dispose();
        });
        
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(setDefaultButton);
        buttonPanel.add(closeButton);
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
        
        dialog.add(mainPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.setVisible(true);
    }
    
    /**
     * 刷新服务器IP列表模型
     * @param listModel 
     */
    private void refreshServerIPList(DefaultListModel<String> listModel) {
        listModel.clear();
        
        for (Map.Entry<String, String> entry : serverIPRemarks.entrySet()) {
            String displayText = entry.getKey();
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                displayText += " (" + entry.getValue() + ")";
            }
            listModel.addElement(displayText);
        }
    }
    
    /**
     * 为朋友设置备注
     * @param friendUsername 
     * @param remark 
     */
    private void setFriendRemark(String friendUsername, String remark) {
        if (friendUsername != null && !friendUsername.isEmpty()) {
            if (remark != null && !remark.isEmpty()) {
                friendRemarks.put(friendUsername, remark);
            } else {
                friendRemarks.remove(friendUsername);
            }
            
            saveFriendRemarks();
            
            refreshFriendList();
        }
    }
    
    /**
     * 保存朋友备注到文件
     */
    private static void saveFriendRemarks() {
        try {
            Properties props = new Properties();
            
            for (Map.Entry<String, String> entry : friendRemarks.entrySet()) {
                props.setProperty(entry.getKey(), entry.getValue());
            }
            
            props.store(new FileOutputStream("friend_remarks.properties"), "Friend Remarks");
        } catch (Exception e) {
            System.err.println("Error saving friend remarks: " + e.getMessage());
        }
    }
    
    /**
     * 从文件加载朋友备注
     */
    private static void loadFriendRemarks() {
        try {
            File remarksFile = new File("friend_remarks.properties");
            if (remarksFile.exists()) {
                Properties props = new Properties();
                props.load(new FileInputStream(remarksFile));
                
                // 加载朋友备注
                for (String key : props.stringPropertyNames()) {
                    friendRemarks.put(key, props.getProperty(key));
                }
            }
        } catch (Exception e) {
            System.err.println("Error loading friend remarks: " + e.getMessage());
        }
    }
    
    /**
     * 刷新朋友列表显示
     */
    private void refreshFriendList() {
        DefaultListModel<FriendItem> newListModel = new DefaultListModel<>();
        for (int i = 0; i < friendListModel.getSize(); i++) {
            FriendItem item = friendListModel.getElementAt(i);
            newListModel.addElement(item);
        }
        friendList.setModel(newListModel);
        friendListModel = newListModel;
        friendList.revalidate();
        friendList.repaint();
    }
    
    private void browseFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setMultiSelectionEnabled(true); 
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            
            
            fileList.clear();
            fileListModel.clear();
            
         
            for (File file : selectedFiles) {
                fileList.add(file);
                fileListModel.addElement(file.getName() + " (" + file.length() + " bytes)");
            }
            
         
            if (selectedFiles.length > 0) {
                filePathField.setText(selectedFiles[0].getAbsolutePath());
            }
        }
    }
    
    /**
     * 从文件列表中移除所选文件
     */
    private void removeSelectedFile() {
        int selectedIndex = fileListView.getSelectedIndex();
        if (selectedIndex >= 0) {
            fileList.remove(selectedIndex);
            fileListModel.remove(selectedIndex);
            
            // 更新文件路径字段（如果需要）
            if (fileList.isEmpty()) {
                filePathField.setText("");
            } else if (selectedIndex == 0 && !fileList.isEmpty()) {
                // 如果第一个文件被移除，更新路径字段为新的第一个文件
                filePathField.setText(fileList.get(0).getAbsolutePath());
            } else if (selectedIndex > 0 && selectedIndex < fileList.size()) {
                // 如果一个中间文件被移除，保持路径字段不变
            }
            
            updateSendButtonState();
        } else {
            JOptionPane.showMessageDialog(this, "Please select a file to remove from the list", 
                "No File Selected", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    /**
     * 从文件列表中移除所有文件
     */
    private void clearAllFiles() {
        fileList.clear();
        fileListModel.clear();
        filePathField.setText("");
        updateSendButtonState();
    }
    
    private void browseDownloadFolder() {
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        folderChooser.setDialogTitle("Select Download Folder");
        
        // 设置当前目录为现有的下载路径（如果存在）
        String currentPath = downloadPathField.getText();
        if (currentPath != null && !currentPath.isEmpty()) {
            File currentDir = new File(currentPath);
            if (currentDir.exists()) {
                folderChooser.setCurrentDirectory(currentDir);
            }
        }
        
        int result = folderChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFolder = folderChooser.getSelectedFile();
            String selectedPath = selectedFolder.getAbsolutePath();
            downloadPathField.setText(selectedPath);
            appendMessage("Download folder set to: " + selectedPath);
            
            // Update file transfer handler with new download path
            fileTransferHandler = new transfer.FileTransferHandler(selectedPath);
        }
    }
    
    private void showFriendSelectionDialog() {
        if (friendListModel.getSize() == 0) {
            JOptionPane.showMessageDialog(this, "You have no friends to select from. Please add friends first.", 
                "No Friends", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 创建一个对话框，显示朋友列表
        JDialog friendDialog = new JDialog(this, "Select Friend", true);
        friendDialog.setLayout(new BorderLayout());
        friendDialog.setSize(300, 400);
        friendDialog.setLocationRelativeTo(this);
        
        // 创建一个只显示朋友名称的列表供选择
        DefaultListModel<String> friendNameModel = new DefaultListModel<>();
        for (int i = 0; i < friendListModel.getSize(); i++) {
            FriendItem friend = friendListModel.getElementAt(i);
            friendNameModel.addElement(friend.getUsername() + (friend.isOnline() ? " [ONLINE]" : " [OFFLINE]"));
        }
        
        JList<String> friendNameList = new JList<>(friendNameModel);
        friendNameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(friendNameList);
        
        // 添加双击监听器
        friendNameList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    selectFriendFromDialog(friendNameList, friendDialog);
                }
            }
        });
        
        // 添加选择按钮
        JButton selectButton = new JButton("Select");
        selectButton.addActionListener(e -> selectFriendFromDialog(friendNameList, friendDialog));
        
        // 添加取消按钮
        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> friendDialog.dispose());
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(selectButton);
        buttonPanel.add(cancelButton);
        
        friendDialog.add(scrollPane, BorderLayout.CENTER);
        friendDialog.add(buttonPanel, BorderLayout.SOUTH);
        
        friendDialog.setVisible(true);
    }
    
    private void selectFriendFromDialog(JList<String> friendNameList, JDialog dialog) {
        String selectedFriend = friendNameList.getSelectedValue();
        if (selectedFriend != null) {
            // 提取用户名
            String username = selectedFriend;
            if (username.contains(" [")) {
                username = username.substring(0, username.indexOf(" ["));
            }
            fileRecipientField.setText(username);
            appendMessage("Selected " + username + " as file recipient");
            dialog.dispose();
        } else {
            JOptionPane.showMessageDialog(dialog, "Please select a friend from the list", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
        }
    }
    
    //  设置文件接收者
    private void setFileRecipient(String username) {
        fileRecipientField.setText(username);
        appendMessage("Selected " + username + " as file recipient");
        JOptionPane.showMessageDialog(this, "Selected " + username + " as file recipient", 
            "Recipient Selected", JOptionPane.INFORMATION_MESSAGE);
    }
    
    // 通过用户名移除朋友
    private void removeFriendByUsername(String username) {
        friendField.setText(username);
        removeFriend();
    }
    
    public String getDownloadPath() {
        if (downloadPathField != null && !downloadPathField.getText().trim().isEmpty()) {
            return downloadPathField.getText().trim();
        }
        // 返回默认下载路径如果未设置
        return System.getProperty("user.home") + File.separator + "Downloads";
    }
    
    private void selectFriendFromListWithButtons() {
        FriendItem selectedFriend = friendList.getSelectedValue();
        if (selectedFriend != null) {
            fileRecipientField.setText(selectedFriend.getUsername());
            appendMessage("Selected " + selectedFriend.getUsername() + " as file recipient");
            JOptionPane.showMessageDialog(this, "Selected " + selectedFriend.getUsername() + " as file recipient", 
                "Recipient Selected", JOptionPane.INFORMATION_MESSAGE);
        }
    }
    
    private void updateSendButtonState() {
        if (sendFileButton != null && filePathField != null && loggedIn) {
            sendFileButton.setEnabled(!filePathField.getText().trim().isEmpty());
        }
    }
    
    private void connectToServer() {
        try {
            socket = new Socket();
            socket.connect(new java.net.InetSocketAddress(SERVER_HOST, SERVER_PORT), 5000); // 5 second timeout
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
            
            // 开始监听服务器消息
            new Thread(new ServerListener()).start();
            
            appendMessage("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);
            
            // 尝试自动登录如果已启用且存在登录凭证
            if (autoLoginEnabled && !loginCredentials.isEmpty()) {
                Map.Entry<String, String> entry = loginCredentials.entrySet().iterator().next();
                String username = entry.getKey();
                String password = entry.getValue();
                
                usernameField.setText(username);
                passwordField.setText(password);

                SwingUtilities.invokeLater(() -> {
                    try {
                        Message message = new Message(MessageType.LOGIN, username, "SERVER", username + ":" + password);
                        output.writeObject(message);
                        output.flush();
                        appendMessage("Auto-login request sent for " + username + ". Waiting for server response...");
                    } catch (IOException e) {
                        appendMessage("Error sending auto-login request: " + e.getMessage());
                    }
                });
            }
        } catch (IOException e) {
            // If initial connection fails, prompt user for server IP
            String serverIP = JOptionPane.showInputDialog(this, 
                "Failed to connect to server at " + SERVER_HOST + ":" + SERVER_PORT + 
                ". Please enter the server IP address:", "Server Connection", 
                JOptionPane.QUESTION_MESSAGE);
            
            if (serverIP != null && !serverIP.trim().isEmpty()) {
                try {
                    socket = new Socket();
                    socket.connect(new java.net.InetSocketAddress(serverIP.trim(), SERVER_PORT), 5000); 
                    output = new ObjectOutputStream(socket.getOutputStream());
                    input = new ObjectInputStream(socket.getInputStream());
                    
                    new Thread(new ServerListener()).start();
                    
                    appendMessage("Connected to server at " + serverIP + ":" + SERVER_PORT);
                    
                    if (autoLoginEnabled && !loginCredentials.isEmpty()) {
                        final String autoLoginUsername;
                        final String autoLoginPassword;
                        
    
                        if (defaultLoginUsername != null && loginCredentials.containsKey(defaultLoginUsername)) {
                            autoLoginUsername = defaultLoginUsername;
                            autoLoginPassword = loginCredentials.get(defaultLoginUsername);
                        } else {
                            Map.Entry<String, String> entry = loginCredentials.entrySet().iterator().next();
                            autoLoginUsername = entry.getKey();
                            autoLoginPassword = entry.getValue();
                        }
                        
                        usernameField.setText(autoLoginUsername);
                        passwordField.setText(autoLoginPassword);
                        
                        SwingUtilities.invokeLater(() -> {
                            try {
                                Message message = new Message(MessageType.LOGIN, autoLoginUsername, "SERVER", autoLoginUsername + ":" + autoLoginPassword);
                                output.writeObject(message);
                                output.flush();
                                appendMessage("Auto-login request sent for " + autoLoginUsername + ". Waiting for server response...");
                            } catch (IOException ex) {
                                appendMessage("Error sending auto-login request: " + ex.getMessage());
                            }
                        });
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Could not connect to server: " + ex.getMessage(), 
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
                    System.exit(1);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Could not connect to server: " + e.getMessage(), 
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }
    }
    
    private void register() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both username and password", 
                "Registration Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Message message = new Message(MessageType.REGISTER, username, "SERVER", username + ":" + password);
            output.writeObject(message);
            output.flush();
            appendMessage("Registration request sent. Waiting for server response...");
        } catch (IOException e) {
            appendMessage("Error sending registration request: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error sending registration request: " + e.getMessage(), 
                "Network Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void login() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword()).trim();
        
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter both username and password", 
                "Login Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        loginCredentials.put(username, password);

        defaultLoginUsername = username;
        saveLoginHistory();
        
        try {
            Message message = new Message(MessageType.LOGIN, username, "SERVER", username + ":" + password);
            output.writeObject(message);
            output.flush();
            appendMessage("Login request sent. Waiting for server response...");
        } catch (IOException e) {
            appendMessage("Error sending login request: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error sending login request: " + e.getMessage(), 
                "Network Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void logout() {
        if (!loggedIn) return;
        
        try {
            Message message = new Message(MessageType.LOGOUT, username, "SERVER", "");
            output.writeObject(message);
            output.flush();
            
            loggedIn = false;
            username = null;
            updateGUIState();
            appendMessage("Logged out successfully");
        } catch (IOException e) {
            appendMessage("Error sending logout request: " + e.getMessage());
        }
    }
    
    private void sendFriendRequest() {
        String friend = friendField.getText().trim();
        if (friend.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a friend username", 
                "Friend Request Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Message message = new Message(MessageType.FRIEND_REQUEST, username, friend, "");
            output.writeObject(message);
            output.flush();
            appendMessage("Friend request sent to " + friend);
        } catch (IOException e) {
            appendMessage("Error sending friend request: " + e.getMessage());
        }
    }
    
    private void removeFriend() {
        String friend = friendField.getText().trim();
        if (friend.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a friend username", 
                "Remove Friend Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        try {
            Message message = new Message(MessageType.FRIEND_REMOVE, username, friend, "");
            output.writeObject(message);
            output.flush();
            appendMessage("Friend removal request sent for " + friend);
        } catch (IOException e) {
            appendMessage("Error sending friend removal request: " + e.getMessage());
        }
    }
    
    private void sendFile() {
        String recipient = fileRecipientField.getText().trim();
        
        if (recipient.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a recipient from your friend list", 
                "File Transfer Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        if (fileList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select at least one file to send", 
                "File Transfer Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        
        for (File file : fileList) {
            if (!file.exists()) {
                JOptionPane.showMessageDialog(this, "File does not exist: " + file.getAbsolutePath(), 
                    "File Transfer Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }
        
        try {
            int successCount = 0;
            for (File file : fileList) {
                Message message = new Message(MessageType.FILE_TRANSFER_REQUEST, username, recipient, 
                    "File transfer request for: " + file.getName() + " (Size: " + file.length() + " bytes)");
                output.writeObject(message);
                output.flush();
                appendMessage("File transfer request sent to " + recipient + " for file: " + file.getName());
                successCount++;
            }
            
            JOptionPane.showMessageDialog(this, "Sent " + successCount + " file(s) transfer request(s) to " + recipient, 
                "File Transfer", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            appendMessage("Error sending file transfer request: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error sending file transfer request: " + e.getMessage(), 
                "File Transfer Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void sendMessage() {
        String content = messageField.getText().trim();
        if (content.isEmpty()) return;
        
  
        messageField.setText("");
    }
    
    private void updateGUIState() {
        boolean isLoggedIn = loggedIn;
        
        //  登录状态
        loginButton.setEnabled(!isLoggedIn);
        registerButton.setEnabled(!isLoggedIn);
        logoutButton.setEnabled(isLoggedIn);
        
        // 朋好友面板
        addFriendButton.setEnabled(isLoggedIn);
        removeFriendButton.setEnabled(isLoggedIn);
        friendField.setEnabled(isLoggedIn);
        
        // 文件传输面板
        fileRecipientField.setEnabled(isLoggedIn);
        sendFileButton.setEnabled(isLoggedIn && !filePathField.getText().trim().isEmpty());
        
        if (!isLoggedIn) {
            friendListModel.clear();
            filePathField.setText("");
            fileRecipientField.setText("");
        }
        
        // 更新窗口标题
        if (isLoggedIn && username != null) {
            setTitle("P2P File Transfer Client - Logged in as: " + username);
        } else {
            setTitle("P2P File Transfer Client");
        }
    }
    
    private void appendMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[" + new java.util.Date() + "] " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }
    
    private void updateFriendList(Map<String, Boolean> friendStatus) {
        SwingUtilities.invokeLater(() -> {
            friendListModel.clear();
            
            for (Map.Entry<String, Boolean> entry : friendStatus.entrySet()) {
                friendListModel.addElement(new FriendItem(entry.getKey(), entry.getValue()));
            }
            
            if (!friendStatus.isEmpty()) {
                sendFileButton.setEnabled(loggedIn);
            }
        });
    }
    
    private void disconnect() {
        try {
            if (loggedIn) {
                logout();
            }
            
            if (output != null) output.close();
            if (input != null) input.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    private class ServerListener implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    Message message = (Message) input.readObject();
                    processServerMessage(message);
                }
            } catch (IOException | ClassNotFoundException e) {
                SwingUtilities.invokeLater(() -> {
                    appendMessage("Disconnected from server: " + e.getMessage());
                });
            }
        }
        
        private void processServerMessage(Message message) {
            switch (message.getType()) {
                case REGISTER:
                    SwingUtilities.invokeLater(() -> {
                        appendMessage("Registration: " + message.getContent());
                        if (message.getContent().equals("Registration successful")) {
                            JOptionPane.showMessageDialog(P2PClient.this, "Registration successful! You can now login.", 
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            JOptionPane.showMessageDialog(P2PClient.this, "Registration failed: " + message.getContent(), 
                                "Registration Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    break;
                    
                case LOGIN:
                    SwingUtilities.invokeLater(() -> {
                        if (message.getContent().equals("Login successful")) {
                            P2PClient.this.username = message.getReceiver();
                            P2PClient.this.loggedIn = true;
                            
                            // 存储客户端IP地址如果可用
                            if (message.getData() != null) {
                                String clientIP = (String) message.getData();
                                setClientIP(P2PClient.this.username, clientIP);
                                appendMessage("Stored IP address for " + P2PClient.this.username + ": " + clientIP);
                            }
                            
                            updateGUIState();
                            appendMessage("Login successful");
                            
                            JOptionPane.showMessageDialog(P2PClient.this, "Login successful! Welcome " + P2PClient.this.username + ".", 
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            appendMessage("Login failed: " + message.getContent());
                            JOptionPane.showMessageDialog(P2PClient.this, "Login failed: " + message.getContent(), 
                                "Login Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    break;
                    
                case LOGOUT:
                    SwingUtilities.invokeLater(() -> {
                        loggedIn = false;
                        username = null;
                        updateGUIState();
                        appendMessage("Logged out");
                        JOptionPane.showMessageDialog(P2PClient.this, "You have been logged out successfully.", 
                            "Logout", JOptionPane.INFORMATION_MESSAGE);
                    });
                    break;
                    
                case FRIEND_REQUEST:
                    SwingUtilities.invokeLater(() -> {
                        int option = JOptionPane.showConfirmDialog(P2PClient.this, 
                            message.getSender() + " wants to be your friend. Accept?", 
                            "Friend Request", JOptionPane.YES_NO_OPTION);
                        
                        try {
                            if (option == JOptionPane.YES_OPTION) {
                                Message response = new Message(MessageType.FRIEND_ACCEPT, username, 
                                    message.getSender(), "");
                                output.writeObject(response);
                                output.flush();
                                appendMessage("Accepted friend request from " + message.getSender());
                            } else {
                                Message response = new Message(MessageType.FRIEND_REJECT, username, 
                                    message.getSender(), "");
                                output.writeObject(response);
                                output.flush();
                                appendMessage("Rejected friend request from " + message.getSender());
                            }
                        } catch (IOException e) {
                            appendMessage("Error responding to friend request: " + e.getMessage());
                            JOptionPane.showMessageDialog(P2PClient.this, "Error responding to friend request: " + e.getMessage(), 
                                "Network Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    break;
                    
                case FRIEND_ACCEPT:
                    SwingUtilities.invokeLater(() -> {
                        appendMessage(message.getContent());
                        JOptionPane.showMessageDialog(P2PClient.this, message.getContent(), 
                            "Friend Request Accepted", JOptionPane.INFORMATION_MESSAGE);
                    });
                    break;
                    
                case FRIEND_REJECT:
                    SwingUtilities.invokeLater(() -> {
                        appendMessage(message.getContent());
                        JOptionPane.showMessageDialog(P2PClient.this, message.getContent(), 
                            "Friend Request Rejected", JOptionPane.INFORMATION_MESSAGE);
                    });
                    break;
                    
                case FRIEND_REMOVE:
                    SwingUtilities.invokeLater(() -> {
                        appendMessage(message.getContent());
                        JOptionPane.showMessageDialog(P2PClient.this, message.getContent(), 
                            "Friend Removed", JOptionPane.INFORMATION_MESSAGE);
                    });
                    break;
                    
                case FRIEND_LIST_UPDATE:
                    @SuppressWarnings("unchecked")
                    Map<String, Object> friendData = (Map<String, Object>) message.getData();
                    if (friendData != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Boolean> friendStatus = (Map<String, Boolean>) friendData.get("status");

                        @SuppressWarnings("unchecked")
                        Map<String, String> friendIPs = (Map<String, String>) friendData.get("ips");

                        if (friendIPs != null) {
                            for (Map.Entry<String, String> entry : friendIPs.entrySet()) {
                                setClientIP(entry.getKey(), entry.getValue());
                                appendMessage("Updated IP for " + entry.getKey() + ": " + entry.getValue());
                            }
                        }

                        updateFriendList(friendStatus);
                    }
                    break;
                    
                case FILE_TRANSFER_REQUEST:
                    SwingUtilities.invokeLater(() -> {
                        appendMessage("File transfer request from " + message.getSender() + ": " + message.getContent());
                        
                        // 自动接受文件传输
                        appendMessage("Automatically accepting file transfer from " + message.getSender());
                        
                        int transferPort = fileTransferHandler.startTransferServer(0); 
                        appendMessage("File transfer server started on port " + transferPort);

                        try {
                            Map<String, Object> responseData = new HashMap<>();
                            responseData.put("port", transferPort);
                            responseData.put("ip", NetworkUtil.getLocalIPAddress());
                            
                            Message response = new Message(
                                MessageType.FILE_TRANSFER_RESPONSE,
                                username,
                                message.getSender(),
                                "ACCEPT",
                                responseData
                            );
                            output.writeObject(response);
                            output.flush();
                            appendMessage("File transfer acceptance sent to " + message.getSender());
                            JOptionPane.showMessageDialog(P2PClient.this, 
                                "Accepting file transfer from " + message.getSender() + "\n" + message.getContent(),
                                "File Transfer Accepted", JOptionPane.INFORMATION_MESSAGE);
                        } catch (IOException e) {
                            appendMessage("Error sending file transfer acceptance: " + e.getMessage());
                            JOptionPane.showMessageDialog(P2PClient.this, 
                                "Error sending file transfer acceptance: " + e.getMessage(),
                                "File Transfer Error", JOptionPane.ERROR_MESSAGE);
                        }
                    });
                    break;
                    
                case FILE_TRANSFER_RESPONSE:
                    SwingUtilities.invokeLater(() -> {
                        if ("ACCEPT".equals(message.getContent())) {
                            
                            appendMessage("File transfer accepted by " + message.getSender());
                            
                            // 从数据中获取接收者的端口和IP
                            @SuppressWarnings("unchecked")
                            Map<String, Object> responseData = (Map<String, Object>) message.getData();
                            if (responseData != null) {
                                int receiverPort = (Integer) responseData.get("port");
                                String receiverIP = (String) responseData.get("ip");
                                
                                // 存储接收者的IP地址
                                if (receiverIP != null && !receiverIP.isEmpty()) {
                                    setClientIP(message.getSender(), receiverIP);
                                    appendMessage("Stored IP for " + message.getSender() + ": " + receiverIP);
                                }
                                
                                // 发送所有文件列表中的文件
                                if (!fileList.isEmpty()) {
                                    for (File fileToSend : fileList) {
                                        if (fileToSend.exists()) {
                                            // 使用接收者的IP地址进行文件传输   
                                            if (receiverIP == null || receiverIP.isEmpty()) {
                                                receiverIP = getClientIP(message.getSender());
                                                if (receiverIP == null || receiverIP.isEmpty()) {
                                                    receiverIP = "localhost";
                                                }
                                            }
                                            
                                            fileTransferHandler.initiateFileTransfer(username, message.getSender(), receiverIP, receiverPort, fileToSend);
                                            appendMessage("Initiating file transfer to " + message.getSender() + " on port " + receiverPort + " at IP " + receiverIP + " for file: " + fileToSend.getName());
                                        } else {
                                            appendMessage("File does not exist: " + fileToSend.getAbsolutePath());
                                            JOptionPane.showMessageDialog(P2PClient.this, 
                                                "File does not exist: " + fileToSend.getAbsolutePath(), 
                                                "File Transfer Error", JOptionPane.ERROR_MESSAGE);
                                        }
                                    }
                                    
                                    JOptionPane.showMessageDialog(P2PClient.this, 
                                        "File transfer initiated to " + message.getSender() + "\nSending " + fileList.size() + " file(s)", 
                                        "File Transfer Started", JOptionPane.INFORMATION_MESSAGE);
                                } else {
                                    appendMessage("No files selected for transfer");
                                    JOptionPane.showMessageDialog(P2PClient.this, 
                                        "No files selected for transfer", 
                                        "File Transfer Error", JOptionPane.ERROR_MESSAGE);
                                }
                            } else {
                                appendMessage("Invalid response data received");
                                JOptionPane.showMessageDialog(P2PClient.this, 
                                    "Invalid response data received", 
                                    "File Transfer Error", JOptionPane.ERROR_MESSAGE);
                            }
                        } else if ("REJECT".equals(message.getContent())) {
                            // Receiver rejected the file transfer
                            appendMessage("File transfer rejected by " + message.getSender());
                            JOptionPane.showMessageDialog(P2PClient.this, 
                                "File transfer rejected by " + message.getSender(),
                                "File Transfer Rejected", JOptionPane.INFORMATION_MESSAGE);
                        } else {
                            // Other response
                            appendMessage("File transfer response from " + message.getSender() + ": " + message.getContent());
                        }
                    });
                    break;
                    
                case ERROR:
                    SwingUtilities.invokeLater(() -> {
                        appendMessage("Error: " + message.getContent());
                        JOptionPane.showMessageDialog(P2PClient.this, message.getContent(), 
                            "Server Error", JOptionPane.ERROR_MESSAGE);
                    });
                    break;
                    
                default:
                    SwingUtilities.invokeLater(() -> {
                        appendMessage("Unknown message: " + message.getContent());
                    });
                    break;
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new P2PClient();
        });
    }
}