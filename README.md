# P2P_File_Transmission_Tool
这是一个基于P2P架构的文件传输系统，支持用户登录、好友管理和点对点文件传输。该系统使用Java开发，具有直观的图形用户界面，能够安全高效地传输各种大小的文件。与其他传统局域网文件传输工具不同，本系统创新性地集成了Tailscale虚拟网络技术，突破了传统局域网的地理限制，使用户能够在任何网络环境下实现设备间的直连传输。无论是跨城市的办公协作，还是移动设备与家庭电脑之间的文件共享，都能轻松实现。系统采用端到端的安全通信机制，确保文件传输过程中的数据完整性与隐私保护。通过P2P直连传输模式，避免了中间服务器中转，既提高了传输效率，又增强了安全性，特别适合个人或小型团队设备间传输大文件和敏感数据。

代码文件已放master分支。其中，standalone文件为能直接运行的客户端与用户端打包文件。

使用教程：

    客户端使用：双击 standalone/client/run_client.bat或P2PClient.exe启动客户端
  
    服务端使用:双击 standalone/server/run_server.bat或P2PClient.exe启动服务端
  
    注意事项：必须在服务端启动服务，客户端才能连接。同时确保客户端与服务端在同一局域网下
  
    进阶玩法：下载TailScale自行组建虚拟局域网，加入TailScale的设备即可实现远程文件传输。
  
配置文件说明

  客户端配置文件
  
    config.properties: 客户端基本配置
    
    friend_remarks.properties: 好友备注信息
    
    login_history.properties: 登录历史记录
    
    server_history.properties: 历史连接服务器IP
    
  服务端配置文件
  
    config.properties: 服务端基本配置
