package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import model.Message;
import model.MessageType;
import model.User;
import storage.FileStorage;

public class P2PServer {
    private static final int PORT = 8080;
    private static final int FRIEND_UPDATE_INTERVAL = 30000; // 30 seconds
    
    // IP地址类型枚举
    public enum IPAddressType {
        PUBLIC_IP,
        TAILSCALE_IP
    }
    
    private IPAddressType ipAddressType = IPAddressType.PUBLIC_IP;
    
    /**
     * 获取Tailscale IP地址（如果可用）
     * @return Tailscale IP地址，如果未找到则返回null
     */
    private String getTailscaleIP() {
        try {
            // 尝试查找Tailscale网络接口
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface networkInterface = interfaces.nextElement();
                String displayName = networkInterface.getDisplayName();
                String name = networkInterface.getName();
                
                // 检查这是否是Tailscale接口
                if ((displayName != null && displayName.toLowerCase().contains("tailscale")) ||
                    (name != null && name.toLowerCase().contains("tailscale"))) {
                    java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        java.net.InetAddress addr = addresses.nextElement();
                        if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 如果无法获取Tailscale IP，则回退到默认行为
            System.err.println("Could not get Tailscale IP: " + e.getMessage());
        }
        return null; // Return null to bind to all interfaces
    }
    
    /**
     * 设置客户端通信的IP地址类型
     * @param type 要使用的IP地址类型
     */
    public void setIPAddressType(IPAddressType type) {
        this.ipAddressType = type;
    }
    
    /**
     * 根据选定的类型获取用于客户端通信的IP地址
     * @param clientSocket 从中获取IP地址的客户端套接字
     * @return 适当的IP地址
     */
    public String getClientIPAddress(java.net.Socket clientSocket) {
        if (ipAddressType == IPAddressType.TAILSCALE_IP) {
            // Try to get Tailscale IP
            String tailscaleIP = getTailscaleIP();
            if (tailscaleIP != null) {
                return tailscaleIP;
            }
        }
        
        // 回退到客户端的实际IP地址
        return clientSocket.getInetAddress().getHostAddress();
    }
    
    private ServerSocket serverSocket;
        private String bindAddress;
    private Map<String, User> users;
    private Map<String, ClientHandler> connectedClients;
    private ScheduledExecutorService scheduler;
    private FileStorage fileStorage;

    public P2PServer() {
        users = new ConcurrentHashMap<>();
        connectedClients = new ConcurrentHashMap<>();
        scheduler = Executors.newScheduledThreadPool(2);
        fileStorage = FileStorage.getInstance();
        
        // 从文件存储加载现有用户
        users = fileStorage.loadUsers();
    }

    public void start() throws IOException {
        // 获取Tailscale IP地址（如果可用），否则绑定到所有接口
        String bindAddress = getTailscaleIP();
        if (bindAddress != null && !bindAddress.isEmpty()) {
            java.net.InetAddress address = java.net.InetAddress.getByName(bindAddress);
            serverSocket = new ServerSocket(PORT, 50, address);
            this.bindAddress = bindAddress;
            System.out.println("P2P Server started on " + bindAddress + ":" + PORT);
        } else {
            serverSocket = new ServerSocket(PORT);
            this.bindAddress = "0.0.0.0"; // All interfaces
            System.out.println("P2P Server started on port " + PORT + " (all interfaces)");
        }
        
        // 安排定期的好友列表更新
        scheduler.scheduleAtFixedRate(this::updateFriendLists, 0, FRIEND_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
        
        while (true) {
            Socket clientSocket = serverSocket.accept();
            ClientHandler clientHandler = new ClientHandler(clientSocket, this);
            new Thread(clientHandler).start();
        }
    }
    
    /**
     * Gets the server's bind address
     * @return the bind address as a string
     */
    public String getBindAddress() {
        return bindAddress;
    }

    public synchronized boolean registerUser(String username, String password) {
        if (users.containsKey(username)) {
            return false; // User already exists
        }
        
        User newUser = new User(username, password);
        users.put(username, newUser);
        fileStorage.saveUsers(users);
        return true;
    }

    public synchronized boolean loginUser(String username, String password, ClientHandler clientHandler) {
        User user = users.get(username);
        if (user == null || !user.getPassword().equals(password)) {
            return false; // Invalid credentials
        }
        
        if (connectedClients.containsKey(username)) {
            // 用户已登录
            return false;
        }
        
        user.setOnline(true);
        connectedClients.put(username, clientHandler);
        fileStorage.saveUsers(users);
        return true;
    }

    public synchronized void logoutUser(String username) {
        User user = users.get(username);
        if (user != null) {
            user.setOnline(false);
        }
        connectedClients.remove(username);
        fileStorage.saveUsers(users);
    }

    public synchronized boolean sendFriendRequest(String requester, String target) {
        User targetUser = users.get(target);
        if (targetUser == null) {
            return false; 
        }
        
        // 为简单起见，我们只会向目标用户发送消息
        ClientHandler targetHandler = connectedClients.get(target);
        if (targetHandler != null) {
            Message message = new Message(MessageType.FRIEND_REQUEST, requester, target, 
                requester + " wants to be your friend");
            targetHandler.sendMessage(message);
            return true;
        }
        return false; 
    }

    public synchronized boolean acceptFriendRequest(String user, String friend) {
        User userObj = users.get(user);
        User friendObj = users.get(friend);
        
        if (userObj == null || friendObj == null) {
            return false;
        }
        
        userObj.addFriend(friend);
        friendObj.addFriend(user);

        ClientHandler userHandler = connectedClients.get(user);
        ClientHandler friendHandler = connectedClients.get(friend);
        
        if (userHandler != null) {
            Message message = new Message(MessageType.FRIEND_ACCEPT, friend, user, 
                friend + " accepted your friend request");
            userHandler.sendMessage(message);
        }
        
        if (friendHandler != null) {
            Message message = new Message(MessageType.FRIEND_ACCEPT, user, friend, 
                user + " is now your friend");
            friendHandler.sendMessage(message);
        }
        
        fileStorage.saveUsers(users);
        return true;
    }

    public synchronized boolean rejectFriendRequest(String user, String friend) {
        User userObj = users.get(user);
        User friendObj = users.get(friend);
        
        if (userObj == null || friendObj == null) {
            return false;
        }
        
        ClientHandler friendHandler = connectedClients.get(friend);
        if (friendHandler != null) {
            Message message = new Message(MessageType.FRIEND_REJECT, user, friend, 
                user + " rejected your friend request");
            friendHandler.sendMessage(message);
        }
        
        return true;
    }

    public synchronized boolean removeFriend(String user, String friend) {
        User userObj = users.get(user);
        User friendObj = users.get(friend);
        
        if (userObj == null || friendObj == null) {
            return false;
        }
        
        userObj.removeFriend(friend);
        friendObj.removeFriend(user);
        
        ClientHandler userHandler = connectedClients.get(user);
        ClientHandler friendHandler = connectedClients.get(friend);
        
        if (userHandler != null) {
            Message message = new Message(MessageType.FRIEND_REMOVE, "SERVER", user, 
                "You are no longer friends with " + friend);
            userHandler.sendMessage(message);
        }
        
        if (friendHandler != null) {
            Message message = new Message(MessageType.FRIEND_REMOVE, "SERVER", friend, 
                "You are no longer friends with " + user);
            friendHandler.sendMessage(message);
        }
        
        fileStorage.saveUsers(users);
        return true;
    }
    
    /**
     * 停止服务器并关闭所有连接
     */
    public void stop() {
        try {
            // 关闭调度器
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
            
            // 关闭所有客户端连接
            for (ClientHandler handler : connectedClients.values()) {
                try {
                    handler.close();
                } catch (Exception e) {
                    System.err.println("Error closing client connection: " + e.getMessage());
                }
            }
            
            // 关闭服务器套接字
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            
            System.out.println("Server stopped");
        } catch (Exception e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
    }

    public synchronized boolean canTransferFile(String sender, String receiver) {
        User senderUser = users.get(sender);
        User receiverUser = users.get(receiver);
        
        if (senderUser == null || receiverUser == null) {
            return false;
        }
        
        // 两个用户都必须在线且是好友
        return senderUser.isOnline() && receiverUser.isOnline() && 
               senderUser.getFriends().contains(receiver) && 
               receiverUser.getFriends().contains(sender);
    }

    public ClientHandler getClientHandler(String username) {
        return connectedClients.get(username);
    }

    public Collection<User> getAllUsers() {
        return users.values();
    }

    public synchronized void deleteUser(String username) {
        User user = users.get(username);
        if (user == null) {
            return;
        }
        
        // 从所有好友列表中移除此用户
        for (String friendName : user.getFriends()) {
            User friend = users.get(friendName);
            if (friend != null) {
                friend.removeFriend(username);
                
                // 如果好友在线则通知好友
                ClientHandler friendHandler = connectedClients.get(friendName);
                if (friendHandler != null) {
                    Message message = new Message(MessageType.FRIEND_REMOVE, "SERVER", friendName, 
                        username + " has been removed from the system and is no longer your friend");
                    friendHandler.sendMessage(message);
                }
            }
        }
        
        // 如果用户在线则从连接的客户端中移除
        connectedClients.remove(username);
        
        // 从系统中移除用户
        users.remove(username);
        
        fileStorage.saveUsers(users);
    }

    public synchronized void batchDeleteUsers(List<String> usernames) {
        for (String username : usernames) {
            deleteUser(username);
        }
    }

    public void updateFriendLists() {
        System.out.println("Updating friend lists for all connected clients...");
        
        for (Map.Entry<String, ClientHandler> entry : connectedClients.entrySet()) {
            String username = entry.getKey();
            ClientHandler handler = entry.getValue();
            
            User user = users.get(username);
            if (user != null) {
                // 发送更新后的好友列表，包含IP地址
                Map<String, Object> friendData = new HashMap<>();
                Map<String, Boolean> friendStatus = new HashMap<>();
                Map<String, String> friendIPs = new HashMap<>();
                
                for (String friendName : user.getFriends()) {
                    User friend = users.get(friendName);
                    if (friend != null) {
                        friendStatus.put(friendName, friend.isOnline());
                        
                        // 获取好友的IP地址如果在线
                        ClientHandler friendHandler = connectedClients.get(friendName);
                        if (friendHandler != null && friend.isOnline()) {
                            try {
                                String friendIP = getClientIPAddress(friendHandler.getClientSocket());
                                friendIPs.put(friendName, friendIP);
                            } catch (Exception e) {
                                System.err.println("Error getting IP for " + friendName + ": " + e.getMessage());
                            }
                        }
                    }
                }
                
                // 合并状态和IP信息 
                friendData.put("status", friendStatus);
                friendData.put("ips", friendIPs);
                
                Message message = new Message(MessageType.FRIEND_LIST_UPDATE, "SERVER", username, 
                    "Friend list update", friendData);
                handler.sendMessage(message);
            }
        }
    }

    public static void main(String[] args) {
        try {
            P2PServer server = new P2PServer();
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}