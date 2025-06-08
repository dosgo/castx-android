package com.dosgo.castx;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

public class TcpPortFinder {

    /**
     * 获取一个随机未使用的 TCP 端口
     * 
     * @return 找到的可用端口号，如果没有可用端口返回 -1
     */
    public static int getRandomAvailableTcpPort() {
        Random random = new Random();
        int maxAttempts = 50; // 最大尝试次数
        
        // 使用默认的可用端口范围（1024-49151）
        for (int i = 0; i < maxAttempts; i++) {
            // 生成随机端口号（1024-49151 范围）
            int port = 1024 + random.nextInt(49151 - 1024);
            
            if (isPortAvailable(port)) {
                return port;
            }
        }
        
        return -1; // 找不到可用端口
    }

    /**
     * 检查指定 TCP 端口是否可用
     * 
     * @param port 要检查的端口号
     * @return true 如果端口可用，false 如果被占用
     */
    private static boolean isPortAvailable(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true); // 允许端口快速重用
            return true;
        } catch (IOException e) {
            return false; // 端口已被占用
        }
    }
}