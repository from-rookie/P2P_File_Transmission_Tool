package server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

import model.Message;
import model.MessageType;

public class ClientHandler implements Runnable {
    private Socket socket;
    private P2PServer server;
    private ObjectInputStream input;
    private ObjectOutputStream output;
    private String username;
    private boolean connected;

    public ClientHandler(Socket socket, P2PServer server) {
        this.socket = socket;
        this.server = server;
        this.connected = true;
    }
    
    /**
     * 获取客户端套接字
     * @return 客户端套接字
     */
    public Socket getClientSocket() {
        return socket;
    }

    @Override
    public void run() {
        try {
            // 设置流
            output = new ObjectOutputStream(socket.getOutputStream());
            input = new ObjectInputStream(socket.getInputStream());
            
            // 处理消息
            while (connected) {
                try {
                    Message message = (Message) input.readObject();
                    handleMessage(message);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            // 客户端断开连接
            System.out.println("Client " + username + " disconnected");
        } finally {
            disconnect();
        }
    }

    private void handleMessage(Message message) {
        switch (message.getType()) {
            case REGISTER:
                handleRegister(message);
                break;
            case LOGIN:
                handleLogin(message);
                break;
            case LOGOUT:
                handleLogout(message);
                break;
            case FRIEND_REQUEST:
                handleFriendRequest(message);
                break;
            case FRIEND_ACCEPT:
                handleFriendAccept(message);
                break;
            case FRIEND_REJECT:
                handleFriendReject(message);
                break;
            case FRIEND_REMOVE:
                handleFriendRemove(message);
                break;
            case FILE_TRANSFER_REQUEST:
                handleFileTransferRequest(message);
                break;
            case FILE_TRANSFER_RESPONSE:
                handleFileTransferResponse(message);
                break;
            case FRIEND_LIST_UPDATE:
                // 处理好友列表请求
                server.updateFriendLists();
                break;
            default:
                sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                    "Unknown message type: " + message.getType()));
        }
    }

    private void handleRegister(Message message) {
        String[] credentials = message.getContent().split(":");
        if (credentials.length != 2) {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Invalid registration format"));
            return;
        }
        
        String username = credentials[0];
        String password = credentials[1];
        
        boolean success = server.registerUser(username, password);
        if (success) {
            sendMessage(new Message(MessageType.REGISTER, "SERVER", message.getSender(), 
                "Registration successful"));
        } else {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Username already exists"));
        }
    }

    private void handleLogin(Message message) {
        String[] credentials = message.getContent().split(":");
        if (credentials.length != 2) {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Invalid login format"));
            return;
        }
        
        String username = credentials[0];
        String password = credentials[1];
        
        boolean success = server.loginUser(username, password, this);
        if (success) {
            this.username = username;
            // 发送带有客户端IP地址的登录成功消息
            String clientIP = socket.getInetAddress().getHostAddress();
            Message loginMessage = new Message(
                MessageType.LOGIN,
                "SERVER",
                username,
                "Login successful"
            );
            // 在消息数据中存储IP地址
            loginMessage.setData(clientIP);
            sendMessage(loginMessage);
        } else {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Invalid username or password"));
        }
    }

    private void handleLogout(Message message) {
        if (username != null) {
            server.logoutUser(username);
            username = null;
        }
        sendMessage(new Message(MessageType.LOGOUT, "SERVER", message.getSender(), "Logged out"));
    }

    private void handleFriendRequest(Message message) {
        String target = message.getReceiver();
        boolean success = server.sendFriendRequest(message.getSender(), target);
        if (!success) {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Failed to send friend request"));
        }
    }

    private void handleFriendAccept(Message message) {
        String friend = message.getReceiver();
        boolean success = server.acceptFriendRequest(message.getSender(), friend);
        if (!success) {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Failed to accept friend request"));
        }
    }

    private void handleFriendReject(Message message) {
        String friend = message.getReceiver();
        boolean success = server.rejectFriendRequest(message.getSender(), friend);
        if (!success) {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Failed to reject friend request"));
        }
    }

    private void handleFriendRemove(Message message) {
        String friend = message.getReceiver();
        boolean success = server.removeFriend(message.getSender(), friend);
        if (!success) {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Failed to remove friend"));
        }
    }

    private void handleFileTransferRequest(Message message) {
        String receiver = message.getReceiver();
        boolean canTransfer = server.canTransferFile(message.getSender(), receiver);
        
        if (canTransfer) {
            // 将文件传输请求转发给接收方
            ClientHandler receiverHandler = server.getClientHandler(receiver);
            if (receiverHandler != null) {
                receiverHandler.sendMessage(message);
            } else {
                sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                    "Receiver is no longer online"));
            }
        } else {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Cannot transfer file. Both users must be online and friends."));
        }
    }
    
    private void handleFileTransferResponse(Message message) {
        // Handle file transfer response from receiver (accept/reject)
        String receiver = message.getReceiver();
        ClientHandler receiverHandler = server.getClientHandler(receiver);
        
        if (receiverHandler != null) {
            receiverHandler.sendMessage(message);
        } else {
            sendMessage(new Message(MessageType.ERROR, "SERVER", message.getSender(), 
                "Receiver is no longer online"));
        }
    }

    public void sendMessage(Message message) {
        try {
            output.writeObject(message);
            output.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        connected = false;
        if (username != null) {
            server.logoutUser(username);
        }
        
        try {
            if (input != null) input.close();
            if (output != null) output.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 关闭客户端连接
     * @throws IOException
     */
    public void close() throws IOException {
        disconnect();
    }

    public String getUsername() {
        return username;
    }
}