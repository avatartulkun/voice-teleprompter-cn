package com.voiceteleprompter.app;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 美颜相机渲染器。
 * <p>接收 CameraX 预览输出的 OES 纹理（通过 {@link SurfaceTexture}），将相机画面渲染到屏幕，
 * 并应用美颜着色器（磨皮 / 美白 / 红润，参数范围 0.0~1.0）。</p>
 */
public class BeautyCameraRenderer implements GLSurfaceView.Renderer {

    // 顶点坐标：全屏四边形（裁剪空间 -1~1），使用 TRIANGLE_STRIP 绘制
    private static final float[] VERTEX = {
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f,
    };

    // 纹理坐标（与顶点一一对应）
    private static final float[] TEXTURE_COORD = {
            0.0f, 0.0f,
            1.0f, 0.0f,
            0.0f, 1.0f,
            1.0f, 1.0f,
    };

    /** 顶点着色器：标准位置变换 + 传递纹理坐标。 */
    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
            "uniform mat4 uSTMatrix;\n" +
            "attribute vec4 aPosition;\n" +
            "attribute vec4 aTextureCoord;\n" +
            "varying vec2 vTextureCoord;\n" +
            "void main() {\n" +
            "    gl_Position = uMVPMatrix * aPosition;\n" +
            "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
            "}\n";

    /** 片段着色器：OES 纹理采样 + 美白 + 红润 + 磨皮（基于亮度差的加权模糊保留边缘）。 */
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "varying vec2 vTextureCoord;\n" +
            "uniform samplerExternalOES sTexture;\n" +
            "uniform float uSmooth;\n" +
            "uniform float uWhiten;\n" +
            "uniform float uRuddy;\n" +
            "uniform float uTexelWidth;\n" +
            "uniform float uTexelHeight;\n" +
            "const vec3 LUM_COEF = vec3(0.299, 0.587, 0.114);\n" +
            "void main() {\n" +
            "    vec4 color = texture2D(sTexture, vTextureCoord);\n" +
            // 美白：提升整体亮度，并降低蓝色通道
            "    color.rgb = color.rgb * (1.0 + uWhiten * 0.35);\n" +
            "    color.b = color.b * (1.0 - uWhiten * 0.18);\n" +
            // 红润：增加红色通道
            "    color.r = color.r * (1.0 + uRuddy * 0.22);\n" +
            // 磨皮：3x3 邻域加权模糊，权重根据亮度差决定以保留边缘（双边滤波思路）
            "    float centerLum = dot(color.rgb, LUM_COEF);\n" +
            "    vec3 sum = vec3(0.0);\n" +
            "    float totalWeight = 0.0;\n" +
            "    for (int j = -1; j <= 1; j++) {\n" +
            "        for (int i = -1; i <= 1; i++) {\n" +
            "            vec2 offset = vec2(float(i) * uTexelWidth, float(j) * uTexelHeight);\n" +
            "            vec3 s = texture2D(sTexture, vTextureCoord + offset).rgb;\n" +
            "            float sl = dot(s, LUM_COEF);\n" +
            "            float d = centerLum - sl;\n" +
            "            float w = exp(-d * d * 6.0);\n" +
            "            if (i == 0 && j == 0) { w = 1.0; }\n" +
            "            sum += s * w;\n" +
            "            totalWeight += w;\n" +
            "        }\n" +
            "    }\n" +
            "    vec3 blurred = sum / totalWeight;\n" +
            // 对亮区模糊保留暗区边缘：以中心亮度作为混合系数
            "    color.rgb = mix(color.rgb, blurred, uSmooth * clamp(centerLum, 0.0, 1.0));\n" +
            "    gl_FragColor = color;\n" +
            "}\n";

    private final FloatBuffer mVertexBuffer;
    private final FloatBuffer mTextureCoordBuffer;

    // 用于阻塞等待 SurfaceTexture 就绪（GL 线程创建好后才可提供给 CameraX）
    private final CountDownLatch mSurfaceTextureReady = new CountDownLatch(1);
    private volatile SurfaceTexture mSurfaceTexture;
    private volatile int mTextureId = -1;

    private int mProgram;
    private int mAttribPosition;
    private int mAttribTextureCoord;
    private int mUniformMVPMatrix;
    private int mUniformSTMatrix;
    private int mUniformTexture;
    private int mUniformSmooth;
    private int mUniformWhiten;
    private int mUniformRuddy;
    private int mUniformTexelWidth;
    private int mUniformTexelHeight;

    private final float[] mMVPMatrix = new float[16];
    private final float[] mSTMatrix = new float[16];
    private final float[] mTempMatrix = new float[16];

