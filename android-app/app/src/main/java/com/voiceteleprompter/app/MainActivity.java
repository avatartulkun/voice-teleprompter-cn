package com.voiceteleprompter.app;

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
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
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
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.text.SimpleDateFormat;
import java.util.SimpleTimeZone;
import java.util.TreeMap;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.io.FileOutputStream;

public class MainActivity extends Activity {
    private static final String TAG = "VoiceTeleprompter";
    private static final int AUDIO_PERMISSION_REQUEST = 1001;
    private static final int PANEL_REQUEST = 2002;
    private static final String PREFS_NAME = "voice_teleprompter_settings";
    private static final String PREF_BAIDU_API_KEY = "baidu_api_key";
    private static final String PREF_BAIDU_APP_ID = "baidu_app_id";
    private static final String PREF_BAIDU_SECRET_KEY = "baidu_secret_key";
    private static final String PREF_TENCENT_APP_ID = "tencent_app_id";
    private static final String PREF_TENCENT_SECRET_ID = "tencent_secret_id";
    private static final String PREF_TENCENT_SECRET_KEY = "tencent_secret_key";
    private static final String PREF_ALIYUN_APP_KEY = "aliyun_app_key";
    private static final String PREF_ALIYUN_ACCESS_KEY_ID = "aliyun_access_key_id";
    private static final String PREF_ALIYUN_ACCESS_KEY_SECRET = "aliyun_access_key_secret";
    private static final String PREF_SPEECH_PROVIDER = "speech_provider";
    private static final String PREF_OFFLINE_MODE = "offline_mode";
    private static final String VOSK_MODEL_ASSET_DIR = "vosk-model-small-cn";
    private static final String PREF_SCRIPT = "script";
    private static final String PREF_SAVED_SCRIPTS = "saved_scripts";
    private static final String PREF_COLOR_SPEED = "color_speed";
    private static final String PREF_AUTO_SPEED = "auto_speed";
    private static final String PREF_SCROLL_SPEED = "scroll_speed";
    private static final String PREF_FONT_SIZE = "font_size";
    private static final String PREF_READ_COLOR = "read_color";
    private static final String PREF_CURRENT_COLOR = "current_color";
    private static final String PREF_BACKGROUND_COLOR = "background_color";
    private static final String PREF_MATCH_SENSITIVITY = "match_sensitivity";
    private static final String PREF_AGREEMENT_ACCEPTED = "agreement_accepted";
    private static final String PREF_PRACTICE_COUNT = "practice_count";
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
    private static final int BAIDU_REALTIME_DEV_PID = 1537;
    private static final String ALIYUN_NLS_ENDPOINT = "wss://nls-gateway.cn-shanghai.aliyuncs.com/ws/v1";
    private static final String ALIYUN_TOKEN_ENDPOINT = "https://nls-meta.cn-shanghai.aliyuncs.com/";
    private static final String[] PROVIDER_VALUES = {
        PROVIDER_BAIDU,
        PROVIDER_TENCENT,
        PROVIDER_ALIYUN
    };
    private static final String[] PROVIDER_NAMES = {
        "百度智能云",
        "腾讯云",
        "阿里云"
    };

    // 首页配色（对齐设计稿 design tokens）
    private static final int C_BRAND = 0xFF0E7A70;
    private static final int C_BRAND_500 = 0xFF12988B;
    private static final int C_BRAND_50 = 0xFFE3F5F1;
    private static final int C_BG = 0xFFEFF6F5;
    private static final int C_SURFACE = 0xFFFFFFFF;
    private static final int C_SURFACE_2 = 0xFFF5F9F8;
    private static final int C_LINE = 0xFFE6EDEC;
    private static final int C_TEXT_1 = 0xFF1B2B2E;
    private static final int C_TEXT_2 = 0xFF6B7D80;
    private static final int C_TEXT_3 = 0xFF9AA9AB;
    private static final int C_BLUE = 0xFF3D7DF6;
    private static final int C_BLUE_50 = 0xFFE8F0FE;
    private static final int C_SUCCESS = 0xFF22B07D;
    private static final int C_WARN = 0xFFE8A33D;

    private TextView statusView;
    private TextView testResultView;
    private TextView promptView;
    private TextView titleView;
    private EditText scriptEditView;
    private TextView wordCountView;
    private TextView scriptTitleView;
    private TextView scriptPreviewView;
    private LinearLayout homeTabBar;
    private LinearLayout tabRow;

    private static final int TAB_HOME = 0;
    private static final int TAB_SCRIPTS = 1;
    private static final int TAB_SETTINGS = 2;
    private static final int TAB_PROFILE = 3;
    private int activeTab = TAB_HOME;

    private ScrollView scriptsPage;
    private LinearLayout scriptsPanel;
    private LinearLayout scriptSegBar;
    private LinearLayout scriptListContainer;
    private EditText scriptSearchInput;
    private String scriptFilterCategory = "全部";
    private LinearLayout scriptsFab;

    private ScrollView settingsPage;
    private LinearLayout settingsPanel;
    private ScrollView profilePage;
    private LinearLayout profilePanel;

    private LinearLayout prompterTopBar;
    private TextView prompterTitleView;
    private TextView asrStateView;
    private TextView prompterTimeView;
    private View liveDot;
    private LinearLayout progressTrack;
    private View progressFill;
    private ImageView playPauseIcon;

    private LinearLayout editorPage;
    private EditText editorBodyInput;
    private LinearLayout editorChipRow;
    private TextView editorStatsView;
    private String editorCategory = SCRIPT_CATEGORIES[0];
    private ScriptItem editingItem;
    private String scriptText = "";
    private ScrollView homeScroll;
    private LinearLayout homePanel;
    private LinearLayout controlsPanel;
    private ScrollView promptScroll;
    private Button startButton;
    private Button pauseButton;
    private Button fullscreenButton;
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
    private boolean floatAfterPermission;
    private boolean agreementAccepted;

    private String savedBaiduAppId = "";
    private String savedBaiduApiKey = "";
    private String savedBaiduSecretKey = "";
    private String savedTencentAppId = "";
    private String savedTencentSecretId = "";
    private String savedTencentSecretKey = "";
    private String savedAliyunAppKey = "";
    private String savedAliyunAccessKeyId = "";
    private String savedAliyunAccessKeySecret = "";
    private String cachedAliyunToken = "";
    private long cachedAliyunTokenExpireTime;
    private String savedSpeechProvider = PROVIDER_BAIDU;
    private boolean offlineMode = false;
    private Model voskModel;
    private SpeechService voskSpeechService;
    private boolean voskRunning;
    private String activeRealtimeProvider = PROVIDER_BAIDU;
    private String activeAliyunTaskId = "";
    private String realtimeFinalText = "";
    private String lastRealtimeErrorMessage = "";
    private ArrayList<ScriptItem> savedScripts = new ArrayList<ScriptItem>();

    private static final String[] SCRIPT_CATEGORIES = {"演讲", "录课", "直播", "口播"};
    private static final int MAX_SAVED_SCRIPTS = 50;

    /** 一份稿件：正文 + 分类 + 最后编辑时间。 */
    private static class ScriptItem {
        String text = "";
        String category = SCRIPT_CATEGORIES[0];
        long updatedAt = System.currentTimeMillis();

        String title() {
            String value = text == null ? "" : text.trim();
            if (value.isEmpty()) {
                return "未命名稿件";
            }
            int lineEnd = value.indexOf('\n');
            String head = lineEnd > 0 ? value.substring(0, lineEnd) : value;
            return head.length() > 18 ? head.substring(0, 18) + "…" : head;
        }

        int wordCount() {
            return PinyinMatcher.normalizeText(text == null ? "" : text).length();
        }
    }
    private String normalizedScript = "";
    private int colorStep = 1;
    private int autoAdvanceStep = 1;
    private int scrollSpeed = 1;
    private int matchSensitivity = 3;
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
        if (savedScripts.isEmpty() && scriptText != null && !scriptText.trim().isEmpty()) {
            saveScriptToHistory(scriptText);
        }
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
        promptView.setPadding(dp(22), dp(118), dp(22), dp(260));
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
        controlsPanel.setPadding(dp(14), dp(12), dp(14), dp(16));
        controlsPanel.setBackgroundColor(0xFF16333A);
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        controlParams.gravity = Gravity.BOTTOM;
        root.addView(controlsPanel, controlParams);
        controlsPanel.setVisibility(View.GONE);



        // ================= 首页（对齐 UI 设计稿） =================
        homePanel.setPadding(dp(16), dp(26), dp(16), dp(94));

