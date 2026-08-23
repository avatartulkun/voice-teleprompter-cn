package com.voiceteleprompter.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.IOException;

/**
 * 悬浮窗提词服务（借鉴小白提词器的悬浮窗架构）。
 * 在其他应用上层显示提词文本，同时运行 Vosk 离线识别与拼音匹配自动高亮。
 */
public class PrompterFloatService extends Service {

    private static final String TAG = "FloatPrompter";
    private static final String CHANNEL_ID = "float_prompter";
    private static final int NOTIFICATION_ID = 1001;
    private static final String VOSK_MODEL_ASSET_DIR = "vosk-model-small-cn";

    private WindowManager windowManager;
    private View floatRoot;
    private TextView promptView;
    private TextView statusView;
    private Button startBtn;
    private Button pauseBtn;
    private SeekBar opacitySeek;

    private String scriptText = "";
    private String normalizedScript = "";
    private int readIndex = 0;
    private int targetReadIndex = 0;
    private int fontSize = 32;
    private int readColor = Color.rgb(232, 93, 63);
    private int currentColor = Color.rgb(255, 224, 130);
    private int bgColor = Color.rgb(17, 24, 29);
    private int matchSensitivity = 3;
    private float opacity = 0.85f;

    private Model voskModel;
    private SpeechService voskSpeechService;
    private boolean voskRunning = false;