    // 美颜参数（volatile 保证主线程写入对 GL 线程可见）
    private volatile float mSmoothLevel = 0.0f;
    private volatile float mWhitenLevel = 0.0f;
    private volatile float mRuddyLevel = 0.0f;

    // 前置摄像头镜像
    private volatile boolean mMirror = false;

    // CameraX 通过 SurfaceRequest 给的是传感器方向的原始帧，
    // SurfaceTexture 的变换矩阵只含裁剪和垂直翻转，旋转要在这里自己补。
    private volatile int mRotationDegrees = 0;
    private volatile int mBufferWidth = 0;
    private volatile int mBufferHeight = 0;

    private int mViewWidth = 0;
    private int mViewHeight = 0;

    public BeautyCameraRenderer() {
        mVertexBuffer = toFloatBuffer(VERTEX);
        mTextureCoordBuffer = toFloatBuffer(TEXTURE_COORD);
        Matrix.setIdentityM(mMVPMatrix, 0);
        Matrix.setIdentityM(mSTMatrix, 0);
    }

    private static FloatBuffer toFloatBuffer(float[] array) {
        ByteBuffer bb = ByteBuffer.allocateDirect(array.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(array);
        fb.position(0);
        return fb;
    }

    /**
     * 设置美颜参数（线程安全，可在主线程调用）。
     *
     * @param smooth 磨皮等级 0.0~1.0
     * @param whiten 美白等级 0.0~1.0
     * @param ruddy  红润等级 0.0~1.0
     */
    /**
     * 把「显示坐标」变换到「采样坐标」：镜像 + 等比裁剪，再乘以 SurfaceTexture 自带的矩阵。
     * <p>旋转不在这里做：Preview 设了 targetRotation 之后，CameraX 已经把画面旋转写进了
     * SurfaceTexture 的变换矩阵，这里再转一次就会重复旋转。mRotationDegrees 只用来判断
     * 缓冲区宽高是否需要对调，供裁剪计算使用。</p>
     */
    private void applyDisplayTransform() {
        float[] transform = new float[16];
        Matrix.setIdentityM(transform, 0);

        // 镜像（前置摄像头），作用于显示坐标
        if (mMirror) {
            float[] mirror = new float[16];
            Matrix.setIdentityM(mirror, 0);
            mirror[0] = -1.0f;
            mirror[12] = 1.0f;
            Matrix.multiplyMM(mTempMatrix, 0, transform, 0, mirror, 0);
            System.arraycopy(mTempMatrix, 0, transform, 0, 16);
        }

        // 等比裁剪：画面被旋转 90/270 度时缓冲区宽高要对调，否则会被拉伸
        float[] crop = buildCropMatrix();
        if (crop != null) {
            Matrix.multiplyMM(mTempMatrix, 0, transform, 0, crop, 0);
            System.arraycopy(mTempMatrix, 0, transform, 0, 16);
        }

        Matrix.multiplyMM(mTempMatrix, 0, mSTMatrix, 0, transform, 0);
        System.arraycopy(mTempMatrix, 0, mSTMatrix, 0, 16);
    }

    private float[] buildCropMatrix() {
        if (mBufferWidth <= 0 || mBufferHeight <= 0 || mViewWidth <= 0 || mViewHeight <= 0) {
            return null;
        }
        boolean swapped = mRotationDegrees == 90 || mRotationDegrees == 270;
        float bufferW = swapped ? mBufferHeight : mBufferWidth;
        float bufferH = swapped ? mBufferWidth : mBufferHeight;
        float viewAspect = (float) mViewWidth / mViewHeight;
        float bufferAspect = bufferW / bufferH;

        float scaleX = 1f;
        float scaleY = 1f;
        if (bufferAspect > viewAspect) {
            scaleX = viewAspect / bufferAspect;   // 画面更宽，裁掉左右
        } else {
            scaleY = bufferAspect / viewAspect;   // 画面更高，裁掉上下
        }

        float[] crop = new float[16];
        Matrix.setIdentityM(crop, 0);
        Matrix.translateM(crop, 0, 0.5f, 0.5f, 0f);
        Matrix.scaleM(crop, 0, scaleX, scaleY, 1f);
        Matrix.translateM(crop, 0, -0.5f, -0.5f, 0f);
        return crop;
    }

    /** 设置 CameraX 给出的旋转角度（TransformationInfo.getRotationDegrees）。 */
    public void setRotationDegrees(int degrees) {
        mRotationDegrees = ((degrees % 360) + 360) % 360;
    }

    /** 设置相机输出缓冲区尺寸，用于等比裁剪。 */
    public void setBufferSize(int width, int height) {
        mBufferWidth = width;
        mBufferHeight = height;
    }

    public void setBeautyParams(float smooth, float whiten, float ruddy) {
        mSmoothLevel = clamp01(smooth);
        mWhitenLevel = clamp01(whiten);
        mRuddyLevel = clamp01(ruddy);
    }

    /**
     * 设置是否镜像（前置摄像头需要镜像）。
     */
    public void setMirror(boolean mirror) {
        mMirror = mirror;
    }

    /**
     * 获取用于 CameraX Preview 的 SurfaceTexture。
     * <p>会阻塞调用线程直到 GL 线程创建好 OES 纹理与 SurfaceTexture。</p>
     */
    public SurfaceTexture getSurfaceTexture() {
        try {
            mSurfaceTextureReady.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return mSurfaceTexture;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // 创建 OES 纹理
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        mTextureId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        // 创建 SurfaceTexture 供 CameraX 输出，并设置默认缓冲区尺寸
        mSurfaceTexture = new SurfaceTexture(mTextureId);
        mSurfaceTexture.setDefaultBufferSize(1280, 720);
        mSurfaceTextureReady.countDown();

        // 编译并链接着色器程序
        mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
        mAttribPosition = GLES20.glGetAttribLocation(mProgram, "aPosition");
        mAttribTextureCoord = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        mUniformMVPMatrix = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        mUniformSTMatrix = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        mUniformTexture = GLES20.glGetUniformLocation(mProgram, "sTexture");
        mUniformSmooth = GLES20.glGetUniformLocation(mProgram, "uSmooth");
        mUniformWhiten = GLES20.glGetUniformLocation(mProgram, "uWhiten");
        mUniformRuddy = GLES20.glGetUniformLocation(mProgram, "uRuddy");
        mUniformTexelWidth = GLES20.glGetUniformLocation(mProgram, "uTexelWidth");
        mUniformTexelHeight = GLES20.glGetUniformLocation(mProgram, "uTexelHeight");

        Matrix.setIdentityM(mMVPMatrix, 0);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        mViewWidth = width;
        mViewHeight = height;
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        if (mSurfaceTexture == null) {
            return;
        }
        // 更新最新一帧到 OES 纹理
        mSurfaceTexture.updateTexImage();
        mSurfaceTexture.getTransformMatrix(mSTMatrix);

        applyDisplayTransform();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glUseProgram(mProgram);

        // 激活并绑定 OES 纹理
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
        GLES20.glUniform1i(mUniformTexture, 0);

        // 美颜参数
        GLES20.glUniform1f(mUniformSmooth, mSmoothLevel);
        GLES20.glUniform1f(mUniformWhiten, mWhitenLevel);
        GLES20.glUniform1f(mUniformRuddy, mRuddyLevel);

        // 纹素大小（用于磨皮采样步长，用视图尺寸近似）
        int texW = mViewWidth > 0 ? mViewWidth : 1280;
        int texH = mViewHeight > 0 ? mViewHeight : 720;
        GLES20.glUniform1f(mUniformTexelWidth, 1.0f / texW);
        GLES20.glUniform1f(mUniformTexelHeight, 1.0f / texH);

        // 变换矩阵
        GLES20.glUniformMatrix4fv(mUniformMVPMatrix, 1, false, mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(mUniformSTMatrix, 1, false, mSTMatrix, 0);

        // 顶点坐标
        mVertexBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mAttribPosition);
        GLES20.glVertexAttribPointer(mAttribPosition, 2, GLES20.GL_FLOAT, false, 8, mVertexBuffer);

        // 纹理坐标
        mTextureCoordBuffer.position(0);
        GLES20.glEnableVertexAttribArray(mAttribTextureCoord);
        GLES20.glVertexAttribPointer(mAttribTextureCoord, 2, GLES20.GL_FLOAT, false, 8, mTextureCoordBuffer);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(mAttribPosition);
        GLES20.glDisableVertexAttribArray(mAttribTextureCoord);
    }

    private static float clamp01(float v) {
        if (v < 0.0f) return 0.0f;
        if (v > 1.0f) return 1.0f;
        return v;
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            throw new RuntimeException("链接着色器程序失败: " + GLES20.glGetProgramInfoLog(program));
        }
        return program;
    }

    private static int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] != GLES20.GL_TRUE) {
            throw new RuntimeException("编译着色器失败: " + GLES20.glGetShaderInfoLog(shader));
        }
        return shader;
    }
}
