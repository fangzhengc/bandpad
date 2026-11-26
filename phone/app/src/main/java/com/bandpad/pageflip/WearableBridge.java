package com.bandpad.pageflip;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.xiaomi.xms.wearable.Wearable;
import com.xiaomi.xms.wearable.auth.AuthApi;
import com.xiaomi.xms.wearable.auth.Permission;
import com.xiaomi.xms.wearable.message.MessageApi;
import com.xiaomi.xms.wearable.message.OnMessageReceivedListener;
import com.xiaomi.xms.wearable.node.Node;
import com.xiaomi.xms.wearable.node.NodeApi;
import com.xiaomi.xms.wearable.tasks.OnFailureListener;
import com.xiaomi.xms.wearable.tasks.OnSuccessListener;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 与手环的 interconnect 通道。
 *
 * 官方测试文档规定的顺序，缺一步就会报 permission denied：
 *   1. getConnectedNodes() 找到已连接手环
 *   2. isWearAppInstalled() 确认手环上装了同包名 RPK
 *   3. AuthApi.requestPermission() 申请 DEVICE_MANAGER / NOTIFY
 *   4. MessageApi.addListener() 注册消息监听
 *
 * 前提：本 App 的 applicationId 与手环 RPK 的 package 完全一致，且两者签名同源。
 */
public final class WearableBridge {

    private static final WearableBridge INSTANCE = new WearableBridge();
    private static final long RETRY_MS = 15000L;
    private static final Pattern DIR_PATTERN = Pattern.compile("\"d\"\\s*:\\s*(-?\\d+)");

    private final Handler main = new Handler(Looper.getMainLooper());

    private final OnMessageReceivedListener listener = new OnMessageReceivedListener() {
        @Override
        public void onMessageReceived(String deviceId, byte[] message) {
            handlePayload(message);
        }
    };

    private final Runnable retryTask = new Runnable() {
        @Override
        public void run() {
            query();
        }
    };

    private Context app;
    private NodeApi nodeApi;
    private MessageApi messageApi;
    private AuthApi authApi;

    private volatile String nodeId;
    private volatile boolean listening;
    private volatile boolean registering;
    private volatile boolean querying;
    private volatile boolean permissionGranted;
    private volatile boolean appInstalled = true;

    private WearableBridge() {
    }

    public static WearableBridge get() {
        return INSTANCE;
    }

    public void init(Context context) {
        if (app == null && context != null) {
            app = context.getApplicationContext();
        }
    }

    public synchronized void start() {
        if (app == null) {
            return;
        }
        try {
            if (nodeApi == null) {
                nodeApi = Wearable.getNodeApi(app);
            }
            if (messageApi == null) {
                messageApi = Wearable.getMessageApi(app);
            }
            if (authApi == null) {
                authApi = Wearable.getAuthApi(app);
            }
        } catch (Throwable t) {
            UiLog.add("穿戴 SDK 初始化失败：" + t.getMessage());
            return;
        }
        query();
    }

