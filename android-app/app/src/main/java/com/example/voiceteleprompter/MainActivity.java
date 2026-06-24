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
import android.graphics.Typeface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
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
import android.widget.CheckBox;
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
    private static final String PREF_SAVED_SCRIPTS = "saved_scripts";
    private static final String PREF_COLOR_SPEED = "color_speed";
    private static final String PREF_AUTO_SPEED = "auto_speed";
    private static final String PREF_SCROLL_SPEED = "scroll_speed";
    private static final String PREF_FONT_SIZE = "font_size";
    private static final String PREF_READ_COLOR = "read_color";
    private static final String PREF_CURRENT_COLOR = "current_color";
    private static final String PREF_BACKGROUND_COLOR = "background_color";
    private static final String PREF_AGREEMENT_ACCEPTED = "agreement_accepted";
    private static final String BUILT_IN_BAIDU_API_KEY = "";
    private static final String BUILT_IN_BAIDU_APP_ID = "";
    private static final String BUILT_IN_BAIDU_SECRET_KEY = "";

    private TextView statusView;
    private TextView testResultView;
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
    private Button settingsFloatButton;
    private Button mailFloatButton;
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
    private boolean agreementAccepted;

    private String savedBaiduAppId = "";
    private String savedBaiduApiKey = "";
    private String savedBaiduSecretKey = "";
    private String realtimeFinalText = "";
    private ArrayList<String> savedScripts = new ArrayList<String>();
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
        setupSpeechRecognizer();
        renderScript();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(241, 247, 246));
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
        promptView.setLineSpacing(dp(10), 1.28f);
        promptView.setGravity(Gravity.START);
        promptView.setPadding(dp(28), dp(124), dp(30), dp(280));
        promptView.setOnClickListener(view -> togglePlayPauseFromPrompt());
        promptScroll.setOnClickListener(view -> togglePlayPauseFromPrompt());
        promptScroll.addView(promptView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        homeScroll = new ScrollView(this);
        homeScroll.setFillViewport(true);
        homeScroll.setBackgroundColor(Color.rgb(241, 247, 246));
        root.addView(homeScroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        homePanel = new LinearLayout(this);
        homePanel.setOrientation(LinearLayout.VERTICAL);
        homePanel.setPadding(dp(18), dp(22), dp(18), dp(22));
        homeScroll.addView(homePanel, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        controlsPanel = new LinearLayout(this);
        controlsPanel.setOrientation(LinearLayout.VERTICAL);
        controlsPanel.setPadding(dp(8), dp(8), dp(8), dp(8));
        controlsPanel.setBackgroundResource(R.drawable.control_bar);
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlParams.gravity = Gravity.BOTTOM;
        controlParams.setMargins(dp(10), 0, dp(10), dp(10));
        root.addView(controlsPanel, controlParams);
        controlsPanel.setVisibility(View.GONE);

        backHomeButton = makeSecondaryButton(getString(R.string.button_back_home));
        settingsFloatButton = makeButton("⋮");
        settingsFloatButton.setTextSize(14);
        settingsFloatButton.setPadding(0, 0, 0, 0);
        settingsFloatButton.setMinWidth(0);
        settingsFloatButton.setMinHeight(0);
        FrameLayout.LayoutParams floatParams = new FrameLayout.LayoutParams(dp(30), dp(30));
        floatParams.gravity = Gravity.TOP | Gravity.END;
        floatParams.setMargins(0, dp(42), dp(12), 0);
        root.addView(settingsFloatButton, floatParams);
        settingsFloatButton.setVisibility(View.GONE);

        mailFloatButton = makeSecondaryButton("✉");
        mailFloatButton.setTextSize(26);
        mailFloatButton.setTextColor(Color.rgb(15, 139, 141));
        mailFloatButton.setBackgroundColor(Color.TRANSPARENT);
        mailFloatButton.setElevation(0);
        mailFloatButton.setPadding(0, 0, 0, 0);
        mailFloatButton.setMinWidth(0);
        mailFloatButton.setMinHeight(0);
        mailFloatButton.setContentDescription(getString(R.string.button_feedback_email));
        FrameLayout.LayoutParams mailParams = new FrameLayout.LayoutParams(dp(48), dp(48));
        mailParams.gravity = Gravity.BOTTOM | Gravity.END;
        mailParams.setMargins(0, 0, dp(16), dp(18));
        root.addView(mailFloatButton, mailParams);

        titleView = new TextView(this);
        titleView.setText(R.string.title_main);
        titleView.setTextSize(22);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setTextColor(Color.rgb(16, 26, 31));
        titleView.setGravity(Gravity.START);
        titleView.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, dp(8), 0, dp(8));
        homePanel.addView(titleView, titleParams);

        TextView subtitleView = makeText(getString(R.string.subtitle_main), 15, Color.rgb(85, 99, 106), Typeface.NORMAL);
        subtitleView.setLineSpacing(dp(3), 1.1f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.setMargins(0, 0, 0, dp(16));
        homePanel.addView(subtitleView, subtitleParams);





        statusView = new TextView(this);
        // statusView is removed from home page but kept for prompter mode feedback





        Button scriptButton = makeSecondaryButton(getString(R.string.button_script));
        Button settingsButton = makeSecondaryButton(getString(R.string.button_settings));
        Button followSettingsButton = makeSecondaryButton(getString(R.string.button_follow_settings));
        Button displaySettingsButton = makeSecondaryButton(getString(R.string.button_display_settings));
        Button aboutButton = makeSecondaryButton(getString(R.string.button_about));
        Button enterPrompterButton = makeButton(getString(R.string.button_enter_prompter));
        enterPrompterButton.setBackgroundResource(R.drawable.enter_circle_button);
        enterPrompterButton.setTextSize(14);
        enterPrompterButton.setPadding(dp(8), 0, dp(8), 0);
        aboutButton.setTextColor(Color.rgb(16, 26, 31));
        aboutButton.setBackgroundColor(Color.TRANSPARENT);
        aboutButton.setElevation(0);
        // 第一步：设置识别密钥
        LinearLayout step1Card = makeCard();
        TextView step1Title = makeText(getString(R.string.label_setup_section), 17, Color.rgb(16, 26, 31), Typeface.BOLD);
        step1Card.addView(step1Title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams singleBtnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        singleBtnParams.setMargins(0, dp(8), 0, 0);
        step1Card.addView(settingsButton, singleBtnParams);
        homePanel.addView(step1Card, cardParams());

        // 第二步：测试麦克风
        Button testButton = makeButton(getString(R.string.button_mic_test));
        testButton.setTextSize(16);
        LinearLayout micTestCard = makeCard();
        TextView micTestTitle = makeText(getString(R.string.label_mic_test), 17, Color.rgb(16, 26, 31), Typeface.BOLD);
        micTestCard.addView(micTestTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams testBtnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        testBtnParams.setMargins(0, dp(10), 0, dp(6));
        micTestCard.addView(testButton, testBtnParams);
        testResultView = new TextView(this);
        testResultView.setTextSize(13);
        testResultView.setTextColor(Color.rgb(85, 99, 106));
        testResultView.setLineSpacing(dp(4), 1.0f);
        testResultView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultParams.setMargins(0, dp(2), 0, dp(6));
        micTestCard.addView(testResultView, resultParams);
        homePanel.addView(micTestCard, cardParams());

        // 第三步：导入与设置
        LinearLayout step3Card = makeCard();
        TextView step3Title = makeText(getString(R.string.label_step3), 17, Color.rgb(16, 26, 31), Typeface.BOLD);
        step3Card.addView(step3Title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout step3Row1 = new LinearLayout(this);
        step3Row1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        rowParams.setMargins(0, dp(8), 0, 0);
        LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        cellParams.setMargins(dp(3), 0, dp(3), 0);
        step3Row1.addView(scriptButton, cellParams);
        step3Row1.addView(followSettingsButton, cellParams);
        step3Row1.addView(displaySettingsButton, cellParams);
        step3Card.addView(step3Row1, rowParams);
        homePanel.addView(step3Card, cardParams());

        View topSpacer = new View(this);
        homePanel.addView(topSpacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout.LayoutParams enterParams = new LinearLayout.LayoutParams(dp(88), dp(88));
        enterParams.gravity = Gravity.CENTER_HORIZONTAL;
        enterParams.setMargins(0, 0, 0, 0);
        homePanel.addView(enterPrompterButton, enterParams);

        View bottomSpacer = new View(this);
        homePanel.addView(bottomSpacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout.LayoutParams aboutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36));
        aboutParams.setMargins(0, 0, 0, 0);
        homePanel.addView(aboutButton, aboutParams);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        controlsPanel.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        
        startButton = makeButton(getString(R.string.button_start));
        pauseButton = makeSecondaryButton(getString(R.string.button_pause));
        Button resetButton = makeSecondaryButton(getString(R.string.button_reset));
                                                fullscreenButton = makeSecondaryButton(getString(R.string.button_full_landscape));
        autoScrollButton = makeSecondaryButton(getString(R.string.button_auto_scroll));
        Button backButton = makeSecondaryButton(getString(R.string.button_back));
        Button forwardButton = makeSecondaryButton(getString(R.string.button_forward));
        pauseButton.setEnabled(false);

        buttons.addView(startButton, smallButtonParams());
        buttons.addView(pauseButton, smallButtonParams());
        buttons.addView(resetButton, smallButtonParams());
        buttons.addView(fullscreenButton, smallButtonParams());
        buttons.addView(autoScrollButton, smallButtonParams());
        buttons.addView(backButton, smallButtonParams());
        buttons.addView(forwardButton, smallButtonParams());
        buttons.addView(backHomeButton, smallButtonParams());



        testButton.setOnClickListener(view -> testMic());
        enterPrompterButton.setOnClickListener(view -> enterPrompterAfterAgreement());
        backHomeButton.setOnClickListener(view -> enterHomeMode());
        settingsFloatButton.setOnClickListener(view -> showPrompterSettingsDialog());
        mailFloatButton.setOnClickListener(view -> openEmailFeedback());
        startButton.setOnClickListener(view -> startReading());
        pauseButton.setOnClickListener(view -> stopReading());
        resetButton.setOnClickListener(view -> resetReading());
        settingsButton.setOnClickListener(view -> showSettingsDialog());
        scriptButton.setOnClickListener(view -> showScriptDialog());
        followSettingsButton.setOnClickListener(view -> showFollowSettingsDialog());
        displaySettingsButton.setOnClickListener(view -> showDisplaySettingsDialog());
        aboutButton.setOnClickListener(view -> showAboutDialog(false));
        fullscreenButton.setOnClickListener(view -> toggleFullLandscape());
        autoScrollButton.setOnClickListener(view -> toggleAutoScroll());
        backButton.setOnClickListener(view -> nudgeReadIndex(-5));
        forwardButton.setOnClickListener(view -> nudgeReadIndex(5));
        applyOrientationLayout();
        applyDisplaySettings();
    }

    private Button makeButton(String text) {
        return makeStyledButton(text, true);
    }

    private Button makeSecondaryButton(String text) {
        return makeStyledButton(text, false);
    }

    private Button makeStyledButton(String text, boolean primary) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(primary ? R.drawable.button_primary : R.drawable.button_secondary);
        button.setElevation(primary ? dp(3) : 0);
        button.setStateListAnimator(null);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setIncludeFontPadding(false);
        button.setPadding(dp(6), 0, dp(6), 0);
        return button;
    }

    private TextView makeText(String text, int textSize, int textColor, int typefaceStyle) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(textSize);
        textView.setTextColor(textColor);
        textView.setTypeface(Typeface.DEFAULT, typefaceStyle);
        return textView;
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackgroundResource(R.drawable.panel_card);
        card.setClipToOutline(false);
        return card;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(14));
        return params;
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
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private void enterPrompterMode() {
        prompterMode = true;
        homeScroll.setVisibility(View.GONE);
        controlsPanel.setVisibility(View.VISIBLE);
        backHomeButton.setVisibility(View.VISIBLE);
        settingsFloatButton.setVisibility(View.VISIBLE);
        mailFloatButton.setVisibility(View.GONE);
        statusView.setText(R.string.status_tap_mic);
        applyOrientationLayout();
    }

    private void enterPrompterAfterAgreement() {
        if (agreementAccepted) {
            enterPrompterMode();
            return;
        }
        showAboutDialog(true);
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
        settingsFloatButton.setVisibility(View.GONE);
        mailFloatButton.setVisibility(View.VISIBLE);
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
        loadSavedScripts();
        colorStep = prefs.getInt(PREF_COLOR_SPEED, 1);
        autoAdvanceStep = prefs.getInt(PREF_AUTO_SPEED, 1);
        scrollSpeed = prefs.getInt(PREF_SCROLL_SPEED, 1);
        promptFontSize = prefs.getInt(PREF_FONT_SIZE, 38);
        readColor = prefs.getInt(PREF_READ_COLOR, Color.rgb(232, 93, 63));
        currentColor = prefs.getInt(PREF_CURRENT_COLOR, Color.rgb(255, 224, 130));
        backgroundColor = prefs.getInt(PREF_BACKGROUND_COLOR, Color.rgb(17, 24, 29));
        agreementAccepted = prefs.getBoolean(PREF_AGREEMENT_ACCEPTED, false);
    }

    private void showPrompterSettingsDialog() {
        String[] options = {getString(R.string.button_display_settings), getString(R.string.button_follow_settings), getString(R.string.button_script)};
        new AlertDialog.Builder(this)
            .setTitle("快捷设置")
            .setItems(options, (dialog, which) -> {
                if (which == 0) showDisplaySettingsDialog();
                else if (which == 1) showFollowSettingsDialog();
                else showScriptDialog();
            })
            .show();
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

        TextView keyHelpView = new TextView(this);
        keyHelpView.setText(R.string.hint_realtime_key_help);
        keyHelpView.setTextColor(Color.rgb(71, 85, 105));
        keyHelpView.setTextSize(13);
        keyHelpView.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams helpTextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpTextParams.setMargins(0, dp(8), 0, 0);
        form.addView(keyHelpView, helpTextParams);

        Button keyHelpButton = new Button(this);
        keyHelpButton.setText(R.string.button_open_realtime_doc);
        keyHelpButton.setTextColor(Color.rgb(15, 139, 141));
        keyHelpButton.setAllCaps(false);
        keyHelpButton.setBackgroundColor(Color.TRANSPARENT);
        keyHelpButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        keyHelpButton.setPadding(0, 0, 0, 0);
        keyHelpButton.setOnClickListener(view -> openRealtimeDoc());
        LinearLayout.LayoutParams helpButtonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        helpButtonParams.setMargins(0, dp(6), 0, dp(6));
        form.addView(keyHelpButton, helpButtonParams);

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

    private void openRealtimeDoc() {
        Uri docUri = Uri.parse("https://github.com/avatartulkun/voice-teleprompter-cn/blob/main/docs/%E5%AE%9E%E6%97%B6%E8%AF%AD%E9%9F%B3%E8%AF%86%E5%88%AB%E5%BC%80%E9%80%9A%E8%AF%B4%E6%98%8E.md");
        Intent intent = new Intent(Intent.ACTION_VIEW, docUri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            statusView.setText(R.string.status_no_browser_app);
        }
    }


    private void showAboutDialog(boolean requireAgreement) {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(18), dp(8), dp(18), dp(4));

        TextView content = new TextView(this);
        content.setText(R.string.about_content);
        content.setTextSize(14);
        content.setTextColor(Color.rgb(30, 45, 52));
        content.setLineSpacing(dp(4), 1.0f);
        content.setPadding(0, 0, 0, dp(12));
        dialogLayout.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        CheckBox agreementCheck = new CheckBox(this);
        agreementCheck.setText(R.string.about_agreement_check);
        agreementCheck.setChecked(agreementAccepted);
        agreementCheck.setTextSize(14);
        agreementCheck.setTextColor(Color.rgb(30, 45, 52));
        agreementCheck.setPadding(0, dp(4), 0, 0);
        dialogLayout.addView(agreementCheck, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_about)
            .setView(dialogLayout)
            .setPositiveButton(requireAgreement ? R.string.dialog_agree_continue : R.string.dialog_close, null)
            .create();
        dialog.setOnShowListener(view -> {
            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setEnabled(!requireAgreement || agreementCheck.isChecked());
            agreementCheck.setOnCheckedChangeListener((buttonView, isChecked) -> positiveButton.setEnabled(!requireAgreement || isChecked));
            positiveButton.setOnClickListener(click -> {
                if (agreementCheck.isChecked()) {
                    agreementAccepted = true;
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_AGREEMENT_ACCEPTED, true)
                        .apply();
                }
                dialog.dismiss();
                if (requireAgreement && agreementAccepted) {
                    enterPrompterMode();
                }
            });
        });
        dialog.show();
    }

    private void openEmailFeedback() {
        String subject = Uri.encode(getString(R.string.feedback_email_subject));
        String body = Uri.encode(getString(R.string.feedback_email_body));
        Uri mailUri = Uri.parse("mailto:tulkun@foxmail.com?subject=" + subject + "&body=" + body);
        Intent intent = new Intent(Intent.ACTION_SENDTO, mailUri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            statusView.setText(R.string.status_no_email_app);
        }
    }

    private void showScriptDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(18), dp(8), dp(18), dp(8));

        EditText scriptField = new EditText(this);

        // Add current script to front of list for display
        ArrayList<String> displayScripts = new ArrayList<String>(savedScripts);
        if (!displayScripts.contains(scriptText)) {
            displayScripts.add(0, scriptText);
        }
        
        TextView historyLabel = new TextView(this);
        historyLabel.setText("历史稿件（点击加载）");
        historyLabel.setTextSize(14);
        historyLabel.setTextColor(Color.rgb(104, 119, 126));
        historyLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        historyLabel.setPadding(dp(4), dp(4), dp(4), dp(6));
        dialogLayout.addView(historyLabel);

        for (int i = 0; i < displayScripts.size() && i < 6; i++) {
            String preview = displayScripts.get(i).length() > 28 ? displayScripts.get(i).substring(0, 28) + "..." : displayScripts.get(i);
            TextView item = new TextView(this);
            item.setText("· " + preview);
            item.setTextSize(13);
            item.setTextColor(Color.rgb(15, 139, 141));
            item.setPadding(dp(6), dp(5), dp(6), dp(5));
            final int idx = i;
            item.setOnClickListener(v -> {
                scriptText = displayScripts.get(idx);
                scriptField.setText(scriptText);
                renderScript();
                statusView.setText("已加载历史稿件");
            });
            dialogLayout.addView(item);
        }

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(210, 216, 222));
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        divParams.setMargins(0, dp(6), 0, dp(8));
        dialogLayout.addView(divider, divParams);


        scriptField.setText(scriptText);
        scriptField.setMinLines(8);
        scriptField.setGravity(Gravity.TOP);
        scriptField.setPadding(dp(18), dp(8), dp(18), dp(8));
        dialogLayout.addView(scriptField);

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_script)
            .setView(dialogLayout)
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                scriptText = scriptField.getText().toString();
                readIndex = 0;
                targetReadIndex = 0;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(PREF_SCRIPT, scriptText)
                    .apply();
                saveScriptToHistory(scriptText);
                statusView.setText(R.string.script_saved);
                renderScript();
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void loadSavedScripts() {
        String saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_SAVED_SCRIPTS, "[]");
        try {
            org.json.JSONArray arr = new org.json.JSONArray(saved);
            savedScripts = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) savedScripts.add(arr.getString(i));
        } catch (Exception e) {
            savedScripts = new ArrayList<>();
        }
    }

    private void saveScriptToHistory(String script) {
        savedScripts.remove(script);
        savedScripts.add(0, script);
        if (savedScripts.size() > 10) savedScripts = new ArrayList<>(savedScripts.subList(0, 10));
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String s : savedScripts) arr.put(s);
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_SAVED_SCRIPTS, arr.toString()).apply();
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
            settingsFloatButton.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
            mailFloatButton.setVisibility(View.GONE);
            promptView.setPadding(dp(40), dp(120), dp(42), dp(160));
            promptView.setTextSize(Math.max(34, promptView.getTextSize() / getResources().getDisplayMetrics().scaledDensity));
            return;
        }

        homeScroll.setVisibility(prompterMode ? View.GONE : View.VISIBLE);
        controlsPanel.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
        backHomeButton.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
        settingsFloatButton.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
        mailFloatButton.setVisibility(prompterMode ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams settingsParams = (FrameLayout.LayoutParams) settingsFloatButton.getLayoutParams();
        settingsParams.setMargins(0, landscape ? dp(18) : dp(42), dp(12), 0);
        settingsFloatButton.setLayoutParams(settingsParams);
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
        String baiduKey = getBaiduApiKey();
        String baiduAppId = getBaiduAppId();
        boolean keysOk = !baiduKey.isEmpty() && !baiduAppId.isEmpty();
        String keyStatus = keysOk ? "✓ 识别密钥已设置" : "✗ 识别密钥未设置，请先点击设置填写";
        String micStatus = getString(R.string.status_mic_allowed);
        statusView.setText(micStatus + "\n" + keyStatus);
        testResultView.setText(micStatus + "\n" + keyStatus);
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


