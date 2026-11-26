package com.bandpad.pageflip;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 自检面板：确认无障碍是否开启、手环通道是否连上，并可脱离手环先测翻页。
 */
public class MainActivity extends Activity {

    private TextView statusView;
    private TextView logView;
    private Button permButton;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            updateStatus();
            logView.setText(UiLog.dump());
            ui.postDelayed(this, 900);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
        WearableBridge.get().init(this);
        WearableBridge.get().start();
        KeepAliveJob.schedule(this);
        UiLog.add("应用已启动");
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(refreshTask);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(refreshTask);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    /** 申请「忽略电池优化」白名单，这是后台不被系统主动冻结的前提。 */
    private void requestIgnoreBattery() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                Toast.makeText(this, "该系统版本不需要设置", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isIgnoringBattery()) {
                Toast.makeText(this, "已经在白名单里了", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable t) {
            // 部分机型没有这个页面，退到应用详情让用户手动设「省电策略 → 无限制」
            openAppDetails();
            Toast.makeText(this, "在「省电策略」里选“无限制”", Toast.LENGTH_LONG).show();
        }
    }

    private void openAutoStart() {
        try {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            startActivity(intent);
        } catch (Throwable t) {
            openAppDetails();
            Toast.makeText(this, "在「权限」或「自启动」里允许", Toast.LENGTH_LONG).show();
        }
    }

    private void openAppDetails() {
        try {
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Throwable ignored) {
        }
    }

    private boolean isIgnoringBattery() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return true;
            }
            android.os.PowerManager pm =
                    (android.os.PowerManager) getSystemService(POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        } catch (Throwable t) {
            return false;
        }
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#121212"));
        root.setPadding(dp(20), dp(28), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("腕上翻页");
        title.setTextSize(24f);
        title.setTextColor(Color.parseColor("#F1EFE8"));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("手环滑一下 → 手机翻一页 · 按住 → 2 倍速");
        subtitle.setTextSize(13f);
        subtitle.setTextColor(Color.parseColor("#888780"));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.topMargin = dp(4);
        root.addView(subtitle, subParams);

        statusView = new TextView(this);
        statusView.setTextSize(13f);
        statusView.setTextColor(Color.parseColor("#B4B2A9"));
        statusView.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(18);
        root.addView(statusView, statusParams);

        root.addView(buildButton("① 打开无障碍设置", 12, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    Toast.makeText(MainActivity.this,
                            "找到「腕上翻页」并打开", Toast.LENGTH_LONG).show();
                } catch (Throwable t) {
                    Toast.makeText(MainActivity.this, "打不开设置页", Toast.LENGTH_SHORT).show();
                }
            }
        }));

        root.addView(buildButton("② 测试 下一页", 10, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FlipService.flipFromBridge(1);
            }
        }));

        root.addView(buildButton("③ 测试 上一页", 10, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FlipService.flipFromBridge(-1);
            }
        }));

        root.addView(buildButton("④ 重新连接手环", 10, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiLog.add("手动重新连接");
                WearableBridge.get().reconnect();
            }
        }));

        permButton = buildButton("⑤ 申请手环权限（首次必做）", 10, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (WearableBridge.get().isListening()) {
                    Toast.makeText(MainActivity.this,
                            "通道已经连通，不需要再申请", Toast.LENGTH_SHORT).show();
                    return;
                }
                WearableBridge.get().requestPermissionNow();
            }
        });
        root.addView(permButton);

        root.addView(buildButton("⑥ 关闭电池优化（保活必做）", 10, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestIgnoreBattery();
            }
        }));

        root.addView(buildButton("⑦ 打开自启动设置", 10, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openAutoStart();
            }
        }));

        root.addView(buildButton("⑧ 测试长按加速（按住 1.5 秒）", 10, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!FlipService.pressStartFromBridge()) {
                    Toast.makeText(MainActivity.this,
                            "无障碍服务没开，先点①", Toast.LENGTH_SHORT).show();
                    return;
                }
                Toast.makeText(MainActivity.this,
                        "按住 1.5 秒后自动松开，观察前台画面是否加速", Toast.LENGTH_SHORT).show();
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        FlipService.pressEndFromBridge();
                    }
                }, 1500);
            }
        }));

        TextView logTitle = new TextView(this);
        logTitle.setText("运行记录");
        logTitle.setTextSize(13f);
        logTitle.setTextColor(Color.parseColor("#5DCAA5"));
        LinearLayout.LayoutParams logTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logTitleParams.topMargin = dp(22);
        root.addView(logTitle, logTitleParams);

        logView = new TextView(this);
        logView.setTextSize(11f);
        logView.setTextColor(Color.parseColor("#888780"));
        logView.setTypeface(android.graphics.Typeface.MONOSPACE);
        logView.setLineSpacing(dp(4), 1f);
        logView.setText(UiLog.dump());
        LinearLayout.LayoutParams logParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        logParams.topMargin = dp(8);
        root.addView(logView, logParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor("#121212"));
        scroll.addView(root);
        return scroll;
    }

    private Button buildButton(String text, int topMargin, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(14f);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        params.topMargin = dp(topMargin);
        button.setLayoutParams(params);
        return button;
    }

    private void updateStatus() {
        String accessibility = isAccessibilityOn() ? "已开启" : "未开启";
        boolean connected = WearableBridge.get().isListening();
        String bridge = WearableBridge.get().statusText();
        String permission = WearableBridge.get().permissionText();
        String battery = isIgnoringBattery() ? "已放行" : "未放行";
        String text = "无障碍服务：" + accessibility
                + "\n手环权限：" + permission
                + "\n后台保活：" + battery
                + "\n手环通道：" + bridge
                + "\n包名：" + getPackageName();
        statusView.setText(text);
        if (isAccessibilityOn()) {
            statusView.setTextColor(Color.parseColor("#9FE1CB"));
        } else {
            statusView.setTextColor(Color.parseColor("#F09595"));
        }
        if (permButton != null) {
            // 已连通时禁用⑤：重复注册会被 SDK 拒绝，反而把状态刷坏
            permButton.setText(connected ? "⑤ 已连通，无需再申请" : "⑤ 申请手环权限（首次必做）");
            permButton.setEnabled(!connected);
            permButton.setAlpha(connected ? 0.45f : 1f);
        }
    }

    private boolean isAccessibilityOn() {
        if (FlipService.isReady()) {
            return true;
        }
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) {
            return false;
        }
        ComponentName component = new ComponentName(this, FlipService.class);
        String target = component.flattenToString();
        return enabled.contains(target) || enabled.contains(component.flattenToShortString());
    }
}
