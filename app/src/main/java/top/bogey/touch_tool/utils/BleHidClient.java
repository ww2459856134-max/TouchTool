package top.bogey.touch_tool.utils;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;

import java.util.UUID;


import top.bogey.touch_tool.MainApplication;

/**
 * ESP32 HID 设备 BLE GATT 指令客户端（固件 v1.2.3+）。
 * <p>
 * 服务/特征 UUID 与分包协议见固件仓库 BLE指令通道协议_v1.2.3.md：
 *   服务 4fafc201-1fb5-459e-8fcc-c5c9c331914b
 *   特征 beb5483e-36e1-4688-b7f5-ea07361b26a8（READ|WRITE|WRITE_NR）
 *   单包 [cmd][json...]；超长 JSON 首包 [0xFE][lenLo][lenHi][chunk] + 后续 [chunk...]
 * <p>
 * 线程模型：sendFrame 可在任意后台线程调用（动作线程本来就是后台），
 * 内部用锁串行化 GATT 写操作；断线后自动重连（指数退避到 5 秒）。
 */
public class BleHidClient {
    public static final UUID SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b");
    public static final UUID CHAR_UUID = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8");

    private static final Object LOCK = new Object();
    private static BluetoothGatt gatt;
    private static BluetoothGattCharacteristic cmdChar;
    private static volatile boolean ready = false;
    private static volatile long lastErrorAt = 0;
    private static long gattStartMs = 0;
    private static int mtu = 23;
    private static volatile boolean mtuDone = false;

    // 写完成同步：WRITE_TYPE_DEFAULT 是带响应写，同一时刻只允许一个在途写。
    // 之前 writeChunk 只 sleep 8ms 就发下一包，而空中传输一包要 15~60ms，
    // 第二包提交时上一包未完成 → Android 返回 busy → 整条分包指令静默失败。
    // 表现为"3 字节复位能到、52 字节手势永远丢"。现在等 onCharacteristicWrite 回调。
    private static volatile java.util.concurrent.CountDownLatch writeLatch;
    private static volatile int lastWriteStatus = -1;

