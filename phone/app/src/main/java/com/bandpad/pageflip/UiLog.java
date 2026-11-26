package com.bandpad.pageflip;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/**
 * 极简日志缓存，界面轮询展示用。
 */
public final class UiLog {

    private static final int MAX = 80;
    private static final Deque<String> LINES = new ArrayDeque<>();
    private static final SimpleDateFormat FMT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private UiLog() {
    }

    public static synchronized void add(String line) {
        String text = FMT.format(new Date()) + "  " + line;
        LINES.addFirst(text);
        while (LINES.size() > MAX) {
            LINES.removeLast();
        }
        android.util.Log.i("BandPad", line);
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (String line : LINES) {
            sb.append(line).append('\n');
        }
        if (sb.length() == 0) {
            sb.append("（暂无记录）");
        }
        return sb.toString();
    }
}
