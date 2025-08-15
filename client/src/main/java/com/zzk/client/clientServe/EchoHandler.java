package com.zzk.client.clientServe;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

//对RemoteID测试.txt文件进行解析，并写入bmhFlyData.txt文件
//对bmhFlyData.txt文件进行解析，并写入loginBytes和cardBytes
//对loginBytes和cardBytes进行解析，并写入bmhFlyData.txt文件
//对bmhFlyData.txt文件进行解析，并写入loginBytes和cardBytes
public class EchoHandler extends ChannelInboundHandlerAdapter {

    private static final String BMH_FLY_DATA_PATH = "D:\\GraduateStudent\\ONE\\test\\Fly-master(CAMERA)\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\bmhFlyData.txt";
    private static final String REMOTE_ID_PATH = "D:\\GraduateStudent\\ONE\\test\\Fly-master(CAMERA)\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\500m内.txt";

    private ExecutorService fileWriteExecutor = new ThreadPoolExecutor(
            5, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(100)
    );
    private ExecutorService fileReadExecutor = new ThreadPoolExecutor(
            5, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(100)
    );
    private ReentrantLock fileWriteLock = new ReentrantLock();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 线程1：实时读取bmhFlyData.txt文件并进行编码解码
        fileReadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    processBmhFlyData(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // 线程2：监控RemoteID测试.txt文件并解析写入bmhFlyData.txt
        fileWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    watchAndParseRemoteID();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void watchAndParseRemoteID() throws IOException, InterruptedException {
        Path remoteIdPath = Paths.get(REMOTE_ID_PATH);
        if (!Files.exists(remoteIdPath)) {
            throw new FileNotFoundException("RemoteID测试.txt not found");
        }

        // 文件变化监听器
        WatchService watchService = FileSystems.getDefault().newWatchService();
        remoteIdPath.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

        // 初始解析RemoteID测试.txt并写入bmhFlyData.txt
        parseRemoteIDAndWriteToBmhFlyData();

        while (true) {
            WatchKey key = watchService.take();
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    Path changedFile = (Path) event.context();
                    if (changedFile.equals(remoteIdPath.getFileName())) {
                        fileWriteLock.lock();
                        try {
                            // 读取新增的数据并写入bmhFlyData.txt
                            parseNewRemoteIDData();
                        } finally {
                            fileWriteLock.unlock();
                        }
                    }
                }
            }
            boolean valid = key.reset();
            if (!valid) {
                break;
            }
        }
    }

    // 解析RemoteID测试.txt所有内容，写入bmhFlyData.txt（保留前两行）
    private void parseRemoteIDAndWriteToBmhFlyData() {
        try {
            fileWriteLock.lock();
            Path bmhPath = Paths.get(BMH_FLY_DATA_PATH);
            if (!Files.exists(bmhPath.getParent())) {
                Files.createDirectories(bmhPath.getParent());
            }

            // 读取前两行并保留
            List<String> existingLines = new ArrayList<String>();
            if (Files.exists(bmhPath)) {
                BufferedReader reader = new BufferedReader(new FileReader(bmhPath.toString()));
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null && count < 2) {
                    existingLines.add(line);
                    count++;
                }
                reader.close();
            }

            // 清空文件并写入前两行
            BufferedWriter writer = new BufferedWriter(new FileWriter(bmhPath.toString(), false));
            for (String line : existingLines) {
                writer.write(line);
                writer.newLine();
            }

            // 解析RemoteID测试.txt
            BufferedReader reader = new BufferedReader(new FileReader(REMOTE_ID_PATH));
            String line;
            int index = 0;
            List<String> group = new ArrayList<String>();
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                group.add(line.trim());
                if (group.size() == 10) {
                    // 解析经纬度
                    String droneLat = null, droneLng = null;
                    for (String item : group) {
                        if (item.startsWith("Drone latitude:")) {
                            droneLat = item.substring("Drone latitude:".length()).trim();
                        }
                        if (item.startsWith("Drone longitude:")) {
                            droneLng = item.substring("Drone longitude:".length()).trim();
                        }
                    }
                    if (droneLat != null && droneLng != null) {
                        String formattedLine = String.format("%d  %s  %s", index, droneLng, droneLat);
                        writer.write(formattedLine);
                        writer.newLine();
                        writer.flush();
                        index++;
                    }
                    group.clear();
                }
            }
            reader.close();
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileWriteLock.isHeldByCurrentThread()) {
                fileWriteLock.unlock();
            }
        }
    }

    // 只追加新增的组
    private void parseNewRemoteIDData() {
        try {
            Path bmhPath = Paths.get(BMH_FLY_DATA_PATH);
            if (!Files.exists(bmhPath)) {
                return;
            }

            // 记录bmhFlyData.txt的当前行数
            long bmhLineCount = 0;
            BufferedReader reader = new BufferedReader(new FileReader(bmhPath.toString()));
            while (reader.readLine() != null) {
                bmhLineCount++;
            }
            reader.close();

            // 读取并写入新增的数据
            reader = new BufferedReader(new FileReader(REMOTE_ID_PATH));
            String line;
            int index = (int) bmhLineCount - 2; // 跳过前两行
            int skippedGroups = index;
            int groupCount = 0;
            List<String> group = new ArrayList<String>();
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                group.add(line.trim());
                if (group.size() == 11) {
                    if (groupCount < skippedGroups) {
                        groupCount++;
                        group.clear();
                        continue;
                    }
                    // 解析经纬度
                    String droneLat = null, droneLng = null;
                    for (String item : group) {
                        if (item.startsWith("Drone latitude:")) {
                            droneLat = item.substring("Drone latitude:".length()).trim();
                        }
                        if (item.startsWith("Drone longitude:")) {
                            droneLng = item.substring("Drone longitude:".length()).trim();
                        }
                    }
                    if (droneLat != null && droneLng != null) {
                        String formattedLine = String.format("%d  %s  %s", index, droneLng, droneLat);
                        BufferedWriter writer = new BufferedWriter(new FileWriter(bmhPath.toString(), true));
                        writer.write(formattedLine);
                        writer.newLine();
                        writer.flush();
                        writer.close();
                        index++;
                    }
                    group.clear();
                    groupCount++;
                }
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 下面是原有的bmhFlyData.txt实时读取和编码解码逻辑
    private void processBmhFlyData(ChannelHandlerContext ctx) throws IOException {
        Path path = Paths.get(BMH_FLY_DATA_PATH);
        Scanner scanner = new Scanner(new BufferedReader(new FileReader(path.toString())));
        String droneId = "";
        String cardId = "";
        byte[] loginBytes = null;
        byte[] cardBytes = null;

        // 读取前两行
        if (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.trim().isEmpty()) {
                droneId = line.trim();
            }
        }
        if (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.trim().isEmpty()) {
                cardId = line.trim();
            }
        }

        // 构造loginBytes
        if (!droneId.isEmpty() && !cardId.isEmpty()) {
            loginBytes = new byte[18];
            // 这里假设你有ByteUtils工具类
            byte[] droneBytes = ByteUtils.hexString2Bytes(droneId);
            cardBytes = ByteUtils.string2HexBytes(cardId);

            loginBytes[0] = 0x01;
            ByteUtils.fillBytes(loginBytes, droneBytes, 1);
            ByteUtils.fillBytes(loginBytes, cardBytes, 9);
            loginBytes[14] = 0x00;
            loginBytes[15] = 0x01;
            loginBytes[16] = (byte) 0x8C;
            loginBytes[17] = (byte) 0xDD;

            // 发送loginBytes
            if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                ctx.writeAndFlush(loginBytes);
            } else {
                System.err.println("Connection is not active or writable.");
                scanner.close();
                return;
            }
        }

        // 处理后续的定位数据
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (line.trim().isEmpty())
                continue;

            String[] parts = line.split("  ");
            if (parts.length >= 3) {
                // 这里假设你有ByteUtils和Gps工具类
                byte[] positioningBytes = new byte[40];
                positioningBytes[0] = 0x02;
                ByteUtils.fillBytes(positioningBytes, cardBytes, 1);
                positioningBytes[6] = 0x01;
                LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                positioningBytes[7] = (byte) (now.getYear() - 2000);
                positioningBytes[8] = (byte) now.getMonthValue();
                positioningBytes[9] = (byte) now.getDayOfMonth();
                positioningBytes[10] = (byte) now.getHour();
                positioningBytes[11] = (byte) now.getMinute();
                positioningBytes[12] = (byte) now.getSecond();

                double lngDouble = Double.parseDouble(parts[1]);
                double latDouble = Double.parseDouble(parts[2]);
                Gps gps = new Gps(latDouble, lngDouble);

                byte[] lngBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLon() * 60 * 30000));
                ByteUtils.fillBytes(positioningBytes, lngBytes, 13);

                byte[] latBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLat() * 60 * 30000));
                ByteUtils.fillBytes(positioningBytes, latBytes, 17);

                Random rand = new Random();
                positioningBytes[21] = 0x00;
                positioningBytes[22] = (byte) (rand.nextInt(61) + 40);
                positioningBytes[23] = 0x00;
                positioningBytes[24] = (byte) (rand.nextInt(71) + 40);
                positioningBytes[25] = 0x00;
                positioningBytes[26] = (byte) (rand.nextInt(71) + 40);
                positioningBytes[27] = 0x01;
                positioningBytes[28] = (byte) 0xCC;
                positioningBytes[29] = 0x00;
                positioningBytes[30] = 0x28;
                positioningBytes[31] = 0x7D;
                positioningBytes[32] = 0x00;
                positioningBytes[33] = 0x00;
                positioningBytes[34] = 0x1F;
                positioningBytes[35] = (byte) 0xB8;
                positioningBytes[36] = 0x00;
                positioningBytes[37] = 0x01;
                positioningBytes[38] = (byte) 0x8C;
                positioningBytes[39] = (byte) 0xDD;

                if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                    ctx.writeAndFlush(positioningBytes);
                } else {
                    System.err.println("Connection is not active or writable.");
                    break;
                }
            }

            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        scanner.close();
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
}