    /** 设备 MAC 是否已配置 */
    public static String getConfigMac() {
        String mac = top.bogey.touch_tool.bean.save.setting.SettingSaver.HID_BLE_MAC.get();
        if (mac == null) return null;
        mac = mac.trim().toUpperCase();
        return mac.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}") ? mac : null;
    }

    public static boolean isConnected() {
        return ready && gatt != null;
    }

    /** 确保已连接（已连着直接 true；没连则发起连接，本次先返回 false） */
    public static boolean ensureConnected() {
        if (isConnected()) return true;
        String mac = getConfigMac();
        if (mac == null) return false;
        // 距上次错误 5 秒内不再反复重试，防止动作线程疯狂重连
        if (SystemClock.elapsedRealtime() - lastErrorAt < 5000) return false;
        synchronized (LOCK) {
            if (isConnected()) return true;
            if (gatt != null) {
                // 已有连接尝试进行中：挂起超过 15 秒还没就绪 → 关掉重来
                if (SystemClock.elapsedRealtime() - gattStartMs < 15000) return false;
                try {
                    gatt.close();
                } catch (Exception ignored) {
                }
                gatt = null;
                cmdChar = null;
            }
            android.bluetooth.BluetoothManager bm =
                    (android.bluetooth.BluetoothManager) MainApplication.getInstance()
                            .getSystemService(Context.BLUETOOTH_SERVICE);
            if (bm == null || bm.getAdapter() == null || !bm.getAdapter().isEnabled()) return false;
            if (!hasConnectPermission()) return false;
            BluetoothDevice device = bm.getAdapter().getRemoteDevice(mac);
            gattStartMs = SystemClock.elapsedRealtime();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                gatt = device.connectGatt(MainApplication.getInstance(), false, callback,
                        BluetoothDevice.TRANSPORT_LE);
            } else {
                gatt = device.connectGatt(MainApplication.getInstance(), false, callback);
            }
            return false; // 连接是异步的，本次动作走 UDP 兜底，连上后自动切换
        }
    }

    private static boolean hasConnectPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return MainApplication.getInstance().checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    public static void disconnect() {
        synchronized (LOCK) {
            ready = false;
            cmdChar = null;
            if (gatt != null) {
                try {
                    gatt.disconnect();
                    gatt.close();
                } catch (Exception ignored) {
                }
                gatt = null;
            }
        }
    }

    /**
     * 发送一条完整指令（阻塞，最多约 1.5 秒）。
     *
     * @return true = 已写入 GATT（不代表设备执行成功，BLE 无 ACK）
     */
    public static boolean sendFrame(byte[] data) {
        if (data == null || data.length == 0) return false;
        synchronized (LOCK) {
            if (!isConnected() && !waitConnected(1500)) return false;
            try {
                // 等 MTU 协商完成（最多 600ms），协商成功后 52 字节手势单包直发，不进分包路径
                if (!mtuDone) {
                    long deadline = SystemClock.elapsedRealtime() + 600;
                    while (!mtuDone && SystemClock.elapsedRealtime() < deadline) {
                        Thread.sleep(20);
                    }
                }
                BluetoothGattCharacteristic c = cmdChar;
                if (c == null) return false;
                int chunkMax = Math.max(20, mtu - 3);
                if (data.length <= chunkMax) {
                    return writeChunk(c, data);
                }
                // 分包：首包 0xFE + 总长 + 内容
                byte[] first = new byte[3 + Math.min(chunkMax - 3, data.length)];
                first[0] = (byte) 0xFE;
                first[1] = (byte) (data.length & 0xFF);
                first[2] = (byte) ((data.length >> 8) & 0xFF);
                int head = first.length - 3;
                System.arraycopy(data, 0, first, 3, head);
                if (!writeChunk(c, first)) return false;
                int off = head;
                while (off < data.length) {
                    int take = Math.min(chunkMax, data.length - off);
                    byte[] chunk = new byte[take];
                    System.arraycopy(data, off, chunk, 0, take);
                    if (!writeChunk(c, chunk)) return false;
                    off += take;
                }
                return true;
            } catch (Exception e) {
                lastErrorAt = SystemClock.elapsedRealtime();
                ready = false;
                return false;
            }
        }
    }

    /** 提交一次带响应写并等待完成回调；失败（含 busy）自动重试最多 3 次 */
    private static boolean writeChunk(BluetoothGattCharacteristic c, byte[] data) {
        for (int attempt = 0; attempt < 3; attempt++) {
            lastWriteStatus = -1;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            writeLatch = latch;
            boolean queued;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int r = gatt.writeCharacteristic(c, data, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                queued = (r == android.bluetooth.BluetoothStatusCodes.SUCCESS);
            } else {
                c.setValue(data);
                c.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                queued = gatt.writeCharacteristic(c);
            }
            if (!queued) {
                writeLatch = null;
                try {
                    Thread.sleep(25);   // 可能是 busy：等在途写完成再重试
                } catch (InterruptedException ignored) {
                }
                continue;
            }
            try {
                latch.await(400, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) {
            }
            writeLatch = null;
            // 有回调：按真实状态判定；无回调（个别平台不派发）：已成功入队即视为完成
            if (lastWriteStatus == 0 || lastWriteStatus == -1) return true;
            try {
                Thread.sleep(25);
            } catch (InterruptedException ignored) {
            }
        }
        return false;
    }

    private static boolean waitConnected(long timeoutMs) {
        ensureConnected();
        long deadline = SystemClock.elapsedRealtime() + timeoutMs;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (isConnected()) return true;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        return isConnected();
    }

    @SuppressLint("MissingPermission")
    private static final BluetoothGattCallback callback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt g, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mtu = 23;
                g.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                ready = false;
                cmdChar = null;
                lastErrorAt = SystemClock.elapsedRealtime();
                // 保留 gatt 引用等待下次 ensureConnected 复用？直接关闭最干净
                try {
                    g.close();
                } catch (Exception ignored) {
                }
                if (g == BleHidClient.gatt) BleHidClient.gatt = null;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt g, int status) {
            BluetoothGattService s = (status == BluetoothGatt.GATT_SUCCESS) ? g.getService(SERVICE_UUID) : null;
            BluetoothGattCharacteristic c = (s == null) ? null : s.getCharacteristic(CHAR_UUID);
            if (c == null) {
                // 找不到指令特征：关掉本次 GATT，让下次动作重新发起（避免卡死在"已连但不可用"）
                lastErrorAt = SystemClock.elapsedRealtime();
                ready = false;
                cmdChar = null;
                try {
                    g.close();
                } catch (Exception ignored) {
                }
                if (g == BleHidClient.gatt) BleHidClient.gatt = null;
                return;
            }
            cmdChar = c;
            ready = true;   // 先置可用（MTU 默认 23 也能发分包），MTU 协商成功后再扩容
            mtuDone = false;
            g.requestMtu(247);
        }

        @Override
        public void onMtuChanged(BluetoothGatt g, int mtu, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) BleHidClient.mtu = mtu;
            mtuDone = true;   // 失败也算完成，sendFrame 不再干等
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, int status) {
            noteWriteDone(status);
        }

        // Android 13+ 新签名（部分编译环境的 android.jar 缺这个重载，故不加 @Override，运行时靠多态分发）
        public void onCharacteristicWrite(BluetoothGatt g, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
            noteWriteDone(status);
        }

        private void noteWriteDone(int status) {
            lastWriteStatus = status;
            java.util.concurrent.CountDownLatch l = writeLatch;
            if (l != null) l.countDown();
        }
    };
}
