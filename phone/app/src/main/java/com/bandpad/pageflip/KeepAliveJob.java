package com.bandpad.pageflip;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

/**
 * 兜底守护：由系统调度器每 15 分钟唤醒一次，发现通道断开就重连。
 *
 * 它解决的是「用户在最近任务里划掉 App，进程没了」这种情况：
 * 无障碍服务通常会被系统自动重新绑定，通道能自己恢复；万一没有，
 * 这个定时任务会在下一次唤醒时把通道拉回来。
 *
 * 不做的那些事（双进程互拉、1px 通知、静默播放音频、循环闹钟）在 Android 8+
 * 的后台限制下基本失效，还会被判异常耗电、拖累续航，这里一概不用。
 */
public class KeepAliveJob extends JobService {

    private static final int JOB_ID = 7801;

    /** 幂等：重复调用不会堆叠任务。 */
    public static void schedule(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) {
                return;
            }
            JobInfo.Builder builder = new JobInfo.Builder(
                    JOB_ID, new ComponentName(context, KeepAliveJob.class))
                    .setPeriodic(15 * 60 * 1000L)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
                    .setPersisted(true);
            scheduler.schedule(builder.build());
        } catch (Throwable t) {
            UiLog.add("保活任务注册失败：" + t.getMessage());
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        try {
            WearableBridge.get().init(this);
            if (WearableBridge.get().isListening()) {
                UiLog.add("守护检查：通道正常");
            } else {
                UiLog.add("守护检查：通道断开，自动重连");
                WearableBridge.get().start();
            }
        } catch (Throwable t) {
            UiLog.add("守护任务异常：" + t.getMessage());
        }
        jobFinished(params, false);
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
