package com.bandpad.pageflip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;

/**
 * 无障碍服务：唯一职责是在当前前台应用上模拟手势。
 *   - 一次短滑动 → 翻页
 *   - 按住不放 → 长按（抖音/快手等的 2 倍速），松手抬起
 * 不读取屏幕内容，不截屏。
 */
public class FlipService extends AccessibilityService {

    /** 长按最长持续时长：万一「抬起」指令丢了，不能真把手指按死在屏幕上。 */
    private static final long MAX_HOLD_MS = 20000L;

    private static volatile FlipService sInstance;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 正在进行的长按手势；只有 Android 8+ 才能续写，低版本为 null。 */
    private GestureDescription.StrokeDescription holdStroke;
    /** 当前是否处于「按住」状态（低版本用固定时长，没有可续写的笔画）。 */
    private boolean holdActive;

    private final Runnable autoRelease = new Runnable() {
        @Override
        public void run() {
            if (holdActive) {
                UiLog.add("长按超时，自动松开手指");
                releaseHold();
            }
        }
    };

    public static boolean isReady() {
        return sInstance != null;
    }

    public static boolean flipFromBridge(int dir) {
        return flipFromBridge(dir, false);
    }

    /**
     * @param dir        1 = 手指往左 / 往上，-1 = 往右 / 往下
     * @param horizontal true = 横向滑一下（左右滑），false = 竖向翻页
     */
    public static boolean flipFromBridge(int dir, boolean horizontal) {
        FlipService service = sInstance;
        if (service == null) {
            UiLog.add("无障碍服务未开启，无法滑动手势");
            return false;
        }
        return service.flip(dir, horizontal);
    }

    /** 手环上「按住不动」：落下手指并保持，用于抖音 2 倍速。 */
    public static boolean pressStartFromBridge() {
        FlipService service = sInstance;
        if (service == null) {
            UiLog.add("无障碍服务未开启，无法长按");
            return false;
        }
        return service.startHold();
    }

    /**
     * 手环上连点：2 下 = 双击（点赞），3 下 = 三击（收藏）。
     *
     * @param n 连点次数
     */
    public static void tapFromBridge(int n) {
        FlipService service = sInstance;
        if (service == null) {
            UiLog.add("无障碍服务未开启，无法双击");
            return;
        }
        service.tap(n);
    }

    /** 手环松手：抬起手指，恢复 1 倍速。 */
    public static void pressEndFromBridge() {
        FlipService service = sInstance;
        if (service == null) {
            return;
        }
        service.releaseHold();
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
        releaseHold();
        sInstance = null;
        UiLog.add("无障碍服务已断开");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        releaseHold();
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
     * @param dir        1 = 手指往左 / 往上，-1 = 往右 / 往下
     * @param horizontal true = 横向滑一下，false = 竖向翻页
     */
    private boolean flip(int dir, boolean horizontal) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            UiLog.add("系统版本过低，不支持模拟手势");
            return false;
        }
        if (holdActive) {
            // 正在加速中又收到滑动手势，先松开，否则两个手势会打架
            releaseHold();
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float w = dm.widthPixels;
        float h = dm.heightPixels;

        Path path = new Path();
        if (horizontal) {
            // 横滑落在屏幕中部，起止点都离左右边缘远一点，避免被当成系统「返回」手势
            float y = h * 0.5f;
            float fromX = dir > 0 ? w * 0.75f : w * 0.25f;
            float toX = dir > 0 ? w * 0.25f : w * 0.75f;
            path.moveTo(fromX, y);
            path.lineTo(toX, y);
        } else {
            float x = w * 0.5f;
            float fromY = dir > 0 ? h * 0.72f : h * 0.30f;
            float toY = dir > 0 ? h * 0.30f : h * 0.72f;
            path.moveTo(x, fromY);
            path.lineTo(x, toY);
        }

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 180));
        try {
            return dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            UiLog.add("模拟滑动失败：" + t.getMessage());
            return false;
        }
    }

    /** 在画面中央连点 n 下：抖音双击点赞，三击收藏。 */
    private void tap(int n) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            UiLog.add("系统版本过低，不支持模拟手势");
            return;
        }
        if (holdActive) {
            releaseHold();
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float x = dm.widthPixels * 0.5f;
        float y = dm.heightPixels * 0.45f;

        GestureDescription.Builder builder = new GestureDescription.Builder();
        for (int i = 0; i < n; i++) {
            Path path = new Path();
            path.moveTo(x, y);
            // 每下 40ms，间隔 110ms：慢于 300ms 就不算连击，快于 App 的双击阈值
            builder.addStroke(new GestureDescription.StrokeDescription(path, i * 110L, 40));
        }
        try {
            dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            UiLog.add("连点失败：" + t.getMessage());
        }
    }

    /**
     * 落下手指并保持按住。用 willContinue=true 的笔画，之后由 {@link #releaseHold()} 续写收尾，
     * 这样手指在屏幕上是真的按着，抖音会一直保持 2 倍速。
     */
    private boolean startHold() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            UiLog.add("系统版本过低，不支持模拟手势");
            return false;
        }
        if (holdActive) {
            return true;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        // 画面正中偏上一点：避开底部评论栏和右侧点赞栏，落在视频画面上
        float x = dm.widthPixels * 0.5f;
        float y = dm.heightPixels * 0.45f;

        Path path = new Path();
        path.moveTo(x, y);

        // willContinue 的笔画要 Android 8+ 才能用 continueStroke 收尾；
        // 7.x 上退化成一次固定 1.5 秒的长按（加速能感觉到，但松手不受控）
        final boolean canContinue = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        GestureDescription.StrokeDescription stroke = canContinue
                ? new GestureDescription.StrokeDescription(path, 0, 1, true)
                : new GestureDescription.StrokeDescription(path, 0, 1500);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke);
        try {
            if (!dispatchGesture(builder.build(), null, null)) {
                return false;
            }
        } catch (Throwable t) {
            UiLog.add("长按失败：" + t.getMessage());
            return false;
        }
        holdActive = true;
        holdStroke = canContinue ? stroke : null;
        handler.removeCallbacks(autoRelease);
        if (canContinue) {
            handler.postDelayed(autoRelease, MAX_HOLD_MS);
        } else {
            UiLog.add("系统低于 Android 8，长按固定 1.5 秒");
            handler.postDelayed(autoRelease, 1600);
        }
        return true;
    }

    private void releaseHold() {
        handler.removeCallbacks(autoRelease);
        holdActive = false;
        GestureDescription.StrokeDescription stroke = holdStroke;
        holdStroke = null;
        if (stroke == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Path path = new Path();
        path.moveTo(dm.widthPixels * 0.5f, dm.heightPixels * 0.45f);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke.continueStroke(path, 0, 1, false));
        try {
            dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            UiLog.add("松手失败：" + t.getMessage());
        }
    }
}
