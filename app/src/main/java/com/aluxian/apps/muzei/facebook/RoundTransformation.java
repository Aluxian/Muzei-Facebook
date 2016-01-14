package com.aluxian.apps.muzei.facebook;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Locale;

/**
 * Transformation class for Picasso to round an image's corners
 */
public class RoundTransformation implements com.squareup.picasso.Transformation {

    private int radius; // Corner radii in dp

    public RoundTransformation(int radius) {
        this.radius = radius;
    }

    @Override
    public Bitmap transform(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();

        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(0, 0, width, height), radius, radius, paint);

        if (output != source) {
            source.recycle();
        }

        return output;
    }

    @Override
    public String key() {
        return String.format(Locale.US, "round(%d)", radius);
    }

}
