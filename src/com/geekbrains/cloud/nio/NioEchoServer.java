package com.geekbrains.cloud.nio;

import com.sun.javafx.runtime.SystemProperties;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.*;

public class NioEchoServer {

    /**
     * Сделать терминал, которые умеет обрабатывать команды:
     * ls - список файлов в директории
     * cd dir_name - переместиться в директорию
     * cat file_name - распечатать содержание файла на экран
     * mkdir dir_name - создать директорию в текущей
     * touch file_name - создать пустой файл в текущей директории
     */

    private ServerSocketChannel serverChannel;
    private Selector selector;
    private ByteBuffer buf;
    private Path currentDir;
    private final String WORKING_DIR_ROOT = "workingDir";

    public NioEchoServer() throws IOException {
        currentDir = Paths.get(System.getProperty("user.dir"), WORKING_DIR_ROOT);
        buf = ByteBuffer.allocate(5);
        serverChannel = ServerSocketChannel.open();
        selector = Selector.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(8189));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started...");
        while (serverChannel.isOpen()) {
            selector.select(); // block
            System.out.println("Keys selected...");
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept();
                }
                if (key.isReadable()) {
                    handleRead(key);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        //StringBuilder s = new StringBuilder();
        List<Byte> arr = new ArrayList<>();
        int read = 0;
        while (true) {
            read = channel.read(buf);
            if (read == 0) {
                break;
            }
            if (read < 0) {
                channel.close();
                return;
            }
            buf.flip();
            while (buf.hasRemaining()) {
                //s.append((char) buf.get());
                arr.add(buf.get());
            }
            buf.clear();
        }
        // process(s)
        byte[] bytes = new byte[arr.size()];
        for (int i = 0; i < arr.size(); i++){
            bytes[i] = arr.get(i);
        }

        String s = new String (bytes, "UTF-8");

        System.out.println("Received: " + s);
        String[] tokens = s.toString().split("\\s+", 2);
        String command = tokens[0];
        String param = tokens[1].trim();
        switch (command) {
            case "ls":
                sendFileList(channel);
                break;
            case "cd":
                changeDir(channel, param);
                break;
            case "cat":
                viewFile(channel, param);
                break;
            case "mkdir":
                makeDir(channel, param);
                break;
            case "touch":
                createFile(channel, param);
                break;
            default:
                channel.write(ByteBuffer.wrap(s.toString().getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void viewFile(SocketChannel channel, String param) throws IOException {
        try {
            Path path = currentDir.resolve(param);
            if (path.toFile().isDirectory()) {
                channel.write(ByteBuffer.wrap(("Not a file provided " + param + "\n\r").getBytes(StandardCharsets.UTF_8)));
                return;
            }
            ByteChannel file = Files.newByteChannel(path);

            buf.clear();
            int read = 0;
            while (true) {
                read = file.read(buf);
                if (read == 0){
                    break;
                }
                if (read < 0){
                    file.close();
                    break;
                }
                buf.flip();
                channel.write(buf);
                buf.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
            channel.write(ByteBuffer.wrap(("Failed to read file " + param + " [" + e.getClass().getName() + "]" + "\n\r").getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void createFile(SocketChannel channel, String name) throws IOException {
        try {
            Files.createFile(currentDir.resolve(Paths.get(name)));
        } catch (Exception e) {
            e.printStackTrace();
            channel.write(ByteBuffer.wrap(("Failed to create file " + name + " [" + e.getClass().getName() + "]" + "\n\r").getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void makeDir(SocketChannel channel, String name) throws IOException {
        try {
            Files.createDirectory(currentDir.resolve(Paths.get(name)));
        } catch (Exception e) {
            e.printStackTrace();
            channel.write(ByteBuffer.wrap(("Failed to create directory " + name + " [" + e.getClass().getName() + "]" + "\n\r").getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void changeDir(SocketChannel channel, String param) throws IOException {
        if (currentDir.getFileName().toString().equals(WORKING_DIR_ROOT) && param.equals("..")) return;
        try {
            Path dir = Paths.get(param);
            if (currentDir.resolve(dir).toFile().exists()) {
                currentDir = currentDir.resolve(dir);
                if (currentDir.getFileName().toString().equals("..")) {
                    currentDir = currentDir.getRoot().resolve(currentDir.subpath(0, currentDir.getNameCount() - 2));
                }
                channel.write(ByteBuffer.wrap(("Current working path is /" + currentDir.getFileName().toString() + "\n\r").getBytes(StandardCharsets.UTF_8)));
                System.out.println(currentDir.toString());
            } else {
                channel.write(ByteBuffer.wrap(("Directory " + param + " doesn`t exist" + "\n\r").getBytes(StandardCharsets.UTF_8)));
            }
        } catch (Exception e) {
            e.printStackTrace();
            channel.write(ByteBuffer.wrap(("Can`t resolve given path. " + param + "\n\r").getBytes(StandardCharsets.UTF_8)));
        }
    }

    private void sendFileList(SocketChannel channel) throws IOException {
        String[] fileArray = currentDir.toFile().list();
        for (String item : fileArray) {
            channel.write(ByteBuffer.wrap((item + "\n\r").getBytes(StandardCharsets.UTF_8)));
        }
    }


    private void handleAccept() throws IOException {
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        channel.write(ByteBuffer.wrap(
                "Hello user. Welcome to our terminal\n\r".getBytes(StandardCharsets.UTF_8)
        ));
        System.out.println("Client accepted...");
    }

    public static void main(String[] args) throws IOException {
        new NioEchoServer();
    }
}