        // --- 品牌行：标题 + 副标题 + 模式标签 ---
        LinearLayout brandRow = new LinearLayout(this);
        brandRow.setOrientation(LinearLayout.HORIZONTAL);
        brandRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout brandTexts = new LinearLayout(this);
        brandTexts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(this);
        titleView.setText("语音跟读提词器");
        titleView.setTextSize(21);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        titleView.setTextColor(C_TEXT_1);
        titleView.setIncludeFontPadding(false);
        brandTexts.addView(titleView);
        TextView brandSub = makeText("AI 跟读自动高亮", 12, C_BRAND_500, Typeface.BOLD);
        LinearLayout.LayoutParams brandSubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        brandSubParams.setMargins(0, dp(5), 0, 0);
        brandTexts.addView(brandSub, brandSubParams);
        brandRow.addView(brandTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        brandRow.addView(chipView(offlineMode ? "离线" : "云端"));

        LinearLayout.LayoutParams brandRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        homePanel.addView(brandRow, brandRowParams);

        statusView = new TextView(this);
        // statusView 不在首页显示，仅用于提词模式与弹窗的状态反馈

        // --- 卡片1：当前稿件 ---
        homePanel.addView(sectionLabel("当前稿件"), sectionLabelParams());

        LinearLayout scriptCard = uiCard();
        scriptCard.setOnClickListener(view -> openEditor(findSavedScript(scriptText)));

        LinearLayout docRow = new LinearLayout(this);
        docRow.setOrientation(LinearLayout.HORIZONTAL);
        docRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout docIcon = iconBox(R.drawable.ic_ui_doc, C_BRAND_500, C_SURFACE_2, 19);
        LinearLayout.LayoutParams docIconParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        docIconParams.setMargins(0, 0, dp(10), 0);
        docRow.addView(docIcon, docIconParams);

        LinearLayout docTexts = new LinearLayout(this);
        docTexts.setOrientation(LinearLayout.VERTICAL);
        scriptTitleView = makeText(scriptHeadline(), 14, C_TEXT_1, Typeface.BOLD);
        scriptTitleView.setSingleLine(true);
        scriptTitleView.setEllipsize(TextUtils.TruncateAt.END);
        scriptTitleView.setIncludeFontPadding(false);
        docTexts.addView(scriptTitleView);
        wordCountView = makeText("", 11, C_TEXT_3, Typeface.NORMAL);
        LinearLayout.LayoutParams wordCountParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wordCountParams.setMargins(0, dp(5), 0, 0);
        docTexts.addView(wordCountView, wordCountParams);
        docRow.addView(docTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        docRow.addView(chipView("跟读"));
        scriptCard.addView(docRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout previewBox = new LinearLayout(this);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        previewBox.setPadding(dp(11), dp(10), dp(11), dp(10));
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setCornerRadius(dp(12));
        previewBg.setColor(C_SURFACE_2);
        previewBox.setBackground(previewBg);
        TextView previewLabel = makeText("稿件预览", 11, C_TEXT_3, Typeface.NORMAL);
        previewBox.addView(previewLabel);
        scriptPreviewView = makeText(scriptPreview(), 12, C_TEXT_2, Typeface.NORMAL);
        scriptPreviewView.setLineSpacing(dp(5), 1.15f);
        LinearLayout.LayoutParams previewTextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewTextParams.setMargins(0, dp(5), 0, 0);
        previewBox.addView(scriptPreviewView, previewTextParams);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        previewParams.setMargins(0, dp(11), 0, 0);
        scriptCard.addView(previewBox, previewParams);
        homePanel.addView(scriptCard, cardParams());

        // --- 卡片2：识别状态 ---
        homePanel.addView(sectionLabel("识别状态"), sectionLabelParams());

        LinearLayout voiceCard = uiCard();
        voiceCard.setOnClickListener(view -> showSettingsDialog());

        LinearLayout voiceRow = new LinearLayout(this);
        voiceRow.setOrientation(LinearLayout.HORIZONTAL);
        voiceRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView wifiIcon = uiIcon(R.drawable.ic_ui_wifi, C_BRAND_500, 26);
        LinearLayout.LayoutParams wifiParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        wifiParams.setMargins(dp(3), 0, dp(12), 0);
        voiceRow.addView(wifiIcon, wifiParams);

        LinearLayout voiceTexts = new LinearLayout(this);
        voiceTexts.setOrientation(LinearLayout.VERTICAL);
        boolean cloudReady = offlineMode || hasCloudCredentials();
        TextView voiceTitle = makeText(
                offlineMode ? "离线识别可用" : (cloudReady ? "云端识别可用" : "云端识别未配置"),
                14, C_TEXT_1, Typeface.BOLD);
        voiceTitle.setIncludeFontPadding(false);
        voiceTexts.addView(voiceTitle);
        TextView voiceEngine = makeText(
                offlineMode ? "Vosk" : PROVIDER_NAMES[getProviderIndex(savedSpeechProvider)],
                11, C_TEXT_3, Typeface.NORMAL);
        LinearLayout.LayoutParams voiceEngineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        voiceEngineParams.setMargins(0, dp(3), 0, 0);
        voiceTexts.addView(voiceEngine, voiceEngineParams);
        voiceRow.addView(voiceTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout readyTag = new LinearLayout(this);
        readyTag.setOrientation(LinearLayout.HORIZONTAL);
        readyTag.setGravity(Gravity.CENTER_VERTICAL);
        int readyColor = cloudReady ? C_SUCCESS : C_WARN;
        ImageView readyIcon = uiIcon(cloudReady ? R.drawable.ic_ui_check : R.drawable.ic_ui_info,
                readyColor, 14);
        LinearLayout.LayoutParams readyIconParams = new LinearLayout.LayoutParams(dp(14), dp(14));
        readyIconParams.setMargins(0, 0, dp(4), 0);
        readyTag.addView(readyIcon, readyIconParams);
        readyTag.addView(makeText(
                offlineMode ? "模型已就绪" : (cloudReady ? "密钥已配置" : "点此配置密钥"),
                11, readyColor, Typeface.BOLD));
        voiceRow.addView(readyTag);
        voiceCard.addView(voiceRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View voiceDivider = new View(this);
        voiceDivider.setBackgroundColor(C_LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        dividerParams.setMargins(0, dp(12), 0, dp(10));
        voiceCard.addView(voiceDivider, dividerParams);

        testResultView = new TextView(this);
        testResultView.setText(cloudReady
                ? "点按「麦克风检测」确认录音权限与音量"
                : "云端识别需要你自己的服务商密钥，点本卡片前往配置；或在设置里开启离线识别，无需联网即可使用");
        testResultView.setTextSize(11);
        testResultView.setTextColor(C_TEXT_3);
        testResultView.setLineSpacing(dp(3), 1.15f);
        voiceCard.addView(testResultView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        homePanel.addView(voiceCard, cardParams());

        // --- 快捷入口：4 列 × 2 行 ---
        homePanel.addView(sectionLabel("快捷入口"), sectionLabelParams());

        LinearLayout quickRow1 = new LinearLayout(this);
        quickRow1.setOrientation(LinearLayout.HORIZONTAL);
        quickRow1.addView(quickTile(R.drawable.ic_ui_files, "稿件库", true, () -> showTab(TAB_SCRIPTS)), quickCellParams());
        quickRow1.addView(quickTile(R.drawable.ic_ui_float, "悬浮提词", false, this::startFloatPrompter), quickCellParams());
        quickRow1.addView(quickTile(R.drawable.ic_ui_camera, "相机拍摄", true, this::startCameraPrompter), quickCellParams());
        quickRow1.addView(quickTile(R.drawable.ic_ui_monitor, "显示设置", false, this::showDisplaySettingsDialog), quickCellParams());
        homePanel.addView(quickRow1, quickRowParams());

        LinearLayout quickRow2 = new LinearLayout(this);
        quickRow2.setOrientation(LinearLayout.HORIZONTAL);
        quickRow2.addView(quickTile(R.drawable.ic_ui_wifi, "识别设置", true, this::showSettingsDialog), quickCellParams());
        quickRow2.addView(quickTile(R.drawable.ic_ui_gear, "跟读设置", false, this::showFollowSettingsDialog), quickCellParams());
        quickRow2.addView(quickTile(R.drawable.ic_ui_mic, "麦克风检测", true, this::testMic), quickCellParams());
        quickRow2.addView(quickTile(R.drawable.ic_ui_user, "意见反馈", false, this::openEmailFeedback), quickCellParams());
        homePanel.addView(quickRow2, quickRowParams());

        // --- 主按钮：开始跟读提词 ---
        LinearLayout ctaButton = new LinearLayout(this);
        ctaButton.setOrientation(LinearLayout.HORIZONTAL);
        ctaButton.setGravity(Gravity.CENTER);
        GradientDrawable ctaBg = new GradientDrawable();
        ctaBg.setCornerRadius(dp(16));
        ctaBg.setColor(C_BRAND);
        ctaButton.setBackground(ctaBg);
        ctaButton.setElevation(dp(4));
        ImageView ctaIcon = uiIcon(R.drawable.ic_ui_mic, Color.WHITE, 19);
        LinearLayout.LayoutParams ctaIconParams = new LinearLayout.LayoutParams(dp(19), dp(19));
        ctaIconParams.setMargins(0, 0, dp(8), 0);
        ctaButton.addView(ctaIcon, ctaIconParams);
        TextView ctaText = makeText("开始跟读提词", 15, Color.WHITE, Typeface.BOLD);
        ctaText.setIncludeFontPadding(false);
        ctaButton.addView(ctaText);
        ctaButton.setOnClickListener(view -> enterPrompterAfterAgreement());
        LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        ctaParams.setMargins(0, dp(14), 0, 0);
        homePanel.addView(ctaButton, ctaParams);

        // --- 底部 Tab 栏 ---
        homeTabBar = new LinearLayout(this);
        homeTabBar.setOrientation(LinearLayout.VERTICAL);
        homeTabBar.setBackgroundColor(C_SURFACE);
        View tabDivider = new View(this);
        tabDivider.setBackgroundColor(C_LINE);
        homeTabBar.addView(tabDivider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        tabRow = new LinearLayout(this);
        tabRow.setOrientation(LinearLayout.HORIZONTAL);
        homeTabBar.addView(tabRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56)));

        FrameLayout.LayoutParams tabBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabBarParams.gravity = Gravity.BOTTOM;
        root.addView(homeTabBar, tabBarParams);

        scriptsPage = buildScriptsPage();
        root.addView(scriptsPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        settingsPage = buildSettingsPage();
        root.addView(settingsPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        profilePage = buildProfilePage();
        root.addView(profilePage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        editorPage = buildEditorPage();
        root.addView(editorPage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scriptsFab = new LinearLayout(this);
        scriptsFab.setGravity(Gravity.CENTER);
        GradientDrawable fabBg = new GradientDrawable();
        fabBg.setShape(GradientDrawable.OVAL);
        fabBg.setColor(C_BRAND);
        scriptsFab.setBackground(fabBg);
        scriptsFab.setElevation(dp(6));
        scriptsFab.addView(uiIcon(R.drawable.ic_ui_plus, Color.WHITE, 22),
                new LinearLayout.LayoutParams(dp(22), dp(22)));
        scriptsFab.setOnClickListener(view -> openEditor(null));
        scriptsFab.setVisibility(View.GONE);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(dp(50), dp(50));
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        root.addView(scriptsFab, fabParams);

        // 页面是后加进 root 的，Tab 栏要显式提到最上层才不会被盖住
        homeTabBar.bringToFront();

        // 让首页避开状态栏与导航栏（Android 15 起强制 edge-to-edge）
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = Math.max(insets.getSystemWindowInsetTop(), systemBarHeight("status_bar_height"));
            int bottomInset = Math.max(insets.getSystemWindowInsetBottom(), systemBarHeight("navigation_bar_height"));
            homePanel.setPadding(dp(16), topInset + dp(10), dp(16), bottomInset + dp(78));
            homeTabBar.setPadding(0, 0, 0, bottomInset);
            int pageTop = topInset + dp(14);
            int pageBottom = bottomInset + dp(78);
            scriptsPanel.setPadding(dp(16), pageTop, dp(16), pageBottom);
            settingsPanel.setPadding(dp(16), pageTop, dp(16), pageBottom);
            profilePanel.setPadding(dp(16), pageTop, dp(16), pageBottom);
            editorPage.setPadding(dp(16), pageTop, dp(16), bottomInset + dp(10));
            prompterTopBar.setPadding(dp(15), topInset + dp(8), dp(15), dp(2));
            controlsPanel.setPadding(dp(14), dp(12), dp(14), bottomInset + dp(14));
            FrameLayout.LayoutParams fabLp = (FrameLayout.LayoutParams) scriptsFab.getLayoutParams();
            fabLp.setMargins(0, 0, dp(16), bottomInset + dp(70));
            scriptsFab.setLayoutParams(fabLp);
            return insets;
        });
        // 监听器可能注册在系统首次派发之后，主动要一次，避免这一帧的内边距不生效
        root.requestApplyInsets();

        // ================= ② 全屏提词 / 跟读模式 =================
        // 顶栏：返回 / 稿件标题 / 更多
        prompterTopBar = new LinearLayout(this);
        prompterTopBar.setOrientation(LinearLayout.VERTICAL);
        prompterTopBar.setVisibility(View.GONE);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        navRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout navBack = new LinearLayout(this);
        navBack.setGravity(Gravity.CENTER);
        navBack.addView(uiIcon(R.drawable.ic_ui_back, Color.WHITE, 20),
                new LinearLayout.LayoutParams(dp(20), dp(20)));
        navBack.setOnClickListener(view -> enterHomeMode());
        navRow.addView(navBack, new LinearLayout.LayoutParams(dp(32), dp(32)));
        prompterTitleView = makeText("", 14, Color.WHITE, Typeface.BOLD);
        prompterTitleView.setGravity(Gravity.CENTER);
        prompterTitleView.setSingleLine(true);
        prompterTitleView.setEllipsize(TextUtils.TruncateAt.END);
        navRow.addView(prompterTitleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout navMore = new LinearLayout(this);
        navMore.setGravity(Gravity.CENTER);
        GradientDrawable moreBg = new GradientDrawable();
        moreBg.setShape(GradientDrawable.OVAL);
        moreBg.setColor(0x1AFFFFFF);
        navMore.setBackground(moreBg);
        navMore.addView(uiIcon(R.drawable.ic_ui_more, Color.WHITE, 18),
                new LinearLayout.LayoutParams(dp(18), dp(18)));
        navMore.setOnClickListener(view -> showPrompterSettingsDialog());
        navRow.addView(navMore, new LinearLayout.LayoutParams(dp(30), dp(30)));
        prompterTopBar.addView(navRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 识别状态 + 计时
        LinearLayout asrRow = new LinearLayout(this);
        asrRow.setOrientation(LinearLayout.HORIZONTAL);
        asrRow.setGravity(Gravity.CENTER_VERTICAL);
        liveDot = new View(this);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.setMargins(dp(2), 0, dp(7), 0);
        asrRow.addView(liveDot, dotParams);
        asrStateView = makeText("", 11, 0xB3FFFFFF, Typeface.NORMAL);
        asrRow.addView(asrStateView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        prompterTimeView = makeText("00:00 / 00:00", 11, 0x9EFFFFFF, Typeface.NORMAL);
        asrRow.addView(prompterTimeView);
        LinearLayout.LayoutParams asrParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        asrParams.setMargins(0, dp(6), 0, 0);
        prompterTopBar.addView(asrRow, asrParams);

        // 进度条
        progressTrack = new LinearLayout(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(2));
        trackBg.setColor(0x24FFFFFF);
        progressTrack.setBackground(trackBg);
        progressFill = new View(this);
        GradientDrawable fillBg = new GradientDrawable();
        fillBg.setCornerRadius(dp(2));
        fillBg.setColor(C_BRAND_500);
        progressFill.setBackground(fillBg);
        progressTrack.addView(progressFill, new LinearLayout.LayoutParams(0, dp(3)));
        LinearLayout.LayoutParams trackParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(3));
        trackParams.setMargins(0, dp(9), 0, 0);
        prompterTopBar.addView(progressTrack, trackParams);

        FrameLayout.LayoutParams topBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBarParams.gravity = Gravity.TOP;
        root.addView(prompterTopBar, topBarParams);

        // 控制栏第一行：设置 / 速度 / 大圆播放键 / 重置 / 退出
        LinearLayout transportRow = new LinearLayout(this);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setGravity(Gravity.CENTER_VERTICAL);
        controlsPanel.addView(transportRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // startButton / pauseButton 不进布局，只作为状态载体：
        // 全项目有三十多处 setEnabled 调用，用子类拦截后大圆按钮能自动跟着变，调用点一处都不用改
        startButton = new TransportButton(this);
        pauseButton = new TransportButton(this);
        pauseButton.setEnabled(false);

        transportRow.addView(transportItem(R.drawable.ic_ui_gear, "设置",
                this::showPrompterSettingsDialog), transportCellParams());
        transportRow.addView(transportItem(R.drawable.ic_ui_speed, "速度",
                this::showFollowSettingsDialog), transportCellParams());

        LinearLayout playButton = new LinearLayout(this);
        playButton.setGravity(Gravity.CENTER);
        GradientDrawable playBg = new GradientDrawable();
        playBg.setShape(GradientDrawable.OVAL);
        playBg.setColor(C_BRAND_500);
        playButton.setBackground(playBg);
        playButton.setElevation(dp(6));
        playPauseIcon = uiIcon(R.drawable.ic_ui_play, Color.WHITE, 24);
        playButton.addView(playPauseIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        playButton.setOnClickListener(view -> togglePlayPauseFromPrompt());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(54), dp(54));
        playParams.setMargins(dp(6), 0, dp(6), 0);
        transportRow.addView(playButton, playParams);

        transportRow.addView(transportItem(R.drawable.ic_ui_reset, "重置",
                this::resetReading), transportCellParams());
        transportRow.addView(transportItem(R.drawable.ic_ui_exit, "退出",
                this::enterHomeMode), transportCellParams());

        // 控制栏第二行：朗读时需要随手可点的四项，收进菜单会打断表达
        LinearLayout secondaryRow = new LinearLayout(this);
        secondaryRow.setOrientation(LinearLayout.HORIZONTAL);
        secondaryRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        secondaryParams.setMargins(0, dp(10), 0, 0);
        controlsPanel.addView(secondaryRow, secondaryParams);

        secondaryRow.addView(transportItem(R.drawable.ic_ui_prev, "后退",
                () -> nudgeReadIndex(-5)), transportCellParams());
        secondaryRow.addView(transportItem(R.drawable.ic_ui_next, "前进",
                () -> nudgeReadIndex(5)), transportCellParams());

        fullscreenButton = makeSecondaryButton(getString(R.string.button_full_landscape));
        autoScrollButton = makeSecondaryButton(getString(R.string.button_auto_scroll));
        styleDarkButton(fullscreenButton);
        styleDarkButton(autoScrollButton);
        secondaryRow.addView(fullscreenButton, transportCellParams());
        secondaryRow.addView(autoScrollButton, transportCellParams());

        startButton.setOnClickListener(view -> startReading());
        pauseButton.setOnClickListener(view -> stopReading());
        fullscreenButton.setOnClickListener(view -> toggleFullLandscape());
        autoScrollButton.setOnClickListener(view -> toggleAutoScroll());
        syncTransport();

        applyOrientationLayout();
        applyDisplaySettings();
        showTab(TAB_HOME);
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
        button.setTextColor(primary ? Color.parseColor("#FFFFFF") : Color.parseColor("#1A2029"));
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

    // ================= 首页 UI 助手（对齐设计稿） =================
    private ImageView uiIcon(int drawableId, int tint, int sizeDp) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(drawableId);
        icon.setColorFilter(tint);
        icon.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return icon;
    }

    private LinearLayout iconBox(int drawableId, int tint, int boxColor, int iconDp) {
        LinearLayout box = new LinearLayout(this);
        box.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        background.setColor(boxColor);
        box.setBackground(background);
        box.addView(uiIcon(drawableId, tint, iconDp), new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        return box;
    }

    private LinearLayout uiCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(C_SURFACE);
        card.setBackground(background);
        card.setElevation(dp(2));
        return card;
    }

    private TextView chipView(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextSize(11);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chip.setTextColor(C_BRAND);
        chip.setIncludeFontPadding(false);
        chip.setPadding(dp(9), dp(4), dp(9), dp(4));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(20));
        background.setColor(C_BRAND_50);
        chip.setBackground(background);
        return chip;
    }

    private TextView sectionLabel(String text) {
        TextView label = makeText(text, 12, C_TEXT_2, Typeface.BOLD);
        label.setIncludeFontPadding(false);
        return label;
    }

    private LinearLayout.LayoutParams sectionLabelParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(2), dp(13), 0, dp(7));
        return params;
    }

    private LinearLayout quickTile(int drawableId, String label, boolean blue, Runnable action) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(2), dp(11), dp(2), dp(9));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(16));
        background.setColor(C_SURFACE);
        tile.setBackground(background);
        tile.setElevation(dp(2));
        tile.addView(iconBox(drawableId, blue ? C_BLUE : C_BRAND_500, blue ? C_BLUE_50 : C_BRAND_50, 19),
                new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView text = makeText(label, 11, C_TEXT_2, Typeface.NORMAL);
        text.setGravity(Gravity.CENTER);
        text.setSingleLine(true);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.setMargins(0, dp(7), 0, 0);
        tile.addView(text, textParams);
        tile.setOnClickListener(view -> action.run());
        return tile;
    }

    private LinearLayout.LayoutParams quickCellParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private LinearLayout.LayoutParams quickRowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(-4), 0, dp(-4), dp(8));
        return params;
    }

    private LinearLayout tabItem(int drawableId, String label, boolean active, Runnable action) {
        LinearLayout tab = new LinearLayout(this);
        tab.setOrientation(LinearLayout.VERTICAL);
        tab.setGravity(Gravity.CENTER);
        int tint = active ? C_BRAND : C_TEXT_3;
        tab.addView(uiIcon(drawableId, tint, 20), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView text = makeText(label, 10, tint, active ? Typeface.BOLD : Typeface.NORMAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.setMargins(0, dp(4), 0, 0);
        tab.addView(text, textParams);
        if (action != null) {
            tab.setOnClickListener(view -> action.run());
        }
        return tab;
    }

    private LinearLayout.LayoutParams tabCellParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private String scriptHeadline() {
        String script = scriptText == null || scriptText.trim().isEmpty()
                ? getString(R.string.default_script) : scriptText.trim();
        int lineEnd = script.indexOf('\n');
        String headline = lineEnd > 0 ? script.substring(0, lineEnd) : script;
        return headline.length() > 15 ? headline.substring(0, 15) + "…" : headline;
    }

    private String scriptPreview() {
        String script = scriptText == null || scriptText.trim().isEmpty()
                ? getString(R.string.default_script) : scriptText.trim();
        script = script.replace('\n', ' ');
        return script.length() > 40 ? script.substring(0, 40) + "…" : script;
    }

    private void refreshHomeScriptCard() {
        if (scriptTitleView == null) {
            return;
        }
        scriptTitleView.setText(scriptHeadline());
        scriptPreviewView.setText(scriptPreview());
        int count = PinyinMatcher.normalizeText(scriptText == null ? "" : scriptText).length();
        wordCountView.setText("约 " + count + " 字 · 点按编辑稿件");
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

    // ================= 页面框架 =================
    private void showTab(int tab) {
        activeTab = tab;
        editorPage.setVisibility(View.GONE);
        homeScroll.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.GONE);
        scriptsPage.setVisibility(tab == TAB_SCRIPTS ? View.VISIBLE : View.GONE);
        settingsPage.setVisibility(tab == TAB_SETTINGS ? View.VISIBLE : View.GONE);
        profilePage.setVisibility(tab == TAB_PROFILE ? View.VISIBLE : View.GONE);
        scriptsFab.setVisibility(tab == TAB_SCRIPTS ? View.VISIBLE : View.GONE);
        homeTabBar.setVisibility(View.VISIBLE);
        if (tab == TAB_HOME) {
            refreshHomeScriptCard();
        } else if (tab == TAB_SCRIPTS) {
            renderScriptList();
        } else if (tab == TAB_SETTINGS) {
            renderSettingsPage();
        } else {
            renderProfilePage();
        }
        renderTabBar();
    }

    /** 提词模式下隐藏全部首页外壳；退出提词时回到之前所在的 Tab。 */
    private void showHomeChrome(boolean visible) {
        if (visible) {
            showTab(activeTab);
            return;
        }
        homeScroll.setVisibility(View.GONE);
        scriptsPage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);
        profilePage.setVisibility(View.GONE);
        editorPage.setVisibility(View.GONE);
        scriptsFab.setVisibility(View.GONE);
        homeTabBar.setVisibility(View.GONE);
    }

    private void renderTabBar() {
        tabRow.removeAllViews();
        tabRow.addView(tabItem(R.drawable.ic_ui_home, "首页", activeTab == TAB_HOME,
                () -> showTab(TAB_HOME)), tabCellParams());
        tabRow.addView(tabItem(R.drawable.ic_ui_files, "稿件", activeTab == TAB_SCRIPTS,
                () -> showTab(TAB_SCRIPTS)), tabCellParams());
        tabRow.addView(tabItem(R.drawable.ic_ui_gear, "设置", activeTab == TAB_SETTINGS,
                () -> showTab(TAB_SETTINGS)), tabCellParams());
        tabRow.addView(tabItem(R.drawable.ic_ui_user, "我的", activeTab == TAB_PROFILE,
                () -> showTab(TAB_PROFILE)), tabCellParams());
    }

    /** 各页统一的滚动容器。 */
    private ScrollView buildPageScroll(LinearLayout panel) {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(C_BG);
        page.setClipToPadding(false);
        panel.setOrientation(LinearLayout.VERTICAL);
        page.addView(panel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.setVisibility(View.GONE);
        return page;
    }

    private LinearLayout pageTitleRow(String title, View trailing) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = makeText(title, 21, C_TEXT_1, Typeface.BOLD);
        text.setIncludeFontPadding(false);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (trailing != null) {
            row.addView(trailing);
        }
        return row;
    }

    // ================= ③ 稿件管理 =================
    private ScrollView buildScriptsPage() {
        scriptsPanel = new LinearLayout(this);
        ScrollView page = buildPageScroll(scriptsPanel);

        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dp(12));
        scriptsPanel.addView(pageTitleRow("稿件管理", null), titleParams);

        // 搜索框
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(dp(12));
        searchBg.setColor(C_SURFACE);
        searchBar.setBackground(searchBg);
        searchBar.setElevation(dp(2));
        searchBar.addView(uiIcon(R.drawable.ic_ui_search, C_TEXT_3, 16),
                new LinearLayout.LayoutParams(dp(16), dp(16)));
        scriptSearchInput = new EditText(this);
        scriptSearchInput.setHint("搜索稿件标题或内容");
        scriptSearchInput.setTextSize(13);
        scriptSearchInput.setTextColor(C_TEXT_1);
        scriptSearchInput.setHintTextColor(C_TEXT_3);
        scriptSearchInput.setBackgroundColor(Color.TRANSPARENT);
        scriptSearchInput.setSingleLine(true);
        scriptSearchInput.setPadding(dp(8), 0, 0, 0);
        scriptSearchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                renderScriptList();
            }
        });
        searchBar.addView(scriptSearchInput,
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        scriptsPanel.addView(searchBar, searchParams);

        // 分类分段栏
        scriptSegBar = new LinearLayout(this);
        scriptSegBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams segParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        segParams.setMargins(0, dp(12), 0, dp(4));
        scriptsPanel.addView(scriptSegBar, segParams);

        View segLine = new View(this);
        segLine.setBackgroundColor(C_LINE);
        scriptsPanel.addView(segLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        scriptListContainer = new LinearLayout(this);
        scriptListContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        listParams.setMargins(0, dp(11), 0, 0);
        scriptsPanel.addView(scriptListContainer, listParams);

        return page;
    }

    private void renderSegBar() {
        scriptSegBar.removeAllViews();
        String[] segments = new String[SCRIPT_CATEGORIES.length + 1];
        segments[0] = "全部";
        System.arraycopy(SCRIPT_CATEGORIES, 0, segments, 1, SCRIPT_CATEGORIES.length);
        for (String segment : segments) {
            boolean active = segment.equals(scriptFilterCategory);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setPadding(0, dp(7), 0, 0);
            TextView text = makeText(segment, 13, active ? C_BRAND : C_TEXT_2,
                    active ? Typeface.BOLD : Typeface.NORMAL);
            cell.addView(text);
            View underline = new View(this);
            underline.setBackgroundColor(active ? C_BRAND : Color.TRANSPARENT);
            LinearLayout.LayoutParams underlineParams = new LinearLayout.LayoutParams(dp(26), dp(3));
            underlineParams.setMargins(0, dp(7), 0, 0);
            cell.addView(underline, underlineParams);
            cell.setOnClickListener(view -> {
                scriptFilterCategory = segment;
                renderScriptList();
            });
            scriptSegBar.addView(cell, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    private void renderScriptList() {
        renderSegBar();
        scriptListContainer.removeAllViews();

        String keyword = scriptSearchInput.getText().toString().trim();
        ArrayList<ScriptItem> shown = new ArrayList<>();
        for (ScriptItem item : savedScripts) {
            boolean matchCategory = "全部".equals(scriptFilterCategory)
                    || scriptFilterCategory.equals(item.category);
            boolean matchKeyword = keyword.isEmpty() || item.text.contains(keyword);
            if (matchCategory && matchKeyword) {
                shown.add(item);
            }
        }

        if (shown.isEmpty()) {
            LinearLayout empty = uiCard();
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(14), dp(34), dp(14), dp(34));
            TextView hint = makeText(savedScripts.isEmpty()
                    ? "还没有保存的稿件，点右下角 + 新建" : "没有匹配的稿件", 13, C_TEXT_3, Typeface.NORMAL);
            empty.addView(hint);
            scriptListContainer.addView(empty, cardParams());
            return;
        }

        for (ScriptItem item : shown) {
            scriptListContainer.addView(scriptRow(item), cardParams());
        }
    }

    private LinearLayout scriptRow(ScriptItem item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(11), dp(11), dp(11), dp(11));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(C_SURFACE);
        row.setBackground(background);
        row.setElevation(dp(2));

        LinearLayout icon = iconBox(R.drawable.ic_ui_doc, C_BRAND_500, C_BRAND_50, 18);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(30), dp(30));
        iconParams.setMargins(0, 0, dp(10), 0);
        row.addView(icon, iconParams);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = makeText(item.title(), 13, C_TEXT_1, Typeface.BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);
        TextView category = makeText(item.category, 11, C_BRAND_500, Typeface.NORMAL);
        LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        categoryParams.setMargins(0, dp(4), 0, 0);
        texts.addView(category, categoryParams);
        TextView meta = makeText(item.wordCount() + " 字   最后编辑：" + formatUpdatedAt(item.updatedAt),
                11, C_TEXT_3, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        metaParams.setMargins(0, dp(3), 0, 0);
        texts.addView(meta, metaParams);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView startButton = makeText("开始", 12, C_BRAND, Typeface.BOLD);
        startButton.setPadding(dp(13), dp(5), dp(13), dp(5));
        GradientDrawable startBg = new GradientDrawable();
        startBg.setCornerRadius(dp(20));
        startBg.setColor(C_SURFACE);
        startBg.setStroke(dp(1), 0xFF9FDDD3);
        startButton.setBackground(startBg);
        startButton.setOnClickListener(view -> {
            useScript(item);
            enterPrompterAfterAgreement();
        });
        row.addView(startButton);

        row.setOnClickListener(view -> openEditor(item));
        row.setOnLongClickListener(view -> {
            confirmDeleteScript(item);
            return true;
        });
        return row;
    }

    private void confirmDeleteScript(ScriptItem item) {
        new AlertDialog.Builder(this)
                .setTitle("删除稿件")
                .setMessage("确定删除《" + item.title() + "》吗？删除后无法恢复。")
                .setPositiveButton("删除", (dialog, which) -> {
                    savedScripts.remove(item);
                    persistSavedScripts();
                    renderScriptList();
                    Toast.makeText(this, "已删除稿件", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    /** 把某份稿件设为当前提词稿件。 */
    private void useScript(ScriptItem item) {
        scriptText = item.text;
        readIndex = 0;
        targetReadIndex = 0;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_SCRIPT, scriptText).apply();
        renderScript();
    }

    // ================= ④ 稿件编辑 =================
    private LinearLayout buildEditorPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(C_BG);
        page.setVisibility(View.GONE);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout backButton = new LinearLayout(this);
        backButton.setGravity(Gravity.CENTER);
        backButton.addView(uiIcon(R.drawable.ic_ui_back, C_TEXT_1, 20),
                new LinearLayout.LayoutParams(dp(20), dp(20)));
        backButton.setOnClickListener(view -> closeEditor(true));
        topBar.addView(backButton, new LinearLayout.LayoutParams(dp(32), dp(32)));
        TextView topTitle = makeText("编辑稿件", 16, C_TEXT_1, Typeface.BOLD);
        topTitle.setGravity(Gravity.CENTER);
        topBar.addView(topTitle, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView saveButton = makeText("保存", 12, C_BRAND, Typeface.BOLD);
        saveButton.setPadding(dp(13), dp(5), dp(13), dp(5));
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setCornerRadius(dp(20));
        saveBg.setColor(C_SURFACE);
        saveBg.setStroke(dp(1), 0xFF9FDDD3);
        saveButton.setBackground(saveBg);
        saveButton.setOnClickListener(view -> saveEditor(false));
        topBar.addView(saveButton);
        LinearLayout.LayoutParams topBarParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        topBarParams.setMargins(0, 0, 0, dp(12));
        page.addView(topBar, topBarParams);

        ScrollView body = new ScrollView(this);
        body.setFillViewport(true);
        LinearLayout bodyPanel = new LinearLayout(this);
        bodyPanel.setOrientation(LinearLayout.VERTICAL);
        body.addView(bodyPanel, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 分类
        LinearLayout categoryCard = uiCard();
        categoryCard.addView(makeText("稿件分类", 12, C_TEXT_2, Typeface.BOLD));
        editorChipRow = new LinearLayout(this);
        editorChipRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams chipRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipRowParams.setMargins(0, dp(10), 0, 0);
        categoryCard.addView(editorChipRow, chipRowParams);
        bodyPanel.addView(categoryCard, cardParams());

        // 正文
        LinearLayout editorCard = uiCard();
        editorBodyInput = new EditText(this);
        editorBodyInput.setTextSize(14);
        editorBodyInput.setTextColor(C_TEXT_1);
        editorBodyInput.setHint("在这里输入或粘贴稿件正文");
        editorBodyInput.setHintTextColor(C_TEXT_3);
        editorBodyInput.setGravity(Gravity.TOP | Gravity.START);
        editorBodyInput.setBackgroundColor(Color.TRANSPARENT);
        editorBodyInput.setPadding(0, 0, 0, 0);
        editorBodyInput.setLineSpacing(dp(7), 1.2f);
        editorBodyInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editorBodyInput.setHorizontallyScrolling(false);
        editorBodyInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                updateEditorStats();
            }
        });
        editorCard.addView(editorBodyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220)));
        bodyPanel.addView(editorCard, cardParams());

        // 编辑工具
        bodyPanel.addView(sectionLabel("编辑"), sectionLabelParams());
        LinearLayout toolRow = new LinearLayout(this);
        toolRow.setOrientation(LinearLayout.HORIZONTAL);
        toolRow.addView(quickTile(R.drawable.ic_ui_import, "粘贴", true, this::pasteIntoEditor), quickCellParams());
        toolRow.addView(quickTile(R.drawable.ic_ui_text, "分段整理", false, this::reflowEditorText), quickCellParams());
        toolRow.addView(quickTile(R.drawable.ic_ui_camera, "相机提词", true, this::startCameraPrompter), quickCellParams());
        toolRow.addView(quickTile(R.drawable.ic_ui_trash, "清空", false, this::clearEditorText), quickCellParams());
        bodyPanel.addView(toolRow, quickRowParams());

        editorStatsView = makeText("", 11, C_TEXT_3, Typeface.NORMAL);
        editorStatsView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statsParams.setMargins(0, dp(10), 0, 0);
        bodyPanel.addView(editorStatsView, statsParams);

        LinearLayout ctaButton = new LinearLayout(this);
        ctaButton.setOrientation(LinearLayout.HORIZONTAL);
        ctaButton.setGravity(Gravity.CENTER);
        GradientDrawable ctaBg = new GradientDrawable();
        ctaBg.setCornerRadius(dp(16));
        ctaBg.setColor(C_BRAND);
        ctaButton.setBackground(ctaBg);
        ctaButton.setElevation(dp(4));
        ctaButton.addView(uiIcon(R.drawable.ic_ui_mic, Color.WHITE, 19),
                marginParams(dp(19), dp(19), 0, 0, dp(8), 0));
        ctaButton.addView(makeText("保存并开始跟读", 15, Color.WHITE, Typeface.BOLD));
        ctaButton.setOnClickListener(view -> saveEditor(true));
        LinearLayout.LayoutParams ctaParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        ctaParams.setMargins(0, dp(14), 0, dp(10));
        bodyPanel.addView(ctaButton, ctaParams);

        return page;
    }

    private LinearLayout.LayoutParams marginParams(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private void renderEditorChips() {
        editorChipRow.removeAllViews();
        for (String category : SCRIPT_CATEGORIES) {
            boolean active = category.equals(editorCategory);
            TextView chip = new TextView(this);
            chip.setText(category);
            chip.setTextSize(12);
            chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            chip.setTextColor(active ? Color.WHITE : C_BRAND);
            chip.setIncludeFontPadding(false);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(12), dp(7), dp(12), dp(7));
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(20));
            background.setColor(active ? C_BRAND : C_BRAND_50);
            chip.setBackground(background);
            chip.setOnClickListener(view -> {
                editorCategory = category;
                renderEditorChips();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chipParams.setMargins(0, 0, dp(8), 0);
            editorChipRow.addView(chip, chipParams);
        }
    }

    private void openEditor(ScriptItem item) {
        editingItem = item;
        editorCategory = item == null ? SCRIPT_CATEGORIES[0] : item.category;
        editorBodyInput.setText(item == null ? "" : item.text);
        renderEditorChips();
        updateEditorStats();
        homeScroll.setVisibility(View.GONE);
        scriptsPage.setVisibility(View.GONE);
        settingsPage.setVisibility(View.GONE);
        profilePage.setVisibility(View.GONE);
        scriptsFab.setVisibility(View.GONE);
        homeTabBar.setVisibility(View.GONE);
        editorPage.setVisibility(View.VISIBLE);
    }

    private void closeEditor(boolean backToTab) {
        editorPage.setVisibility(View.GONE);
        if (backToTab) {
            showTab(activeTab);
        }
    }

    private void updateEditorStats() {
        String text = editorBodyInput.getText().toString();
        int count = PinyinMatcher.normalizeText(text).length();
        // 中文口播约每分钟 220 字，按当前滚动速度折算
        int seconds = (int) Math.round(count / 220.0 * 60);
        editorStatsView.setText("共 " + count + " 字 · 正常语速约需 "
                + (seconds / 60) + " 分 " + (seconds % 60) + " 秒");
    }

    private void pasteIntoEditor() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip() || clipboard.getPrimaryClip() == null
                || clipboard.getPrimaryClip().getItemCount() == 0) {
            Toast.makeText(this, "剪贴板是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence pasted = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
        if (pasted == null || pasted.length() == 0) {
            Toast.makeText(this, "剪贴板是空的", Toast.LENGTH_SHORT).show();
            return;
        }
        editorBodyInput.setText(editorBodyInput.getText().toString() + pasted);
        editorBodyInput.setSelection(editorBodyInput.getText().length());
    }

    /** 按中文句末标点断行，让提词时每行是一个自然停顿。 */
    private void reflowEditorText() {
        String text = editorBodyInput.getText().toString().replace("\n", "");
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "先输入稿件正文", Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            builder.append(c);
            if ("。！？；".indexOf(c) >= 0 && i < text.length() - 1) {
                builder.append('\n');
            }
        }
        editorBodyInput.setText(builder.toString());
        Toast.makeText(this, "已按句号断行", Toast.LENGTH_SHORT).show();
    }

    private void clearEditorText() {
        if (editorBodyInput.getText().length() == 0) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("清空正文")
                .setMessage("确定清空当前编辑的正文吗？")
                .setPositiveButton("清空", (dialog, which) -> editorBodyInput.setText(""))
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void saveEditor(boolean startAfterSave) {
        String text = editorBodyInput.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "稿件正文不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (editingItem != null) {
            savedScripts.remove(editingItem);
        }
        saveScriptToHistory(text, editorCategory);
        scriptText = text;
        readIndex = 0;
        targetReadIndex = 0;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_SCRIPT, scriptText).apply();
        renderScript();
        editingItem = findSavedScript(text);
        Toast.makeText(this, R.string.script_saved, Toast.LENGTH_SHORT).show();
        if (startAfterSave) {
            closeEditor(false);
            enterPrompterAfterAgreement();
        } else {
            closeEditor(true);
        }
    }

    // ================= ⑤ 设置 =================
    private ScrollView buildSettingsPage() {
        settingsPanel = new LinearLayout(this);
        return buildPageScroll(settingsPanel);
    }

    private void renderSettingsPage() {
        settingsPanel.removeAllViews();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dp(4));
        settingsPanel.addView(pageTitleRow("设置", null), titleParams);

        settingsPanel.addView(sectionLabel("提词显示"), sectionLabelParams());
        LinearLayout displayCard = listCard();
        displayCard.addView(settingRow(R.drawable.ic_ui_text, "字号", promptFontSize + " px",
                this::showDisplaySettingsDialog, true));
        displayCard.addView(settingRow(R.drawable.ic_ui_scroll, "滚动速度", String.valueOf(scrollSpeed),
                this::showFollowSettingsDialog, true));
        displayCard.addView(settingRow(R.drawable.ic_ui_monitor, "配色与背景", "自定义",
                this::showDisplaySettingsDialog, false));
        settingsPanel.addView(displayCard, cardParams());

        settingsPanel.addView(sectionLabel("语音识别"), sectionLabelParams());
        LinearLayout voiceCard = listCard();
        voiceCard.addView(switchRow(R.drawable.ic_ui_wifi, "离线识别（Vosk）", offlineMode, checked -> {
            offlineMode = checked;
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putBoolean(PREF_OFFLINE_MODE, offlineMode).apply();
            renderSettingsPage();
            Toast.makeText(this, offlineMode ? "已切换到离线识别" : "已切换到云端识别",
                    Toast.LENGTH_SHORT).show();
        }));
        voiceCard.addView(settingRow(R.drawable.ic_ui_gear, "识别服务商",
                offlineMode ? "离线 Vosk"
                        : PROVIDER_NAMES[getProviderIndex(savedSpeechProvider)]
                                + (hasCloudCredentials() ? "" : " · 待配置"),
                this::showSettingsDialog, true));
        voiceCard.addView(settingRow(R.drawable.ic_ui_speed, "跟读匹配灵敏度", String.valueOf(matchSensitivity),
                this::showFollowSettingsDialog, true));
        voiceCard.addView(settingRow(R.drawable.ic_ui_mic, "麦克风检测", "点按开始",
                this::testMic, false));
        settingsPanel.addView(voiceCard, cardParams());

        settingsPanel.addView(sectionLabel("通用"), sectionLabelParams());
        LinearLayout generalCard = listCard();
        generalCard.addView(settingRow(R.drawable.ic_ui_files, "稿件库",
                savedScripts.size() + " / " + MAX_SAVED_SCRIPTS, () -> showTab(TAB_SCRIPTS), true));
        generalCard.addView(settingRow(R.drawable.ic_ui_info, "关于与协议", appVersionName(),
                () -> showAboutDialog(false), false));
        settingsPanel.addView(generalCard, cardParams());
    }

