# P2P文件传输系统

## 项目简介

这是一个基于Java开发的P2P（点对点）文件传输系统，支持用户注册、登录、好友管理、安全文件传输等功能。系统采用客户端-服务端架构，通过Socket通信实现数据传输，并提供了图形用户界面(GUI)以便于操作。

## 技术栈

- Java 11+
- Maven (项目构建工具)
- Socket编程 (网络通信)
- 多线程技术 (并发处理)
- Swing (GUI界面)
- JUnit 5 & Mockito (单元测试)

## 项目结构

```
PROJECT_ROOT/
├── src/
│   └── main/
│       ├── java/
│       │   ├── client/
│       │   │   └── P2PClient.java          # 客户端主程序
│       │   ├── model/
│       │   │   ├── Message.java            # 消息实体类
│       │   │   ├── MessageType.java        # 消息类型枚举
│       │   │   └── User.java              # 用户实体类
│       │   ├── server/
│       │   │   ├── ClientHandler.java      # 客户端处理器
│       │   │   ├── P2PServer.java          # 服务端主程序
│       │   │   └── ServerGUI.java         # 服务端图形界面
│       │   ├── storage/
│       │   │   └── FileStorage.java       # 文件存储管理
│       │   ├── transfer/
│       │   │   └── FileTransferHandler.java # 文件传输处理器
│       │   └── util/
│       │       └── NetworkUtil.java        # 网络工具类
│       └── resources/
├── target/                                 # 编译输出目录
├── jre/                                    # 嵌入式Java运行环境
├── PIC/                                    # 图标资源文件
├── standalone/                             # 独立运行包
├── packaged/                               # 打包文件
└── pom.xml                                 # Maven配置文件
```

## 核心模块说明

### 1. 客户端模块 (client)
- **P2PClient.java**: 客户端主程序，负责用户界面展示、用户交互处理、与服务端通信等核心功能。

### 2. 数据模型模块 (model)
- **Message.java**: 消息实体类，定义了系统中传输的消息格式。
- **MessageType.java**: 消息类型枚举，定义了系统中各种消息的类型标识。
- **User.java**: 用户实体类，包含用户的基本信息和好友列表。

### 3. 服务端模块 (server)
- **P2PServer.java**: 服务端主程序，负责监听客户端连接、管理用户状态、转发消息等核心功能。
- **ClientHandler.java**: 客户端处理器，为每个连接的客户端创建独立的处理线程。
- **ServerGUI.java**: 服务端图形界面，提供服务端运行状态监控和管理功能。

### 4. 存储模块 (storage)
- **FileStorage.java**: 文件存储管理类，负责用户数据的持久化存储。

### 5. 传输模块 (transfer)
- **FileTransferHandler.java**: 文件传输处理器，负责处理客户端之间的文件传输逻辑。

### 6. 工具模块 (util)
- **NetworkUtil.java**: 网络工具类，提供网络相关的辅助功能，如获取本机IP地址等。

## 构建与运行

### 构建项目
```bash
# 使用Maven构建项目
mvn clean package
```

### 运行程序
```bash
# 运行服务端
java -jar P2PServer.jar

# 运行客户端
java -jar P2PClient.jar
```

## 功能特性

1. **用户管理**: 支持用户注册、登录、注销
2. **好友系统**: 支持好友添加、删除、好友列表管理
3. **文件传输**: 支持点对点安全文件传输
4. **图形界面**: 提供友好的用户操作界面
5. **多线程处理**: 支持多客户端并发连接
6. **网络自适应**: 支持Tailscale虚拟网络和公网IP

## 部署说明

项目支持打包为独立的可执行文件(.exe)，可在无Java环境的Windows系统上直接运行。