    /**
     * 断掉重来：若通道正在运行，先 removeListener 解绑，否则重复 addListener 会被 SDK 拒绝。
     * 解绑失败时保持现状不动——宁可不重连，也不能把一个正在能用的通道搞坏。
     */
    public synchronized void reconnect() {
        final String id = nodeId;
        if (listening && messageApi != null && id != null) {
            UiLog.add("先解绑现有通道…");
            try {
                messageApi.removeListener(id)
                        .addOnSuccessListener(new OnSuccessListener<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                                synchronized (WearableBridge.this) {
                                    listening = false;
                                    registering = false;
                                    permissionGranted = false;
                                    nodeId = null;
                                }
                                UiLog.add("旧通道已解绑，重新连接");
                                start();
                            }
                        })
                        .addOnFailureListener(new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                UiLog.add("解绑失败：" + text(e.getMessage())
                                        + "，现有通道仍在运行，未做改动");
                            }
                        });
                return;
            } catch (Throwable t) {
                UiLog.add("解绑异常：" + text(t.getMessage()) + "，继续重连");
            }
        }
        listening = false;
        registering = false;
        permissionGranted = false;
        nodeId = null;
        start();
    }

    /** 手动授权。要在前台界面调用，系统才能弹授权确认。 */
    public void requestPermissionNow() {
        if (listening) {
            UiLog.add("通道已经在运行，不需要再申请权限（重复注册会被 SDK 拒绝）");
            return;
        }
        final String id = nodeId;
        if (id == null) {
            UiLog.add("还没发现手环，先点「④ 重新连接手环」");
            start();
            return;
        }
        UiLog.add("开始向手环申请权限，手环上若弹出确认请点允许");
        ensurePermission(id, new Runnable() {
            @Override
            public void run() {
                register(id);
            }
        });
    }

    /** 通道是否处于已注册状态，供保活任务判断要不要重连。 */
    public boolean isListening() {
        return listening;
    }

    public String statusText() {
        if (listening) {
            return "已连通，可以滑手环了";
        }
        if (!appInstalled) {
            return "手环上未安装 RPK";
        }
        if (nodeId != null) {
            if (registering) {
                return "正在注册监听…";
            }
            return permissionGranted ? "已授权，未注册（点④重连）" : "已发现手环，等待授权";
        }
        return "等待手环";
    }

    public String permissionText() {
        if (permissionGranted) {
            return "已授权";
        }
        return nodeId == null ? "未知" : "未授权";
    }

    private void query() {
        synchronized (this) {
            if (nodeApi == null || listening || querying) {
                return;
            }
            querying = true;
        }
        try {
            nodeApi.getConnectedNodes()
                    .addOnSuccessListener(new OnSuccessListener<List<Node>>() {
                        @Override
                        public void onSuccess(List<Node> nodes) {
                            synchronized (WearableBridge.this) {
                                querying = false;
                            }
                            onNodes(nodes);
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            synchronized (WearableBridge.this) {
                                querying = false;
                            }
                            UiLog.add("查询手环失败：" + e.getMessage());
                            scheduleRetry();
                        }
                    });
        } catch (Throwable t) {
            synchronized (this) {
                querying = false;
            }
            UiLog.add("查询手环异常：" + t.getMessage());
            scheduleRetry();
        }
    }

    private void onNodes(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            nodeId = null;
            UiLog.add("没发现已连接的手环，15 秒后重试（确认小米运动健康已连上手环）");
            scheduleRetry();
            return;
        }
        Node node = nodes.get(0);
        if (node == null || node.id == null) {
            scheduleRetry();
            return;
        }
        nodeId = node.id;
        UiLog.add("发现设备：" + (node.name == null ? node.id : node.name));
        checkInstalled(node.id);
    }

    /** 第 2 步：手环上是否装了同包名 RPK。没装的话注册必然失败，先讲明白。 */
    private void checkInstalled(final String id) {
        final Runnable next = new Runnable() {
            @Override
            public void run() {
                register(id);
            }
        };
        try {
            nodeApi.isWearAppInstalled(id)
                    .addOnSuccessListener(new OnSuccessListener<Boolean>() {
                        @Override
                        public void onSuccess(Boolean installed) {
                            boolean ok = installed != null && installed;
                            appInstalled = ok;
                            if (ok) {
                                UiLog.add("手环端应用已安装");
                                ensurePermission(id, next);
                            } else {
                                UiLog.add("手环上没装「腕上翻页」RPK：先把 RPK 装到手环，再点「④ 重新连接手环」");
                                scheduleRetry();
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            UiLog.add("查手环应用失败：" + e.getMessage() + "，继续尝试授权");
                            ensurePermission(id, next);
                        }
                    });
        } catch (Throwable t) {
            UiLog.add("查手环应用异常：" + t.getMessage());
            ensurePermission(id, next);
        }
    }

    /** 第 3 步：权限，permission denied 的真正来源。官方要求先申请再注册监听。 */
    private void ensurePermission(final String id, final Runnable next) {
        if (authApi == null) {
            next.run();
            return;
        }
        final Permission[] perms = new Permission[]{Permission.DEVICE_MANAGER, Permission.NOTIFY};
        try {
            authApi.checkPermissions(id, perms)
                    .addOnSuccessListener(new OnSuccessListener<boolean[]>() {
                        @Override
                        public void onSuccess(boolean[] granted) {
                            boolean all = granted != null && granted.length == perms.length;
                            if (all) {
                                for (boolean g : granted) {
                                    if (!g) {
                                        all = false;
                                        break;
                                    }
                                }
                            }
                            if (all) {
                                permissionGranted = true;
                                UiLog.add("手环权限已授权");
                                next.run();
                            } else {
                                requestPermission(id, perms, next);
                            }
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            UiLog.add("查询权限失败：" + e.getMessage() + "，改为直接申请");
                            requestPermission(id, perms, next);
                        }
                    });
        } catch (Throwable t) {
            UiLog.add("查询权限异常：" + t.getMessage());
            requestPermission(id, perms, next);
        }
    }

    private void requestPermission(final String id, final Permission[] perms, final Runnable next) {
        try {
            UiLog.add("正在申请手环权限，手环上若弹出确认请点允许");
            authApi.requestPermission(id, perms)
                    .addOnSuccessListener(new OnSuccessListener<Permission[]>() {
                        @Override
                        public void onSuccess(Permission[] granted) {
                            StringBuilder sb = new StringBuilder();
                            if (granted != null) {
                                for (Permission p : granted) {
                                    sb.append(p.getName()).append(' ');
                                }
                            }
                            permissionGranted = true;
                            UiLog.add("授权完成：" + (sb.length() == 0 ? "无返回" : sb.toString().trim()));
                            next.run();
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            permissionGranted = false;
                            UiLog.add("权限申请失败：" + e.getMessage() + "，仍尝试注册监听");
                            next.run();
                        }
                    });
        } catch (Throwable t) {
            UiLog.add("权限申请异常：" + t.getMessage());
            next.run();
        }
    }

    private void register(final String id) {
        synchronized (this) {
            if (listening) {
                UiLog.add("通道已经在运行，跳过重复注册");
                return;
            }
            if (registering) {
                UiLog.add("上一次注册还没结束，先等它完成");
                return;
            }
            registering = true;
        }
        try {
            messageApi.addListener(id, listener)
                    .addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            synchronized (WearableBridge.this) {
                                listening = true;
                                registering = false;
                            }
                            UiLog.add("消息通道已就绪，可以滑手环了");
                        }
                    })
                    .addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            final String msg = text(e == null ? null : e.getMessage());
                            // 重复注册：SDK 只允许同一设备注册一次。
                            // 关键词命中，或者「权限此前已授予却仍被拒」，都判定为通道已存在，
                            // 此时旧监听依然有效（手环还能用），绝不能把状态刷成断开。
                            boolean dup = msg.contains("already") || msg.contains("exist")
                                    || msg.contains("registered")
                                    || (permissionGranted && msg.contains("permission"));
                            synchronized (WearableBridge.this) {
                                registering = false;
                                listening = dup;
                            }
                            if (dup) {
                                UiLog.add("注册被拒但通道其实还在（重复注册），继续正常使用即可");
                                return;
                            }
                            if (msg.contains("permission")) {
                                UiLog.add("注册监听被拒（权限未授予），点「⑤ 申请手环权限」并在手环上允许");
                            } else if (msg.contains("fingerprint") || msg.contains("signature")) {
                                UiLog.add("签名校验失败：两端证书不同源，需同一 keystore 重签 RPK 与 APK");
                            } else if (msg.contains("not installed")) {
                                UiLog.add("手环端应用未安装或包名不一致：" + msg);
                            } else {
                                UiLog.add("注册监听失败：" + msg);
                            }
                            scheduleRetry();
                        }
                    });
        } catch (Throwable t) {
            synchronized (this) {
                registering = false;
            }
            UiLog.add("注册监听异常：" + text(t.getMessage()));
            scheduleRetry();
        }
    }

    private static String text(String s) {
        return s == null ? "未知错误" : s;
    }

    private void scheduleRetry() {
        main.removeCallbacks(retryTask);
        main.postDelayed(retryTask, RETRY_MS);
    }

    private void handlePayload(byte[] message) {
        if (message == null) {
            return;
        }
        String text = new String(message, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            return;
        }
        if (text.length() > 1 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            text = text.substring(1, text.length() - 1).replace("\\\"", "\"");
        }

        int dir = 0;
        try {
            JSONObject obj = new JSONObject(text);
            dir = obj.optInt("d", 0);
        } catch (Throwable ignored) {
            Matcher m = DIR_PATTERN.matcher(text);
            if (m.find()) {
                try {
                    dir = Integer.parseInt(m.group(1));
                } catch (Throwable ignored2) {
                    dir = 0;
                }
            }
        }

        if (dir == 0) {
            UiLog.add("收到未知指令：" + text);
            return;
        }
        boolean ok = FlipService.flipFromBridge(dir);
        UiLog.add((dir > 0 ? "下一页" : "上一页") + (ok ? " 已执行" : " 失败"));
    }
}
