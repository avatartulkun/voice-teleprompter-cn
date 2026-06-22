package com.example.voiceteleprompter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.content.res.Configuration;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends Activity {
    private static final int AUDIO_PERMISSION_REQUEST = 1001;
    private static final int PANEL_REQUEST = 2002;
    private static final String PREFS_NAME = "voice_teleprompter_settings";
    private static final String PREF_BAIDU_API_KEY = "baidu_api_key";
    private static final String PREF_BAIDU_APP_ID = "baidu_app_id";
    private static final String PREF_BAIDU_SECRET_KEY = "baidu_secret_key";
    private static final String PREF_SCRIPT = "script";
    private static final String PREF_COLOR_SPEED = "color_speed";
    private static final String PREF_AUTO_SPEED = "auto_speed";
    private static final String PREF_SCROLL_SPEED = "scroll_speed";
    private static final String PREF_FONT_SIZE = "font_size";
    private static final String PREF_READ_COLOR = "read_color";
    private static final String PREF_CURRENT_COLOR = "current_color";
    private static final String PREF_BACKGROUND_COLOR = "background_color";
    private static final String BUILT_IN_BAIDU_API_KEY = "";
    private static final String BUILT_IN_BAIDU_APP_ID = "";
    private static final String BUILT_IN_BAIDU_SECRET_KEY = "";

    private TextView statusView;
    private TextView promptView;
    private TextView titleView;
    private String scriptText = "";
    private ScrollView homeScroll;
    private LinearLayout homePanel;
    private LinearLayout controlsPanel;
    private ScrollView promptScroll;
    private Button startButton;
    private Button pauseButton;
    private Button fullscreenButton;
    private Button backHomeButton;
    private Button autoScrollButton;

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private OkHttpClient realtimeClient;
    private WebSocket realtimeWebSocket;
    private AudioRecord audioRecord;
    private Thread audioThread;
    private MediaRecorder mediaRecorder;
    private File recordingFile;
    private boolean speechServiceAvailable;
    private boolean cloudRecording;
    private volatile boolean realtimeStreaming;
    private boolean shouldListen;
    private boolean autoScrolling;
    private boolean fullscreenMode;
    private boolean landscapeMode;
    private boolean prompterMode;
    private boolean startAfterPermission;
    private boolean testAfterPermission;
    private String savedBaiduAppId = "";
    private String savedBaiduApiKey = "";
    private String savedBaiduSecretKey = "";
    private String realtimeFinalText = "";
    private String normalizedScript = "";
    private int colorStep = 1;
    private int autoAdvanceStep = 1;
    private int scrollSpeed = 1;
    private int promptFontSize = 38;
    private int readColor = Color.rgb(232, 93, 63);
    private int currentColor = Color.rgb(255, 224, 130);
    private int backgroundColor = Color.rgb(17, 24, 29);
    private int readIndex;
    private int targetReadIndex;
    private long lastProgressAt;
    private final Runnable autoAdvanceRunnable = new Runnable() {
        @Override
        public void run() {
            if (realtimeStreaming && normalizedScript.length() > 0) {
                if (targetReadIndex > readIndex) {
                    readIndex = Math.min(targetReadIndex, readIndex + Math.max(1, colorStep));
                    renderScript();
                    promptView.postDelayed(this, 160);
                    return;
                }
                promptView.postDelayed(this, 450);
            }
        }
    };
    private final Runnable autoScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (autoScrolling) {
                promptScroll.smoothScrollBy(0, Math.max(1, scrollSpeed * 2));
                promptScroll.postDelayed(this, 90);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loadSettings();
        buildUi();
        speechServiceAvailable = false;
        renderScript();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(245, 247, 248));
        setContentView(root);

        promptScroll = new ScrollView(this);
        promptScroll.setFillViewport(true);
        promptScroll.setVerticalScrollBarEnabled(true);
        promptScroll.setScrollbarFadingEnabled(false);
        promptScroll.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        promptScroll.setClipToPadding(false);
        promptScroll.setBackgroundColor(backgroundColor);
        root.addView(promptScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        promptView = new TextView(this);
        promptView.setTextSize(promptFontSize);
        promptView.setTextColor(Color.rgb(235, 240, 242));
        promptView.setLineSpacing(dp(12), 1.22f);
        promptView.setGravity(Gravity.START);
        promptView.setPadding(dp(24), dp(120), dp(28), dp(260));
        promptView.setOnClickListener(view -> togglePlayPauseFromPrompt());
        promptScroll.setOnClickListener(view -> togglePlayPauseFromPrompt());
        promptScroll.addView(promptView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.setBackgroundColor(Color.argb(245, 245, 247, 248));
        root.addView(homeScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homePanel = new LinearLayout(this);
        homePanel.setOrientation(LinearLayout.VERTICAL);
        homePanel.setPadding(dp(18), dp(18), dp(18), dp(18));
        homeScroll.addView(homePanel, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        controlsPanel = new LinearLayout(this);
        controlsPanel.setOrientation(LinearLayout.VERTICAL);
        controlsPanel.setPadding(dp(10), dp(8), dp(10), dp(10));
        controlsPanel.setBackgroundColor(Color.argb(228, 245, 247, 248));
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlParams.gravity = Gravity.BOTTOM;
        controlParams.setMargins(dp(10), 0, dp(10), dp(10));
        root.addView(controlsPanel, controlParams);

        backHomeButton = makeButton(getString(R.string.button_back_home));
        FrameLayout.LayoutParams backHomeParams = new FrameLayout.LayoutParams(dp(108), dp(42));
        backHomeParams.gravity = Gravity.TOP | Gravity.START;
        backHomeParams.setMargins(dp(12), dp(12), 0, 0);
        root.addView(backHomeButton, backHomeParams);
        backHomeButton.setVisibility(View.GONE);

        titleView = new TextView(this);
        titleView.setText(R.string.title_main);
        titleView.setTextSize(24);
        titleView.setTextColor(Color.rgb(23, 32, 38));
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        homePanel.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        statusView = new TextView(this);
        statusView.setText(R.string.status_ready);
        statusView.setTextSize(15);
        statusView.setTextColor(Color.rgb(93, 105, 115));
        homePanel.addView(statusView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        LinearLayout homeButtons = new LinearLayout(this);
        homeButtons.setOrientation(LinearLayout.VERTICAL);
        homePanel.addView(homeButtons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        controlsPanel.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));

        Button testButton = makeButton(getString(R.string.button_mic_test));
        startButton = makeButton(getString(R.string.button_start));
        pauseButton = makeButton(getString(R.string.button_pause));
        Button resetButton = makeButton(getString(R.string.button_reset));
        Button settingsButton = makeButton(getString(R.string.button_settings));
        Button scriptButton = makeButton(getString(R.string.button_script));
        Button followSettingsButton = makeButton(getString(R.string.button_follow_settings));
        Button displaySettingsButton = makeButton(getString(R.string.button_display_settings));
        Button enterPrompterButton = makeButton(getString(R.string.button_enter_prompter));
        fullscreenButton = makeButton(getString(R.string.button_full_landscape));
        autoScrollButton = makeButton(getString(R.string.button_auto_scroll));
        Button backButton = makeButton(getString(R.string.button_back));
        Button forwardButton = makeButton(getString(R.string.button_forward));
        pauseButton.setEnabled(false);

        buttons.addView(startButton, smallButtonParams());
        buttons.addView(pauseButton, smallButtonParams());
        buttons.addView(resetButton, smallButtonParams());
        buttons.addView(fullscreenButton, smallButtonParams());
        buttons.addView(autoScrollButton, smallButtonParams());
        buttons.addView(backButton, smallButtonParams());
        buttons.addView(forwardButton, smallButtonParams());

        homeButtons.addView(testButton, verticalButtonParams());
        homeButtons.addView(scriptButton, verticalButtonParams());
        homeButtons.addView(settingsButton, verticalButtonParams());
        homeButtons.addView(followSettingsButton, verticalButtonParams());
        homeButtons.addView(displaySettingsButton, verticalButtonParams());
        homeButtons.addView(enterPrompterButton, verticalButtonParams());

        testButton.setOnClickListener(view -> testMic());
        enterPrompterButton.setOnClickListener(view -> enterPrompterMode());
        backHomeButton.setOnClickListener(view -> enterHomeMode());
        startButton.setOnClickListener(view -> startReading());
        pauseButton.setOnClickListener(view -> stopReading());
        resetButton.setOnClickListener(view -> resetReading());
        settingsButton.setOnClickListener(view -> showSettingsDialog());
        scriptButton.setOnClickListener(view -> showScriptDialog());
        followSettingsButton.setOnClickListener(view -> showFollowSettingsDialog());
        displaySettingsButton.setOnClickListener(view -> showDisplaySettingsDialog());
        fullscreenButton.setOnClickListener(view -> toggleFullLandscape());
        autoScrollButton.setOnClickListener(view -> toggleAutoScroll());
        backButton.setOnClickListener(view -> nudgeReadIndex(-5));
        forwardButton.setOnClickListener(view -> nudgeReadIndex(5));
        applyOrientationLayout();
        applyDisplaySettings();
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.button_primary);
        button.setElevation(dp(4));
        button.setStateListAnimator(null);
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(dp(3), 0, dp(3), 0);
        return params;
    }

    private LinearLayout.LayoutParams smallButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(dp(1), 0, dp(1), 0);
        return params;
    }

    private LinearLayout.LayoutParams verticalButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private void enterPrompterMode() {
        prompterMode = true;
        homeScroll.setVisibility(View.GONE);
        controlsPanel.setVisibility(View.VISIBLE);
        statusView.setText(R.string.status_tap_mic);
        applyOrientationLayout();
    }

    private void enterHomeMode() {
        stopAutoScroll();
        if (realtimeStreaming || cloudRecording || shouldListen) {
            stopReading();
        }
        fullscreenMode = false;
        landscapeMode = false;
        fullscreenButton.setText(R.string.button_full_landscape);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        Window window = getWindow();
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        fullscreenMode = false;
        prompterMode = false;
        homeScroll.setVisibility(View.VISIBLE);
        controlsPanel.setVisibility(View.GONE);
        backHomeButton.setVisibility(View.GONE);
        statusView.setText(R.string.status_ready);
        promptScroll.postDelayed(this::applyOrientationLayout, 350);
    }

    private void togglePlayPauseFromPrompt() {
        if (!prompterMode) {
            return;
        }
        if (autoScrolling && !realtimeStreaming && !cloudRecording && !shouldListen) {
            stopAutoScroll();
            return;
        }
        if (realtimeStreaming || cloudRecording || shouldListen) {
            stopReading();
        } else {
            startReading();
        }
    }

    private void toggleAutoScroll() {
        if (autoScrolling) {
            stopAutoScroll();
        } else {
            startAutoScroll();
        }
    }

    private void startAutoScroll() {
        autoScrolling = true;
        autoScrollButton.setText(R.string.button_stop_scroll);
        promptScroll.removeCallbacks(autoScrollRunnable);
        promptScroll.postDelayed(autoScrollRunnable, 120);
    }

    private void stopAutoScroll() {
        autoScrolling = false;
        if (autoScrollButton != null) {
            autoScrollButton.setText(R.string.button_auto_scroll);
        }
        promptScroll.removeCallbacks(autoScrollRunnable);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        savedBaiduApiKey = prefs.getString(PREF_BAIDU_API_KEY, "");
        savedBaiduAppId = prefs.getString(PREF_BAIDU_APP_ID, "");
        savedBaiduSecretKey = prefs.getString(PREF_BAIDU_SECRET_KEY, "");
        scriptText = prefs.getString(PREF_SCRIPT, getString(R.string.default_script));
        colorStep = prefs.getInt(PREF_COLOR_SPEED, 1);
        autoAdvanceStep = prefs.getInt(PREF_AUTO_SPEED, 1);
        scrollSpeed = prefs.getInt(PREF_SCROLL_SPEED, 1);
        promptFontSize = prefs.getInt(PREF_FONT_SIZE, 38);
        readColor = prefs.getInt(PREF_READ_COLOR, Color.rgb(232, 93, 63));
        currentColor = prefs.getInt(PREF_CURRENT_COLOR, Color.rgb(255, 224, 130));
        backgroundColor = prefs.getInt(PREF_BACKGROUND_COLOR, Color.rgb(17, 24, 29));
    }

    private void showSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        EditText apiKeyField = new EditText(this);
        apiKeyField.setHint(R.string.hint_baidu_api_key);
        apiKeyField.setSingleLine(true);
        apiKeyField.setText(savedBaiduApiKey);
        form.addView(apiKeyField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        EditText appIdField = new EditText(this);
        appIdField.setHint(R.string.hint_baidu_app_id);
        appIdField.setSingleLine(true);
        appIdField.setText(savedBaiduAppId);
        form.addView(appIdField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        EditText secretKeyField = new EditText(this);
        secretKeyField.setHint(R.string.hint_baidu_secret_key);
        secretKeyField.setSingleLine(true);
        secretKeyField.setText(savedBaiduSecretKey);
        form.addView(secretKeyField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_settings)
            .setView(form)
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                savedBaiduApiKey = apiKeyField.getText().toString().trim();
                savedBaiduAppId = appIdField.getText().toString().trim();
                savedBaiduSecretKey = secretKeyField.getText().toString().trim();
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_BAIDU_API_KEY, savedBaiduApiKey)
                    .putString(PREF_BAIDU_APP_ID, savedBaiduAppId)
                    .putString(PREF_BAIDU_SECRET_KEY, savedBaiduSecretKey)
                    .apply();
                statusView.setText(R.string.settings_saved);
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void showScriptDialog() {
        EditText scriptField = new EditText(this);
        scriptField.setText(scriptText);
        scriptField.setMinLines(8);
        scriptField.setGravity(Gravity.TOP);
        scriptField.setPadding(dp(18), dp(8), dp(18), dp(8));

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_script)
            .setView(scriptField)
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                scriptText = scriptField.getText().toString();
                readIndex = 0;
                targetReadIndex = 0;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_SCRIPT, scriptText)
                    .apply();
                statusView.setText(R.string.script_saved);
                renderScript();
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void showFollowSettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        TextView colorLabel = settingLabel(getString(R.string.label_color_speed) + ": " + colorStep);
        SeekBar colorSeek = settingSeekBar(5, colorStep);
        TextView autoLabel = settingLabel(getString(R.string.label_auto_speed) + ": " + autoAdvanceStep);
        SeekBar autoSeek = settingSeekBar(5, autoAdvanceStep);
        TextView scrollLabel = settingLabel(getString(R.string.label_scroll_speed) + ": " + scrollSpeed);
        SeekBar scrollSeek = settingSeekBar(5, scrollSpeed);

        form.addView(colorLabel);
        form.addView(colorSeek);
        form.addView(autoLabel);
        form.addView(autoSeek);
        form.addView(scrollLabel);
        form.addView(scrollSeek);

        colorSeek.setOnSeekBarChangeListener(settingListener(colorLabel, getString(R.string.label_color_speed)));
        autoSeek.setOnSeekBarChangeListener(settingListener(autoLabel, getString(R.string.label_auto_speed)));
        scrollSeek.setOnSeekBarChangeListener(settingListener(scrollLabel, getString(R.string.label_scroll_speed)));

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_follow_settings)
            .setView(form)
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                colorStep = Math.max(1, colorSeek.getProgress());
                autoAdvanceStep = Math.max(1, autoSeek.getProgress());
                scrollSpeed = Math.max(1, scrollSeek.getProgress());
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(PREF_COLOR_SPEED, colorStep)
                    .putInt(PREF_AUTO_SPEED, autoAdvanceStep)
                    .putInt(PREF_SCROLL_SPEED, scrollSpeed)
                    .apply();
                statusView.setText(R.string.settings_saved);
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void showDisplaySettingsDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), 0);

        TextView fontLabel = settingLabel(getString(R.string.label_font_size) + ": " + promptFontSize);
        SeekBar fontSeek = settingSeekBar(72, promptFontSize);
        form.addView(fontLabel);
        form.addView(fontSeek);
        int originalFontSize = promptFontSize;
        int originalReadColor = readColor;
        int originalCurrentColor = currentColor;
        int originalBackgroundColor = backgroundColor;

        fontSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                promptFontSize = Math.max(24, progress);
                fontLabel.setText(getString(R.string.label_font_size) + ": " + promptFontSize);
                applyDisplaySettings();
                renderScript();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        TextView readLabel = settingLabel(getString(R.string.label_read_color));
        LinearLayout readColors = colorRow(new int[] {
            Color.rgb(232, 93, 63),
            Color.rgb(66, 184, 131),
            Color.rgb(79, 140, 255),
            Color.rgb(255, 193, 7)
        }, color -> readColor = color);

        TextView currentLabel = settingLabel(getString(R.string.label_current_color));
        LinearLayout currentColors = colorRow(new int[] {
            Color.rgb(255, 224, 130),
            Color.rgb(181, 234, 215),
            Color.rgb(174, 214, 241),
            Color.rgb(255, 204, 188)
        }, color -> currentColor = color);

        TextView bgLabel = settingLabel(getString(R.string.label_background_color));
        LinearLayout bgColors = colorRow(new int[] {
            Color.rgb(17, 24, 29),
            Color.rgb(0, 0, 0),
            Color.rgb(33, 37, 41),
            Color.rgb(245, 247, 248)
        }, color -> backgroundColor = color);

        form.addView(readLabel);
        form.addView(readColors);
        form.addView(currentLabel);
        form.addView(currentColors);
        form.addView(bgLabel);
        form.addView(bgColors);

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_display_settings)
            .setView(form)
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                promptFontSize = Math.max(24, fontSeek.getProgress());
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(PREF_FONT_SIZE, promptFontSize)
                    .putInt(PREF_READ_COLOR, readColor)
                    .putInt(PREF_CURRENT_COLOR, currentColor)
                    .putInt(PREF_BACKGROUND_COLOR, backgroundColor)
                    .apply();
                applyDisplaySettings();
                renderScript();
                statusView.setText(R.string.settings_saved);
            })
            .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> {
                promptFontSize = originalFontSize;
                readColor = originalReadColor;
                currentColor = originalCurrentColor;
                backgroundColor = originalBackgroundColor;
                applyDisplaySettings();
                renderScript();
            })
            .setOnCancelListener(dialog -> {
                promptFontSize = originalFontSize;
                readColor = originalReadColor;
                currentColor = originalCurrentColor;
                backgroundColor = originalBackgroundColor;
                applyDisplaySettings();
                renderScript();
            })
            .show();
    }

    private interface ColorChoice {
        void onChoose(int color);
    }

    private LinearLayout colorRow(int[] colors, ColorChoice choice) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        for (int color : colors) {
            Button button = new Button(this);
            button.setText("");
            button.setBackgroundColor(color);
            button.setOnClickListener(view -> {
                choice.onChoose(color);
                applyDisplaySettings();
                renderScript();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
            params.setMargins(dp(4), dp(6), dp(4), dp(10));
            row.addView(button, params);
        }
        return row;
    }

    private TextView settingLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(15);
        label.setTextColor(Color.rgb(93, 105, 115));
        return label;
    }

    private SeekBar settingSeekBar(int max, int value) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max);
        seekBar.setProgress(Math.max(1, value));
        return seekBar;
    }

    private SeekBar.OnSeekBarChangeListener settingListener(TextView label, String name) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText(name + ": " + Math.max(1, progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private void applyDisplaySettings() {
        promptView.setTextSize(promptFontSize);
        promptScroll.setBackgroundColor(backgroundColor);
        if (backgroundColor == Color.rgb(245, 247, 248)) {
            promptView.setTextColor(Color.rgb(23, 32, 38));
        } else {
            promptView.setTextColor(Color.rgb(235, 240, 242));
        }
    }

    private void toggleFullLandscape() {
        boolean enable = !(fullscreenMode && landscapeMode);
        fullscreenMode = enable;
        landscapeMode = enable;
        fullscreenButton.setText(enable ? getString(R.string.button_exit_full_landscape) : getString(R.string.button_full_landscape));
        setRequestedOrientation(enable ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        Window window = getWindow();
        if (enable) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        promptScroll.postDelayed(this::applyOrientationLayout, 350);
    }

    private void applyOrientationLayout() {
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (fullscreenMode) {
            homeScroll.setVisibility(View.GONE);
            controlsPanel.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
            backHomeButton.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
            promptView.setPadding(dp(40), dp(120), dp(42), dp(160));
            promptView.setTextSize(Math.max(34, promptView.getTextSize() / getResources().getDisplayMetrics().scaledDensity));
            return;
        }

        homeScroll.setVisibility(prompterMode ? View.GONE : View.VISIBLE);
        controlsPanel.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
        backHomeButton.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
        titleView.setVisibility(landscape ? View.GONE : View.VISIBLE);
        statusView.setMinHeight(landscape ? dp(28) : dp(42));
        promptView.setPadding(
            landscape ? dp(34) : dp(24),
            landscape ? dp(74) : dp(120),
            landscape ? dp(38) : dp(28),
            landscape ? dp(180) : dp(290)
        );
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyOrientationLayout();
    }

    private void setupSpeechRecognizer() {
        speechServiceAvailable = SpeechRecognizer.isRecognitionAvailable(this);
        if (!speechServiceAvailable) {
            statusView.setText(R.string.status_no_system_service);
            return;
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { statusView.setText(R.string.status_listening); }
            @Override public void onBeginningOfSpeech() { statusView.setText(R.string.status_voice_detected); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override public void onPartialResults(Bundle partialResults) { handleSpeechBundle(partialResults); }

            @Override
            public void onResults(Bundle results) {
                handleSpeechBundle(results);
                restartListening();
            }

            @Override
            public void onError(int error) {
                if (shouldListen) {
                    restartListening();
                    return;
                }
                statusView.setText(getString(R.string.status_speech_error) + error);
                startButton.setEnabled(true);
                pauseButton.setEnabled(false);
            }
        });
    }

    private void testMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            testAfterPermission = true;
            startAfterPermission = false;
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, AUDIO_PERMISSION_REQUEST);
            return;
        }
        statusView.setText(getString(R.string.status_mic_allowed) + getSpeechServiceSummary());
    }

    private void startReading() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startAfterPermission = true;
            testAfterPermission = false;
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, AUDIO_PERMISSION_REQUEST);
            return;
        }

        startRealtimeRecognition();
    }

    private void startRecognizerPanelFallback() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINA.toString());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.speech_panel_prompt));
            statusView.setText(R.string.status_try_panel);
            startActivityForResult(intent, PANEL_REQUEST);
        } catch (ActivityNotFoundException error) {
            statusView.setText(R.string.status_no_speech_component);
        }
    }

    private void startCloudRecording() {
        String apiKey = getBaiduApiKey();
        String secretKey = getBaiduSecretKey();
        if (apiKey.isEmpty() || secretKey.isEmpty()) {
            statusView.setText(R.string.status_enter_keys);
            return;
        }

        try {
            recordingFile = new File(getCacheDir(), "cloud_asr.amr");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.AMR_NB);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setAudioSamplingRate(8000);
            mediaRecorder.setOutputFile(recordingFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            cloudRecording = true;
            startButton.setEnabled(false);
            pauseButton.setEnabled(true);
            statusView.setText(R.string.status_cloud_recording);
        } catch (Exception error) {
            cleanupRecorder();
            statusView.setText(getString(R.string.status_record_failed) + error.getMessage());
        }
    }

    private void startRealtimeRecognition() {
        if (realtimeStreaming) {
            return;
        }
        String apiKey = getBaiduApiKey();
        String appId = getBaiduAppId();
        if (apiKey.isEmpty() || appId.isEmpty()) {
            statusView.setText(R.string.status_enter_realtime_keys);
            return;
        }

        int appIdNumber;
        try {
            appIdNumber = Integer.parseInt(appId);
        } catch (NumberFormatException error) {
            statusView.setText(getString(R.string.status_realtime_failed) + "AppID must be numeric.");
            return;
        }

        realtimeFinalText = "";
        targetReadIndex = readIndex;
        lastProgressAt = System.currentTimeMillis();
        realtimeClient = new OkHttpClient();
        Request request = new Request.Builder()
            .url("wss://vop.baidu.com/realtime_asr?sn=" + UUID.randomUUID())
            .build();
        statusView.setText(R.string.status_realtime_starting);
        startButton.setEnabled(false);
        pauseButton.setEnabled(true);

        realtimeWebSocket = realtimeClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                JSONObject start = new JSONObject();
                try {
                    JSONObject data = new JSONObject();
                    data.put("appid", appIdNumber);
                    data.put("appkey", apiKey);
                    data.put("dev_pid", 15372);
                    data.put("cuid", "voice-teleprompter-android");
                    data.put("format", "pcm");
                    data.put("sample", 16000);
                    start.put("type", "START");
                    start.put("data", data);
                    webSocket.send(start.toString());
                    runOnUiThread(() -> statusView.setText(R.string.status_realtime_listening));
                    startRealtimeAudioLoop(webSocket);
                } catch (Exception error) {
                    runOnUiThread(() -> statusView.setText(getString(R.string.status_realtime_failed) + error.getMessage()));
                    stopRealtimeRecognition();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleRealtimeMessage(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                runOnUiThread(() -> {
                    statusView.setText(getString(R.string.status_realtime_failed) + throwable.getMessage());
                    startButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                });
                cleanupRealtime();
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                cleanupRealtime();
                webSocket.close(code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                runOnUiThread(() -> {
                    statusView.setText(R.string.status_realtime_stopped);
                    startButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                });
                cleanupRealtime();
            }
        });
    }

    private void startRealtimeAudioLoop(WebSocket webSocket) {
        int sampleRate = 16000;
        int minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        );
        int bufferSize = Math.max(minBuffer, 5120);
        audioRecord = new AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        );
        realtimeStreaming = true;
        audioRecord.startRecording();
        promptView.removeCallbacks(autoAdvanceRunnable);
        promptView.postDelayed(autoAdvanceRunnable, 800);

        audioThread = new Thread(() -> {
            byte[] buffer = new byte[5120];
            while (realtimeStreaming && audioRecord != null) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    boolean accepted = webSocket.send(okio.ByteString.of(buffer, 0, read));
                    if (!accepted) {
                        realtimeStreaming = false;
                    }
                }
            }
        });
        audioThread.start();
    }

    private void handleRealtimeMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            int errNo = json.optInt("err_no", 0);
            if (errNo != 0) {
                realtimeStreaming = false;
                runOnUiThread(() -> statusView.setText(getString(R.string.status_realtime_failed) + compact(text)));
                return;
            }

            String type = json.optString("type");
            String result = json.optString("result");
            if (result.isEmpty()) {
                return;
            }

            if ("MID_TEXT".equals(type)) {
                updateRealtimeTranscript(realtimeFinalText + result);
            } else if ("FIN_TEXT".equals(type)) {
                realtimeFinalText = realtimeFinalText + result;
                updateRealtimeTranscript(realtimeFinalText);
            }
        } catch (Exception error) {
            runOnUiThread(() -> statusView.setText(getString(R.string.status_realtime_failed) + error.getMessage()));
        }
    }

    private void updateRealtimeTranscript(String transcript) {
        runOnUiThread(() -> {
            statusView.setText(getString(R.string.status_heard) + transcript);
            int nextIndex = findBestProgress(transcript);
            if (nextIndex > targetReadIndex) {
                targetReadIndex = nextIndex;
                lastProgressAt = System.currentTimeMillis();
                promptView.removeCallbacks(autoAdvanceRunnable);
                promptView.post(autoAdvanceRunnable);
            }
        });
    }

    private void nudgeReadIndex(int delta) {
        readIndex = Math.max(0, Math.min(normalizedScript.length(), readIndex + delta));
        targetReadIndex = readIndex;
        lastProgressAt = System.currentTimeMillis();
        renderScript();
    }

    private void stopRealtimeRecognition() {
        realtimeStreaming = false;
        promptView.removeCallbacks(autoAdvanceRunnable);
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }

        if (realtimeWebSocket != null) {
            JSONObject finish = new JSONObject();
            try {
                finish.put("type", "FINISH");
                finish.put("data", new JSONObject());
                realtimeWebSocket.send(finish.toString());
            } catch (Exception ignored) {
            }
            realtimeWebSocket.close(1000, "finished");
            realtimeWebSocket = null;
        }

        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        targetReadIndex = readIndex;
        statusView.setText(R.string.status_realtime_stopped);
    }

    private void cleanupRealtime() {
        realtimeStreaming = false;
        promptView.removeCallbacks(autoAdvanceRunnable);
        targetReadIndex = readIndex;
        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (IllegalStateException ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
        realtimeWebSocket = null;
    }

    private String getSpeechServiceSummary() {
        Intent serviceIntent = new Intent("android.speech.RecognitionService");
        List<ResolveInfo> services = getPackageManager().queryIntentServices(serviceIntent, PackageManager.MATCH_ALL);
        if (services == null || services.isEmpty()) {
            return getString(R.string.speech_services_none);
        }
        return getString(R.string.speech_service_prefix) + services.get(0).serviceInfo.packageName;
    }

    private void stopReading() {
        stopAutoScroll();
        if (cloudRecording) {
            stopCloudRecordingAndRecognize();
            return;
        }
        if (realtimeStreaming) {
            stopRealtimeRecognition();
            return;
        }

        shouldListen = false;
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        statusView.setText(R.string.status_paused);
        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
    }

    private void resetReading() {
        stopAutoScroll();
        if (cloudRecording) {
            cleanupRecorder();
        }
        if (realtimeStreaming) {
            stopRealtimeRecognition();
        }
        shouldListen = false;
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
        readIndex = 0;
        targetReadIndex = 0;
        promptScroll.scrollTo(0, 0);
        statusView.setText(R.string.status_reset);
        startButton.setEnabled(true);
        pauseButton.setEnabled(false);
        renderScript();
    }

    private void stopCloudRecordingAndRecognize() {
        try {
            mediaRecorder.stop();
        } catch (RuntimeException ignored) {
        }
        cleanupRecorder();
        startButton.setEnabled(false);
        pauseButton.setEnabled(false);
        statusView.setText(R.string.status_uploading);

        String apiKey = getBaiduApiKey();
        String secretKey = getBaiduSecretKey();
        File file = recordingFile;
        long fileSize = file == null ? 0 : file.length();
        statusView.setText(getString(R.string.status_uploading_bytes) + fileSize);
        new Thread(() -> {
            try {
                String transcript = recognizeWithBaidu(file, apiKey, secretKey);
                runOnUiThread(() -> {
                    statusView.setText(getString(R.string.status_heard) + transcript);
                    readIndex = findBestProgress(transcript);
                    renderScript();
                    startButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    statusView.setText(getString(R.string.status_cloud_failed) + error.getMessage());
                    startButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                });
            }
        }).start();
    }

    private void cleanupRecorder() {
        cloudRecording = false;
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private String getBaiduApiKey() {
        return savedBaiduApiKey.isEmpty() ? BUILT_IN_BAIDU_API_KEY : savedBaiduApiKey;
    }

    private String getBaiduAppId() {
        return savedBaiduAppId.isEmpty() ? BUILT_IN_BAIDU_APP_ID : savedBaiduAppId;
    }

    private String getBaiduSecretKey() {
        return savedBaiduSecretKey.isEmpty() ? BUILT_IN_BAIDU_SECRET_KEY : savedBaiduSecretKey;
    }

    private String recognizeWithBaidu(File file, String apiKey, String secretKey) throws Exception {
        if (file == null || !file.exists() || file.length() == 0) {
            throw new Exception("empty recording");
        }

        String token = fetchBaiduToken(apiKey, secretKey);
        byte[] audio = readFile(file);
        String speech = Base64.encodeToString(audio, Base64.NO_WRAP);

        JSONObject body = new JSONObject();
        body.put("format", "amr");
        body.put("rate", 8000);
        body.put("channel", 1);
        body.put("cuid", "voice-teleprompter-android");
        body.put("token", token);
        body.put("dev_pid", 1537);
        body.put("len", audio.length);
        body.put("speech", speech);

        String response = postJson("https://vop.baidu.com/server_api", body.toString());
        JSONObject json = new JSONObject(response);
        int errNo = json.optInt("err_no", -1);
        if (errNo != 0) {
            throw new Exception("Baidu err " + errNo + ": " + json.optString("err_msg") + " / " + compact(response));
        }

        return json.getJSONArray("result").getString(0);
    }

    private String fetchBaiduToken(String apiKey, String secretKey) throws Exception {
        String url = "https://aip.baidubce.com/oauth/2.0/token"
            + "?grant_type=client_credentials"
            + "&client_id=" + URLEncoder.encode(apiKey, "UTF-8")
            + "&client_secret=" + URLEncoder.encode(secretKey, "UTF-8");
        String response = getText(url);
        JSONObject json = new JSONObject(response);
        String token = json.optString("access_token");
        if (token.isEmpty()) {
            throw new Exception("token failed: " + json.optString("error_description", response) + " / " + compact(response));
        }
        return token;
    }

    private String compact(String value) {
        if (value == null) {
            return "";
        }
        String compacted = value.replace("\n", " ").replace("\r", " ");
        return compacted.length() > 220 ? compacted.substring(0, 220) : compacted;
    }

    private String getText(String urlString) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod("GET");
        return readResponse(connection);
    }

    private String postJson(String urlString, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(30000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.getBytes("UTF-8");
        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
        return readResponse(connection);
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        InputStream input = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    private byte[] readFile(File file) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private void restartListening() {
        if (!shouldListen || speechRecognizer == null) {
            return;
        }
        promptView.postDelayed(() -> {
            if (shouldListen) {
                speechRecognizer.startListening(recognizerIntent);
            }
        }, 220);
    }

    private void handleSpeechBundle(Bundle bundle) {
        ArrayList<String> matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches == null || matches.isEmpty()) {
            return;
        }
        String transcript = matches.get(0);
        statusView.setText(getString(R.string.status_heard) + transcript);
        readIndex = findBestProgress(transcript);
        renderScript();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PANEL_REQUEST || resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (matches == null || matches.isEmpty()) {
            return;
        }
        String transcript = matches.get(0);
        statusView.setText(getString(R.string.status_heard) + transcript);
        readIndex = findBestProgress(transcript);
        renderScript();
    }

    private int findBestProgress(String transcript) {
        String normalizedTranscript = normalizeText(transcript);
        if (normalizedTranscript.isEmpty()) {
            return readIndex;
        }
        int searchStart = Math.max(0, readIndex - 6);
        int searchEnd = Math.min(normalizedScript.length(), readIndex + 80);
        String forwardScript = normalizedScript.substring(searchStart, searchEnd);
        int bestIndex = readIndex;

        int maxLength = Math.min(normalizedTranscript.length(), 22);
        for (int length = maxLength; length >= 2; length--) {
            String snippet = normalizedTranscript.substring(normalizedTranscript.length() - length);
            int foundAt = forwardScript.indexOf(snippet);
            if (foundAt >= 0) {
                bestIndex = Math.max(bestIndex, searchStart + foundAt + length);
                break;
            }
        }

        if (bestIndex == readIndex) {
            bestIndex = fuzzyForwardProgress(normalizedTranscript, searchStart, searchEnd);
        }

        if (readIndex < normalizedScript.length()) {
            String lastChar = normalizedTranscript.substring(normalizedTranscript.length() - 1);
            if (normalizedScript.substring(readIndex).startsWith(lastChar)) {
                bestIndex = Math.max(bestIndex, readIndex + 1);
            }
        }
        return Math.min(bestIndex, normalizedScript.length());
    }

    private int fuzzyForwardProgress(String normalizedTranscript, int searchStart, int searchEnd) {
        int bestScore = 0;
        int bestEnd = readIndex;
        int maxLength = Math.min(normalizedTranscript.length(), 14);
        if (maxLength < 3) {
            return readIndex;
        }

        String tail = normalizedTranscript.substring(normalizedTranscript.length() - maxLength);
        for (int start = searchStart; start < searchEnd; start++) {
            int score = 0;
            int limit = Math.min(maxLength, searchEnd - start);
            for (int offset = 0; offset < limit; offset++) {
                if (tail.charAt(offset) == normalizedScript.charAt(start + offset)) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestEnd = start + limit;
            }
        }

        return bestScore >= Math.max(3, maxLength - 3) ? Math.max(readIndex, bestEnd) : readIndex;
    }

    private void renderScript() {
        String script = scriptText == null || scriptText.isEmpty() ? getString(R.string.default_script) : scriptText;
        normalizedScript = normalizeText(script);
        SpannableString styled = new SpannableString(script);

        int normalizedCount = 0;
        int currentVisualIndex = normalizedIndexToVisualIndex(script, readIndex + 1);
        for (int index = 0; index < script.length(); index++) {
            String normalizedChar = normalizeText(String.valueOf(script.charAt(index)));
            if (!normalizedChar.isEmpty()) {
                normalizedCount += normalizedChar.length();
            }
            if (normalizedCount > 0 && normalizedCount <= readIndex) {
                styled.setSpan(new ForegroundColorSpan(readColor), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (index == currentVisualIndex && readIndex < normalizedScript.length()) {
                styled.setSpan(new BackgroundColorSpan(currentColor), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                styled.setSpan(new ForegroundColorSpan(Color.rgb(23, 32, 38)), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        promptView.setText(styled);
        scrollCurrentLineIntoView(currentVisualIndex);
    }

    private int normalizedIndexToVisualIndex(String script, int targetIndex) {
        int normalizedCount = 0;
        for (int index = 0; index < script.length(); index++) {
            String normalizedChar = normalizeText(String.valueOf(script.charAt(index)));
            if (normalizedChar.isEmpty()) {
                continue;
            }
            normalizedCount += normalizedChar.length();
            if (normalizedCount >= targetIndex) {
                return index;
            }
        }
        return Math.max(0, script.length() - 1);
    }

    private void scrollCurrentLineIntoView(int visualIndex) {
        promptView.post(() -> {
            if (promptView.getLayout() == null || visualIndex < 0 || visualIndex >= promptView.length()) {
                return;
            }
            int line = promptView.getLayout().getLineForOffset(visualIndex);
            int lineTop = promptView.getLayout().getLineTop(line);
            int target = Math.max(0, lineTop - promptScroll.getHeight() / 3);
            int current = promptScroll.getScrollY();
            int adjustedTarget = current + ((target - current) * scrollSpeed / 5);
            promptScroll.smoothScrollTo(0, adjustedTarget);
        });
    }

    private String normalizeText(String value) {
        return value.toLowerCase(Locale.CHINA).replaceAll("[，。！？、；：“”‘’《》（）【】,.!?;:\"'()\\[\\]\\s]", "");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != AUDIO_PERMISSION_REQUEST) {
            return;
        }

        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            testAfterPermission = false;
            startAfterPermission = false;
            statusView.setText(R.string.status_mic_denied);
            return;
        }

        if (testAfterPermission) {
            testAfterPermission = false;
            statusView.setText(getString(R.string.status_mic_allowed) + getSpeechServiceSummary());
            return;
        }
        if (startAfterPermission) {
            startAfterPermission = false;
            startReading();
        }
    }

    @Override
    protected void onDestroy() {
        shouldListen = false;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
