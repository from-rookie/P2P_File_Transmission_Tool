package transfer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import model.Message;
import model.MessageType;

public class FileTransferHandler {
    private static final int TRANSFER_PORT_START = 9000;
    private static final int MAX_CONCURRENT_TRANSFERS = 10;
    
    private ExecutorService executorService;
    private int nextPort;
    private String downloadPath;
    
    public FileTransferHandler() {
        this.executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_TRANSFERS);
        this.nextPort = TRANSFER_PORT_START;
        // 设置默认下载路径
        this.downloadPath = System.getProperty("user.home") + java.io.File.separator + "Downloads";
    }
    
    public FileTransferHandler(String downloadPath) {
        this();
        this.downloadPath = downloadPath;
    }
    
    /**
     * 发起从发送方到接收方的文件传输
     */
    public void initiateFileTransfer(String sender, String receiver, String receiverIP, int receiverPort, File file) {
        // 将文件传输任务提交给执行服务
        executorService.submit(() -> {
            try {
                // 连接到接收方的文件传输服务器
                try (Socket socket = new Socket(receiverIP, receiverPort);
                     ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                     FileInputStream fis = new FileInputStream(file);
                     BufferedInputStream bis = new BufferedInputStream(fis)) {
                    
                    // 首先发送元数据
                    Message metadata = new Message(
                        MessageType.FILE_TRANSFER_REQUEST,
                        sender,
                        receiver,
                        file.getName(),
                        file.length()
                    );
                    
                    output.writeObject(metadata);
                    output.flush();
                    
                    // 分块发送文件数据
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long totalBytesSent = 0;
                    long fileSize = file.length();
                    
                    System.out.println("Starting file transfer: " + file.getName() + 
                                     " (" + fileSize + " bytes) from " + sender + " to " + receiver);
                    
                    while ((bytesRead = bis.read(buffer)) != -1) {
                        socket.getOutputStream().write(buffer, 0, bytesRead);
                        totalBytesSent += bytesRead;
                        
                        // 报告进度
                        double progress = (double) totalBytesSent / fileSize * 100;
                        System.out.printf("Transfer progress: %.2f%% (%d/%d bytes)\n", 
                                        progress, totalBytesSent, fileSize);
                    }
                    
                    System.out.println("File transfer completed: " + file.getName() + 
                                     " from " + sender + " to " + receiver);
                    
                    // 向发送方发送文件成功发送的确认
                    System.out.println("File transfer confirmed to sender: " + sender);
                }
            } catch (IOException e) {
                System.err.println("Error during file transfer from " + sender + " to " + receiver + 
                                 ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 启动监听传入文件传输的文件传输服务器
     */
    public int startTransferServer(int port) {
        final int[] assignedPort = new int[1];
        
        executorService.submit(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                assignedPort[0] = serverSocket.getLocalPort();
                System.out.println("File transfer server started on port " + assignedPort[0]);
                
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    handleIncomingTransfer(clientSocket);
                }
            } catch (IOException e) {
                System.err.println("Error in file transfer server: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // 等待服务器启动并获取端口
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return assignedPort[0];
    }
    
    /**
     * 处理传入的文件传输连接
     */
    private void handleIncomingTransfer(Socket clientSocket) {
        executorService.submit(() -> {
            try (Socket socket = clientSocket;
                 ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                
                // 读取传输元数据
                Message metadata = (Message) input.readObject();
                
                if (metadata.getType() == MessageType.FILE_TRANSFER_REQUEST) {
                    String filename = metadata.getContent();
                    long fileSize = (Long) metadata.getData();
                    String sender = metadata.getSender();
                    
                    System.out.println("Receiving file: " + filename + " (" + fileSize + " bytes) from " + sender);
                    
                    // 接收文件数据
                    receiveFileData(socket, filename, fileSize);
                    
                    // 文件传输成功完成
                    System.out.println("File transfer completed successfully: " + filename + " from " + sender);
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Error handling incoming transfer: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 从套接字接收文件数据并保存到磁盘
     */
    private void receiveFileData(Socket socket, String filename, long fileSize) throws IOException {
        // 确保下载目录存在
        java.io.File downloadDir = new java.io.File(downloadPath);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        
        // 创建文件路径
        String filePath = downloadPath + java.io.File.separator + "received_" + filename;
        
        try (FileOutputStream fos = new FileOutputStream(filePath);
             BufferedOutputStream bos = new BufferedOutputStream(fos)) {
            
            InputStream inputStream = socket.getInputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;
            
            while (totalBytesRead < fileSize && 
                   (bytesRead = inputStream.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
                
                // 报告进度（在实际实现中）
                double progress = (double) totalBytesRead / fileSize * 100;
                System.out.printf("Transfer progress: %.2f%%\n", progress);
            }
            
            System.out.println("File transfer completed: " + filename);
        }
    }
    
    /**
     * 向另一个客户端发送文件
     */
    public void sendFile(String hostname, int port, String sender, String receiver, File file) {
        executorService.submit(() -> {
            try (Socket socket = new Socket(hostname, port);
                 ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream());
                 FileInputStream fis = new FileInputStream(file);
                 BufferedInputStream bis = new BufferedInputStream(fis)) {
                
                // 首先发送元数据
                Message metadata = new Message(
                    MessageType.FILE_TRANSFER_REQUEST,
                    sender,
                    receiver,
                    file.getName(),
                    file.length()
                );
                
                output.writeObject(metadata);
                output.flush();
                
                byte[] buffer = new byte[4096];
                int bytesRead;
                long totalBytesSent = 0;
                long fileSize = file.length();
                
                System.out.println("Starting file transfer: " + file.getName() + 
                                 " (" + fileSize + " bytes) from " + sender + " to " + receiver);
                
                while ((bytesRead = bis.read(buffer)) != -1) {
                    socket.getOutputStream().write(buffer, 0, bytesRead);
                    totalBytesSent += bytesRead;
                    
                    // 报告进度
                    double progress = (double) totalBytesSent / fileSize * 100;
                    System.out.printf("Transfer progress: %.2f%% (%d/%d bytes)\n", 
                                    progress, totalBytesSent, fileSize);
                }
                
                System.out.println("File sent successfully: " + file.getName());
            } catch (IOException e) {
                System.err.println("Error sending file: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }
    
    /**
     * 关闭文件传输器
     */
    public void shutdown() {
        executorService.shutdownNow();
    }
}