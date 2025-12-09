package server;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;

import model.User;

public class ServerGUI extends JFrame {
    private P2PServer server;
    private boolean serverRunning = false;
    
    // IP地址选择组件
    private JRadioButton publicIPRadio;
    private JRadioButton tailscaleIPRadio;
    
    // GUI组件
    private JButton startStopButton;
    private JTextArea logArea;
    private JTable userTable;
    private DefaultTableModel userTableModel;
    private JButton deleteUserButton;
    private JButton deleteAllUsersButton;
    private JTextField deleteUsernameField;
    private JLabel currentIPLabel;
    
    public ServerGUI() {
        initializeGUI();
        initializeServer();
    }
    
    private void initializeGUI() {
        setTitle("P2P File Transfer Server");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
        
        // 处理窗口关闭
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdownServer();
                System.exit(0);
            }
        });
        
        // 创建使用边框布局的主面板
        setLayout(new BorderLayout());
        
        // 顶部控制面板
        JPanel topPanel = new JPanel(new FlowLayout());
        startStopButton = new JButton("Start Server");
        startStopButton.addActionListener(e -> toggleServer());
        topPanel.add(startStopButton);
        
        // 当前服务器IP显示
        JPanel ipDisplayPanel = new JPanel(new FlowLayout());
        ipDisplayPanel.setBorder(BorderFactory.createTitledBorder("Current Server IP"));
        currentIPLabel = new JLabel("Not started");
        ipDisplayPanel.add(new JLabel("IP Address:"));
        ipDisplayPanel.add(currentIPLabel);
        topPanel.add(ipDisplayPanel);
        
        // IP地址选择
        JPanel ipSelectionPanel = new JPanel(new FlowLayout());
        ipSelectionPanel.setBorder(BorderFactory.createTitledBorder("IP Address"));
        publicIPRadio = new JRadioButton("Public IP", true);
        tailscaleIPRadio = new JRadioButton("Tailscale IP");
        ButtonGroup ipGroup = new ButtonGroup();
        ipGroup.add(publicIPRadio);
        ipGroup.add(tailscaleIPRadio);
        
        // 添加事件监听器到单选按钮
        publicIPRadio.addActionListener(e -> updateCurrentIPDisplay());
        tailscaleIPRadio.addActionListener(e -> updateCurrentIPDisplay());
        
        ipSelectionPanel.add(publicIPRadio);
        ipSelectionPanel.add(tailscaleIPRadio);
        topPanel.add(ipSelectionPanel);
        
        // 用户表格的中央面板
        String[] columnNames = {"Username", "Online Status", "Friends Count"};
        userTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        userTable = new JTable(userTableModel);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane userTableScrollPane = new JScrollPane(userTable);
        userTableScrollPane.setBorder(BorderFactory.createTitledBorder("Connected Users"));
        
        // 用户管理的底部面板
        JPanel bottomPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        
        gbc.gridx = 0; gbc.gridy = 0;
        bottomPanel.add(new JLabel("Username:"), gbc);
        
        gbc.gridx = 1; gbc.gridy = 0;
        deleteUsernameField = new JTextField(15);
        bottomPanel.add(deleteUsernameField, gbc);
        
        gbc.gridx = 2; gbc.gridy = 0;
        deleteUserButton = new JButton("Delete User");
        deleteUserButton.addActionListener(e -> deleteSelectedUser());
        bottomPanel.add(deleteUserButton, gbc);
        
        gbc.gridx = 3; gbc.gridy = 0;
        deleteAllUsersButton = new JButton("Delete All Users");
        deleteAllUsersButton.addActionListener(e -> deleteAllUsers());
        bottomPanel.add(deleteAllUsersButton, gbc);
        
        // 日志区域
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Server Log"));
        
        // 将组件添加到主框架
        add(topPanel, BorderLayout.NORTH);
        add(userTableScrollPane, BorderLayout.CENTER);
        add(bottomPanel, BorderLayout.SOUTH);
        add(logScrollPane, BorderLayout.EAST);
        
        setVisible(true);
    }
    
    private void initializeServer() {
        try {
            server = new P2PServer();
            appendLog("Server initialized. Click 'Start Server' to begin.");
        } catch (Exception e) {
            appendLog("Error initializing server: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error initializing server: " + e.getMessage(), 
                "Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void toggleServer() {
        if (serverRunning) {
            stopServer();
        } else {
            startServer();
        }
    }
    
    private void startServer() {
        try {
            // 根据选择设置IP地址类型
            if (tailscaleIPRadio.isSelected()) {
                server.setIPAddressType(P2PServer.IPAddressType.TAILSCALE_IP);
                appendLog("Using Tailscale IP for client communication");
            } else {
                server.setIPAddressType(P2PServer.IPAddressType.PUBLIC_IP);
                appendLog("Using Public IP for client communication");
            }
            
            // 在启动服务器之前获取绑定地址
            String bindAddress = null;
            if (tailscaleIPRadio.isSelected()) {
                bindAddress = util.NetworkUtil.getLocalIPAddress();
                if (bindAddress == null || bindAddress.isEmpty() || bindAddress.equals("localhost")) {
                    bindAddress = "0.0.0.0"; // All interfaces
                }
            } else {
                // For public IP, try to get the actual local IP address (excluding Tailscale)
                bindAddress = util.NetworkUtil.getPublicIPAddress();
                if (bindAddress == null || bindAddress.isEmpty() || bindAddress.equals("localhost")) {
                    bindAddress = "0.0.0.0"; // All interfaces
                }
            }
            
            // 在启动服务器之前更新当前IP显示
            if (currentIPLabel != null) {
                currentIPLabel.setText(bindAddress);
            }
            
            new Thread(() -> {
                try {
                    server.start();
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() -> {
                        appendLog("Server error: " + e.getMessage());
                        JOptionPane.showMessageDialog(this, "Server error: " + e.getMessage(), 
                            "Server Error", JOptionPane.ERROR_MESSAGE);
                        serverRunning = false;
                        startStopButton.setText("Start Server");
                        if (currentIPLabel != null) {
                            currentIPLabel.setText("Error");
                        }
                    });
                }
            }).start();
            
            serverRunning = true;
            startStopButton.setText("Stop Server");
            
            appendLog("Server started on port 8080");
            updateUserTable();
            
            new Thread(() -> {
                while (serverRunning) {
                    try {
                        Thread.sleep(5000); 
                        SwingUtilities.invokeLater(this::updateUserTable);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }).start();
        } catch (Exception e) {
            appendLog("Error starting server: " + e.getMessage());
            JOptionPane.showMessageDialog(this, "Error starting server: " + e.getMessage(), 
                "Server Error", JOptionPane.ERROR_MESSAGE);
        }
    }
    
    private void stopServer() {
        serverRunning = false;
        startStopButton.setText("Start Server");
        
        // 停止服务器
        if (server != null) {
            server.stop();
        }
        
        // 更新当前IP显示
        if (currentIPLabel != null) {
            currentIPLabel.setText("Not started");
        }
        
        appendLog("Server stopped");
    }
    
    private void shutdownServer() {
        serverRunning = false;
        appendLog("Server shutting down...");
        
        // 停止服务器
        if (server != null) {
            server.stop();
        }
    }
    
    // 添加新方法用于更新当前IP显示
    private void updateCurrentIPDisplay() {
        if (!serverRunning) {
            return; // 服务器未运行时不更新
        }
        
        String ipAddress = "";
        if (tailscaleIPRadio.isSelected()) {
            ipAddress = util.NetworkUtil.getLocalIPAddress();
            if (ipAddress == null || ipAddress.isEmpty() || ipAddress.equals("localhost")) {
                ipAddress = "0.0.0.0";
            }
        } else {
            // For public IP, try to get the actual local IP address (excluding Tailscale)
            ipAddress = util.NetworkUtil.getPublicIPAddress();
            if (ipAddress == null || ipAddress.isEmpty() || ipAddress.equals("localhost")) {
                ipAddress = "0.0.0.0";
            }
        }
        
        if (currentIPLabel != null) {
            currentIPLabel.setText(ipAddress);
        }
    }
    
    private void updateUserTable() {
        if (server == null) return;
        
        userTableModel.setRowCount(0); // 清除现有数据
        
        try {
            for (User user : server.getAllUsers()) {
                Object[] rowData = {
                    user.getUsername(),
                    user.isOnline() ? "Online" : "Offline",
                    user.getFriends().size()
                };
                userTableModel.addRow(rowData);
            }
        } catch (Exception e) {
            appendLog("Error updating user table: " + e.getMessage());
        }
    }
    
    private void deleteSelectedUser() {
        String username = deleteUsernameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a username to delete", 
                "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete user '" + username + "'?", 
            "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                server.deleteUser(username);
                appendLog("User '" + username + "' deleted");
                deleteUsernameField.setText("");
                updateUserTable();
            } catch (Exception e) {
                appendLog("Error deleting user: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Error deleting user: " + e.getMessage(), 
                    "Deletion Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    private void deleteAllUsers() {
        int confirm = JOptionPane.showConfirmDialog(this, 
            "Are you sure you want to delete ALL users? This action cannot be undone.", 
            "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        
        if (confirm == JOptionPane.YES_OPTION) {
            try {
                appendLog("All users deleted");
                updateUserTable();
            } catch (Exception e) {
                appendLog("Error deleting all users: " + e.getMessage());
                JOptionPane.showMessageDialog(this, "Error deleting all users: " + e.getMessage(), 
                    "Deletion Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + new java.util.Date() + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new ServerGUI();
        });
    }
}