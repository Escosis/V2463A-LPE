package com.escosis.v2463alpe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    public static final String EXTRA_MODE = "mode";
    public static final String MODE_STEP1 = "step1";
    public static final String MODE_STEP2 = "step2";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)) {
            return;
        }

        Log.d(TAG, "LOCKED_BOOT_COMPLETED 收到");

        if (!Prefs.INSTANCE.isAppEnabled(context)) {
            Log.d(TAG, "应用已禁用，跳过锁屏界面");
            return;
        }

        Context deContext = context.createDeviceProtectedStorageContext();

        File stage1Flag = new File(deContext.getFilesDir(), "escalate_stage1_done.flag");
        boolean stage1Done = stage1Flag.exists();
        if (stage1Done) {
            try {
                stage1Flag.delete();
                Log.d(TAG, "已删除第一阶段标志");
            } catch (Exception e) {
                Log.e(TAG, "删除标志失败", e);
            }

            Log.d(TAG, "启动 LockScreenActivity, mode=step2");
            Intent lockIntent = new Intent(context, LockScreenActivity.class);
            lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            lockIntent.putExtra(EXTRA_MODE, MODE_STEP2);
            try {
                context.startActivity(lockIntent);
            } catch (Exception e) {
                Log.e(TAG, "启动失败", e);
            }
            return;
        }

        if (hasRoot()) {
            Log.w(TAG, "su 返回 uid=0，跳过锁屏界面");
            return;
        }

        Log.d(TAG, "启动 LockScreenActivity, mode=step1");
        Intent lockIntent = new Intent(context, LockScreenActivity.class);
        lockIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        lockIntent.putExtra(EXTRA_MODE, MODE_STEP1);
        try {
            context.startActivity(lockIntent);
        } catch (Exception e) {
            Log.e(TAG, "启动失败", e);
        }
    }

    private boolean hasRoot() {
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("su", "-c", "id");
            pb.redirectErrorStream(true);
            p = pb.start();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }

            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                p.destroy();
                Log.w(TAG, "su 执行超时，视为无 root");
                return false;
            }

            String out = sb.toString();
            if (out.contains("uid=0")) {
                Log.d(TAG, "su 返回 uid=0: " + out.trim());
                return true;
            }
            Log.w(TAG, "su 未返回 uid=0: " + out.trim());
            return false;
        } catch (Exception e) {
            Log.e(TAG, "su 执行异常", e);
            return false;
        } finally {
            if (p != null) {
                try { p.destroy(); } catch (Exception ignored) {}
            }
        }
    }
}