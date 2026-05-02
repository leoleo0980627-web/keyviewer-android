package com.KV;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import rikka.shizuku.Shizuku;

public class BinaryKeyInjector {

    private static final String TAG = "KV_BinaryInjector";
    private static final String BINARY_DIR = "/data/data/com.KV/files";
    private static final String KV_BINARY = BINARY_DIR + "/kv";
    private static final String CLIENT_BINARY = BINARY_DIR + "/kv_client";
    private static final String SOCKET_NAME = "kv_socket";
    private static final String DEVICE_NAME = "KV Virtual Input";

    // 从 assets 加载 libkvsocket.so
    static {
        try {
            File dir = new File(BINARY_DIR);
            if (!dir.exists()) dir.mkdirs();
            File soFile = new File(BINARY_DIR, "libkvsocket.so");
            if (!soFile.exists()) {
                InputStream is = MainActivity.getAppContext().getAssets().open("libkvsocket.so");
                OutputStream os = new FileOutputStream(soFile);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
                os.close();
                is.close();
                Log.i(TAG, "Extracted libkvsocket.so to " + soFile.getAbsolutePath());
            }
            System.load(soFile.getAbsolutePath());
            Log.i(TAG, "Loaded libkvsocket.so from " + soFile.getAbsolutePath());
        } catch (Throwable e) {
            Log.e(TAG, "Failed to load libkvsocket.so", e);
            throw new RuntimeException("Cannot load native library", e);
        }
    }

    private native int nativeConnect(String name);
    private native int nativeWrite(int fd, byte[] data, int len);
    private native void nativeClose(int fd);

    private Process process;
    private int socketFd = -1;
    private volatile boolean running = false;
    private String eventPath = null;

    private static BinaryKeyInjector instance;

    public static BinaryKeyInjector getInstance() {
        if (instance == null) instance = new BinaryKeyInjector();
        return instance;
    }

    private BinaryKeyInjector() {}

    private void extractAsset(String assetName, String destPath) throws Exception {
        File dest = new File(destPath);
        if (dest.exists()) return;
        InputStream is = MainActivity.getAppContext().getAssets().open(assetName);
        OutputStream os = new FileOutputStream(dest);
        byte[] buf = new byte[8192];
        int len;
        while ((len = is.read(buf)) != -1) os.write(buf, 0, len);
        os.close();
        is.close();
        Log.i(TAG, "Extracted " + assetName + " to " + destPath);
    }

    private void execShell(String cmd) throws Exception {
        Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
        Method newProcess = shizukuClass.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
        newProcess.setAccessible(true);
        String[] argv = {"sh", "-c", cmd};
        Process p = (Process) newProcess.invoke(null, (Object) argv, null, null);
        p.waitFor();
    }

    private String findKVEventNode() {
        try {
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method newProcess = shizukuClass.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            String[] cmd = {
                "sh", "-c",
                "cat /proc/bus/input/devices | awk '/" + DEVICE_NAME + "/{flag=1} flag && /H: Handlers/{print; exit}' | grep -o 'event[0-9]*'"
            };
            Process p = (Process) newProcess.invoke(null, (Object) cmd, null, null);
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = reader.readLine();
            p.waitFor();
            if (line != null && !line.isEmpty()) {
                return "/dev/input/" + line;
            }
        } catch (Exception e) {
            Log.e(TAG, "findKVEventNode failed", e);
        }
        return null;
    }

    public boolean start() {
        if (running) return true;
        try {
            // 1. 创建目录
            execShell("mkdir -p " + BINARY_DIR + " && chmod 755 " + BINARY_DIR);

            // 2. 提取二进制文件（如果不存在）
            extractAsset("kv", KV_BINARY);
            extractAsset("kv_client", CLIENT_BINARY);
            execShell("chmod 755 " + KV_BINARY + " " + CLIENT_BINARY);

            // 3. 杀掉旧进程
            execShell("killall kv 2>/dev/null; killall kv_client 2>/dev/null; sleep 0.3");

            // 4. 启动 kv 守护进程
            Class<?> shizukuClass = Class.forName("rikka.shizuku.Shizuku");
            Method newProcess = shizukuClass.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            newProcess.setAccessible(true);
            String[] startCmd = {KV_BINARY};
            process = (Process) newProcess.invoke(null, (Object) startCmd, null, null);
            Log.i(TAG, "kv process started");

            // 5. 等待设备注册
            Thread.sleep(1500);

            // 6. 查找虚拟设备节点
            eventPath = findKVEventNode();
            if (eventPath == null) {
                Log.e(TAG, "Failed to find " + DEVICE_NAME + " event node");
                destroyProcess();
                return false;
            }
            Log.i(TAG, "Event path: " + eventPath);

            // 7. 连接抽象 socket
            socketFd = nativeConnect(SOCKET_NAME);
            if (socketFd < 0) {
                Log.e(TAG, "Socket connect failed");
                destroyProcess();
                return false;
            }
            Log.i(TAG, "Socket connected");

            running = true;
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Start failed", e);
            destroyProcess();
            return false;
        }
    }

    private void destroyProcess() {
        running = false;
        if (socketFd >= 0) {
            nativeClose(socketFd);
            socketFd = -1;
        }
        if (process != null) {
            process.destroy();
            process = null;
        }
        try {
            execShell("killall kv kv_client 2>/dev/null");
        } catch (Exception ignored) {}
        eventPath = null;
    }

    public void injectKey(int keycode, boolean down) {
        if (!running || socketFd < 0) return;
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (down ? 0 : 1));
        buf.putShort((short) keycode);
        nativeWrite(socketFd, buf.array(), 4);
    }

    public void stop() {
        destroyProcess();
    }

    public boolean isRunning() { return running; }
    public String getEventPath() { return eventPath; }

    // 按键名称转 Linux 键码
    public static int keyNameToLinuxCode(String name) {
        if (name == null) return -1;
        switch (name.toLowerCase()) {
            case "space": return 57; case "enter": return 28; case "tab": return 15;
            case "esc": case "escape": return 1; case "backspace": return 14;
            case "del": return 111; case "lshift": return 42; case "rshift": return 54;
            case "lctrl": return 29; case "rctrl": return 97;
            case "lalt": return 56; case "ralt": return 100;
            case "up": return 103; case "down": return 108;
            case "left": return 105; case "right": return 106;
            case "comma": return 51; case "dot": return 52;
            case "semicolon": return 39; case "slash": return 53;
            case "backslash": return 43; case "quote": return 40; case "backquote": return 41;
            case "bracketleft": return 26; case "bracketright": return 27;
            case "minus": return 12; case "equal": return 13;
            case "pageup": return 104; case "pagedown": return 109;
            case "home": return 102; case "end": return 107; case "capslock": return 58;
            case "a": return 30; case "b": return 48; case "c": return 46;
            case "d": return 32; case "e": return 18; case "f": return 33;
            case "g": return 34; case "h": return 35; case "i": return 23;
            case "j": return 36; case "k": return 37; case "l": return 38;
            case "m": return 50; case "n": return 49; case "o": return 24;
            case "p": return 25; case "q": return 16; case "r": return 19;
            case "s": return 31; case "t": return 20; case "u": return 22;
            case "v": return 47; case "w": return 17; case "x": return 45;
            case "y": return 21; case "z": return 44;
            case "1": return 2; case "2": return 3; case "3": return 4;
            case "4": return 5; case "5": return 6; case "6": return 7;
            case "7": return 8; case "8": return 9; case "9": return 10; case "0": return 11;
            default: return -1;
        }
    }
}