    private LinearLayout listCard() {
        LinearLayout card = uiCard();
        card.setPadding(dp(14), dp(2), dp(14), dp(2));
        return card;
    }

    private LinearLayout settingRowBase(int drawableId, String title, boolean divider) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        LinearLayout icon = iconBox(drawableId, C_BRAND_500, C_BRAND_50, 14);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        iconParams.setMargins(0, 0, dp(10), 0);
        row.addView(icon, iconParams);
        TextView text = makeText(title, 13, C_TEXT_1, Typeface.NORMAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    /** 设置项外壳：底部分隔线要画在行外面，否则会被行内 padding 顶开。 */
    private LinearLayout wrapSettingRow(LinearLayout row, boolean divider) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (divider) {
            View line = new View(this);
            line.setBackgroundColor(C_LINE);
            wrap.addView(line, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
        }
        return wrap;
    }

    private LinearLayout settingRow(int drawableId, String title, String value,
                                    Runnable action, boolean divider) {
        LinearLayout row = settingRowBase(drawableId, title, divider);
        if (value != null && !value.isEmpty()) {
            row.addView(makeText(value, 12, C_TEXT_3, Typeface.NORMAL));
        }
        LinearLayout wrap = wrapSettingRow(row, divider);
        if (action != null) {
            wrap.setOnClickListener(view -> action.run());
        }
        return wrap;
    }

    private interface SwitchAction {
        void onChanged(boolean checked);
    }

    private LinearLayout switchRow(int drawableId, String title, boolean checked, SwitchAction action) {
        LinearLayout row = settingRowBase(drawableId, title, true);
        View track = new View(this);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(dp(20));
        trackBg.setColor(checked ? C_BRAND_500 : 0xFFDCE5E4);
        track.setBackground(trackBg);
        FrameLayout switchWrap = new FrameLayout(this);
        switchWrap.addView(track, new FrameLayout.LayoutParams(dp(38), dp(22)));
        View knob = new View(this);
        GradientDrawable knobBg = new GradientDrawable();
        knobBg.setShape(GradientDrawable.OVAL);
        knobBg.setColor(Color.WHITE);
        knob.setBackground(knobBg);
        knob.setElevation(dp(1));
        FrameLayout.LayoutParams knobParams = new FrameLayout.LayoutParams(dp(18), dp(18));
        knobParams.gravity = Gravity.CENTER_VERTICAL | (checked ? Gravity.END : Gravity.START);
        knobParams.setMargins(dp(2), 0, dp(2), 0);
        switchWrap.addView(knob, knobParams);
        row.addView(switchWrap, new LinearLayout.LayoutParams(dp(38), dp(22)));
        LinearLayout wrap = wrapSettingRow(row, true);
        wrap.setOnClickListener(view -> action.onChanged(!checked));
        return wrap;
    }

    // ================= ⑥ 我的 =================
    private ScrollView buildProfilePage() {
        profilePanel = new LinearLayout(this);
        return buildPageScroll(profilePanel);
    }

    private void renderProfilePage() {
        profilePanel.removeAllViews();
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.setMargins(0, 0, 0, dp(12));
        profilePanel.addView(pageTitleRow("我的", null), titleParams);

        // 资料卡：本地应用，没有账号体系，如实写成本机数据
        LinearLayout profileCard = uiCard();
        LinearLayout profileRow = new LinearLayout(this);
        profileRow.setOrientation(LinearLayout.HORIZONTAL);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView avatar = new ImageView(this);
        avatar.setImageResource(R.drawable.ic_avatar);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        avatarParams.setMargins(0, 0, dp(11), 0);
        profileRow.addView(avatar, avatarParams);
        LinearLayout profileTexts = new LinearLayout(this);
        profileTexts.setOrientation(LinearLayout.VERTICAL);
        profileTexts.addView(makeText("本机用户", 15, C_TEXT_1, Typeface.BOLD));
        TextView profileSub = makeText("稿件与设置全部保存在本机，不上传云端", 11, C_TEXT_3, Typeface.NORMAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subParams.setMargins(0, dp(5), 0, 0);
        profileTexts.addView(profileSub, subParams);
        profileRow.addView(profileTexts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        profileCard.addView(profileRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        profilePanel.addView(profileCard, cardParams());

        // 统计
        int totalWords = 0;
        for (ScriptItem item : savedScripts) {
            totalWords += item.wordCount();
        }
        int practiceCount = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getInt(PREF_PRACTICE_COUNT, 0);
        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        statRow.addView(statCell(String.valueOf(savedScripts.size()), "稿件"), quickCellParams());
        statRow.addView(statCell(String.valueOf(practiceCount), "跟读次数"), quickCellParams());
        statRow.addView(statCell(totalWords >= 10000
                ? String.format(Locale.CHINA, "%.1f万", totalWords / 10000f)
                : String.valueOf(totalWords), "累计字数"), quickCellParams());
        profilePanel.addView(statRow, quickRowParams());

        // 最近稿件
        profilePanel.addView(sectionLabel("最近稿件"), sectionLabelParams());
        LinearLayout recentCard = listCard();
        if (savedScripts.isEmpty()) {
            LinearLayout emptyRow = settingRowBase(R.drawable.ic_ui_doc, "还没有保存的稿件", false);
            recentCard.addView(wrapSettingRow(emptyRow, false));
        } else {
            int limit = Math.min(3, savedScripts.size());
            for (int i = 0; i < limit; i++) {
                ScriptItem item = savedScripts.get(i);
                recentCard.addView(settingRow(R.drawable.ic_ui_doc, item.title(),
                        item.wordCount() + " 字", () -> openEditor(item), i < limit - 1));
            }
        }
        profilePanel.addView(recentCard, cardParams());

        // 关于
        profilePanel.addView(sectionLabel("关于"), sectionLabelParams());
        LinearLayout aboutCard = listCard();
        aboutCard.addView(settingRow(R.drawable.ic_ui_info, "关于与用户协议", "",
                () -> showAboutDialog(false), true));
        aboutCard.addView(settingRow(R.drawable.ic_ui_mail, "意见反馈", "",
                this::openEmailFeedback, true));
        aboutCard.addView(settingRow(R.drawable.ic_ui_share, "分享给朋友", "",
                this::shareAppInfo, true));
        aboutCard.addView(settingRow(R.drawable.ic_ui_gear, "版本", appVersionName(), null, false));
        profilePanel.addView(aboutCard, cardParams());
    }

    private LinearLayout statCell(String value, String label) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(4), dp(13), dp(4), dp(12));
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(12));
        background.setColor(C_SURFACE);
        cell.setBackground(background);
        cell.setElevation(dp(2));
        TextView valueView = makeText(value, 19, C_BRAND, Typeface.BOLD);
        valueView.setIncludeFontPadding(false);
        cell.addView(valueView);
        TextView labelView = makeText(label, 11, C_TEXT_3, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.setMargins(0, dp(5), 0, 0);
        cell.addView(labelView, labelParams);
        return cell;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ================= 提词页助手 =================
    /**
     * 播放/暂停状态散落在三十多处 setEnabled 调用里，
     * 用子类拦截后大圆按钮和识别状态行能自动同步，不必改动那些调用点。
     */
    private class TransportButton extends Button {
        TransportButton(android.content.Context context) {
            super(context);
        }

        @Override
        public void setEnabled(boolean enabled) {
            super.setEnabled(enabled);
            syncTransport();
        }
    }

    private LinearLayout transportItem(int drawableId, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.addView(uiIcon(drawableId, 0x9EFFFFFF, 19),
                new LinearLayout.LayoutParams(dp(19), dp(19)));
        TextView text = makeText(label, 10, 0x9EFFFFFF, Typeface.NORMAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.setMargins(0, dp(5), 0, 0);
        item.addView(text, textParams);
        item.setOnClickListener(view -> action.run());
        return item;
    }

    private LinearLayout.LayoutParams transportCellParams() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
    }

    private void styleDarkButton(Button button) {
        button.setTextSize(11);
        button.setTextColor(0xC7FFFFFF);
        button.setAllCaps(false);
        button.setIncludeFontPadding(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(4), dp(8), dp(4), dp(8));
        button.setElevation(0);
        button.setStateListAnimator(null);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(10));
        background.setColor(0x14FFFFFF);
        button.setBackground(background);
    }

    /** 让大圆按钮、呼吸绿点和识别状态文字跟随播放状态。 */
    private void syncTransport() {
        if (playPauseIcon == null || pauseButton == null) {
            return;
        }
        boolean reading = pauseButton.isEnabled();
        playPauseIcon.setImageResource(reading ? R.drawable.ic_ui_pause : R.drawable.ic_ui_play);
        playPauseIcon.setColorFilter(Color.WHITE);
        if (liveDot != null) {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(reading ? 0xFF3BD9A4 : 0xFF8A9BA0);
            liveDot.setBackground(dot);
        }
        if (asrStateView != null) {
            String engine = offlineMode ? "离线识别中 · Vosk"
                    : "云端识别中 · " + PROVIDER_NAMES[getProviderIndex(savedSpeechProvider)];
            asrStateView.setText(reading ? engine : "已暂停，点中间按钮继续");
        }
    }

    private void updatePrompterProgress() {
        if (progressTrack == null || progressFill == null) {
            return;
        }
        int total = normalizedScript == null ? 0 : normalizedScript.length();
        float ratio = total <= 0 ? 0f : Math.min(1f, readIndex / (float) total);
        int trackWidth = progressTrack.getWidth();
        if (trackWidth <= 0) {
            progressTrack.post(this::updatePrompterProgress);
            return;
        }
        ViewGroup.LayoutParams params = progressFill.getLayoutParams();
        params.width = Math.round(trackWidth * ratio);
        progressFill.setLayoutParams(params);

        if (prompterTimeView != null) {
            // 中文口播约每分钟 220 字，用已读比例折算进度时间
            int totalSeconds = (int) Math.round(total / 220.0 * 60);
            int doneSeconds = Math.round(totalSeconds * ratio);
            prompterTimeView.setText(formatClock(doneSeconds) + " / " + formatClock(totalSeconds));
        }
    }

    private String formatClock(int seconds) {
        return String.format(Locale.CHINA, "%02d:%02d", seconds / 60, seconds % 60);
    }

    // ================= Dialog UI 助手 =================
    private LinearLayout dialogRoot() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(10));
        return layout;
    }

