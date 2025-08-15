package com.zzk.web.service;

import org.springframework.stereotype.Service;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Service
public class DroneClientManager {
    
    private Process droneClientProcess;
    
    /**
     * 启动无人机客户端模拟程序
     */
    public boolean startDroneClient() throws IOException {
        // 如果已经运行，先停止
        if (isRunning()) {
            stopDroneClient();
        }
        
        // 获取jar文件路径（需要根据实际路径进行调整）
        String jarPath = "D:/GraduateStudent/ONE/test/Fly-master(CAMERA)/client/target/client-0.0.1-SNAPSHOT.jar";
        
        // 检查jar文件是否存在
        java.io.File jarFile = new java.io.File(jarPath);
        if (!jarFile.exists()) {
            throw new IOException("Drone client jar file not found: " + jarPath);
        }
        
        // 构建运行DroneClient的命令
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + "/bin/java";
        
        ProcessBuilder processBuilder = new ProcessBuilder(
            javaBin, "-jar", jarPath
        );
        
        // 重定向输出，以便在主程序中查看子进程的输出
        processBuilder.inheritIO();
        
        // 启动进程
        droneClientProcess = processBuilder.start();
        
        // 可选：启动线程来读取子进程的输出
        startOutputThread();
        
        return true;
    }
    
    /**
     * 启动线程读取子进程的输出
     */
    private void startOutputThread() {
        if (droneClientProcess != null) {
            // 启动线程读取标准输出
            Thread outputThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(droneClientProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[DroneClient] " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading DroneClient output: " + e.getMessage());
                }
            });
            outputThread.setDaemon(true);
            outputThread.start();
            
            // 启动线程读取错误输出
            Thread errorThread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(droneClientProcess.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.err.println("[DroneClient] " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading DroneClient error output: " + e.getMessage());
                }
            });
            errorThread.setDaemon(true);
            errorThread.start();
        }
    }
    
    /**
     * 停止无人机客户端模拟程序
     */
    public boolean stopDroneClient() {
        if (droneClientProcess != null && droneClientProcess.isAlive()) {
            droneClientProcess.destroy();
            try {
                droneClientProcess.waitFor();
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
    
    /**
     * 检查无人机客户端是否正在运行
     */
    public boolean isRunning() {
        return droneClientProcess != null && droneClientProcess.isAlive();
    }
}