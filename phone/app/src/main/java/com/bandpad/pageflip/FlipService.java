package com.bandpad.pageflip;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/**
 * 无障碍服务：唯一职责是在当前前台应用上模拟手势。
 *   - 一次短滑动 → 翻页
 *   - 按住不放 → 长按（抖音/快手等的 2 倍速），松手抬起
 * 平时只派发手势，不碰界面内容；只有「收藏」会在当前界面里找一次「收藏」按钮的节点，
 * 不截屏、不保存、不上传任何界面数据。
 */
public class FlipService extends AccessibilityService {

    /** 长按最长持续时长：万一「抬起」指令丢了，不能真把手指按死在屏幕上。 */
    private static final long MAX_HOLD_MS = 20000L;

    /** 锁定加速的最长时长：锁定后手环可能被摘下，没人来发「取消」，这里兜一次底。 */
    private static final long LOCK_MAX_HOLD_MS = 600000L;

    /**
     * 长按落点：抖音的「长按 2 倍速」只认屏幕两侧约 1/5 宽的边缘区，按在正中间会弹出倍速浮层。
     * 取左侧 15% 处（避开最边缘的系统返回手势区，也不压到右侧那排点赞/评论图标）。
     */
    private static final float HOLD_X_RATIO = 0.15f;
    private static final float HOLD_Y_RATIO = 0.45f;

    /** 收藏按钮兜底坐标：右侧竖排按钮第三个（点赞 / 评论 / 收藏 / 分享）。 */
    private static final float FAV_X_RATIO = 0.93f;
    private static final float FAV_Y_RATIO = 0.60f;

    private static volatile FlipService sInstance;

    private final Handler handler = new Handler(Looper.getMainLooper());

    /** 正在进行的长按手势；只有 Android 8+ 才能续写，低版本为 null。 */
    private GestureDescription.StrokeDescription holdStroke;
    /** 当前是否处于「按住」状态（低版本用固定时长，没有可续写的笔画）。 */
    private boolean holdActive;
    /** 是否已锁定加速：锁定期间手环松手不解速，只有「取消锁定」才抬起手指。 */
    private boolean holdLocked;

    private final Runnable autoRelease = new Runnable() {
        @Override
        public void run() {
            if (holdActive) {
                UiLog.add(holdLocked ? "锁定加速超时，自动松开手指" : "长按超时，自动松开手指");
                holdLocked = false;
                releaseHold(true);
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

    /** 手环松手：抬起手指，恢复 1 倍速。已锁定时忽略。 */
    public static void pressEndFromBridge() {
        FlipService service = sInstance;
        if (service == null) {
            return;
        }
        service.releaseHold(false);
    }

    /** 手环在加速中下滑：锁定 2 倍速，之后松手也不恢复。 */
    public static void pressLockFromBridge() {
        FlipService service = sInstance;
        if (service == null) {
            UiLog.add("无障碍服务未开启，无法锁定加速");
            return;
        }
        service.lockHold();
    }

    /** 手环在锁定后上滑：取消锁定，抬起手指。 */
    public static void pressUnlockFromBridge() {
        FlipService service = sInstance;
        if (service == null) {
            return;
        }
        service.unlockHold();
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
        releaseHold(true);
        sInstance = null;
        UiLog.add("无障碍服务已断开");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        releaseHold(true);
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
            releaseHold(true);
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

    /** 三击收藏：优先找无障碍节点里的「收藏」按钮，找不到再按坐标兜底。 */
    public static void favoriteFromBridge() {
        FlipService service = sInstance;
        if (service == null) {
            UiLog.add("无障碍服务未开启，无法收藏");
            return;
        }
        service.favorite();
    }

    /** 在画面中央连点 n 下：抖音双击点赞。 */
    private void tap(int n) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            UiLog.add("系统版本过低，不支持模拟手势");
            return;
        }
        if (holdActive) {
            releaseHold(true);
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

    private void favorite() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            UiLog.add("系统版本过低，不支持模拟手势");
            return;
        }
        if (holdActive) {
            releaseHold(true);
        }
        if (clickFavoriteNode()) {
            UiLog.add("收藏：已点中「收藏」按钮");
            return;
        }
        tapAt(FAV_X_RATIO, FAV_Y_RATIO);
        UiLog.add("收藏：没找到按钮节点，已按坐标点屏幕右侧");
    }

    /**
     * 在当前界面找 contentDescription / 文本里带「收藏」的节点并点击。
     * 只在需要收藏时读一次界面结构，不做任何遍历留存。
     */
    private boolean clickFavoriteNode() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) {
                return false;
            }
            AccessibilityNodeInfo target = findByDesc(root, 0, true);
            if (target == null) {
                target = findByDesc(root, 0, false);
            }
            if (target == null) {
                return false;
            }
            AccessibilityNodeInfo clickable = target;
            for (int i = 0; i < 6 && clickable != null; i++) {
                if (clickable.isClickable()) {
                    return clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                clickable = clickable.getParent();
            }
            return target.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Throwable t) {
            UiLog.add("查找收藏按钮失败：" + t.getMessage());
            return false;
        }
    }