//对DroneFlightLog.txt文件进行解析，并写入bmhFlyData.txt文件
//对bmhFlyData.txt文件进行解析，并写入loginBytes和cardBytes
//对loginBytes和cardBytes进行解析，并写入bmhFlyData.txt文件
//对bmhFlyData.txt文件进行解析，并写入loginBytes和cardBytes
/*public class EchoHandler extends ChannelInboundHandlerAdapter {

    private static final String BMH_FLY_DATA_PATH = "D:\\GraduateStudent\\ONE\\test\\Fly-master1\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\bmhFlyData.txt";
    private static final String DRONE_LOG_PATH = "D:\\GraduateStudent\\ONE\\test\\Fly-master1\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\DroneFlightLog.txt";

    private ExecutorService fileWriteExecutor = new ThreadPoolExecutor(
            5, // 核心线程数
            10, // 最大线程数
            60, // 空闲线程存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100) // 任务队列大小
    );

    private ExecutorService fileReadExecutor = new ThreadPoolExecutor(
            5, // 核心线程数
            10, // 最大线程数
            60, // 空闲线程存活时间
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100) // 任务队列大小
    );

    private ReentrantLock fileWriteLock = new ReentrantLock();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        // 启动一个线程用于实时读取bmhFlyData.txt文件并进行编码解码
        fileReadExecutor.execute(() -> {
            try {
                processBmhFlyData(ctx);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // 启动一个线程用于监控DroneFlightLog.txt文件并解析写入bmhFlyData.txt
        fileWriteExecutor.execute(() -> {
            try {
                watchAndParseDroneLog();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void watchAndParseDroneLog() throws IOException, InterruptedException {
        Path droneLogPath = Paths.get(DRONE_LOG_PATH);
        if (!Files.exists(droneLogPath)) {
            throw new FileNotFoundException("DroneFlightLog.txt not found");
        }

        // 文件变化监听器
        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            droneLogPath.getParent().register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            // 初始解析DroneFlightLog.txt并写入bmhFlyData.txt
            parseDroneLogAndWriteToBmhFlyData();

            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        Path changedFile = (Path) event.context();
                        if (changedFile.equals(droneLogPath.getFileName())) {
                            fileWriteLock.lock();
                            try {
                                // 读取新增的数据并写入bmhFlyData.txt
                                parseNewDroneLogData();
                            } finally {
                                fileWriteLock.unlock();
                            }
                        }
                    }
                }
                boolean valid = key.reset();
                if (!valid) {
                    break;
                }
            }
        }
    }

    private void parseDroneLogAndWriteToBmhFlyData() {
        try {
            fileWriteLock.lock();
            Path bmhPath = Paths.get(BMH_FLY_DATA_PATH);
            if (!Files.exists(bmhPath.getParent())) {
                Files.createDirectories(bmhPath.getParent());
            }

            // 读取前两行并保留
            List<String> existingLines = new ArrayList<>();
            if (Files.exists(bmhPath)) {
                try (BufferedReader reader = new BufferedReader(new FileReader(bmhPath.toString()))) {
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null && count < 2) {
                        existingLines.add(line);
                        count++;
                    }
                }
            }

            // 清空文件并写入前两行
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(bmhPath.toString(), false))) {
                for (String line : existingLines) {
                    writer.write(line);
                    writer.newLine();
                }

                // 从第三行开始写入新的数据
                try (BufferedReader reader = new BufferedReader(new FileReader(DRONE_LOG_PATH))) {
                    StringBuilder buffer = new StringBuilder();
                    String line;
                    int index = 0;

                    while ((line = reader.readLine()) != null) {
                        if (line.trim().isEmpty())
                            continue;

                        // 去除行尾的"*#"字符
                        if (line.endsWith("*#")) {
                            line = line.substring(0, line.length() - 2);
                        }

                        // 检查是否是JSON对象的一部分
                        if (line.startsWith("{")) {
                            buffer.setLength(0); // 清空缓冲区
                            buffer.append(line);
                        } else if (line.endsWith("}")) {
                            buffer.append(line);
                            String jsonStr = buffer.toString();
                            try {
                                // 预处理JSON字符串
                                String processedLine = preprocessLine(jsonStr);
                                if (processedLine == null) {
                                    System.err.println("Skipped invalid line: " + jsonStr);
                                    continue;
                                }

                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode rootNode = mapper.readTree(processedLine);

                                if (rootNode.has("Location")) {
                                    JsonNode locationNode = rootNode.get("Location");
                                    if (locationNode.has("lat") && locationNode.has("lon")) {
                                        double lat = locationNode.get("lat").asDouble();
                                        double lon = locationNode.get("lon").asDouble();

                                        String formattedLine = String.format("%d  %f  %f", index, lon, lat);
                                        writer.write(formattedLine);
                                        writer.newLine();
                                        writer.flush();

                                        System.out.println("Written to bmhFlyData.txt: " + formattedLine);
                                        index++;
                                    }
                                }
                            } catch (Exception e) {
                                System.err.println("Error parsing line: " + jsonStr);
                                e.printStackTrace();
                            }
                        } else {
                            buffer.append(line);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileWriteLock.isHeldByCurrentThread()) {
                fileWriteLock.unlock();
            }
        }
    }

    private void parseNewDroneLogData() {
        try {
            Path bmhPath = Paths.get(BMH_FLY_DATA_PATH);
            if (!Files.exists(bmhPath)) {
                return;
            }

            // 记录bmhFlyData.txt的当前行数
            long bmhLineCount = 0;
            try (BufferedReader reader = new BufferedReader(new FileReader(bmhPath.toString()))) {
                while (reader.readLine() != null) {
                    bmhLineCount++;
                }
            }

            // 读取并写入新增的数据
            try (BufferedReader reader = new BufferedReader(new FileReader(DRONE_LOG_PATH))) {
                StringBuilder buffer = new StringBuilder();
                String line;
                int index = (int) bmhLineCount - 2; // 跳过前两行
                boolean skipLines = true;
                int skippedLines = 0;

                while ((line = reader.readLine()) != null) {
                    if (skipLines) {
                        if (skippedLines < bmhLineCount - 2) {
                            skippedLines++;
                            continue;
                        }
                        skipLines = false;
                    }

                    if (line.trim().isEmpty())
                        continue;

                    // 去除行尾的"*#"字符
                    if (line.endsWith("*#")) {
                        line = line.substring(0, line.length() - 2);
                    }

                    // 检查是否是JSON对象的一部分
                    if (line.startsWith("{")) {
                        buffer.setLength(0); // 清空缓冲区
                        buffer.append(line);
                    } else if (line.endsWith("}")) {
                        buffer.append(line);
                        String jsonStr = buffer.toString();
                        try {
                            // 预处理JSON字符串
                            String processedLine = preprocessLine(jsonStr);
                            if (processedLine == null) {
                                System.err.println("Skipped invalid line: " + jsonStr);
                                continue;
                            }

                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode rootNode = mapper.readTree(processedLine);

                            if (rootNode.has("Location")) {
                                JsonNode locationNode = rootNode.get("Location");
                                if (locationNode.has("lat") && locationNode.has("lon")) {
                                    double lat = locationNode.get("lat").asDouble();
                                    double lon = locationNode.get("lon").asDouble();

                                    String formattedLine = String.format("%d  %f  %f", index, lon, lat);
                                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(bmhPath.toString(), true))) {
                                        writer.write(formattedLine);
                                        writer.newLine();
                                        writer.flush();
                                    }

                                    System.out.println("Appended to bmhFlyData.txt: " + formattedLine);
                                    index++;
                                }
                            }
                        } catch (Exception e) {
                            System.err.println("Error parsing line: " + jsonStr);
                            e.printStackTrace();
                        }
                    } else {
                        buffer.append(line);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String preprocessLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        // 去除行尾的"*#"字符
        if (line.endsWith("*#")) {
            line = line.substring(0, line.length() - 2);
        }

        // 确保行以大括号开头和结尾
        if (!line.startsWith("{")) {
            line = "{" + line;
        }
        if (!line.endsWith("}")) {
            line = line + "}";
        }

        // 检查是否包含必要的字段
        if (!line.contains("\"Location\":")) {
            return null;
        }

        return line;
    }

    private void processBmhFlyData(ChannelHandlerContext ctx) throws IOException {
        Path path = Paths.get(BMH_FLY_DATA_PATH);
        try (Scanner scanner = new Scanner(new BufferedReader(new FileReader(path.toString())))) {
            // 读取前两行以构造loginBytes
            String droneId = "";
            String cardId = "";
            byte[] loginBytes = null;
            byte[] cardBytes = null;

            // 读取前两行
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.trim().isEmpty()) {
                    droneId = line.trim();
                }
            }
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (!line.trim().isEmpty()) {
                    cardId = line.trim();
                }
            }

            // 构造loginBytes
            if (!droneId.isEmpty() && !cardId.isEmpty()) {
                loginBytes = new byte[18];
                byte[] droneBytes = ByteUtils.hexString2Bytes(droneId);
                cardBytes = ByteUtils.string2HexBytes(cardId);
                System.out.println("cardBylen" + cardBytes.length);

                loginBytes[0] = 0x01;
                ByteUtils.fillBytes(loginBytes, droneBytes, 1);
                ByteUtils.fillBytes(loginBytes, cardBytes, 9);
                loginBytes[14] = 0x00;
                loginBytes[15] = 0x01;
                loginBytes[16] = (byte) 0x8C;
                loginBytes[17] = (byte) 0xDD;

                // 发送loginBytes
                if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                    ctx.writeAndFlush(loginBytes);
                } else {
                    System.err.println("Connection is not active or writable.");
                    return;
                }
            }

            // 处理后续的定位数据
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.trim().isEmpty())
                    continue;

                String[] parts = line.split("  ");
                if (parts.length >= 3) {
                    String positionData = parts[2];

                    // 定位数据包
                    byte[] positioningBytes = new byte[40];
                    positioningBytes[0] = 0x02;
                    ByteUtils.fillBytes(positioningBytes, cardBytes, 1);
                    positioningBytes[6] = 0x01;
                    LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                    positioningBytes[7] = (byte) (now.getYear() - 2000);
                    positioningBytes[8] = (byte) now.getMonthValue();
                    positioningBytes[9] = (byte) now.getDayOfMonth();
                    positioningBytes[10] = (byte) now.getHour();
                    positioningBytes[11] = (byte) now.getMinute();
                    positioningBytes[12] = (byte) now.getSecond();

                    // 经度和纬度
                    double lngDouble = Double.parseDouble(parts[1]);
                    double latDouble = Double.parseDouble(parts[2]);

                    // 假设 PositionUtil.gcj02_To_Gps84 是正确的
                    // 如果不需要转换，可以直接使用 latDouble 和 lngDouble
                    // Gps gps = PositionUtil.gcj02_To_Gps84(latDouble, lngDouble);
                    Gps gps = new Gps(latDouble,lngDouble);

                    byte[] lngBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLon() * 60 * 30000));
                    ByteUtils.fillBytes(positioningBytes, lngBytes, 13);

                    byte[] latBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLat() * 60 * 30000));
                    ByteUtils.fillBytes(positioningBytes, latBytes, 17);

                    // 随机数据
                    Random rand = new Random();
                    positioningBytes[21] = 0x00;
                    positioningBytes[22] = (byte) (rand.nextInt(61) + 40);
                    positioningBytes[23] = 0x00;
                    positioningBytes[24] = (byte) (rand.nextInt(71) + 40);
                    positioningBytes[25] = 0x00;
                    positioningBytes[26] = (byte) (rand.nextInt(71) + 40);
                    positioningBytes[27] = 0x01;
                    positioningBytes[28] = (byte) 0xCC;
                    positioningBytes[29] = 0x00;
                    positioningBytes[30] = 0x28;
                    positioningBytes[31] = 0x7D;
                    positioningBytes[32] = 0x00;
                    positioningBytes[33] = 0x00;
                    positioningBytes[34] = 0x1F;
                    positioningBytes[35] = (byte) 0xB8;
                    positioningBytes[36] = 0x00;
                    positioningBytes[37] = 0x01;
                    positioningBytes[38] = (byte) 0x8C;
                    positioningBytes[39] = (byte) 0xDD;

                    // 发送定位数据包
                    if (ctx.channel().isActive() && ctx.channel().isWritable()) {
                        ctx.writeAndFlush(positioningBytes);
                    } else {
                        System.err.println("Connection is not active or writable.");
                        break;
                    }
                }

                // 等待一段时间以模拟实时处理
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }
} */

