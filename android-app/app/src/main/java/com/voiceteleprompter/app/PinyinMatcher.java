package com.voiceteleprompter.app;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.Locale;

/**
 * 拼音匹配工具类（借鉴小白提词器的拼音同步算法）。
 * 提供文本归一化、拼音转换、相似度计算与跟读进度匹配，供 MainActivity 和悬浮窗服务复用。
 */
public class PinyinMatcher {

    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();
    static {
        PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
    }

    /** 文本归一化：转小写并去除标点空白。 */
    public static String normalizeText(String value) {
        return value.toLowerCase(Locale.CHINA).replaceAll("[，。！？、；：“”‘’《》（）【】,.!?;:\"'()\\[\\]\\s]", "");
    }

    /** 将中文文本转为小写拼音（音节间用空格分隔），非汉字保留原字符。 */
    public static String toPinyin(String text) {
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
    public static float pinyinSimilarity(String a, String b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0f;
        }
        int distance = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return 1.0f - (float) distance / maxLen;
    }

    /** 计算两个字符串的Levenshtein编辑距离。 */
    public static int levenshteinDistance(String a, String b) {
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

    /**
     * 拼音匹配：将识别文本与提词文本片段转拼音后计算相似度。
     * 灵敏度越高阈值越低（容错越强）。
     *
     * @param normalizedTranscript 归一化后的ASR识别文本
     * @param normalizedScript     归一化后的完整提词文本
     * @param searchStart          搜索起始位置
     * @param searchEnd            搜索结束位置
     * @param readIndex            当前已读位置
     * @param matchSensitivity     灵敏度（1-5）
     * @return 匹配后的新进度位置，未达阈值返回 readIndex
     */
    public static int pinyinForwardProgress(String normalizedTranscript, String normalizedScript,
                                            int searchStart, int searchEnd, int readIndex, int matchSensitivity) {
        int maxLen = Math.min(normalizedTranscript.length(), 14);
        if (maxLen < 3) {
            return readIndex;
        }
        String asrTail = normalizedTranscript.substring(normalizedTranscript.length() - maxLen);
        String asrPinyin = toPinyin(asrTail);
        if (asrPinyin.isEmpty()) {
            return readIndex;
        }
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

    /**
     * 综合匹配进度（三级：精确匹配 → 拼音匹配 → 跨行匹配 → 单字兜底）。
     *
     * @param transcript         ASR识别原文
     * @param normalizedScript   归一化后的完整提词文本
     * @param readIndex          当前已读位置
     * @param matchSensitivity   灵敏度（1-5）
     * @return 新的进度位置
     */
    public static int findBestProgress(String transcript, String normalizedScript, int readIndex, int matchSensitivity) {
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

        // 第二优先级：拼音匹配
        if (bestIndex == readIndex) {
            bestIndex = pinyinForwardProgress(normalizedTranscript, normalizedScript, searchStart, searchEnd, readIndex, matchSensitivity);
        }

        // 跨行匹配
        if (bestIndex == readIndex && normalizedTranscript.length() > 10) {
            int wideEnd = Math.min(normalizedScript.length(), readIndex + 160);
            bestIndex = pinyinForwardProgress(normalizedTranscript, normalizedScript, searchStart, wideEnd, readIndex, matchSensitivity);
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
}