    private AccessibilityNodeInfo findByDesc(AccessibilityNodeInfo node, int depth, boolean descOnly) {
        if (node == null || depth > 12) {
            return null;
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.toString().contains("收藏")) {
            return node;
        }
        if (!descOnly) {
            CharSequence text = node.getText();
            if (text != null && text.toString().contains("收藏")) {
                return node;
            }
        }
        int count = node.getChildCount();
        for (int i = 0; i < count; i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo hit = findByDesc(child, depth + 1, descOnly);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    private void tapAt(float xRatio, float yRatio) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Path path = new Path();
        path.moveTo(dm.widthPixels * xRatio, dm.heightPixels * yRatio);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 40));
        try {
            dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            UiLog.add("点按失败：" + t.getMessage());
        }
    }

    /**
     * 落下手指并保持按住。用 willContinue=true 的笔画，之后由 {@link #releaseHold(boolean)} 续写收尾，
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
        float x = dm.widthPixels * HOLD_X_RATIO;
        float y = dm.heightPixels * HOLD_Y_RATIO;

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

    /** 手环在加速中下滑：锁定 2 倍速，松手也不再恢复。 */
    private void lockHold() {
        if (!holdActive) {
            UiLog.add("还没进入加速，无法锁定");
            return;
        }
        holdLocked = true;
        handler.removeCallbacks(autoRelease);
        handler.postDelayed(autoRelease, LOCK_MAX_HOLD_MS);
        UiLog.add("2 倍速已锁定（手环上滑取消）");
    }

    /** 手环在锁定后上滑：解除锁定并抬起手指。 */
    private void unlockHold() {
        if (!holdActive) {
            holdLocked = false;
            UiLog.add("当前没有加速");
            return;
        }
        holdLocked = false;
        releaseHold(true);
        UiLog.add("已取消锁定，恢复 1 倍速");
    }

    /**
     * @param force true = 无论如何都抬起手指（解锁、服务销毁、切换手势）；
     *              false = 手环松手，若已锁定则保持按住
     */
    private void releaseHold(boolean force) {
        if (!force && holdLocked) {
            // 已锁定：手环松手不解速，等「取消锁定」或超时兜底
            return;
        }
        handler.removeCallbacks(autoRelease);
        holdLocked = false;
        holdActive = false;
        GestureDescription.StrokeDescription stroke = holdStroke;
        holdStroke = null;
        if (stroke == null) {
            return;
        }
        DisplayMetrics dm = getResources().getDisplayMetrics();
        Path path = new Path();
        path.moveTo(dm.widthPixels * HOLD_X_RATIO, dm.heightPixels * HOLD_Y_RATIO);

        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(stroke.continueStroke(path, 0, 1, false));
        try {
            dispatchGesture(builder.build(), null, null);
        } catch (Throwable t) {
            UiLog.add("松手失败：" + t.getMessage());
        }
    }
}