/*实现写入和编解码，未加入监听文件代码如下： */
// package com.zzk.client.clientServe;

// import io.netty.buffer.ByteBuf;
// import io.netty.channel.ChannelHandlerContext;
// import io.netty.channel.ChannelInboundHandlerAdapter;

// import com.fasterxml.jackson.databind.ObjectMapper;//新加入的包，用于进行预处理，生成合法的JSON字符串
// import com.fasterxml.jackson.databind.JsonNode;

// import java.io.*;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.nio.file.Paths;
// import java.time.LocalDateTime;
// import java.time.ZoneId;
// import java.util.*;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;
// import java.util.concurrent.LinkedBlockingQueue;
// import java.util.concurrent.ThreadPoolExecutor;
// import java.util.concurrent.TimeUnit;
// import java.util.concurrent.locks.ReentrantLock;


// public class EchoHandler extends ChannelInboundHandlerAdapter {

//     private static final String BMH_FLY_DATA_PATH = "D:\\GraduateStudent\\ONE\\test\\Fly-master(Uav nose)\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\bmhFlyData.txt";
//     private static final String DRONE_LOG_PATH = "D:\\GraduateStudent\\ONE\\test\\Fly-master(Uav nose)\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\DroneFlightLog.txt";

//     private ExecutorService fileWriteExecutor = new ThreadPoolExecutor(
//             5, // 核心线程数
//             10, // 最大线程数
//             60, // 空闲线程存活时间
//             TimeUnit.SECONDS,
//             new LinkedBlockingQueue<>(100) // 任务队列大小
//     );
//     private ExecutorService fileReadExecutor = new ThreadPoolExecutor(
//             5, // 核心线程数
//             10, // 最大线程数
//             60, // 空闲线程存活时间
//             TimeUnit.SECONDS,
//             new LinkedBlockingQueue<>(100) // 任务队列大小
//     );