    private GradientDrawable dialogInputBg() {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(10));
        d.setColor(Color.parseColor("#FFFFFF"));
        d.setStroke(1, Color.parseColor("#D6E0E1"));
        return d;
    }

    private void styleDialogInput(EditText edit) {
        edit.setTextSize(14);
        edit.setTextColor(Color.parseColor("#1A2029"));
        edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        edit.setBackground(dialogInputBg());
        edit.setIncludeFontPadding(false);
    }

    private TextView dialogLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(Color.parseColor("#55636A"));
        t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setPadding(0, dp(12), 0, dp(6));
        return t;
    }

    private TextView dialogCaption(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12);
        t.setTextColor(Color.parseColor("#8FA3AC"));
        t.setLineSpacing(dp(2), 1.2f);
        t.setPadding(dp(4), dp(2), dp(4), dp(4));
        return t;
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
        showHomeChrome(false);
        prompterTopBar.setVisibility(View.VISIBLE);
        prompterTopBar.bringToFront();
        prompterTitleView.setText(scriptHeadline());
        controlsPanel.setVisibility(View.VISIBLE);
        controlsPanel.bringToFront();
        updatePrompterProgress();
        syncTransport();
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

    /** 启动悬浮窗提词（借鉴小白提词器的悬浮窗架构）。 */
    private void startFloatPrompter() {
        if (scriptText == null || scriptText.trim().isEmpty()) {
            Toast.makeText(this, R.string.status_enter_realtime_keys, Toast.LENGTH_SHORT).show();
            return;
        }
        // 悬浮提词以 microphone 类型的前台服务运行，没有录音权限时系统会直接拒绝启动
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            floatAfterPermission = true;
            startAfterPermission = false;
            testAfterPermission = false;
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, AUDIO_PERMISSION_REQUEST);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.status_float_permission, Toast.LENGTH_LONG).show();
            Intent permIntent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(permIntent);
            return;
        }
        Intent intent = new Intent(this, PrompterFloatService.class);
        intent.putExtra("script", scriptText);
        intent.putExtra("fontSize", Math.max(24, promptFontSize - 6));
        intent.putExtra("sensitivity", matchSensitivity);
        startService(intent);
        Toast.makeText(this, "悬浮窗已开启，可拖动调整位置", Toast.LENGTH_SHORT).show();
        // 切回桌面让悬浮窗显示在其他应用上
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    /** 启动相机拍摄提词（借鉴小白提词器的美颜相机模块，用开源CameraX替代）。 */
    private void startCameraPrompter() {
        if (scriptText == null || scriptText.trim().isEmpty()) {
            Toast.makeText(this, R.string.status_enter_realtime_keys, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, CameraPrompterActivity.class);
        intent.putExtra("script", scriptText);
        intent.putExtra("fontSize", Math.max(24, promptFontSize - 8));
        intent.putExtra("sensitivity", matchSensitivity);
        startActivity(intent);
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
        showHomeChrome(true);
        prompterTopBar.setVisibility(View.GONE);
        controlsPanel.setVisibility(View.GONE);
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
        savedAliyunAppKey = prefs.getString(PREF_ALIYUN_APP_KEY, "");
        savedAliyunAccessKeyId = prefs.getString(PREF_ALIYUN_ACCESS_KEY_ID, "");
        savedAliyunAccessKeySecret = prefs.getString(PREF_ALIYUN_ACCESS_KEY_SECRET, "");
        savedSpeechProvider = prefs.getString(PREF_SPEECH_PROVIDER, PROVIDER_BAIDU);
        offlineMode = prefs.getBoolean(PREF_OFFLINE_MODE, false);
        scriptText = prefs.getString(PREF_SCRIPT, getString(R.string.default_script));
        loadSavedScripts();
        colorStep = prefs.getInt(PREF_COLOR_SPEED, 1);
        autoAdvanceStep = prefs.getInt(PREF_AUTO_SPEED, 1);
        scrollSpeed = prefs.getInt(PREF_SCROLL_SPEED, 1);
        matchSensitivity = prefs.getInt(PREF_MATCH_SENSITIVITY, 3);
        promptFontSize = prefs.getInt(PREF_FONT_SIZE, 38);
        readColor = prefs.getInt(PREF_READ_COLOR, Color.rgb(232, 93, 63));
        currentColor = prefs.getInt(PREF_CURRENT_COLOR, Color.rgb(255, 224, 130));
        backgroundColor = prefs.getInt(PREF_BACKGROUND_COLOR, Color.rgb(17, 24, 29));
        agreementAccepted = prefs.getBoolean(PREF_AGREEMENT_ACCEPTED, false);
    }

    private void showPrompterSettingsDialog() {
        String[] options = {getString(R.string.button_speech_settings), getString(R.string.button_display_settings), getString(R.string.button_follow_settings), getString(R.string.button_script)};
        new AlertDialog.Builder(this)
            .setTitle("快捷设置")
            .setItems(options, (dialog, which) -> {
                if (which == 0) showSettingsDialog();
                else if (which == 1) showDisplaySettingsDialog();
                else if (which == 2) showFollowSettingsDialog();
                else openEditor(findSavedScript(scriptText));
            })
            .show();
    }

        private void showSettingsDialog() {
        LinearLayout form = dialogRoot();

        // 离线模式卡片
        GradientDrawable offlineCardBg = new GradientDrawable();
        offlineCardBg.setCornerRadius(dp(12));
        offlineCardBg.setColor(Color.parseColor(offlineMode ? "#ECFAFA" : "#F4F8F8"));
        offlineCardBg.setStroke(1, Color.parseColor(offlineMode ? "#D3EEF0" : "#E2EAEB"));
        LinearLayout offlineWrap = new LinearLayout(this);
        offlineWrap.setOrientation(LinearLayout.VERTICAL);
        offlineWrap.setPadding(dp(14), dp(14), dp(14), dp(14));
        offlineWrap.setBackground(offlineCardBg);
        LinearLayout.LayoutParams offlineWrapLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        offlineWrapLp.setMargins(0, 0, 0, dp(16));
        form.addView(offlineWrap, offlineWrapLp);

        CheckBox offlineCheck = new CheckBox(this);
        offlineCheck.setText(R.string.label_offline_mode);
        offlineCheck.setChecked(offlineMode);
        offlineCheck.setTextColor(Color.parseColor("#1A2029"));
        offlineCheck.setTextSize(15);
        offlineCheck.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        offlineCheck.setPadding(0, 0, 0, 0);
        offlineWrap.addView(offlineCheck, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView offlineHint = dialogCaption(getString(R.string.hint_offline_mode));
        offlineHint.setTextColor(offlineMode ? Color.parseColor("#0F8B8D") : Color.parseColor("#55636A"));
        offlineHint.setPadding(dp(30), dp(6), dp(4), 0);
        offlineWrap.addView(offlineHint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout cloudBlock = new LinearLayout(this);
        cloudBlock.setOrientation(LinearLayout.VERTICAL);
        form.addView(cloudBlock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView providerLabel = dialogLabel(getString(R.string.label_speech_provider));
        cloudBlock.addView(providerLabel);

        final int[] providerIndex = {getProviderIndex(savedSpeechProvider)};
        Button providerButton = makeSecondaryButton(PROVIDER_NAMES[providerIndex[0]]);
        providerButton.setTextColor(Color.parseColor("#1A2029"));
        providerButton.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams providerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
        providerParams.setMargins(0, dp(2), 0, dp(4));
        cloudBlock.addView(providerButton, providerParams);

        TextView firstFieldLabel = dialogLabel("");
        cloudBlock.addView(firstFieldLabel);
        EditText apiKeyField = new EditText(this);
        apiKeyField.setSingleLine(true);
        styleDialogInput(apiKeyField);
        cloudBlock.addView(apiKeyField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView appIdFieldLabel = dialogLabel("");
        cloudBlock.addView(appIdFieldLabel);
        EditText appIdField = new EditText(this);
        appIdField.setSingleLine(true);
        styleDialogInput(appIdField);
        cloudBlock.addView(appIdField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView secretFieldLabel = dialogLabel("");
        cloudBlock.addView(secretFieldLabel);
        EditText secretKeyField = new EditText(this);
        secretKeyField.setSingleLine(true);
        styleDialogInput(secretKeyField);
        cloudBlock.addView(secretKeyField, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        updateCredentialFields(PROVIDER_VALUES[providerIndex[0]], firstFieldLabel, apiKeyField, appIdFieldLabel, appIdField, secretFieldLabel, secretKeyField);
        providerButton.setOnClickListener(view ->
            showProviderPicker(providerIndex, providerButton, firstFieldLabel, apiKeyField, appIdFieldLabel, appIdField, secretFieldLabel, secretKeyField));

        TextView keyHelpView = dialogCaption(getString(R.string.hint_realtime_key_help));
        keyHelpView.setTextColor(Color.parseColor("#55636A"));
        LinearLayout.LayoutParams helpTextParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        helpTextParams.setMargins(0, dp(10), 0, 0);
        cloudBlock.addView(keyHelpView, helpTextParams);

        Button keyHelpButton = new Button(this);
        keyHelpButton.setText(R.string.button_open_realtime_doc);
        keyHelpButton.setTextColor(Color.parseColor("#0F8B8D"));
        keyHelpButton.setAllCaps(false);
        keyHelpButton.setBackgroundColor(Color.TRANSPARENT);
        keyHelpButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        keyHelpButton.setPadding(0, dp(6), 0, dp(6));
        keyHelpButton.setOnClickListener(view -> showRealtimeHelpDialog());
        LinearLayout.LayoutParams helpButtonParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cloudBlock.addView(keyHelpButton, helpButtonParams);

        cloudBlock.setVisibility(offlineCheck.isChecked() ? View.GONE : View.VISIBLE);
        offlineHint.setVisibility(offlineCheck.isChecked() ? View.VISIBLE : View.GONE);
        offlineCheck.setOnCheckedChangeListener((button, checked) -> {
            cloudBlock.setVisibility(checked ? View.GONE : View.VISIBLE);
            offlineHint.setVisibility(checked ? View.VISIBLE : View.GONE);
            offlineCardBg.setColor(Color.parseColor(checked ? "#ECFAFA" : "#F4F8F8"));
            offlineCardBg.setStroke(1, Color.parseColor(checked ? "#D3EEF0" : "#E2EAEB"));
            offlineHint.setTextColor(checked ? Color.parseColor("#0F8B8D") : Color.parseColor("#55636A"));
        });

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_settings)
            .setView(scrollableDialogView(form))
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                saveSpeechSettings(providerIndex[0], apiKeyField, appIdField, secretKeyField, offlineCheck.isChecked());
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

    private void saveSpeechSettings(int providerIndex, EditText apiKeyField, EditText appIdField, EditText secretKeyField, boolean offlineChecked) {
        savedSpeechProvider = PROVIDER_VALUES[providerIndex];
        offlineMode = offlineChecked;
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(PREF_SPEECH_PROVIDER, savedSpeechProvider)
            .putBoolean(PREF_OFFLINE_MODE, offlineMode);
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
            savedAliyunAccessKeyId = appIdField.getText().toString().trim();
            savedAliyunAccessKeySecret = secretKeyField.getText().toString().trim();
            cachedAliyunToken = "";
            cachedAliyunTokenExpireTime = 0L;
            editor
                .putString(PREF_ALIYUN_APP_KEY, savedAliyunAppKey)
                .putString(PREF_ALIYUN_ACCESS_KEY_ID, savedAliyunAccessKeyId)
                .putString(PREF_ALIYUN_ACCESS_KEY_SECRET, savedAliyunAccessKeySecret);
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
        if (offlineMode) {
            statusView.setText(getString(R.string.settings_saved) + " 已开启离线模式，无需云端密钥。");
        } else {
            statusView.setText(getString(R.string.settings_saved) + " 当前服务商：" + PROVIDER_NAMES[providerIndex] + "\n" + getCredentialStatus());
        }
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
            appIdLabel.setText("阿里云 AccessKey ID");
            appIdField.setHint("阿里云 AccessKey ID");
            appIdField.setText(savedAliyunAccessKeyId);
            secretLabel.setText("阿里云 AccessKey Secret");
            secretField.setHint("阿里云 AccessKey Secret");
            secretField.setText(savedAliyunAccessKeySecret);
            return;
        }
        firstLabel.setText("百度智能云 AppID");
        firstField.setHint(R.string.hint_baidu_app_id);
        firstField.setText(savedBaiduAppId);
        appIdLabel.setText("百度智能云 API Key");
        appIdField.setHint(R.string.hint_baidu_api_key);
        appIdField.setText(savedBaiduApiKey);
        secretLabel.setText("百度智能云 Secret Key（可选填）");
        secretField.setHint("语音识别 Secret Key（可选填）");
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

    /** 当前云端服务商的密钥是否配齐（复用 getCredentialStatus 的校验口径）。 */
    private boolean hasCloudCredentials() {
        return getCredentialStatus().startsWith("✓");
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
            addIfEmpty(missing, savedAliyunAccessKeyId, "AccessKey ID");
            addIfEmpty(missing, savedAliyunAccessKeySecret, "AccessKey Secret");
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

    private void loadSavedScripts() {
        String saved = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(PREF_SAVED_SCRIPTS, "[]");
        savedScripts = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(saved);
            for (int i = 0; i < arr.length(); i++) {
                Object raw = arr.get(i);
                ScriptItem item = new ScriptItem();
                if (raw instanceof JSONObject) {
                    JSONObject obj = (JSONObject) raw;
                    item.text = obj.optString("text", "");
                    item.category = obj.optString("category", SCRIPT_CATEGORIES[0]);
                    item.updatedAt = obj.optLong("updatedAt", System.currentTimeMillis());
                } else {
                    // 兼容 1.7.0 及更早版本：稿件历史只存了纯文本
                    item.text = String.valueOf(raw);
                }
                if (!item.text.trim().isEmpty()) {
                    savedScripts.add(item);
                }
            }
        } catch (Exception e) {
            savedScripts = new ArrayList<>();
        }
    }

    private ScriptItem findSavedScript(String script) {
        for (ScriptItem item : savedScripts) {
            if (item.text.equals(script)) {
                return item;
            }
        }
        return null;
    }

    private void saveScriptToHistory(String script) {
        ScriptItem existing = findSavedScript(script);
        saveScriptToHistory(script, existing == null ? SCRIPT_CATEGORIES[0] : existing.category);
    }

    private void saveScriptToHistory(String script, String category) {
        if (script == null || script.trim().isEmpty()) {
            return;
        }
        ScriptItem existing = findSavedScript(script);
        if (existing != null) {
            savedScripts.remove(existing);
        }
        ScriptItem item = existing == null ? new ScriptItem() : existing;
        item.text = script;
        item.category = category;
        item.updatedAt = System.currentTimeMillis();
        savedScripts.add(0, item);
        if (savedScripts.size() > MAX_SAVED_SCRIPTS) {
            savedScripts = new ArrayList<>(savedScripts.subList(0, MAX_SAVED_SCRIPTS));
        }
        persistSavedScripts();
    }

    private void persistSavedScripts() {
        JSONArray arr = new JSONArray();
        for (ScriptItem item : savedScripts) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("text", item.text);
                obj.put("category", item.category);
                obj.put("updatedAt", item.updatedAt);
            } catch (Exception e) {
                continue;
            }
            arr.put(obj);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putString(PREF_SAVED_SCRIPTS, arr.toString()).apply();
    }

    private String formatUpdatedAt(long timestamp) {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(timestamp));
    }

    private void showFollowSettingsDialog() {
        LinearLayout form = dialogRoot();

        TextView colorLabel = settingLabel(getString(R.string.label_color_speed) + ": " + colorStep);
        SeekBar colorSeek = settingSeekBar(5, colorStep);
        TextView autoLabel = settingLabel(getString(R.string.label_auto_speed) + ": " + autoAdvanceStep);
        SeekBar autoSeek = settingSeekBar(5, autoAdvanceStep);
        TextView scrollLabel = settingLabel(getString(R.string.label_scroll_speed) + ": " + scrollSpeed);
        SeekBar scrollSeek = settingSeekBar(5, scrollSpeed);
        TextView sensitivityLabel = settingLabel(getString(R.string.label_match_sensitivity) + ": " + matchSensitivity);
        SeekBar sensitivitySeek = settingSeekBar(5, matchSensitivity);
        TextView sensitivityHint = new TextView(this);
        sensitivityHint.setText(R.string.hint_match_sensitivity);
        sensitivityHint.setTextColor(Color.rgb(71, 85, 105));
        sensitivityHint.setTextSize(12);
        sensitivityHint.setLineSpacing(dp(2), 1.0f);
        LinearLayout.LayoutParams sensitivityHintParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sensitivityHintParams.setMargins(dp(4), dp(2), dp(4), dp(8));
        sensitivityHint.setLayoutParams(sensitivityHintParams);

        form.addView(colorLabel);
        form.addView(colorSeek);
        form.addView(autoLabel);
        form.addView(autoSeek);
        form.addView(scrollLabel);
        form.addView(scrollSeek);
        form.addView(sensitivityLabel);
        form.addView(sensitivitySeek);
        form.addView(sensitivityHint);

        colorSeek.setOnSeekBarChangeListener(settingListener(colorLabel, getString(R.string.label_color_speed)));
        autoSeek.setOnSeekBarChangeListener(settingListener(autoLabel, getString(R.string.label_auto_speed)));
        scrollSeek.setOnSeekBarChangeListener(settingListener(scrollLabel, getString(R.string.label_scroll_speed)));
        sensitivitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                matchSensitivity = Math.max(1, progress);
                sensitivityLabel.setText(getString(R.string.label_match_sensitivity) + ": " + matchSensitivity);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_follow_settings)
            .setView(scrollableDialogView(form))
            .setPositiveButton(R.string.dialog_save, (dialog, which) -> {
                colorStep = Math.max(1, colorSeek.getProgress());
                autoAdvanceStep = Math.max(1, autoSeek.getProgress());
                scrollSpeed = Math.max(1, scrollSeek.getProgress());
                matchSensitivity = Math.max(1, sensitivitySeek.getProgress());
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putInt(PREF_COLOR_SPEED, colorStep)
                    .putInt(PREF_AUTO_SPEED, autoAdvanceStep)
                    .putInt(PREF_SCROLL_SPEED, scrollSpeed)
                    .putInt(PREF_MATCH_SENSITIVITY, matchSensitivity)
                    .apply();
                statusView.setText(R.string.settings_saved);
            })
            .setNegativeButton(R.string.dialog_cancel, null)
            .show();
    }

    private void showDisplaySettingsDialog() {
        LinearLayout form = dialogRoot();

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
            GradientDrawable colorShape = new GradientDrawable();
            colorShape.setShape(GradientDrawable.OVAL);
            colorShape.setColor(color);
            colorShape.setStroke(2, Color.parseColor("#E2EAEB"));
            button.setBackground(colorShape);
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
        label.setTextSize(12);
        label.setTextColor(Color.parseColor("#55636A"));
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setPadding(0, dp(12), 0, dp(6));
        return label;
    }

    private SeekBar settingSeekBar(int max, int value) {
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max);
        seekBar.setProgress(Math.max(1, value));
        seekBar.setPadding(0, dp(2), 0, dp(6));
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
            showHomeChrome(false);
            prompterTopBar.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
            controlsPanel.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
            promptView.setPadding(dp(40), dp(112), dp(42), dp(200));
            promptView.setTextSize(Math.max(34, promptView.getTextSize() / getResources().getDisplayMetrics().scaledDensity));
            return;
        }

        showHomeChrome(!prompterMode);
        prompterTopBar.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
        controlsPanel.setVisibility(prompterMode ? View.VISIBLE : View.GONE);
        if (prompterMode) {
            prompterTopBar.bringToFront();
            controlsPanel.bringToFront();
        }
        statusView.setMinHeight(landscape ? dp(28) : dp(42));
        promptView.setPadding(
            landscape ? dp(34) : dp(22),
            landscape ? dp(90) : dp(118),
            landscape ? dp(38) : dp(22),
            landscape ? dp(190) : dp(260)
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

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(PREF_PRACTICE_COUNT, prefs.getInt(PREF_PRACTICE_COUNT, 0) + 1).apply();
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

    private void startVoskRecognition() {
        android.util.Log.d("VoiceTeleprompter", "startVoskRecognition: voskRunning=" + voskRunning);
        if (voskRunning) {
            android.util.Log.d("VoiceTeleprompter", "startVoskRecognition: already running, return");
            return;
        }
        try {
            String[] files = getAssets().list(VOSK_MODEL_ASSET_DIR);
            android.util.Log.d("VoiceTeleprompter", "startVoskRecognition: assets list = " + (files == null ? "null" : java.util.Arrays.toString(files)));
            if (files == null || files.length == 0) {
                statusView.setText(R.string.status_offline_model_missing);
                return;
            }
        } catch (IOException e) {
            android.util.Log.d("VoiceTeleprompter", "startVoskRecognition: IOException listing assets", e);
            statusView.setText(R.string.status_offline_model_missing);
            return;
        }

        statusView.setText(R.string.status_offline_starting);
        startButton.setEnabled(false);
        pauseButton.setEnabled(true);

        realtimeFinalText = "";
        targetReadIndex = readIndex;
        lastProgressAt = System.currentTimeMillis();
        activeRealtimeProvider = "vosk";
        realtimeStreaming = true;

        if (voskModel != null) {
            beginVoskListening();
            return;
        }

        new Thread(() -> {
            try {
                File outDir = new File(getFilesDir(), VOSK_MODEL_ASSET_DIR);
                android.util.Log.d("VoiceTeleprompter", "startVoskRecognition: copying assets to " + outDir.getAbsolutePath());
                copyAssetFolder(VOSK_MODEL_ASSET_DIR, outDir.getAbsolutePath());
                android.util.Log.d("VoiceTeleprompter", "startVoskRecognition: loading Model...");
                voskModel = new Model(outDir.getAbsolutePath());
                android.util.Log.d("VoiceTeleprompter", "startVoskRecognition: Model loaded, begin listening");
                runOnUiThread(this::beginVoskListening);
            } catch (Exception ex) {
                android.util.Log.e("VoiceTeleprompter", "startVoskRecognition: failed", ex);
                runOnUiThread(() -> {
                    statusView.setText(getString(R.string.status_offline_failed) + ex.getMessage());
                    realtimeStreaming = false;
                    voskRunning = false;
                    startButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                });
            }
        }, "vosk-model-loader").start();
    }

    private void copyAssetFolder(String assetPath, String dstPath) throws IOException {
        String[] children = getAssets().list(assetPath);
        // children 为 null 说明 assetPath 是文件而非目录
        if (children == null) {
            copyAssetFile(assetPath, dstPath);
            return;
        }
        File dir = new File(dstPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("无法创建目录：" + dstPath);
        }
        if (children.length == 0) {
            // 空目录或异常情况，按文件尝试拷贝
            copyAssetFile(assetPath, dstPath);
            return;
        }
        for (String child : children) {
            String src = assetPath + "/" + child;
            String dst = dstPath + "/" + child;
            String[] sub = getAssets().list(src);
            if (sub != null && sub.length > 0) {
                // 子目录：递归
                copyAssetFolder(src, dst);
            } else {
                // 文件：直接拷贝
                copyAssetFile(src, dst);
            }
        }
    }

    private void copyAssetFile(String assetPath, String dstPath) throws IOException {
        File dst = new File(dstPath);
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建目录：" + parent.getAbsolutePath());
        }
        if (dst.exists()) {
            return;
        }
        try (InputStream in = getAssets().open(assetPath);
             FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
        }
    }

    private void beginVoskListening() {
        if (voskModel == null) {
            statusView.setText(R.string.status_offline_failed);
            realtimeStreaming = false;
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
            return;
        }
        try {
            Recognizer recognizer = new Recognizer(voskModel, 16000.0f);
            voskSpeechService = new SpeechService(recognizer, 16000.0f);
            voskSpeechService.startListening(new org.vosk.android.RecognitionListener() {
                @Override
                public void onPartialResult(String s) {
                    String text = parseVoskJson(s, "partial");
                    if (!text.isEmpty()) {
                        runOnUiThread(() -> updateRealtimeTranscript(text));
                    }
                }

                @Override
                public void onResult(String s) {
                    String text = parseVoskJson(s, "text");
                    if (!text.isEmpty()) {
                        runOnUiThread(() -> updateRealtimeTranscript(text));
                    }
                }

                @Override
                public void onFinalResult(String s) {
                    String text = parseVoskJson(s, "text");
                    if (!text.isEmpty()) {
                        runOnUiThread(() -> updateRealtimeTranscript(text));
                    }
                }

                @Override
                public void onError(Exception e) {
                    runOnUiThread(() -> {
                        statusView.setText(getString(R.string.status_offline_failed) + e.getMessage());
                        voskRunning = false;
                        realtimeStreaming = false;
                        startButton.setEnabled(true);
                        pauseButton.setEnabled(false);
                    });
                }

                @Override
                public void onTimeout() {
                    runOnUiThread(() -> {
                        voskRunning = false;
                        realtimeStreaming = false;
                        startButton.setEnabled(true);
                        pauseButton.setEnabled(false);
                        statusView.setText(R.string.status_offline_stopped);
                    });
                }
            });
            voskRunning = true;
            statusView.setText(R.string.status_offline_listening);
        } catch (Exception e) {
            statusView.setText(getString(R.string.status_offline_failed) + e.getMessage());
            realtimeStreaming = false;
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        }
    }

    private String parseVoskJson(String json, String key) {
        try {
            JSONObject obj = new JSONObject(json);
            return obj.optString(key, "");
        } catch (Exception e) {
            return "";
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
        android.util.Log.d("VoiceTeleprompter", "startRealtimeRecognition: offlineMode=" + offlineMode + " realtimeStreaming=" + realtimeStreaming);
        if (realtimeStreaming) {
            android.util.Log.d("VoiceTeleprompter", "startRealtimeRecognition: already streaming, return");
            return;
        }
        if (offlineMode) {
            android.util.Log.d("VoiceTeleprompter", "startRealtimeRecognition: -> startVoskRecognition");
            startVoskRecognition();
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
        if (isBaiduProvider() && !getBaiduSecretKey().isEmpty()) {
            startCloudRecording();
            return;
        }
        if (!isBaiduProvider()) {
            showRealtimeNotice(getString(R.string.status_provider_not_supported));
            return;
        }
        String apiKey = getBaiduApiKey();
        String appId = getBaiduAppId();
        if (apiKey.isEmpty() || appId.isEmpty()) {
            showRealtimeNotice(getString(R.string.status_enter_realtime_keys));
            return;
        }

        int appIdNumber;
        try {
            appIdNumber = Integer.parseInt(appId);
        } catch (NumberFormatException error) {
            showRealtimeNotice(getString(R.string.status_realtime_failed) + "AppID must be numeric.");
            return;
        }

        realtimeFinalText = "";
        lastRealtimeErrorMessage = "";
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
                    data.put("dev_pid", BAIDU_REALTIME_DEV_PID);
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
                handleRealtimeFailure(throwable, response);
                cleanupRealtime();
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                cleanupRealtime();
                closeWebSocketSafely(webSocket, code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                handleRealtimeClosed(code, reason);
                cleanupRealtime();
            }
        });
    }

    private void startTencentRealtimeRecognition() {
        if (savedTencentAppId.isEmpty() || savedTencentSecretId.isEmpty() || savedTencentSecretKey.isEmpty()) {
            showRealtimeNotice("请先在设置中填写腾讯云 AppID、SecretId 和 SecretKey。");
            return;
        }

        realtimeFinalText = "";
        lastRealtimeErrorMessage = "";
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
                    handleRealtimeFailure(throwable, response);
                    cleanupRealtime();
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    cleanupRealtime();
                    closeWebSocketSafely(webSocket, code, reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    handleRealtimeClosed(code, reason);
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
        savedAliyunAppKey = savedAliyunAppKey.trim();
        savedAliyunAccessKeyId = savedAliyunAccessKeyId.trim();
        savedAliyunAccessKeySecret = savedAliyunAccessKeySecret.trim();
        if (savedAliyunAppKey.isEmpty() || savedAliyunAccessKeyId.isEmpty() || savedAliyunAccessKeySecret.isEmpty()) {
            showRealtimeNotice("请先在设置中填写阿里云 AppKey、AccessKey ID 和 AccessKey Secret。");
            return;
        }

        realtimeFinalText = "";
        lastRealtimeErrorMessage = "";
        targetReadIndex = readIndex;
        lastProgressAt = System.currentTimeMillis();
        activeRealtimeProvider = PROVIDER_ALIYUN;
        activeAliyunTaskId = uuid32();
        realtimeStopRequested = false;
        realtimeClient = new OkHttpClient();
        statusView.setText("正在获取阿里云 Token。");
        startButton.setEnabled(false);
        pauseButton.setEnabled(true);

        new Thread(() -> {
            try {
                String token = getValidAliyunToken();
                runOnUiThread(() -> openAliyunRealtimeWebSocket(token));
            } catch (Exception error) {
                String message = getString(R.string.status_realtime_failed) + "获取阿里云 Token 失败：" + error.getMessage();
                runOnUiThread(() -> {
                    showRealtimeNotice(message);
                    startButton.setEnabled(true);
                    pauseButton.setEnabled(false);
                });
                cleanupRealtime();
            }
        }).start();
    }

    private void openAliyunRealtimeWebSocket(String token) {
        try {
            Request request = new Request.Builder()
                .url(buildAliyunRealtimeUrl(token))
                .build();
            statusView.setText(R.string.status_realtime_starting);
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
                    handleRealtimeFailure(throwable, response);
                    cleanupRealtime();
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    cleanupRealtime();
                    closeWebSocketSafely(webSocket, code, reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    handleRealtimeClosed(code, reason);
                    cleanupRealtime();
                }
            });
        } catch (Exception error) {
            showRealtimeNotice(getString(R.string.status_realtime_failed) + error.getMessage());
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

    private String buildAliyunRealtimeUrl(String token) throws Exception {
        return ALIYUN_NLS_ENDPOINT + "?token=" + URLEncoder.encode(token, "UTF-8");
    }

    private String getValidAliyunToken() throws Exception {
        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (!cachedAliyunToken.isEmpty() && cachedAliyunTokenExpireTime - nowSeconds > 3600L) {
            return cachedAliyunToken;
        }
        JSONObject tokenObject = fetchAliyunToken();
        cachedAliyunToken = tokenObject.getString("Id");
        cachedAliyunTokenExpireTime = tokenObject.optLong("ExpireTime", 0L);
        if (cachedAliyunToken.isEmpty() || cachedAliyunTokenExpireTime <= nowSeconds) {
            throw new Exception("Token 返回为空或已过期。");
        }
        return cachedAliyunToken;
    }

    private JSONObject fetchAliyunToken() throws Exception {
        TreeMap<String, String> params = new TreeMap<String, String>();
        params.put("AccessKeyId", savedAliyunAccessKeyId);
        params.put("Action", "CreateToken");
        params.put("Format", "JSON");
        params.put("RegionId", "cn-shanghai");
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("SignatureVersion", "1.0");
        params.put("Timestamp", aliyunTimestamp());
        params.put("Version", "2019-02-28");

        String canonicalizedQuery = buildAliyunCanonicalizedQuery(params);
        String stringToSign = "GET&" + aliyunPercentEncode("/") + "&" + aliyunPercentEncode(canonicalizedQuery);
        String signature = hmacSha1Base64(stringToSign, savedAliyunAccessKeySecret + "&");
        String url = ALIYUN_TOKEN_ENDPOINT + "?Signature=" + aliyunPercentEncode(signature) + "&" + canonicalizedQuery;
        String response = getText(url);
        JSONObject json = new JSONObject(response);
        JSONObject token = json.optJSONObject("Token");
        if (token == null) {
            String code = json.optString("Code");
            String message = json.optString("Message", json.optString("ErrMsg", response));
            throw new Exception((code.isEmpty() ? "" : code + "：") + message);
        }
        return token;
    }

    private String aliyunTimestamp() {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(new SimpleTimeZone(0, "GMT"));
        return format.format(new Date());
    }

    private String buildAliyunCanonicalizedQuery(TreeMap<String, String> params) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder
                .append(aliyunPercentEncode(entry.getKey()))
                .append("=")
                .append(aliyunPercentEncode(entry.getValue()));
        }
        return builder.toString();
    }

    private String aliyunPercentEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~");
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
                payload.put("enable_ignore_sentence_timeout", true);
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

    private void handleRealtimeFailure(Throwable throwable, Response response) {
        String detail = buildWebSocketFailureDetail(throwable, response);
        lastRealtimeErrorMessage = buildRealtimeDisconnectMessage(detail);
        Log.e(TAG, "Realtime failure: " + detail, throwable);
        runOnUiThread(() -> {
            showRealtimeNotice(lastRealtimeErrorMessage);
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });
    }

    private void handleRealtimeClosed(int code, String reason) {
        runOnUiThread(() -> {
            if (realtimeStopRequested) {
                statusView.setText(R.string.status_realtime_stopped);
            } else if (lastRealtimeErrorMessage != null && !lastRealtimeErrorMessage.isEmpty()) {
                showRealtimeNotice(lastRealtimeErrorMessage);
            } else {
                String message = buildRealtimeClosedMessage(code, reason);
                Log.w(TAG, "Realtime closed: code=" + code + ", reason=" + reason);
                showRealtimeNotice(message);
            }
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });
    }

    private void closeWebSocketSafely(WebSocket webSocket, int code, String reason) {
        int safeCode = isReservedWebSocketCloseCode(code) ? 1000 : code;
        try {
            webSocket.close(safeCode, reason);
        } catch (IllegalArgumentException error) {
            Log.w(TAG, "Fallback close for invalid websocket code=" + code + ", reason=" + reason, error);
            webSocket.close(1000, null);
        }
    }

    private boolean isReservedWebSocketCloseCode(int code) {
        return code == 1005 || code == 1006 || code == 1015;
    }

    private String buildWebSocketFailureDetail(Throwable throwable, Response response) {
        StringBuilder detail = new StringBuilder();
        if (response != null) {
            detail.append("HTTP ").append(response.code());
            if (response.message() != null && !response.message().trim().isEmpty()) {
                detail.append(" ").append(response.message());
            }
        }
        if (throwable != null) {
            if (detail.length() > 0) {
                detail.append("；");
            }
            detail.append(throwable.getClass().getSimpleName());
            if (throwable.getMessage() != null && !throwable.getMessage().trim().isEmpty()) {
                detail.append("：").append(throwable.getMessage());
            }
        }
        return detail.length() == 0 ? "连接被中断" : detail.toString();
    }

    private void showRealtimeServiceError(String detail) {
        realtimeStreaming = false;
        lastRealtimeErrorMessage = buildRealtimeDisconnectMessage(detail);
        Log.w(TAG, "Realtime service error: " + detail);
        runOnUiThread(() -> {
            showRealtimeNotice(lastRealtimeErrorMessage);
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
        });
        cleanupRealtime();
    }

    private void showRealtimeNotice(String message) {
        statusView.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        if (!isFinishing()) {
            new AlertDialog.Builder(this)
                .setTitle("实时识别诊断")
                .setMessage(message)
                .setPositiveButton(R.string.dialog_close, null)
                .show();
        }
    }

    private void startRealtimeAudioLoop(WebSocket webSocket) {
        int sampleRate = 16000;
        int bytesPerSample = 2;
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
            long nextSendAt = SystemClock.elapsedRealtime();
            while (realtimeStreaming && audioRecord != null) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    long now = SystemClock.elapsedRealtime();
                    if (nextSendAt > now) {
                        SystemClock.sleep(nextSendAt - now);
                    }
                    boolean accepted = webSocket.send(okio.ByteString.of(buffer, 0, read));
                    if (!accepted) {
                        realtimeStreaming = false;
                    }
                    long frameDurationMs = Math.max(20, read * 1000L / (sampleRate * bytesPerSample));
                    nextSendAt = Math.max(SystemClock.elapsedRealtime(), nextSendAt) + frameDurationMs;
                }
            }
        });
        audioThread.start();
    }

    private void handleRealtimeMessage(String text) {
        Log.d(TAG, "Baidu realtime message: " + compact(text));
        try {
            JSONObject json = new JSONObject(text);
            int errNo = json.optInt("err_no", 0);
            if (errNo != 0) {
                showRealtimeServiceError(compact(text));
                return;
            }

            String type = json.optString("type");
            String result = extractRealtimeResult(json);
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

    private String extractRealtimeResult(JSONObject json) {
        Object resultValue = json.opt("result");
        if (resultValue instanceof JSONArray) {
            JSONArray resultArray = (JSONArray) resultValue;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < resultArray.length(); i++) {
                builder.append(resultArray.optString(i));
            }
            return builder.toString();
        }
        if (resultValue instanceof JSONObject) {
            JSONObject resultObject = (JSONObject) resultValue;
            String value = resultObject.optString("word");
            if (!value.isEmpty()) {
                return value;
            }
            return resultObject.optString("text");
        }
        return json.optString("result");
    }

    private void handleTencentRealtimeMessage(String text) {
        Log.d(TAG, "Tencent realtime message: " + compact(text));
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
        Log.d(TAG, "Aliyun realtime message: " + compact(text));
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
        // 离线 Vosk 识别的停止路径
        if (voskSpeechService != null) {
            try {
                voskSpeechService.stop();
            } catch (Exception ignored) {
            }
            voskSpeechService = null;
            voskRunning = false;
            startButton.setEnabled(true);
            pauseButton.setEnabled(false);
            targetReadIndex = readIndex;
            statusView.setText(R.string.status_offline_stopped);
            return;
        }
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

        // 第一优先级：精确文本匹配
        int maxLength = Math.min(normalizedTranscript.length(), 22);
        for (int length = maxLength; length >= 2; length--) {
            String snippet = normalizedTranscript.substring(normalizedTranscript.length() - length);
            int foundAt = forwardScript.indexOf(snippet);
            if (foundAt >= 0) {
                bestIndex = Math.max(bestIndex, searchStart + foundAt + length);
                break;
            }
        }

        // 第二优先级：拼音匹配（容错同音字识别错误，借鉴小白提词器）
        if (bestIndex == readIndex) {
            bestIndex = pinyinForwardProgress(normalizedTranscript, searchStart, searchEnd);
        }

        // 跨行匹配：当前匹配失败且ASR文本较长时，扩大搜索范围向前查找
        if (bestIndex == readIndex && normalizedTranscript.length() > 10) {
            int wideEnd = Math.min(normalizedScript.length(), readIndex + 160);
            bestIndex = pinyinForwardProgress(normalizedTranscript, searchStart, wideEnd);
        }

        // 单字兜底
        if (readIndex < normalizedScript.length()) {
            String lastChar = normalizedTranscript.substring(normalizedTranscript.length() - 1);
            if (normalizedScript.substring(readIndex).startsWith(lastChar)) {
                bestIndex = Math.max(bestIndex, readIndex + 1);
            }
        }
        return Math.min(bestIndex, normalizedScript.length());
    }

    /**
     * 拼音匹配：将识别文本与提词文本片段转拼音后计算相似度。
     * 灵敏度越高阈值越低（容错越强），借鉴小白提词器的拼音同步算法。
     */
    private int pinyinForwardProgress(String normalizedTranscript, int searchStart, int searchEnd) {
        int maxLen = Math.min(normalizedTranscript.length(), 14);
        if (maxLen < 3) {
            return readIndex;
        }
        String asrTail = normalizedTranscript.substring(normalizedTranscript.length() - maxLen);
        String asrPinyin = toPinyin(asrTail);
        if (asrPinyin.isEmpty()) {
            return readIndex;
        }
        // 灵敏度阈值：1档≈0.73（精准），5档≈0.45（高容错）
        float threshold = 0.80f - matchSensitivity * 0.07f;

        int bestEnd = readIndex;
        float bestScore = 0;
        for (int start = searchStart; start < searchEnd; start++) {
            int limit = Math.min(maxLen, searchEnd - start);
            if (limit < 3) break;
            String scriptSlice = normalizedScript.substring(start, start + limit);
            String scriptPinyin = toPinyin(scriptSlice);
            float score = pinyinSimilarity(asrPinyin, scriptPinyin);
            if (score > bestScore) {
                bestScore = score;
                bestEnd = start + limit;
            }
        }
        return bestScore >= threshold ? Math.max(readIndex, bestEnd) : readIndex;
    }

    /** 拼音输出格式（无声调），复用避免重复创建。 */
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();
    static {
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    /** 将中文文本转为小写拼音（音节间用空格分隔），非汉字保留原字符。 */
    private String toPinyin(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            String[] pinyinArray = null;
            try {
                pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, PINYIN_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination ignored) {
            }
            if (pinyinArray != null && pinyinArray.length > 0) {
                sb.append(pinyinArray[0].toLowerCase());
                sb.append(' ');
            } else if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString().trim();
    }

    /** 基于编辑距离计算两个拼音字符串的相似度（0~1）。 */
    private float pinyinSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0f;
        }
        int distance = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return 1.0f - (float) distance / maxLen;
    }

    /** 计算两个字符串的Levenshtein编辑距离。 */
    private int levenshteinDistance(String a, String b) {
        int m = a.length();
        int n = b.length();
        if (m == 0) return n;
        if (n == 0) return m;
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        for (int j = 0; j <= n; j++) prev[j] = j;
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[n];
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
        refreshHomeScriptCard();
        updatePrompterProgress();
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
            floatAfterPermission = false;
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
            return;
        }
        if (floatAfterPermission) {
            floatAfterPermission = false;
            startFloatPrompter();
        }
    }

    @Override
    protected void onDestroy() {
        shouldListen = false;
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (voskSpeechService != null) {
            try {
                voskSpeechService.stop();
            } catch (Exception ignored) {
            }
            voskSpeechService = null;
        }
        if (voskModel != null) {
            voskModel.close();
            voskModel = null;
        }
        super.onDestroy();
    }

    /**
     * 读取系统栏高度作为兜底。部分 ROM 在某些时机会把 inset 报成 0，
     * 光靠 WindowInsets 会让底部按钮压在导航栏下面。
     */
    private int systemBarHeight(String resourceName) {
        int id = getResources().getIdentifier(resourceName, "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : 0;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}


