package com.example.voiceteleprompter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.text.TextUtils;
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
import android.widget.Toast;

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
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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
    private static final String PREF_TENCENT_APP_ID = "tencent_app_id";
    private static final String PREF_TENCENT_SECRET_ID = "tencent_secret_id";
    private static final String PREF_TENCENT_SECRET_KEY = "tencent_secret_key";
    private static final String PREF_ALIYUN_TOKEN = "aliyun_token";
    private static final String PREF_ALIYUN_APP_KEY = "aliyun_app_key";
    private static final String PREF_ALIYUN_ENDPOINT = "aliyun_endpoint";
    private static final String PREF_SPEECH_PROVIDER = "speech_provider";
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
    private static final String PROVIDER_BAIDU = "baidu";
    private static final String PROVIDER_TENCENT = "tencent";
    private static final String PROVIDER_ALIYUN = "aliyun";
    private static final String OPEN_SOURCE_URL = "https://github.com/avatartulkun/voice-teleprompter-cn";
    private static final String CONTACT_EMAIL = "tulkun@foxmail.com";
    private static final String BAIDU_ASR_URL = "https://cloud.baidu.com/product/speech.html";
    private static final String TENCENT_ASR_URL = "https://cloud.tencent.com/product/asr";
    private static final String ALIYUN_ASR_URL = "https://ai.aliyun.com/nls/trans";
    private static final String[] PROVIDER_VALUES = {
        PROVIDER_BAIDU,
        PROVIDER_TENCENT,
        PROVIDER_ALIYUN
    };
    private static final String[] PROVIDER_NAMES = {
        "百度智能云（当前可用）",
        "腾讯云（当前可用）",
        "阿里云（当前可用）"
    };

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
    private volatile boolean micTesting;
    private boolean realtimeStopRequested;
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
    private String savedTencentAppId = "";
    private String savedTencentSecretId = "";
    private String savedTencentSecretKey = "";
    private String savedAliyunToken = "";
    private String savedAliyunAppKey = "";
    private String savedAliyunEndpoint = "";
    private String savedSpeechProvider = PROVIDER_BAIDU;
    private String activeRealtimeProvider = PROVIDER_BAIDU;
    private String activeAliyunTaskId = "";
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
        homePanel.setPadding(dp(18), dp(38), dp(18), dp(22));
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
        testBtnParams.setMargins(0, dp(8), 0, dp(6));
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
        savedTencentAppId = prefs.getString(PREF_TENCENT_APP_ID, "");
        savedTencentSecretId = prefs.getString(PREF_TENCENT_SECRET_ID, "");
        savedTencentSecretKey = prefs.getString(PREF_TENCENT_SECRET_KEY, "");
        savedAliyunToken = prefs.getString(PREF_ALIYUN_TOKEN, "");
        savedAliyunAppKey = prefs.getString(PREF_ALIYUN_APP_KEY, "");
        savedAliyunEndpoint = prefs.getString(PREF_ALIYUN_ENDPOINT, "wss://nls-gateway.cn-shanghai.aliyuncs.com/ws/v1");
        savedSpeechProvider = prefs.getString(PREF_SPEECH_PROVIDER, PROVIDER_BAIDU);
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

        TextView providerLabel = settingLabel(getString(R.string.label_speech_provider));
        form.addView(providerLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final int[] providerIndex = {getProviderIndex(savedSpeechProvider)};
        Button providerButton = makeSecondaryButton(PROVIDER_NAMES[providerIndex[0]]);
        providerButton.setTextColor(Color.rgb(16, 26, 31));
        providerButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        providerParams.setMargins(0, dp(6), 0, dp(8));
        form.addView(providerButton, providerParams);

        TextView firstFieldLabel = settingLabel("");
        form.addView(firstFieldLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText apiKeyField = new EditText(this);
        apiKeyField.setSingleLine(true);
        form.addView(apiKeyField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        TextView appIdFieldLabel = settingLabel("");
        form.addView(appIdFieldLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText appIdField = new EditText(this);
        appIdField.setSingleLine(true);
        form.addView(appIdField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        TextView secretFieldLabel = settingLabel("");
        form.addView(secretFieldLabel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        EditText secretKeyField = new EditText(this);
        secretKeyField.setSingleLine(true);
        form.addView(secretKeyField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        updateCredentialFields(PROVIDER_VALUES[providerIndex[0]], firstFieldLabel, apiKeyField, appIdFieldLabel, appIdField, secretFieldLabel, secretKeyField);
        providerButton.setOnClickListener(view ->
            showProviderPicker(providerIndex, providerButton, firstFieldLabel, apiKeyField, appIdFieldLabel, appIdField, secretFieldLabel, secretKeyField));

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
        keyHelpButton.setOnClickListener(view -> showRealtimeHelpDialog());
        LinearLayout.LayoutParams helpButtonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        helpButtonParams.setMargins(0, dp(6), 0, dp(6));
        form.addView(keyHelpButton, helpButtonParams);

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_settings)
            .setView(scrollableDialogView(form))
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                saveSpeechSettings(providerIndex[0], apiKeyField, appIdField, secretKeyField);
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void showProviderPicker(
        int[] providerIndex,
        Button providerButton,
        TextView firstFieldLabel,
        EditText apiKeyField,
        TextView appIdFieldLabel,
        EditText appIdField,
        TextView secretFieldLabel,
        EditText secretKeyField
    ) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.label_speech_provider)
            .setSingleChoiceItems(PROVIDER_NAMES, providerIndex[0], (dialog, which) -> {
                providerIndex[0] = which;
                providerButton.setText(PROVIDER_NAMES[which]);
                providerButton.setTextColor(Color.rgb(16, 26, 31));
                updateCredentialFields(PROVIDER_VALUES[which], firstFieldLabel, apiKeyField, appIdFieldLabel, appIdField, secretFieldLabel, secretKeyField);
                dialog.dismiss();
            })
            .show();
    }

    private void saveSpeechSettings(int providerIndex, EditText apiKeyField, EditText appIdField, EditText secretKeyField) {
        savedSpeechProvider = PROVIDER_VALUES[providerIndex];
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_SPEECH_PROVIDER, savedSpeechProvider);
        if (PROVIDER_TENCENT.equals(savedSpeechProvider)) {
            savedTencentAppId = apiKeyField.getText().toString().trim();
            savedTencentSecretId = appIdField.getText().toString().trim();
            savedTencentSecretKey = secretKeyField.getText().toString().trim();
            editor
                .putString(PREF_TENCENT_SECRET_ID, savedTencentSecretId)
                .putString(PREF_TENCENT_APP_ID, savedTencentAppId)
                .putString(PREF_TENCENT_SECRET_KEY, savedTencentSecretKey);
        } else if (PROVIDER_ALIYUN.equals(savedSpeechProvider)) {
            savedAliyunAppKey = apiKeyField.getText().toString().trim();
            savedAliyunToken = appIdField.getText().toString().trim();
            savedAliyunEndpoint = secretKeyField.getText().toString().trim();
            if (savedAliyunEndpoint.isEmpty()) {
                savedAliyunEndpoint = "wss://nls-gateway.cn-shanghai.aliyuncs.com/ws/v1";
            }
            editor
                .putString(PREF_ALIYUN_TOKEN, savedAliyunToken)
                .putString(PREF_ALIYUN_APP_KEY, savedAliyunAppKey)
                .putString(PREF_ALIYUN_ENDPOINT, savedAliyunEndpoint);
        } else {
            savedBaiduAppId = apiKeyField.getText().toString().trim();
            savedBaiduApiKey = appIdField.getText().toString().trim();
            savedBaiduSecretKey = secretKeyField.getText().toString().trim();
            editor
                .putString(PREF_BAIDU_API_KEY, savedBaiduApiKey)
                .putString(PREF_BAIDU_APP_ID, savedBaiduAppId)
                .putString(PREF_BAIDU_SECRET_KEY, savedBaiduSecretKey);
        }
        editor.apply();
        statusView.setText(getString(R.string.settings_saved) + " 当前服务商：" + PROVIDER_NAMES[providerIndex] + "\n" + getCredentialStatus());
    }

    private void showRealtimeHelpDialog() {
        LinearLayout helpLayout = new LinearLayout(this);
        helpLayout.setOrientation(LinearLayout.VERTICAL);
        helpLayout.setPadding(dp(18), dp(8), dp(18), dp(8));

        TextView helpContent = new TextView(this);
        helpContent.setText(R.string.realtime_open_help_intro);
        helpContent.setTextSize(14);
        helpContent.setTextColor(Color.rgb(30, 45, 52));
        helpContent.setLineSpacing(dp(4), 1.0f);
        helpContent.setPadding(0, 0, 0, dp(10));
        helpLayout.addView(helpContent, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addProviderHelpSection(helpLayout, "二、百度智能云", BAIDU_ASR_URL, getString(R.string.realtime_open_help_baidu));
        addProviderHelpSection(helpLayout, "三、腾讯云", TENCENT_ASR_URL, getString(R.string.realtime_open_help_tencent));
        addProviderHelpSection(helpLayout, "四、阿里云", ALIYUN_ASR_URL, getString(R.string.realtime_open_help_aliyun));

        TextView footer = new TextView(this);
        footer.setText(R.string.realtime_open_help_footer);
        footer.setTextSize(14);
        footer.setTextColor(Color.rgb(30, 45, 52));
        footer.setLineSpacing(dp(4), 1.0f);
        footer.setPadding(0, dp(6), 0, 0);
        helpLayout.addView(footer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
            .setTitle(R.string.button_open_realtime_doc)
            .setView(scrollableDialogView(helpLayout))
            .setPositiveButton(R.string.dialog_close, null)
            .show();
    }

    private void addProviderHelpSection(LinearLayout parent, String title, String url, String body) {
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, dp(8), 0, dp(4));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextSize(14);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setTextColor(Color.rgb(30, 45, 52));
        titleRow.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button copyButton = compactDialogButton("复制网站");
        copyButton.setTextSize(12);
        copyButton.setOnClickListener(view -> copyToClipboard(title.replaceFirst("^[一二三四五六七八九十]+、", "") + "网站", url));
        titleRow.addView(copyButton, new LinearLayout.LayoutParams(dp(96), dp(36)));
        parent.addView(titleRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView bodyView = new TextView(this);
        bodyView.setText(body);
        bodyView.setTextSize(14);
        bodyView.setTextColor(Color.rgb(30, 45, 52));
        bodyView.setLineSpacing(dp(4), 1.0f);
        bodyView.setPadding(0, 0, 0, dp(4));
        parent.addView(bodyView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int getProviderIndex(String provider) {
        for (int i = 0; i < PROVIDER_VALUES.length; i++) {
            if (PROVIDER_VALUES[i].equals(provider)) {
                return i;
            }
        }
        return 0;
    }

    private void updateCredentialFields(
        String provider,
        TextView firstLabel,
        EditText firstField,
        TextView appIdLabel,
        EditText appIdField,
        TextView secretLabel,
        EditText secretField
    ) {
        if (PROVIDER_TENCENT.equals(provider)) {
            firstLabel.setText("腾讯云账号 AppID");
            firstField.setHint("腾讯云账号 AppID，不是 SecretId");
            firstField.setText(savedTencentAppId);
            appIdLabel.setText("腾讯云 SecretId");
            appIdField.setHint("腾讯云 SecretId");
            appIdField.setText(savedTencentSecretId);
            secretLabel.setText("腾讯云 SecretKey");
            secretField.setHint("腾讯云 SecretKey");
            secretField.setText(savedTencentSecretKey);
            return;
        }
        if (PROVIDER_ALIYUN.equals(provider)) {
            firstLabel.setText("阿里云 AppKey");
            firstField.setHint("阿里云 AppKey");
            firstField.setText(savedAliyunAppKey);
            appIdLabel.setText("阿里云 Token");
            appIdField.setHint("阿里云 Token");
            appIdField.setText(savedAliyunToken);
            secretLabel.setText("阿里云 Endpoint");
            secretField.setHint("阿里云 Endpoint，可不填");
            secretField.setText(savedAliyunEndpoint);
            return;
        }
        firstLabel.setText("百度智能云 AppID");
        firstField.setHint(R.string.hint_baidu_app_id);
        firstField.setText(savedBaiduAppId);
        appIdLabel.setText("百度智能云 API Key");
        appIdField.setHint(R.string.hint_baidu_api_key);
        appIdField.setText(savedBaiduApiKey);
        secretLabel.setText("百度智能云 Secret Key");
        secretField.setHint(R.string.hint_baidu_secret_key);
        secretField.setText(savedBaiduSecretKey);
    }

    private boolean isBaiduProvider() {
        return PROVIDER_BAIDU.equals(savedSpeechProvider);
    }

    private boolean isTencentProvider() {
        return PROVIDER_TENCENT.equals(savedSpeechProvider);
    }

    private boolean isAliyunProvider() {
        return PROVIDER_ALIYUN.equals(savedSpeechProvider);
    }

    private String getCredentialStatus() {
        ArrayList<String> missing = new ArrayList<String>();
        String providerName = PROVIDER_NAMES[getProviderIndex(savedSpeechProvider)];

        if (isTencentProvider()) {
            addIfEmpty(missing, savedTencentAppId, "账号 AppID");
            addIfEmpty(missing, savedTencentSecretId, "SecretId");
            addIfEmpty(missing, savedTencentSecretKey, "SecretKey");
        } else if (isAliyunProvider()) {
            addIfEmpty(missing, savedAliyunAppKey, "AppKey");
            addIfEmpty(missing, savedAliyunToken, "Token");
        } else {
            addIfEmpty(missing, getBaiduAppId(), "AppID");
            addIfEmpty(missing, getBaiduApiKey(), "API Key");
        }

        if (!missing.isEmpty()) {
            String status = "✗ 当前服务商：" + providerName + "，缺少：" + joinNames(missing);
            if (isTencentProvider()) {
                status += "\n腾讯云实时识别需要账号 AppID、SecretId、SecretKey 三项；AppID 通常在账号信息或 API 密钥页面查看。";
            }
            return status;
        }
        return "✓ 当前服务商：" + providerName + "，识别密钥已设置";
    }

    private void addIfEmpty(ArrayList<String> missing, String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            missing.add(name);
        }
    }

    private String joinNames(ArrayList<String> names) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                builder.append("、");
            }
            builder.append(names.get(i));
        }
        return builder.toString();
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

        LinearLayout copyRow = new LinearLayout(this);
        copyRow.setOrientation(LinearLayout.HORIZONTAL);
        copyRow.setPadding(0, 0, 0, dp(10));
        Button copySourceButton = compactDialogButton(getString(R.string.button_copy_open_source));
        Button copyEmailButton = compactDialogButton(getString(R.string.button_copy_contact));
        copySourceButton.setOnClickListener(view -> copyToClipboard("开源地址", OPEN_SOURCE_URL));
        copyEmailButton.setOnClickListener(view -> copyToClipboard("联系方式", CONTACT_EMAIL));
        copyRow.addView(copySourceButton, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams copyEmailParams = new LinearLayout.LayoutParams(0, dp(38), 1);
        copyEmailParams.setMargins(dp(8), 0, 0, 0);
        copyRow.addView(copyEmailButton, copyEmailParams);
        dialogLayout.addView(copyRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button shareButton = compactDialogButton(getString(R.string.button_share_app));
        shareButton.setOnClickListener(view -> shareAppInfo());
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38));
        shareParams.setMargins(0, 0, 0, dp(10));
        dialogLayout.addView(shareButton, shareParams);

        CheckBox agreementCheck = new CheckBox(this);
        agreementCheck.setText(R.string.about_agreement_check);
        agreementCheck.setChecked(agreementAccepted);
        agreementCheck.setTextSize(14);
        agreementCheck.setTextColor(Color.rgb(30, 45, 52));
        agreementCheck.setPadding(0, dp(4), 0, 0);
        dialogLayout.addView(agreementCheck, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_about)
            .setView(scrollableDialogView(dialogLayout))
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

    private Button compactDialogButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(Color.WHITE);
        button.setBackgroundResource(R.drawable.button_primary);
        button.setPadding(dp(8), 0, dp(8), 0);
        return button;
    }

    private void copyToClipboard(String label, String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
            Toast.makeText(this, label + "已复制", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAppInfo() {
        String shareText = "语音跟读提词器\n"
            + "开源地址：" + OPEN_SOURCE_URL + "\n"
            + "联系方式：" + CONTACT_EMAIL;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "语音跟读提词器");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.button_share_app)));
        } catch (ActivityNotFoundException error) {
            statusView.setText("未检测到可用的分享应用。");
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
        historyLabel.setText("历史稿件（点击序号加载，可删除）");
        historyLabel.setTextSize(14);
        historyLabel.setTextColor(Color.rgb(104, 119, 126));
        historyLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        historyLabel.setPadding(dp(4), dp(4), dp(4), dp(6));
        dialogLayout.addView(historyLabel);

        TextView historyTip = new TextView(this);
        historyTip.setText("最多保存 5 份稿件，新保存的稿件会排到第 1 位，并自动删除最旧的一份。");
        historyTip.setTextSize(12);
        historyTip.setTextColor(Color.rgb(104, 119, 126));
        historyTip.setPadding(dp(4), 0, dp(4), dp(6));
        dialogLayout.addView(historyTip);

        for (int i = 0; i < displayScripts.size() && i < 5; i++) {
            String scriptValue = displayScripts.get(i);
            String preview = scriptValue.length() > 24 ? scriptValue.substring(0, 24) + "..." : scriptValue;
            LinearLayout historyRow = new LinearLayout(this);
            historyRow.setOrientation(LinearLayout.HORIZONTAL);
            historyRow.setGravity(Gravity.CENTER_VERTICAL);
            historyRow.setPadding(0, dp(2), 0, dp(2));

            TextView item = new TextView(this);
            item.setText((i + 1) + ". " + preview);
            item.setTextSize(13);
            item.setTextColor(Color.rgb(15, 139, 141));
            item.setPadding(dp(6), dp(6), dp(6), dp(6));
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            final int idx = i;
            item.setOnClickListener(v -> {
                scriptText = displayScripts.get(idx);
                scriptField.setText(scriptText);
                renderScript();
                statusView.setText("已加载历史稿件");
            });

            Button deleteButton = new Button(this);
            deleteButton.setText("删除");
            deleteButton.setAllCaps(false);
            deleteButton.setTextSize(12);
            deleteButton.setTextColor(Color.rgb(185, 48, 48));
            deleteButton.setPadding(dp(6), 0, dp(6), 0);
            deleteButton.setOnClickListener(v -> {
                savedScripts.remove(scriptValue);
                persistSavedScripts();
                dialogLayout.removeView(historyRow);
                statusView.setText("已删除历史稿件");
            });

            historyRow.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            historyRow.addView(deleteButton, new LinearLayout.LayoutParams(dp(74), dp(36)));
            dialogLayout.addView(historyRow);
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

        AlertDialog scriptDialog = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_script)
            .setView(scrollableDialogView(dialogLayout))
            .setPositiveButton(R.string.dialog_save, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setNeutralButton(R.string.dialog_close, null)
            .create();
        scriptDialog.setOnShowListener(view -> {
            Button saveButton = scriptDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button closeButton = scriptDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            saveButton.setOnClickListener(click -> {
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
            });
            closeButton.setOnClickListener(click -> scriptDialog.dismiss());
        });
        scriptDialog.show();
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
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        savedScripts.remove(script);
        savedScripts.add(0, script);
        if (savedScripts.size() > 5) savedScripts = new ArrayList<>(savedScripts.subList(0, 5));
        persistSavedScripts();
    }

    private void persistSavedScripts() {
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
            .setView(scrollableDialogView(form))
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
            .setView(scrollableDialogView(form))
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

    private ScrollView scrollableDialogView(View content) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        scrollView.setPadding(0, 0, 0, dp(4));
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int maxHeight = (int) (screenHeight * (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 0.58f : 0.68f));
        scrollView.addView(content, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        scrollView.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Math.max(dp(260), maxHeight)
        ));
        return scrollView;
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
        String keyStatus = getCredentialStatus();
        String micStatus = getString(R.string.status_mic_allowed);
        String phraseTip = "正在监听麦克风，请对着手机说话。";
        statusView.setText(micStatus + "\n" + keyStatus);
        testResultView.setText(micStatus + "\n" + keyStatus + "\n" + phraseTip);
        startMicLevelTest(keyStatus);
    }

    private void startMicLevelTest(String keyStatus) {
        if (micTesting) {
            return;
        }
        micTesting = true;
        Thread testThread = new Thread(() -> {
            int sampleRate = 16000;
            int minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferSize = Math.max(minBuffer, 2048);
            AudioRecord tester = null;
            int peak = 0;
            try {
                tester = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                );
                tester.startRecording();
                byte[] buffer = new byte[2048];
                long endAt = System.currentTimeMillis() + 2600L;
                while (System.currentTimeMillis() < endAt && micTesting) {
                    int read = tester.read(buffer, 0, buffer.length);
                    if (read > 1) {
                        for (int i = 0; i + 1 < read; i += 2) {
                            int sample = (buffer[i] & 0xff) | (buffer[i + 1] << 8);
                            peak = Math.max(peak, Math.abs(sample));
                        }
                    }
                    final int currentPeak = peak;
                    runOnUiThread(() -> {
                        String soundStatus = currentPeak > 900
                            ? "✓ 已检测到麦克风声音"
                            : "正在监听麦克风，请对着手机说话";
                        testResultView.setText(getString(R.string.status_mic_allowed) + "\n" + keyStatus + "\n" + soundStatus);
                    });
                }
                final int finalPeak = peak;
                runOnUiThread(() -> {
                    String soundStatus = finalPeak > 900
                        ? "✓ 麦克风测试通过，已检测到声音"
                        : "✗ 暂未检测到明显声音，请检查麦克风权限、音量或设备麦克风";
                    statusView.setText(soundStatus);
                    testResultView.setText(getString(R.string.status_mic_allowed) + "\n" + keyStatus + "\n" + soundStatus);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    String message = "麦克风测试失败：" + error.getMessage();
                    statusView.setText(message);
                    testResultView.setText(message);
                });
            } finally {
                if (tester != null) {
                    try {
                        tester.stop();
                    } catch (IllegalStateException ignored) {
                    }
                    tester.release();
                }
                micTesting = false;
            }
        });
        testThread.start();
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
        if (!isBaiduProvider()) {
            statusView.setText(R.string.status_provider_not_supported);
            return;
        }
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
        if (isTencentProvider()) {
            startTencentRealtimeRecognition();
            return;
        }
        if (isAliyunProvider()) {
            startAliyunRealtimeRecognition();
            return;
        }
        if (!isBaiduProvider()) {
            statusView.setText(R.string.status_provider_not_supported);
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
        activeRealtimeProvider = PROVIDER_BAIDU;
        realtimeStopRequested = false;
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
                    statusView.setText(buildRealtimeDisconnectMessage(throwable.getMessage()));
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
                    statusView.setText(realtimeStopRequested ? getString(R.string.status_realtime_stopped) : buildRealtimeClosedMessage(code, reason));
                    startButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                });
                cleanupRealtime();
            }
        });
    }

    private void startTencentRealtimeRecognition() {
        if (savedTencentAppId.isEmpty() || savedTencentSecretId.isEmpty() || savedTencentSecretKey.isEmpty()) {
            statusView.setText("请先在设置中填写腾讯云 AppID、SecretId 和 SecretKey。");
            return;
        }

        realtimeFinalText = "";
        targetReadIndex = readIndex;
        lastProgressAt = System.currentTimeMillis();
        activeRealtimeProvider = PROVIDER_TENCENT;
        realtimeStopRequested = false;
        realtimeClient = new OkHttpClient();

        try {
            Request request = new Request.Builder()
                .url(buildTencentRealtimeUrl())
                .build();
            statusView.setText(R.string.status_realtime_starting);
            startButton.setEnabled(false);
            pauseButton.setEnabled(true);

            realtimeWebSocket = realtimeClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    runOnUiThread(() -> statusView.setText(R.string.status_realtime_listening));
                    startRealtimeAudioLoop(webSocket);
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    handleTencentRealtimeMessage(text);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                    runOnUiThread(() -> {
                        statusView.setText(buildRealtimeDisconnectMessage(throwable.getMessage()));
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
                        statusView.setText(realtimeStopRequested ? getString(R.string.status_realtime_stopped) : buildRealtimeClosedMessage(code, reason));
                        startButton.setEnabled(true);
                        pauseButton.setEnabled(false);
                    });
                    cleanupRealtime();
                }
            });
        } catch (Exception error) {
            statusView.setText(getString(R.string.status_realtime_failed) + error.getMessage());
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        }
    }

    private void startAliyunRealtimeRecognition() {
        if (savedAliyunToken.isEmpty() || savedAliyunAppKey.isEmpty()) {
            statusView.setText("请先在设置中填写阿里云 AppKey 和 Token。");
            return;
        }

        realtimeFinalText = "";
        targetReadIndex = readIndex;
        lastProgressAt = System.currentTimeMillis();
        activeRealtimeProvider = PROVIDER_ALIYUN;
        activeAliyunTaskId = uuid32();
        realtimeStopRequested = false;
        realtimeClient = new OkHttpClient();

        try {
            Request request = new Request.Builder()
                .url(buildAliyunRealtimeUrl())
                .build();
            statusView.setText(R.string.status_realtime_starting);
            startButton.setEnabled(false);
            pauseButton.setEnabled(true);

            realtimeWebSocket = realtimeClient.newWebSocket(request, new WebSocketListener() {
                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    webSocket.send(buildAliyunCommand("StartTranscription", true));
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    handleAliyunRealtimeMessage(webSocket, text);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
                    runOnUiThread(() -> {
                        statusView.setText(buildRealtimeDisconnectMessage(throwable.getMessage()));
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
                        statusView.setText(realtimeStopRequested ? getString(R.string.status_realtime_stopped) : buildRealtimeClosedMessage(code, reason));
                        startButton.setEnabled(true);
                        pauseButton.setEnabled(false);
                    });
                    cleanupRealtime();
                }
            });
        } catch (Exception error) {
            statusView.setText(getString(R.string.status_realtime_failed) + error.getMessage());
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        }
    }

    private String buildTencentRealtimeUrl() throws Exception {
        String hostAndPath = "asr.cloud.tencent.com/asr/v2/" + savedTencentAppId;
        long timestamp = System.currentTimeMillis() / 1000L;
        long expired = timestamp + 86400L;
        int nonce = 100000000 + new Random().nextInt(899999999);
        TreeMap<String, String> params = new TreeMap<String, String>();
        params.put("engine_model_type", "16k_zh");
        params.put("expired", String.valueOf(expired));
        params.put("needvad", "1");
        params.put("nonce", String.valueOf(nonce));
        params.put("secretid", savedTencentSecretId);
        params.put("timestamp", String.valueOf(timestamp));
        params.put("voice_format", "1");
        params.put("voice_id", UUID.randomUUID().toString());
        String query = buildQuery(params);
        String source = hostAndPath + "?" + query;
        String signature = hmacSha1Base64(source, savedTencentSecretKey);
        return "wss://" + hostAndPath + "?" + query + "&signature=" + URLEncoder.encode(signature, "UTF-8");
    }

    private String buildAliyunRealtimeUrl() throws Exception {
        String endpoint = savedAliyunEndpoint;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            endpoint = "wss://nls-gateway.cn-shanghai.aliyuncs.com/ws/v1";
        }
        String separator = endpoint.contains("?") ? "&" : "?";
        return endpoint + separator + "token=" + URLEncoder.encode(savedAliyunToken, "UTF-8");
    }

    private String buildAliyunCommand(String name, boolean withPayload) {
        JSONObject command = new JSONObject();
        try {
            JSONObject header = new JSONObject();
            header.put("appkey", savedAliyunAppKey);
            header.put("message_id", uuid32());
            header.put("task_id", activeAliyunTaskId);
            header.put("namespace", "SpeechTranscriber");
            header.put("name", name);
            command.put("header", header);
            if (withPayload) {
                JSONObject payload = new JSONObject();
                payload.put("format", "pcm");
                payload.put("sample_rate", 16000);
                payload.put("enable_intermediate_result", true);
                payload.put("enable_punctuation_prediction", true);
                payload.put("enable_inverse_text_normalization", true);
                command.put("payload", payload);
            }
        } catch (Exception ignored) {
        }
        return command.toString();
    }

    private String uuid32() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String buildQuery(TreeMap<String, String> params) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(entry.getKey()).append("=").append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }
        return builder.toString();
    }

    private String hmacSha1Base64(String source, String secretKey) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA1"));
        return Base64.encodeToString(mac.doFinal(source.getBytes("UTF-8")), Base64.NO_WRAP);
    }

    private String buildRealtimeDisconnectMessage(String detail) {
        String message = detail == null || detail.trim().isEmpty() ? "连接被中断" : detail;
        return getString(R.string.status_realtime_failed)
            + message
            + "\n请检查：服务商是否已开通实时语音识别、密钥是否完整、网络是否可用。";
    }

    private String buildRealtimeClosedMessage(int code, String reason) {
        String provider = PROVIDER_NAMES[getProviderIndex(activeRealtimeProvider)];
        String detail = reason == null || reason.trim().isEmpty() ? ("关闭码：" + code) : ("关闭码：" + code + "，原因：" + reason);
        return "实时识别连接已断开（" + provider + "）。\n" + detail + "\n请检查密钥、服务开通状态和网络后再点击开始。";
    }

    private void showRealtimeServiceError(String detail) {
        realtimeStreaming = false;
        runOnUiThread(() -> {
            statusView.setText(buildRealtimeDisconnectMessage(detail));
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });
        cleanupRealtime();
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
                showRealtimeServiceError(compact(text));
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

    private void handleTencentRealtimeMessage(String text) {
        try {
            JSONObject json = new JSONObject(text);
            int code = json.optInt("code", 0);
            if (code != 0) {
                String message = json.optString("message", compact(text));
                showRealtimeServiceError(message);
                return;
            }

            JSONObject resultObject = json.optJSONObject("result");
            if (resultObject == null) {
                return;
            }
            String result = resultObject.optString("voice_text_str");
            if (result.isEmpty()) {
                return;
            }
            int sliceType = resultObject.optInt("slice_type", 1);
            if (sliceType == 2) {
                realtimeFinalText = realtimeFinalText + result;
                updateRealtimeTranscript(realtimeFinalText);
            } else {
                updateRealtimeTranscript(realtimeFinalText + result);
            }
        } catch (Exception error) {
            runOnUiThread(() -> statusView.setText(getString(R.string.status_realtime_failed) + error.getMessage()));
        }
    }

    private void handleAliyunRealtimeMessage(WebSocket webSocket, String text) {
        try {
            JSONObject json = new JSONObject(text);
            JSONObject header = json.optJSONObject("header");
            if (header == null) {
                return;
            }

            int status = header.optInt("status", 20000000);
            if (status != 20000000) {
                String message = header.optString("status_message", compact(text));
                showRealtimeServiceError(message);
                return;
            }

            String name = header.optString("name");
            if ("TranscriptionStarted".equals(name)) {
                runOnUiThread(() -> statusView.setText(R.string.status_realtime_listening));
                startRealtimeAudioLoop(webSocket);
                return;
            }

            JSONObject payload = json.optJSONObject("payload");
            if (payload == null) {
                return;
            }
            String result = payload.optString("result");
            if (result.isEmpty()) {
                return;
            }
            if ("SentenceEnd".equals(name)) {
                realtimeFinalText = realtimeFinalText + result;
                updateRealtimeTranscript(realtimeFinalText);
            } else if ("TranscriptionResultChanged".equals(name)) {
                updateRealtimeTranscript(realtimeFinalText + result);
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
        realtimeStopRequested = true;
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
            if (PROVIDER_TENCENT.equals(activeRealtimeProvider)) {
                realtimeWebSocket.send("{\"type\":\"end\"}");
            } else if (PROVIDER_ALIYUN.equals(activeRealtimeProvider)) {
                realtimeWebSocket.send(buildAliyunCommand("StopTranscription", false));
            } else {
                JSONObject finish = new JSONObject();
                try {
                    finish.put("type", "FINISH");
                    finish.put("data", new JSONObject());
                    realtimeWebSocket.send(finish.toString());
                } catch (Exception ignored) {
                }
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