//     private ReentrantLock fileWriteLock = new ReentrantLock();

//     @Override
//     public void channelActive(ChannelHandlerContext ctx) throws Exception {
//         // 启动一个线程用于实时读取bmhFlyData.txt文件并进行编码解码
//         fileReadExecutor.execute(() -> {
//             try {
//                 processBmhFlyData(ctx);
//             } catch (Exception e) {
//                 e.printStackTrace();
//             }
//         });

//         // 启动一个线程用于解析无人机飞行日志并写入bmhFlyData.txt
//         fileWriteExecutor.execute(() -> {
//             try {
//                 parseDroneLogAndWriteToBmhFlyData();
//             } catch (Exception e) {
//                 e.printStackTrace();
//             }
//         });
//     }

//     private void parseDroneLogAndWriteToBmhFlyData() {
//         try {
//             fileWriteLock.lock();
//             Path bmhPath = Paths.get(BMH_FLY_DATA_PATH);
//             if (!Files.exists(bmhPath.getParent())) {
//                 Files.createDirectories(bmhPath.getParent());
//             }
    
//             // 读取前两行并保留
//             List<String> existingLines = new ArrayList<>();
//             if (Files.exists(bmhPath)) {
//                 try (BufferedReader reader = new BufferedReader(new FileReader(bmhPath.toString()))) {
//                     String line;
//                     int count = 0;
//                     while ((line = reader.readLine()) != null && count < 2) {
//                         existingLines.add(line);
//                         count++;
//                     }
//                 }
//             }
    
