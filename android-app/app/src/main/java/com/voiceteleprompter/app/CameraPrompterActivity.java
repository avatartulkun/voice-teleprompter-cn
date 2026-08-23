package com.voiceteleprompter.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 相机拍摄提词 Activity（借鉴小白提词器的美颜相机模块，用开源 CameraX 替代商业 XMagic）。
 * CameraX 预览 + OpenGL 美颜相机 + 提词文本叠加 + Vosk 离线识别自动高亮 + 前后摄像头切换。
 */
public class CameraPrompterActivity extends Activity implements LifecycleOwner {

    private static final String TAG = "CameraPrompter";
    private static final String VOSK_MODEL_ASSET_DIR = "vosk-model-small-cn";
    private static final int CAMERA_AUDIO_REQUEST = 3001;
    private LifecycleRegistry lifecycleRegistry;

    private BeautyCameraView beautyCameraView;
    private TextView promptView;
    private TextView statusView;
    private Button startBtn;
    private Button pauseBtn;
    private Button switchCamBtn;
    private Button closeBtn;
    private Button beautyBtn;

    private float beautySmooth = 0.3f;
    private float beautyWhiten = 0.2f;
    private float beautyRuddy = 0.15f;
    private boolean beautyEnabled = true;

    private String scriptText = "";
    private String normalizedScript = "";
    private int readIndex = 0;
    private int targetReadIndex = 0;
    private int matchSensitivity = 3;
    private int fontSize = 28;
    private int readColor = Color.rgb(232, 93, 63);
    private int currentColor = Color.rgb(255, 224, 130);

    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private boolean useFrontCamera = true;
    private ExecutorService cameraExecutor;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        lifecycleRegistry = new LifecycleRegistry(this);
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);

        scriptText = getIntent().getStringExtra("script");
        if (scriptText == null || scriptText.isEmpty()) {
            scriptText = "请先在主界面输入稿件。";
        }
        normalizedScript = PinyinMatcher.normalizeText(scriptText);
        matchSensitivity = getIntent().getIntExtra("sensitivity", 3);
        fontSize = getIntent().getIntExtra("fontSize", 28);

        cameraExecutor = Executors.newSingleThreadExecutor();
        buildUi();

        if (hasPermissions()) {
            startCamera();
            loadVoskModel();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                    CAMERA_AUDIO_REQUEST);
        }
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        setContentView(root);

        beautyCameraView = new BeautyCameraView(this);
        root.addView(beautyCameraView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 提词文本叠层（半透明背景，借鉴小白提词器的拍摄提词叠加）
        LinearLayout promptPanel = new LinearLayout(this);
        promptPanel.setOrientation(LinearLayout.VERTICAL);
        promptPanel.setBackgroundColor(Color.argb(180, 17, 24, 29));
        promptPanel.setPadding(dp(16), dp(10), dp(16), dp(12));
        FrameLayout.LayoutParams promptParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(200));
        promptParams.gravity = Gravity.TOP;
        root.addView(promptPanel, promptParams);

        statusView = new TextView(this);
        statusView.setText("相机提词就绪");
        statusView.setTextColor(Color.rgb(180, 190, 200));
        statusView.setTextSize(11);
        promptPanel.addView(statusView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        promptView = new TextView(this);
        promptView.setTextSize(fontSize);
        promptView.setTextColor(Color.rgb(235, 240, 242));
        promptView.setLineSpacing(dp(3), 1.2f);
        promptView.setGravity(Gravity.START);
        LinearLayout.LayoutParams pvParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        pvParams.setMargins(0, dp(4), 0, 0);
        promptPanel.addView(promptView, pvParams);

        // 底部控制栏
        LinearLayout bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setBackgroundColor(Color.argb(200, 17, 24, 29));
        bottomBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bottomParams.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, bottomParams);

        startBtn = makeBtn("开始", Color.rgb(46, 160, 134));
        startBtn.setOnClickListener(v -> startRecognition());
        bottomBar.addView(startBtn, btnParams(1));

        pauseBtn = makeBtn("暂停", Color.rgb(180, 90, 70));
        pauseBtn.setEnabled(false);
        pauseBtn.setOnClickListener(v -> stopRecognition());
        bottomBar.addView(pauseBtn, btnParams(1));

        switchCamBtn = makeBtn("切换", Color.rgb(70, 110, 160));
        switchCamBtn.setOnClickListener(v -> { useFrontCamera = !useFrontCamera; startCamera(); });
        bottomBar.addView(switchCamBtn, btnParams(1));

        closeBtn = makeBtn("退出", Color.rgb(100, 100, 100));
        closeBtn.setOnClickListener(v -> finish());
        bottomBar.addView(closeBtn, btnParams(1));

        // 美颜按钮：点击弹出设置弹窗，长按一键关闭/开启
        beautyBtn = makeBtn(beautyBtnText(), Color.rgb(150, 80, 160));
        beautyBtn.setOnClickListener(v -> showBeautyDialog());
        beautyBtn.setOnLongClickListener(v -> {
            beautyEnabled = !beautyEnabled;
            applyBeautyParams();
            beautyBtn.setText(beautyBtnText());
            Toast.makeText(this, beautyEnabled ? "美颜已开启" : "美颜已关闭", Toast.LENGTH_SHORT).show();
            return true;
        });
        bottomBar.addView(beautyBtn, btnParams(1));

        // 让提词叠层避开状态栏、底部按钮条避开导航栏，
        // 否则按钮会压在系统导航栏下面点不到
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = Math.max(insets.getSystemWindowInsetTop(), systemBarHeight("status_bar_height"));
            int bottomInset = Math.max(insets.getSystemWindowInsetBottom(), systemBarHeight("navigation_bar_height"));
            promptPanel.setPadding(dp(16), topInset + dp(8), dp(16), dp(12));
            bottomBar.setPadding(dp(12), dp(8), dp(12), bottomInset + dp(8));
            return insets;
        });
        // 监听器可能注册在系统首次派发之后，主动要一次
        root.requestApplyInsets();

        // 初始化美颜参数
        applyBeautyParams();

        renderScript();
    }

    private String beautyBtnText() {
        return "美颜 " + (beautyEnabled ? "开" : "关");
    }

    private void applyBeautyParams() {
        if (beautyCameraView == null) return;
        if (beautyEnabled) {
            beautyCameraView.setBeautyParams(beautySmooth, beautyWhiten, beautyRuddy);
        } else {
            beautyCameraView.setBeautyParams(0f, 0f, 0f);
        }
    }

    private void showBeautyDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(16), dp(24), dp(8));

        SeekBar smoothSeek = addBeautyRow(container, "磨皮", (int) (beautySmooth * 100));
        SeekBar whitenSeek = addBeautyRow(container, "美白", (int) (beautyWhiten * 100));
        SeekBar ruddySeek = addBeautyRow(container, "红润", (int) (beautyRuddy * 100));

        beautyEnabled = true;

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                beautySmooth = smoothSeek.getProgress() / 100f;
                beautyWhiten = whitenSeek.getProgress() / 100f;
                beautyRuddy = ruddySeek.getProgress() / 100f;
                applyBeautyParams();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
        smoothSeek.setOnSeekBarChangeListener(listener);
        whitenSeek.setOnSeekBarChangeListener(listener);
        ruddySeek.setOnSeekBarChangeListener(listener);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("美颜设置")
                .setView(container)
                .setPositiveButton("完成", (d, w) -> {
                    beautyBtn.setText(beautyBtnText());
                })
                .setNegativeButton("关闭美颜", (d, w) -> {
                    beautyEnabled = false;
                    applyBeautyParams();
                    beautyBtn.setText(beautyBtnText());
                })
                .create();
        dialog.show();
    }

    private SeekBar addBeautyRow(LinearLayout container, String label, int progress) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, dp(6), 0, dp(6));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(tv, tvParams);

        SeekBar seek = new SeekBar(this);
        seek.setMax(100);
        seek.setProgress(progress);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, dp(40), 1);
        row.addView(seek, seekParams);

        container.addView(row, rowParams);
        return seek;
    }

    private Button makeBtn(String text, int color) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(color);
        btn.setTextSize(13);
        return btn;
    }

    private LinearLayout.LayoutParams btnParams(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), weight);
        p.setMargins(dp(2), 0, dp(2), 0);
        return p;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                Log.e(TAG, "camera init failed", e);
                statusView.setText("相机初始化失败");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();
        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(useFrontCamera
                        ? CameraSelector.LENS_FACING_FRONT
                        : CameraSelector.LENS_FACING_BACK)
                .build();
        Preview preview = new Preview.Builder()
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .build();
        preview.setSurfaceProvider(new Preview.SurfaceProvider() {
            @Override
            public void onSurfaceRequested(@NonNull SurfaceRequest request) {
                SurfaceTexture texture = beautyCameraView.getSurfaceTexture();
                android.util.Size res = request.getResolution();
                texture.setDefaultBufferSize(res.getWidth(), res.getHeight());
                beautyCameraView.setBufferSize(res.getWidth(), res.getHeight());
                // 自己提供 Surface 时 CameraX 不会替我们旋转画面，
                // 旋转角度只能从 TransformationInfo 拿，再交给渲染器补上。
                request.setTransformationInfoListener(
                        ContextCompat.getMainExecutor(CameraPrompterActivity.this),
                        info -> beautyCameraView.setCameraRotation(info.getRotationDegrees()));
                Surface surface = new Surface(texture);
                request.provideSurface(surface, ContextCompat.getMainExecutor(CameraPrompterActivity.this), result -> {
                    surface.release();
                });
            }
        });
        // 前置镜像，后置不镜像
        beautyCameraView.setMirror(useFrontCamera);
        try {
            camera = cameraProvider.bindToLifecycle(this, selector, preview);
            statusView.setText(useFrontCamera ? "前置相机 · 就绪" : "后置相机 · 就绪");
        } catch (Exception e) {
            Log.e(TAG, "bind camera failed", e);
            statusView.setText("相机绑定失败");
        }
    }

    private void loadVoskModel() {
        new Thread(() -> {
            try {
                File outDir = new File(getFilesDir(), VOSK_MODEL_ASSET_DIR);
                if (!outDir.exists() || !new File(outDir, "am/final.mdl").exists()) {
                    copyAssetFolder(VOSK_MODEL_ASSET_DIR, outDir.getAbsolutePath());
                }
                voskModel = new Model(outDir.getAbsolutePath());
                runOnUi(() -> statusView.setText("模型已加载 · 点开始朗读"));
            } catch (Exception e) {
                Log.e(TAG, "model load failed", e);
                runOnUi(() -> statusView.setText("模型加载失败"));
            }
        }, "cam-vosk-loader").start();
    }

    private void startRecognition() {
        if (voskRunning || voskModel == null) {
            if (voskModel == null) {
                Toast.makeText(this, "模型未加载", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        try {
            Recognizer recognizer = new Recognizer(voskModel, 16000.0f);
            voskSpeechService = new SpeechService(recognizer, 16000.0f);
            voskSpeechService.startListening(new org.vosk.android.RecognitionListener() {
                @Override public void onPartialResult(String s) {
                    String text = parseVoskJson(s, "partial");
                    if (!text.isEmpty()) updateProgress(text);
                }
                @Override public void onResult(String s) {
                    String text = parseVoskJson(s, "text");
                    if (!text.isEmpty()) updateProgress(text);
                }
                @Override public void onFinalResult(String s) {
                    String text = parseVoskJson(s, "text");
                    if (!text.isEmpty()) updateProgress(text);
                }
                @Override public void onError(Exception e) { statusView.setText("识别错误"); }
                @Override public void onTimeout() { voskRunning = false; statusView.setText("识别超时"); }
            });
            voskRunning = true;
            startBtn.setEnabled(false);
            pauseBtn.setEnabled(true);
            statusView.setText("识别中，请朗读");
            promptView.post(autoAdvanceRunnable);
        } catch (Exception e) {
            Log.e(TAG, "listen failed", e);
            statusView.setText("启动识别失败");
        }
    }

    private void stopRecognition() {
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

    private void updateProgress(String transcript) {
        int next = PinyinMatcher.findBestProgress(transcript, normalizedScript, readIndex, matchSensitivity);
        if (next > targetReadIndex) targetReadIndex = next;
    }

    private void renderScript() {
        SpannableString styled = new SpannableString(scriptText);
        String normalized = PinyinMatcher.normalizeText(scriptText);
        int count = 0;
        int currentVisual = normToVisual(scriptText, readIndex + 1);
        for (int i = 0; i < scriptText.length(); i++) {
            String nc = PinyinMatcher.normalizeText(String.valueOf(scriptText.charAt(i)));
            if (!nc.isEmpty()) count += nc.length();
            if (count > 0 && count <= readIndex) {
                styled.setSpan(new ForegroundColorSpan(readColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (i == currentVisual && readIndex < normalized.length()) {
                styled.setSpan(new BackgroundColorSpan(currentColor), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        promptView.setText(styled);
    }

    private int normToVisual(String script, int target) {
        int count = 0;
        for (int i = 0; i < script.length(); i++) {
            String nc = PinyinMatcher.normalizeText(String.valueOf(script.charAt(i)));
            if (nc.isEmpty()) continue;
            count += nc.length();
            if (count >= target) return i;
        }
        return Math.max(0, script.length() - 1);
    }

    private String parseVoskJson(String json, String key) {
        try { return new JSONObject(json).optString(key, ""); }
        catch (Exception e) { return ""; }
    }

    private void copyAssetFolder(String assetPath, String dstPath) throws IOException {
        String[] children = getAssets().list(assetPath);
        if (children == null) { copyAssetFile(assetPath, dstPath); return; }
        File dir = new File(dstPath);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("无法创建目录：" + dstPath);
        if (children.length == 0) { copyAssetFile(assetPath, dstPath); return; }
        for (String child : children) {
            String src = assetPath + "/" + child;
            String dst = dstPath + "/" + child;
            String[] sub = getAssets().list(src);
            if (sub != null && sub.length > 0) copyAssetFolder(src, dst);
            else copyAssetFile(src, dst);
        }
    }

    private void copyAssetFile(String assetPath, String dstPath) throws IOException {
        new File(dstPath).getParentFile().mkdirs();
        java.io.InputStream in = getAssets().open(assetPath);
        java.io.OutputStream out = new java.io.FileOutputStream(dstPath);
        byte[] buf = new byte[8192]; int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }

    /** 读取系统栏高度作为兜底，防止某些 ROM 把 inset 报成 0 导致按钮被导航栏挡住。 */
    private int systemBarHeight(String resourceName) {
        int id = getResources().getIdentifier(resourceName, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private void runOnUi(Runnable r) { promptView.post(r); }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_AUDIO_REQUEST && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
            loadVoskModel();
        } else {
            statusView.setText("需要相机和麦克风权限");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (lifecycleRegistry != null) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (lifecycleRegistry != null) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (lifecycleRegistry != null) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (lifecycleRegistry != null) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return lifecycleRegistry;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lifecycleRegistry != null) lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
        stopRecognition();
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        voskModel = null;
    }
}
