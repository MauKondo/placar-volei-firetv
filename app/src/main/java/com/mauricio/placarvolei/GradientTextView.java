package com.mauricio.placarvolei;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.content.ContextCompat;

/**
 * TextView com gradient vertical do texto + glow do accent.
 * Usado pra nome do vencedor na tela de vitória.
 */
public class GradientTextView extends AppCompatTextView {

    public GradientTextView(Context context) {
        super(context);
        init();
    }

    public GradientTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GradientTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        int accent = ContextCompat.getColor(getContext(), R.color.accent);
        // glow ao redor do texto
        setShadowLayer(40f, 0f, 0f, accent);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        applyGradient(h);
    }

    private void applyGradient(int height) {
        if (height <= 0) return;
        int top = ContextCompat.getColor(getContext(), R.color.fg_primary);
        int bottom = ContextCompat.getColor(getContext(), R.color.accent);
        // top->bottom: branco no topo, accent embaixo. Stops 0/0.45/1.0
        Shader shader = new LinearGradient(
                0f, 0f, 0f, height,
                new int[]{ top, top, bottom },
                new float[]{ 0f, 0.45f, 1f },
                Shader.TileMode.CLAMP);
        getPaint().setShader(shader);
        invalidate();
    }
}