//             // 清空文件并写入前两行
//             try (BufferedWriter writer = new BufferedWriter(new FileWriter(bmhPath.toString(), false))) {
//                 for (String line : existingLines) {
//                     writer.write(line);
//                     writer.newLine();
//                 }
    
//                 // 从第三行开始写入新的数据
//                 try (BufferedReader reader = new BufferedReader(new FileReader(DRONE_LOG_PATH))) {
//                     StringBuilder buffer = new StringBuilder();
//                     String line;
//                     int index = 0;
    
//                     while ((line = reader.readLine()) != null) {
//                         if (line.trim().isEmpty())
//                             continue;
    
//                         // 去除行尾的"*#"字符
//                         if (line.endsWith("*#")) {
//                             line = line.substring(0, line.length() - 2);
//                         }
    
//                         // 检查是否是JSON对象的一部分
//                         if (line.startsWith("{")) {
//                             buffer.setLength(0); // 清空缓冲区
//                             buffer.append(line);
//                         } else if (line.endsWith("}")) {
//                             buffer.append(line);
//                             String jsonStr = buffer.toString();
//                             try {
//                                 // 预处理JSON字符串
//                                 String processedLine = preprocessLine(jsonStr);
//                                 if (processedLine == null) {
//                                     System.err.println("Skipped invalid line: " + jsonStr);
//                                     continue;
//                                 }
    
//                                 ObjectMapper mapper = new ObjectMapper();
//                                 JsonNode rootNode = mapper.readTree(processedLine);
    