    private final Runnable autoAdvanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (targetReadIndex > readIndex) {
                readIndex = Math.min(targetReadIndex, readIndex + 1);
                renderScript();
                promptView.postDelayed(this, 160);
                return;
            }
            promptView.postDelayed(this, 450);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    /**
     * 悬浮提词要在其他应用之上持续录音识别，必须以前台服务运行，
     * 否则 Android 14 起会在切到后台后杀掉服务，提词中断。
     */
    private void startAsForeground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "悬浮提词", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("悬浮提词运行时的常驻通知");
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);

        PendingIntent pending = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮提词进行中")
                .setContentText("点击回到语音跟读提词器")
                .setSmallIcon(R.drawable.ic_ui_mic)
                .setContentIntent(pending)
                .setOngoing(true)
                .build();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            // 缺录音权限等情况下系统会拒绝启动 microphone 类型的前台服务，
            // 这里不能让它把整个应用带崩，退化成普通服务继续显示悬浮窗即可。
            Log.w(TAG, "startForeground failed, fall back to background service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground();
        if (intent != null) {
            scriptText = intent.getStringExtra("script");
            if (scriptText == null || scriptText.isEmpty()) {
                scriptText = "";
            }
            normalizedScript = PinyinMatcher.normalizeText(scriptText);
            fontSize = intent.getIntExtra("fontSize", 32);
            matchSensitivity = intent.getIntExtra("sensitivity", 3);
        }
        if (floatRoot == null) {
            createFloatWindow();
        }
        renderScript();
        return START_NOT_STICKY;
    }

    private void createFloatWindow() {
        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(adjustAlpha(bgColor, opacity));
        root.setPadding(dp(14), dp(10), dp(14), dp(12));
        floatRoot = root;

        // 顶部状态行 + 关闭按钮
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        statusView = new TextView(this);
        statusView.setText("悬浮提词已就绪");
        statusView.setTextColor(Color.rgb(180, 190, 200));
        statusView.setTextSize(11);
        statusView.setMaxLines(1);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        topBar.addView(statusView, statusParams);

        Button closeBtn = new Button(this);
        closeBtn.setText("×");
        closeBtn.setTextColor(Color.rgb(200, 210, 220));
        closeBtn.setBackgroundColor(Color.TRANSPARENT);
        closeBtn.setTextSize(18);
        closeBtn.setPadding(dp(8), 0, dp(8), 0);
        closeBtn.setMinWidth(0);
        closeBtn.setMinHeight(0);
        closeBtn.setOnClickListener(v -> stopSelf());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(40), dp(32));
        topBar.addView(closeBtn, closeParams);
        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 提词文本区
        promptView = new TextView(this);
        promptView.setTextSize(fontSize);
        promptView.setTextColor(Color.rgb(235, 240, 242));
        promptView.setLineSpacing(dp(4), 1.25f);
        promptView.setGravity(Gravity.START);
        LinearLayout.LayoutParams promptParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        promptParams.setMargins(0, dp(6), 0, dp(6));
        root.addView(promptView, promptParams);

        // 底部控制栏
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);

        startBtn = new Button(this);
        startBtn.setText("开始");
        startBtn.setTextColor(Color.WHITE);
        startBtn.setBackgroundColor(Color.rgb(46, 160, 134));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        btnParams.setMargins(0, 0, dp(4), 0);
        startBtn.setOnClickListener(v -> startFloatRecognition());
        bottomBar.addView(startBtn, btnParams);

        pauseBtn = new Button(this);
        pauseBtn.setText("暂停");
        pauseBtn.setTextColor(Color.WHITE);
        pauseBtn.setBackgroundColor(Color.rgb(180, 90, 70));
        pauseBtn.setEnabled(false);
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        pauseParams.setMargins(dp(4), 0, dp(4), 0);
        pauseBtn.setOnClickListener(v -> stopFloatRecognition());
        bottomBar.addView(pauseBtn, pauseParams);

        opacitySeek = new SeekBar(this);
        opacitySeek.setMax(100);
        opacitySeek.setProgress((int) (opacity * 100));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacity = Math.max(0.3f, progress / 100f);
                root.setBackgroundColor(adjustAlpha(bgColor, opacity));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(dp(80), dp(40));
        seekParams.setMargins(dp(4), 0, 0, 0);
        bottomBar.addView(opacitySeek, seekParams);
        root.addView(bottomBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 悬浮窗参数
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                dp(240),
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        params.x = 0;
        params.y = dp(80);

        windowManager.addView(floatRoot, params);
        setupDrag(floatRoot, params);
    }

    /** 设置悬浮窗拖拽（借鉴小白提词器的悬浮窗交互）。 */
    private void setupDrag(View root, WindowManager.LayoutParams params) {
        root.setOnTouchListener(new View.OnTouchListener() {
            int initialX, initialY;
            float touchX, touchY;
            boolean moved;
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        touchX = event.getRawX();
                        touchY = event.getRawY();
                        moved = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - touchX);
                        int dy = (int) (event.getRawY() - touchY);
                        if (Math.abs(dx) > 4 || Math.abs(dy) > 4) moved = true;
                        params.x = initialX + dx;
                        params.y = initialY + dy;
                        windowManager.updateViewLayout(floatRoot, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        return moved;
                }
                return false;
            }
        });
    }

    /** 启动悬浮窗内的 Vosk 离线识别。 */
    private void startFloatRecognition() {
        if (voskRunning) return;
        statusView.setText("正在加载离线模型...");
        startBtn.setEnabled(false);
        pauseBtn.setEnabled(true);
        readIndex = 0;
        targetReadIndex = 0;

        if (voskModel != null) {
            beginListening();
            return;
        }
        new Thread(() -> {
            try {
                File outDir = new File(getFilesDir(), VOSK_MODEL_ASSET_DIR);
                copyAssetFolder(VOSK_MODEL_ASSET_DIR, outDir.getAbsolutePath());
                voskModel = new Model(outDir.getAbsolutePath());
                runOnUi(this::beginListening);
            } catch (Exception ex) {
                Log.e(TAG, "model load failed", ex);
                runOnUi(() -> {
                    statusView.setText("模型加载失败");
                    startBtn.setEnabled(true);
                    pauseBtn.setEnabled(false);
                });
            }
        }, "float-vosk-loader").start();
    }

    private void beginListening() {
        try {
            Recognizer recognizer = new Recognizer(voskModel, 16000.0f);
            voskSpeechService = new SpeechService(recognizer, 16000.0f);
            voskSpeechService.startListening(new org.vosk.android.RecognitionListener() {
                @Override
                public void onPartialResult(String s) {
                    String text = parseVoskJson(s, "partial");
                    if (!text.isEmpty()) {
                        updateProgress(text);
                    }
                }
                @Override
                public void onResult(String s) {
                    String text = parseVoskJson(s, "text");
                    if (!text.isEmpty()) {
                        updateProgress(text);
                    }
                }
                @Override
                public void onFinalResult(String s) {
                    String text = parseVoskJson(s, "text");
                    if (!text.isEmpty()) {
                        updateProgress(text);
                    }
                }
                @Override
                public void onError(Exception e) {
                    statusView.setText("识别错误");
                }
                @Override
                public void onTimeout() {
                    voskRunning = false;
                    statusView.setText("识别超时");
                }
            });
            voskRunning = true;
            statusView.setText("离线识别中，请朗读");
            promptView.post(autoAdvanceRunnable);
        } catch (Exception e) {
            Log.e(TAG, "listen failed", e);
            statusView.setText("启动识别失败");
        }
    }

    private void updateProgress(String transcript) {
        int next = PinyinMatcher.findBestProgress(transcript, normalizedScript, readIndex, matchSensitivity);
        if (next > targetReadIndex) {
            targetReadIndex = next;
        }
    }

    private void stopFloatRecognition() {
        if (voskSpeechService != null) {
            try { voskSpeechService.stop(); } catch (Exception ignored) {}
            voskSpeechService = null;
        }
        voskRunning = false;
        promptView.removeCallbacks(autoAdvanceRunnable);
        startBtn.setEnabled(true);
        pauseBtn.setEnabled(false);
        statusView.setText("已暂停");
    }

    private void renderScript() {
        if (scriptText == null || scriptText.isEmpty()) {
            promptView.setText("（未设置稿件）");
            return;
        }
        SpannableString styled = new SpannableString(scriptText);
        String normalized = PinyinMatcher.normalizeText(scriptText);
        int normalizedCount = 0;
        int currentVisualIndex = normalizedIndexToVisualIndex(scriptText, readIndex + 1);
        for (int index = 0; index < scriptText.length(); index++) {
            String nc = PinyinMatcher.normalizeText(String.valueOf(scriptText.charAt(index)));
            if (!nc.isEmpty()) {
                normalizedCount += nc.length();
            }
            if (normalizedCount > 0 && normalizedCount <= readIndex) {
                styled.setSpan(new ForegroundColorSpan(readColor), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (index == currentVisualIndex && readIndex < normalized.length()) {
                styled.setSpan(new BackgroundColorSpan(currentColor), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        promptView.setText(styled);
    }

    private int normalizedIndexToVisualIndex(String script, int targetIndex) {
        int count = 0;
        for (int i = 0; i < script.length(); i++) {
            String nc = PinyinMatcher.normalizeText(String.valueOf(script.charAt(i)));
            if (nc.isEmpty()) continue;
            count += nc.length();
            if (count >= targetIndex) return i;
        }
        return Math.max(0, script.length() - 1);
    }

    private String parseVoskJson(String json, String key) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString(key, "");
        } catch (Exception e) {
            return "";
        }
    }

    private void copyAssetFolder(String assetPath, String dstPath) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null) {
            copyAssetFile(assetPath, dstPath);
            return;
        }
        File dir = new File(dstPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录：" + dstPath);
        }
        if (children.length == 0) {
            copyAssetFile(assetPath, dstPath);
            return;
        }
        for (String child : children) {
            String src = assetPath + "/" + child;
            String dst = dstPath + "/" + child;
            String[] sub = getAssets().list(src);
            if (sub != null && sub.length > 0) {
                copyAssetFolder(src, dst);
            } else {
                copyAssetFile(src, dst);
            }
        }
    }

    private void copyAssetFile(String assetPath, String dstPath) throws IOException {
        new File(dstPath).getParentFile().mkdirs();
        java.io.InputStream in = getAssets().open(assetPath);
        java.io.OutputStream out = new java.io.FileOutputStream(dstPath);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    private int dp(int value) {
        return (int) (value * Resources.getSystem().getDisplayMetrics().density);
    }

    private int adjustAlpha(int color, float factor) {
        int a = Math.min(255, (int) (Color.alpha(color) * factor));
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void runOnUi(Runnable r) {
        if (floatRoot != null) {
            floatRoot.post(r);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopFloatRecognition();
        if (floatRoot != null && windowManager != null) {
            windowManager.removeView(floatRoot);
            floatRoot = null;
        }
        voskModel = null;
    }
}
