package com.bandpad.pageflip;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 目标设备（手表/手环）描述。
 *
 * 手机端的指令格式对所有 Vela 设备都一样，所以「一个 App 通吃多个型号」是成立的；
 * 这里区分型号的意义在于：不同屏幕比例的设备，长按落点、横滑幅度这些几何参数可能
 * 需要微调，而且日志里能看清楚当前在给哪台设备服务。
 *
 * 加新设备只要在下面加一个静态实例、塞进 {@link #ALL}，界面上的选择框会自动多一项。
 */
public final class DeviceProfile {

    /** 存 SharedPreferences 的键名前缀。 */
    private static final String PREF = "bandpad";
    private static final String KEY_DEVICE = "device_profile";

    /** 小米手环 9 Pro：336×480，designWidth 168。 */
    public static final DeviceProfile BAND_9_PRO = new DeviceProfile(
            "band9pro",
            "小米手环 9 Pro",
            "336×480 · 滑动为主",
            0.15f, 0.45f,   // 长按落点：左侧边缘区（抖音只认两侧约 1/5 宽）
            0.93f, 0.60f,   // 收藏按钮兜底坐标：右侧竖排第三个
            0.75f,          // 横滑起点（终点自动取 1 - 该值）
            0.72f, 0.30f);  // 竖向翻页的起止点

    /** REDMI Watch 6：432×514，designWidth 216。 */
    public static final DeviceProfile WATCH_6 = new DeviceProfile(
            "watch6",
            "REDMI Watch 6",
            "432×514 · 另有屏幕按钮兜底",
            0.15f, 0.45f,
            0.93f, 0.60f,
            0.75f,
            0.72f, 0.30f);

    /** 选择框里列出来的顺序。 */
    public static final DeviceProfile[] ALL = {BAND_9_PRO, WATCH_6};

    public final String id;
    public final String name;
    public final String note;
    public final float holdX;
    public final float holdY;
    public final float favX;
    public final float favY;
    public final float hFrom;
    public final float vFrom;
    public final float vTo;

    private DeviceProfile(String id, String name, String note,
                          float holdX, float holdY,
                          float favX, float favY,
                          float hFrom, float vFrom, float vTo) {
        this.id = id;
        this.name = name;
        this.note = note;
        this.holdX = holdX;
        this.holdY = holdY;
        this.favX = favX;
        this.favY = favY;
        this.hFrom = hFrom;
        this.vFrom = vFrom;
        this.vTo = vTo;
    }

    /** 当前选中的设备；没选过就是手环 9 Pro。 */
    public static DeviceProfile current(Context context) {
        DeviceProfile saved = findById(read(context));
        return saved != null ? saved : BAND_9_PRO;
    }

    public static void select(Context context, DeviceProfile profile) {
        if (profile == null) {
            return;
        }
        try {
            prefs(context).edit().putString(KEY_DEVICE, profile.id).apply();
        } catch (Throwable ignored) {
        }
    }

    public static int indexOf(DeviceProfile profile) {
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].id.equals(profile.id)) {
                return i;
            }
        }
        return 0;
    }

    private static DeviceProfile findById(String id) {
        if (id == null) {
            return null;
        }
        for (DeviceProfile p : ALL) {
            if (p.id.equals(id)) {
                return p;
            }
        }
        return null;
    }

    private static String read(Context context) {
        try {
            return prefs(context).getString(KEY_DEVICE, BAND_9_PRO.id);
        } catch (Throwable t) {
            return BAND_9_PRO.id;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