//                                 if (rootNode.has("Location")) {
//                                     JsonNode locationNode = rootNode.get("Location");
//                                     if (locationNode.has("lat") && locationNode.has("lon")) {
//                                         double lat = locationNode.get("lat").asDouble();
//                                         double lon = locationNode.get("lon").asDouble();
    
//                                         String formattedLine = String.format("%d  %f  %f", index, lon, lat);
//                                         writer.write(formattedLine);
//                                         writer.newLine();
//                                         writer.flush();
    
//                                         System.out.println("Written to bmhFlyData.txt: " + formattedLine);
//                                         index++;
//                                     }
//                                 }
//                             } catch (Exception e) {
//                                 System.err.println("Error parsing line: " + jsonStr);
//                                 e.printStackTrace();
//                             }
//                         } else {
//                             buffer.append(line);
//                         }
//                     }
//                 }
//             }
//         } catch (Exception e) {
//             e.printStackTrace();
//         } finally {
//             if (fileWriteLock.isHeldByCurrentThread()) {
//                 fileWriteLock.unlock();
//             }
//         }
//     }

//     /**
//      * 预处理每一行数据，转换为合法的JSON格式
//      * 
//      * @param line 原始行数据
//      * @return 合法的JSON字符串，或null表示无效行
//      */
//     private String preprocessLine(String line) {
//         if (line == null || line.trim().isEmpty()) {
//             return null;
//         }

//         // 去除行尾的"*#"字符
//         if (line.endsWith("*#")) {
//             line = line.substring(0, line.length() - 2);
//         }

//         // 确保行以大括号开头和结尾
//         if (!line.startsWith("{")) {
//             line = "{" + line;
//         }
//         if (!line.endsWith("}")) {
//             line = line + "}";
//         }

//         // 检查是否包含必要的字段
//         if (!line.contains("\"Location\":")) {
//             return null;
//         }

//         return line;
//     }

//     private void processBmhFlyData(ChannelHandlerContext ctx) throws IOException {
//         Path path = Paths.get(BMH_FLY_DATA_PATH);
//         try (Scanner scanner = new Scanner(new BufferedReader(new FileReader(path.toString())))) {
//             // 读取前两行以构造loginBytes
//             String droneId = "";
//             String cardId = "";
//             byte[] loginBytes = null;
//             byte[] cardBytes = null;

//             // 读取前两行
//             if (scanner.hasNextLine()) {
//                 String line = scanner.nextLine();
//                 if (!line.trim().isEmpty()) {
//                     droneId = line.trim();
//                 }
//             }
//             if (scanner.hasNextLine()) {
//                 String line = scanner.nextLine();
//                 if (!line.trim().isEmpty()) {
//                     cardId = line.trim();
//                 }
//             }

//             // 构造loginBytes
//             if (!droneId.isEmpty() && !cardId.isEmpty()) {
//                 loginBytes = new byte[18];
//                 byte[] droneBytes = ByteUtils.hexString2Bytes(droneId);
//                 cardBytes = ByteUtils.string2HexBytes(cardId);
//                 System.out.println("cardBylen" + cardBytes.length);

//                 loginBytes[0] = 0x01;
//                 ByteUtils.fillBytes(loginBytes, droneBytes, 1);
//                 ByteUtils.fillBytes(loginBytes, cardBytes, 9);
//                 loginBytes[14] = 0x00;
//                 loginBytes[15] = 0x01;
//                 loginBytes[16] = (byte) 0x8C;
//                 loginBytes[17] = (byte) 0xDD;

//                 // 发送loginBytes
//                 if (ctx.channel().isActive() && ctx.channel().isWritable()) {
//                     ctx.writeAndFlush(loginBytes);
//                 } else {
//                     System.err.println("Connection is not active or writable.");
//                     return;
//                 }
//             }

//             // 处理后续的定位数据
//             while (scanner.hasNextLine()) {
//                 String line = scanner.nextLine();
//                 if (line.trim().isEmpty())
//                     continue;

//                 String[] parts = line.split("  ");
//                 if (parts.length >= 3) {
//                     String positionData = parts[2];

//                     // 定位数据包
//                     byte[] positioningBytes = new byte[40];
//                     positioningBytes[0] = 0x02;
//                     ByteUtils.fillBytes(positioningBytes, cardBytes, 1);
//                     positioningBytes[6] = 0x01;
//                     LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
//                     positioningBytes[7] = (byte) (now.getYear() - 2000);
//                     positioningBytes[8] = (byte) now.getMonthValue();
//                     positioningBytes[9] = (byte) now.getDayOfMonth();
//                     positioningBytes[10] = (byte) now.getHour();
//                     positioningBytes[11] = (byte) now.getMinute();
//                     positioningBytes[12] = (byte) now.getSecond();

//                     // 经度和纬度
//                     double lngDouble = Double.parseDouble(parts[1]);
//                     double latDouble = Double.parseDouble(parts[2]);

//                     // 假设 PositionUtil.gcj02_To_Gps84 是正确的
//                     // 如果不需要转换，可以直接使用 latDouble 和 lngDouble
//                     // Gps gps = PositionUtil.gcj02_To_Gps84(latDouble, lngDouble);
//                     Gps gps = new Gps(latDouble,lngDouble);

//                     byte[] lngBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLon() * 60 * 30000));
//                     ByteUtils.fillBytes(positioningBytes, lngBytes, 13);

//                     byte[] latBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLat() * 60 * 30000));
//                     ByteUtils.fillBytes(positioningBytes, latBytes, 17);

