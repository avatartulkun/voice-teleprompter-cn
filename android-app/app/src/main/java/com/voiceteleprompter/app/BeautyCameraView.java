package com.voiceteleprompter.app;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * 美颜相机视图。
 * <p>基于 OpenGL ES 2.0 的 {@link GLSurfaceView}，持有 {@link BeautyCameraRenderer}，
 * 持续渲染以保证相机预览实时美颜。</p>
 */
public class BeautyCameraView extends GLSurfaceView {

    private final BeautyCameraRenderer mRenderer;

    public BeautyCameraView(Context context) {
        this(context, null);
    }

    public BeautyCameraView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // 使用 OpenGL ES 2.0
        setEGLContextClientVersion(2);
        mRenderer = new BeautyCameraRenderer();
        setRenderer(mRenderer);
        // 持续渲染，保证相机预览实时美颜
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    }

    /**
     * 设置美颜参数（代理给 renderer）。
     *
     * @param smooth 磨皮等级 0.0~1.0
     * @param whiten 美白等级 0.0~1.0
     * @param ruddy  红润等级 0.0~1.0
     */
    public void setBeautyParams(float smooth, float whiten, float ruddy) {
        mRenderer.setBeautyParams(smooth, whiten, ruddy);
    }

    /**
     * 获取用于 CameraX Preview 的 SurfaceTexture（阻塞直到 renderer 就绪）。
     */
    public SurfaceTexture getSurfaceTexture() {
        return mRenderer.getSurfaceTexture();
    }

    /**
     * 设置是否镜像（前置摄像头镜像）。
     */
    public void setMirror(boolean mirror) {
        mRenderer.setMirror(mirror);
    }

    /**
     * 设置 CameraX 给出的画面旋转角度。
     * <p>方法名不能叫 setRotation，那会覆盖 {@link android.view.View#setRotation(float)}。</p>
     */
    public void setCameraRotation(int degrees) {
        mRenderer.setRotationDegrees(degrees);
    }

    /** 设置相机输出缓冲区尺寸，供等比裁剪使用。 */
    public void setBufferSize(int width, int height) {
        mRenderer.setBufferSize(width, height);
    }
}
