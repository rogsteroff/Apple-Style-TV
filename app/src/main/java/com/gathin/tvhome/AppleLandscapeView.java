package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/**
 * Original, locally rendered twilight landscape for Apple Style TV.
 * No downloaded art, third-party images, permissions or network required.
 * The scenery scales to any 16:9 television and stays behind the real controls.
 */
public final class AppleLandscapeView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final int W = 1920;
    private static final int H = 1080;

    public AppleLandscapeView(Context context) { this(context, null); }
    public AppleLandscapeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int save = canvas.save();
        canvas.scale(getWidth() / (float) W, getHeight() / (float) H);
        drawSky(canvas);
        drawStars(canvas);
        drawMountains(canvas);
        drawLake(canvas);
        drawForest(canvas);
        drawEdgeShadows(canvas);
        canvas.restoreToCount(save);
    }

    private void fill(Canvas c, int color) {
        paint.setShader(null);
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        c.drawRect(0, 0, W, H, paint);
    }

    private void drawSky(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, 740,
                new int[]{0xFF070F31, 0xFF292354, 0xFF693F78,
                        0xFFCB688C, 0xFFFFA46D, 0xFF4F466B},
                new float[]{0f, .22f, .45f, .68f, .86f, 1f},
                Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, 740, paint);
        paint.setShader(new RadialGradient(1490, 432, 680,
                new int[]{0x8EFFA66A, 0x31F28DA7, 0x00F28DA7},
                null, Shader.TileMode.CLAMP));
        c.drawRect(740, 0, 0, 0, paint); // intentionally no-op: avoids a secondary bitmap
        c.drawRect(660, 0, W, 720, paint);
        paint.setShader(null);
        // Sunset clouds: long translucent drifting bands.
        Random random = new Random(70);
        for (int i = 0; i < 47; i++) {
            float x = random.nextInt(2100) - 150;
            float y = 62 + random.nextInt(365);
            float rw = 90 + random.nextInt(360);
            float rh = 4 + random.nextInt(12);
            paint.setColor(i % 3 == 0 ? 0x2A091B42 : 0x254D274B);
            c.drawOval(x, y, x + rw, y + rh, paint);
        }
    }

    private void drawStars(Canvas c) {
        Random random = new Random(102);
        for (int i = 0; i < 75; i++) {
            float x = random.nextInt(W);
            float y = random.nextInt(300);
            float radius = .55f + random.nextFloat() * 1.2f;
            paint.setColor((i % 4 == 0 ? 0x88FFFFFF : 0x42D0E7FF));
            c.drawCircle(x, y, radius, paint);
        }
        paint.setShader(null);
    }

    private void drawMountains(Canvas c) {
        // Distant violet ridges.
        ridge(c, new float[]{0, 420, 230, 375, 390, 425, 535, 338, 720, 423,
                860, 308, 1025, 416, 1160, 354, 1400, 426, 1550, 365, 1920, 438},
                660, 0xFF4C426A, 0xFF262C55);
        ridge(c, new float[]{0, 522, 190, 449, 320, 527, 540, 335, 700, 526,
                890, 270, 1010, 408, 1150, 482, 1300, 343, 1520, 516,
                1720, 417, 1920, 497}, 738, 0xFF35335E, 0xFF14264A);
        // Sparse snow caps for a crisp mountain silhouette.
        snow(c, new float[]{827, 337, 890, 270, 946, 332, 916, 318, 897, 304,
                884, 317, 868, 300}, 0xC2C7C4EE);
        snow(c, new float[]{497, 389, 540, 335, 583, 410, 555, 387, 538, 371,
                521, 386}, 0xA7C1C1DF);
        snow(c, new float[]{1256, 395, 1300, 343, 1354, 405, 1326, 389,
                1307, 372, 1293, 380}, 0x9ACED3EF);
        // Shoreline to frame the lake.
        ridge(c, new float[]{0, 615, 245, 562, 410, 595, 665, 570, 810, 637,
                1010, 597, 1240, 612, 1520, 568, 1720, 607, 1920, 573},
                750, 0xFF162844, 0xFF08172E);
    }

    private void ridge(Canvas c, float[] points, int bottom, int top, int lower) {
        Path p = new Path();
        p.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
        p.lineTo(W, bottom);
        p.lineTo(0, bottom);
        p.close();
        paint.setShader(new LinearGradient(0, 270, 0, bottom, top, lower, Shader.TileMode.CLAMP));
        c.drawPath(p, paint);
        paint.setShader(null);
    }

    private void snow(Canvas c, float[] points, int color) {
        Path p = new Path();
        p.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2) p.lineTo(points[i], points[i + 1]);
        p.close();
        paint.setColor(color);
        c.drawPath(p, paint);
    }

    private void drawLake(Canvas c) {
        paint.setShader(new LinearGradient(0, 595, 0, H,
                new int[]{0xFF61516F, 0xFF2D365F, 0xFF111F3F, 0xFF070E21},
                null, Shader.TileMode.CLAMP));
        c.drawRect(0, 608, W, H, paint);
        paint.setShader(null);
        Random random = new Random(822);
        for (int i = 0; i < 205; i++) {
            float y = 621 + random.nextFloat() * 449;
            float x = random.nextFloat() * W;
            float length = (3 + random.nextInt(85)) * (y / 900f);
            paint.setColor(y < 780 ? 0x239BC7F1 : 0x135D8ABC);
            paint.setStrokeWidth(.6f + random.nextFloat() * 1.4f);
            c.drawLine(x, y, x + length, y, paint);
        }
        // Golden reflection from the sunset, intentionally diffuse.
        Random glow = new Random(904);
        for (int i = 0; i < 118; i++) {
            float y = 625 + glow.nextFloat() * 330;
            float t = (y - 620) / 400f;
            float center = 1500 + (glow.nextFloat() - .5f) * (24 + t * 360);
            paint.setColor(i % 3 == 0 ? 0x72FFC283 : 0x3FFD97A3);
            paint.setStrokeWidth(.6f + glow.nextFloat() * 2f);
            float half = 2 + glow.nextFloat() * (14 + t * 44);
            c.drawLine(center - half, y, center + half, y, paint);
        }
        // Coastline at the bottom.
        ridge(c, new float[]{0, 952, 170, 918, 315, 951, 520, 926, 680, 974,
                890, 920, 1080, 985, 1210, 944, 1390, 965, 1600, 913,
                1770, 955, 1920, 919}, 1080, 0xFF101B2C, 0xFF030C1A);
    }

    private void drawForest(Canvas c) {
        Random random = new Random(21);
        paint.setColor(0xFF081325);
        // Silhouettes around the lower edges, away from the center focus.
        for (int i = 0; i < 92; i++) {
            float x = (i < 46 ? random.nextInt(460) : 1470 + random.nextInt(450));
            float bottom = 750 + random.nextInt(255);
            float height = 36 + random.nextInt(132);
            tree(c, x, bottom, height);
        }
        Random city = new Random(23);
        for (int i = 0; i < 90; i++) {
            float x = 100 + city.nextInt(1720);
            float y = 578 + city.nextInt(65);
            paint.setColor(i % 4 == 0 ? 0xB1FFCE82 : 0x73FC927E);
            c.drawCircle(x, y, .75f + city.nextFloat() * 1.7f, paint);
        }
    }

    private void tree(Canvas c, float x, float baseline, float height) {
        Path p = new Path();
        p.moveTo(x, baseline - height);
        p.lineTo(x - height * .28f, baseline - height * .18f);
        p.lineTo(x - height * .15f, baseline - height * .18f);
        p.lineTo(x - height * .37f, baseline);
        p.lineTo(x + height * .37f, baseline);
        p.lineTo(x + height * .15f, baseline - height * .18f);
        p.lineTo(x + height * .28f, baseline - height * .18f);
        p.close();
        paint.setShader(null);
        paint.setColor(0xE5051024);
        c.drawPath(p, paint);
    }

    private void drawEdgeShadows(Canvas c) {
        paint.setShader(new LinearGradient(0, 0, 0, H,
                new int[]{0x3B060E27, 0x08081027, 0x1206101D, 0xB5040B18},
                new float[]{0, .26f, .58f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(new RadialGradient(960, 500, 1150,
                new int[]{0x00000000, 0x18060A16, 0x9B030715},
                new float[]{0, .68f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
    }
}