//                     // 随机数据
//                     Random rand = new Random();
//                     positioningBytes[21] = 0x00;
//                     positioningBytes[22] = (byte) (rand.nextInt(61) + 40);
//                     positioningBytes[23] = 0x00;
//                     positioningBytes[24] = (byte) (rand.nextInt(71) + 40);
//                     positioningBytes[25] = 0x00;
//                     positioningBytes[26] = (byte) (rand.nextInt(71) + 40);
//                     positioningBytes[27] = 0x01;
//                     positioningBytes[28] = (byte) 0xCC;
//                     positioningBytes[29] = 0x00;
//                     positioningBytes[30] = 0x28;
//                     positioningBytes[31] = 0x7D;
//                     positioningBytes[32] = 0x00;
//                     positioningBytes[33] = 0x00;
//                     positioningBytes[34] = 0x1F;
//                     positioningBytes[35] = (byte) 0xB8;
//                     positioningBytes[36] = 0x00;
//                     positioningBytes[37] = 0x01;
//                     positioningBytes[38] = (byte) 0x8C;
//                     positioningBytes[39] = (byte) 0xDD;

//                     // 发送定位数据包
//                     if (ctx.channel().isActive() && ctx.channel().isWritable()) {
//                         ctx.writeAndFlush(positioningBytes);
//                     } else {
//                         System.err.println("Connection is not active or writable.");
//                         break;
//                     }
//                 }

//                 // 等待一段时间以模拟实时处理
//                 try {
//                     Thread.sleep(2000);
//                 } catch (InterruptedException e) {
//                     e.printStackTrace();
//                 }
//             }
//         } catch (IOException e) {
//             e.printStackTrace();
//         }
//     }
//     @Override
//     public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
//             throws Exception {
//         cause.printStackTrace();
//         ctx.close();
//     }

// }





    // private void parseDroneLogAndWriteToBmhFlyData() {
    //     try {
    //         fileWriteLock.lock();
    //         Path bmhPath = Paths.get(BMH_FLY_DATA_PATH);
    //         if (!Files.exists(bmhPath.getParent())) {
    //             Files.createDirectories(bmhPath.getParent());
    //         }

    //         try (BufferedWriter writer = new BufferedWriter(new FileWriter(BMH_FLY_DATA_PATH, true))) {
    //             try (BufferedReader reader = new BufferedReader(new FileReader(DRONE_LOG_PATH))) {
    //                 StringBuilder buffer = new StringBuilder();
    //                 String line;
    //                 int index = 0;

    //                 while ((line = reader.readLine()) != null) {
    //                     if (line.trim().isEmpty())
    //                         continue;

    //                     // 去除行尾的"*#"字符
    //                     if (line.endsWith("*#")) {
    //                         line = line.substring(0, line.length() - 2);
    //                     }

    //                     // 检查是否是JSON对象的一部分
    //                     if (line.startsWith("{")) {
    //                         buffer.setLength(0); // 清空缓冲区
    //                         buffer.append(line);
    //                     } else if (line.endsWith("}")) {
    //                         buffer.append(line);
    //                         String jsonStr = buffer.toString();
    //                         try {
    //                             // 预处理JSON字符串
    //                             String processedLine = preprocessLine(jsonStr);
    //                             if (processedLine == null) {
    //                                 System.err.println("Skipped invalid line: " + jsonStr);
    //                                 continue;
    //                             }

    //                             ObjectMapper mapper = new ObjectMapper();
    //                             JsonNode rootNode = mapper.readTree(processedLine);

    //                             if (rootNode.has("Location")) {
    //                                 JsonNode locationNode = rootNode.get("Location");
    //                                 if (locationNode.has("lat") && locationNode.has("lon")) {
    //                                     double lat = locationNode.get("lat").asDouble();
    //                                     double lon = locationNode.get("lon").asDouble();

    //                                     String formattedLine = String.format("%d  %f  %f", index, lon, lat);
    //                                     writer.write(formattedLine);
    //                                     writer.newLine();
    //                                     writer.flush();

    //                                     System.out.println("Written to bmhFlyData.txt: " + formattedLine);
    //                                     index++;
    //                                 }
    //                             }
    //                         } catch (Exception e) {
    //                             System.err.println("Error parsing line: " + jsonStr);
    //                             e.printStackTrace();
    //                         }
    //                     } else {
    //                         buffer.append(line);
    //                     }
    //                 }
    //             }
    //         }
    //     } catch (Exception e) {
    //         e.printStackTrace();
    //     } finally {
    //         if (fileWriteLock.isHeldByCurrentThread()) {
    //             fileWriteLock.unlock();
    //         }
    //     }
    // }
    // }

    // @Override
    // public void channelRead(ChannelHandlerContext ctx, Object msg) throws
    // Exception {
    // ByteBuf in = (ByteBuf) msg;
    // System.out.println("Server response: " +
    // in.toString(io.netty.util.CharsetUtil.UTF_8));
    // }

    

// 连接成功后发送消息测试

// @Override
// public void channelActive(ChannelHandlerContext ctx) throws Exception {

// String fileName = "D:\\GraduateStudent\\ONE\\test\\Fly-master(Uav
// nose)\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\bmhFlyData.txt";
// Path path = Paths.get(fileName);
// // byte[] bytes = Files.readAllBytes (path);
// // List<String> allLines = Files.readAllLines (path, StandardCharsets.UTF_8);
// List<String> allLines = Files.readAllLines(path);
// String droneId = allLines.get(0);
// String cardId = allLines.get(1);
// byte[] loginBytes = new byte[18];
// byte[] droneBytes = ByteUtils.hexString2Bytes(droneId);
// byte[] cardBytes = ByteUtils.string2HexBytes(cardId);
// System.out.println("cardBylen" + cardBytes.length);
// loginBytes[0] = 0x01;
// ByteUtils.fillBytes(loginBytes, droneBytes, 1);
// ByteUtils.fillBytes(loginBytes, cardBytes, 9);
// loginBytes[14] = 0x00;
// loginBytes[15] = 0x01;
// loginBytes[16] = (byte) 0x8C;
// loginBytes[17] = (byte) 0xDD;
// ctx.writeAndFlush(loginBytes);

