package util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

public class NetworkUtil {
    
    /**
     * 获取本机IP地址
     * @return 本机IP地址字符串，如果无法确定则返回"localhost"
     */
    public static String getLocalIPAddress() {
        try {
            // 首先，尝试查找Tailscale IP地址
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                String displayName = iface.getDisplayName();
                String name = iface.getName();
                
                // 检查这是否是Tailscale接口
                if ((displayName != null && displayName.toLowerCase().contains("tailscale")) ||
                    (name != null && name.toLowerCase().contains("tailscale"))) {
                    Enumeration<InetAddress> addresses = iface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress addr = addresses.nextElement();
                        if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            
            // 如果没有找到Tailscale接口，则回退到原始方法
            try {
                // 尝试获取首选的本地IP地址
                InetAddress inetAddress = InetAddress.getLocalHost();
                if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                    return inetAddress.getHostAddress();
                }
            } catch (UnknownHostException e) {
                // 回退到替代方法
            }
            
            // 遍历所有网络接口以找到合适的IP
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || iface.isVirtual() || !iface.isUp()) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (!addr.isLoopbackAddress() && !addr.isLinkLocalAddress() && 
                        addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            // 无法确定IP地址
        }
        
        // 默认为localhost
        return "localhost";
    }
    
    /**
     * 获取公网/本地IP地址（排除Tailscale接口）
     * @return 公网/本地IP地址字符串，如果无法确定则返回"localhost"
     */
    public static String getPublicIPAddress() {
        try {
            // 遍历所有网络接口以找到合适的IP（排除虚拟网络）
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || iface.isVirtual() || !iface.isUp()) {
                    continue;
                }
                
                // 跳过虚拟网络接口（Tailscale、VPN等）
                String displayName = iface.getDisplayName();
                String name = iface.getName();
                if ((displayName != null && (displayName.toLowerCase().contains("tailscale") || 
                     displayName.toLowerCase().contains("vpn") || 
                     displayName.toLowerCase().contains("virtual"))) ||
                    (name != null && (name.toLowerCase().contains("tailscale") || 
                     name.toLowerCase().contains("vpn") || 
                     name.toLowerCase().contains("virtual")))) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    // 查找不是回环地址或链路本地地址的IPv4地址
                    if (addr instanceof java.net.Inet4Address && 
                        !addr.isLoopbackAddress() && 
                        !addr.isLinkLocalAddress()) {
                        // 优先选择站点本地地址（通常是局域网IP，如192.168.x.x或10.x.x.x）
                        if (addr.isSiteLocalAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            
            // 如果没有找到站点本地地址，则再次尝试接受任何非回环IPv4地址
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || iface.isVirtual() || !iface.isUp()) {
                    continue;
                }
                
                // 跳过虚拟网络接口
                String displayName = iface.getDisplayName();
                String name = iface.getName();
                if ((displayName != null && (displayName.toLowerCase().contains("tailscale") || 
                     displayName.toLowerCase().contains("vpn") || 
                     displayName.toLowerCase().contains("virtual"))) ||
                    (name != null && (name.toLowerCase().contains("tailscale") || 
                     name.toLowerCase().contains("vpn") || 
                     name.toLowerCase().contains("virtual")))) {
                    continue;
                }
                
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address && 
                        !addr.isLoopbackAddress() && 
                        !addr.isLinkLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            // 无法确定IP地址
        }
        
        // 尝试获取首选的本地IP地址作为回退
        try {
            InetAddress inetAddress = InetAddress.getLocalHost();
            if (!inetAddress.isLoopbackAddress() && !inetAddress.isLinkLocalAddress()) {
                return inetAddress.getHostAddress();
            }
        } catch (UnknownHostException e) {
            // 回退到替代方法
        }
        
        // 默认为localhost
        return "localhost";
    }
}