package com.bandpad.pageflip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务：唯一职责是在当前前台应用上模拟一次「滑动」，实现翻页。
 * 不读取屏幕内容，不截屏。
 */
public class FlipService extends AccessibilityService {

    private static volatile FlipService sInstance;

    public static boolean isReady() {
        return sInstance != null;
    }

    public static boolean flipFromBridge(int dir) {
        FlipService service = sInstance;
        if (service == null) {
            UiLog.add("无障碍服务未开启，无法翻页");
            return false;
        }
        return service.flip(dir);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        sInstance = this;
        UiLog.add("无障碍服务已连接");
        // 通道挂在这里：无障碍服务由系统绑定，进程优先级高，不需要前台服务占着通知栏
        WearableBridge.get().init(this);
        WearableBridge.get().start();
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        sInstance = null;
        UiLog.add("无障碍服务已断开");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要监听任何事件
    }

    @Override
    public void onInterrupt() {
    }

    /**
     * @param dir 1 = 下一页（内容向上滚），-1 = 上一页
     */
    private boolean flip(int dir) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            UiLog.add("系统版本过低，不支持模拟手势");
            return false;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels * 0.5f;
        float h = dm.heightPixels;
        float fromY = dir > 0 ? h * 0.72f : h * 0.30f;
        float toY = dir > 0 ? h * 0.30f : h * 0.72f;

        Path path = new Path();
        path.moveTo(x, fromY);
        path.lineTo(x, toY);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 170));
        try {
            return dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            UiLog.add("模拟滑动失败：" + t.getMessage());
            return false;
        }
    }
}