// for (int i = 2; i < allLines.size(); i++) {
// // byte[] s = hexStringToByteArray (allLine.replace (" ", ""));
// byte[] positioningBytes = new byte[40];
// // 协议号
// positioningBytes[0] = 0x02;
// // 北斗卡id
// ByteUtils.fillBytes(positioningBytes, cardBytes, 1);
// positioningBytes[6] = 0x01;
// LocalDateTime now = LocalDateTime.now();
// // 年
// positioningBytes[7] = (byte) (now.getYear() - 2000);
// // 月
// positioningBytes[8] = (byte) now.getMonthValue();
// // 日
// positioningBytes[9] = (byte) now.getDayOfMonth();
// // 时
// positioningBytes[10] = (byte) now.getHour();
// // 分
// positioningBytes[11] = (byte) now.getMinute();
// // 秒
// positioningBytes[12] = (byte) now.getSecond();
// // 经度
// String[] position = allLines.get(i).split(" ");
// double lngDouble = Double.parseDouble(position[1]);
// double latDouble = Double.parseDouble(position[2]);

// Gps gps = PositionUtil.gcj02_To_Gps84(latDouble, lngDouble);

// byte[] lngBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLon() * 60 *
// 30000));
// ByteUtils.fillBytes(positioningBytes, lngBytes, 13);

// byte[] latBytes = ByteUtils.long2HexBytes(Math.round(gps.getWgLat() * 60 *
// 30000));
// ByteUtils.fillBytes(positioningBytes, latBytes, 17);
// // 生成60到80之间的随机数字
// Random rand = new Random();
// positioningBytes[21] = 0x00;
// positioningBytes[22] = (byte) (rand.nextInt(61) + 40);
// positioningBytes[23] = 0x00;
// positioningBytes[24] = (byte) (rand.nextInt(71) + 40);
// positioningBytes[25] = 0x00;
// positioningBytes[26] = (byte) (rand.nextInt(71) + 40);
// positioningBytes[27] = 0x01;
// positioningBytes[28] = (byte) 0xCC;
// positioningBytes[29] = 0x00;
// positioningBytes[30] = 0x28;
// positioningBytes[31] = 0x7D;
// positioningBytes[32] = 0x00;
// positioningBytes[33] = 0x00;
// positioningBytes[34] = 0x1F;
// positioningBytes[35] = (byte) 0xB8;
// positioningBytes[36] = 0x00;
// positioningBytes[37] = 0x01;
// positioningBytes[38] = (byte) 0x8C;
// positioningBytes[39] = (byte) 0xDD;

// ctx.writeAndFlush(positioningBytes);
// Thread.sleep(2000);
// }

// }

// public static byte[] hexStringToByteArray(String s) {
// int len = s.length();
// byte[] data = new byte[len / 2];
// for (int i = 0; i < len; i += 2) {
// data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) +
// Character.digit(s.charAt(i + 1), 16));
// }
// return data;
// }

// public static void main(String[] args) throws IOException {
// String fileName = "D:\\GraduateStudent\\ONE\\test\\Fly-master(Uav
// nose)\\client\\src\\main\\java\\com\\zzk\\client\\clientServe\\flyData.txt";
// Path path = Paths.get(fileName);
// // byte[] bytes = Files.readAllBytes (path);
// // List<String> allLines = Files.readAllLines (path, StandardCharsets.UTF_8);
// List<String> allLines = Files.readAllLines(path);
// for (int i = 2; i < allLines.size(); i++) {
// // byte[] s = hexStringToByteArray (allLine.replace (" ", ""));
// String[] position = allLines.get(i).split(" ");
// // System.out.println (position.length);
// String lng = position[1];
// String lat = position[2];
// double positionDouble = Double.parseDouble(position[1]);
// // System.out.println ((int)Math.round (positionDouble*60*30000));
// Random rand = new Random();
// // System.out.println (rand.nextInt(61) + 40);
// // byte[] bytes = ByteUtils.long2HexBytes (rand.nextInt(61) + 40);
// double lngDouble = Double.parseDouble(position[1]);
// byte[] lngBytes = ByteUtils.long2HexBytes(Math.round(lngDouble * 60 *
// 30000));
// System.out.println(ByteUtils.bytes2LngOrLat(lngBytes));
// // System.out.print (Integer.toHexString (lngByte & 0xFF)+" ");

// System.out.println();

// // 生成60到80之间的随机数字

// // System.out.println (bytes.length);
// // for (int i1 = 0; i1 < bytes.length; i1++) {
// // System.out.println (Integer.toHexString (bytes[i1] & 0xFF));
// // }
// // System.out.println ("lng:"+lng);
// // System.out.println ("lat:"+lat);
// // System.out.println ("lng"+lng);
// // buf.writeBytes (s);
// // ctx.writeAndFlush (buf);
// // Thread.sleep (3000);
// }
// // LocalDateTime now = LocalDateTime.now ();
// //
// // System.out.println (Integer.toHexString ((byte)(now.getYear ()-2000) &
// // 0xFF));
// // System.out.println (now.getMonthValue ());

// }

// }
