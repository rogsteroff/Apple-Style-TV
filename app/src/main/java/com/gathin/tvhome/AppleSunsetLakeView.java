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
 * Original offline dusk landscape for Apple Style TV 3.8.
 * Drawn locally: no network access, no personal photos, no bitmap memory usage.
 * A real wallpaper.jpg supplied by the owner takes precedence (MainActivity).
 */
public final class AppleSunsetLakeView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public AppleSunsetLakeView(Context c) { super(c); }
    public AppleSunsetLakeView(Context c, AttributeSet attrs) { super(c, attrs); }

    @Override protected void onDraw(Canvas raw) {
        super.onDraw(raw);
        if (getWidth() == 0 || getHeight() == 0) return;
        raw.save();
        raw.scale(getWidth() / 1920f, getHeight() / 1080f);
        // Midnight indigo to glowing pink-orange sunset.
        p.setShader(new LinearGradient(0, 0, 0, 770,
                new int[]{0xFF0A1638, 0xFF27265A, 0xFF8B4B78, 0xFFE68577, 0xFF6F5477},
                new float[]{0f,.34f,.66f,.85f,1f}, Shader.TileMode.CLAMP));
        raw.drawRect(0, 0, 1920, 760, p);
        p.setShader(new RadialGradient(1370, 405, 530,
                new int[]{0x77FFC49B,0x55FBA37E,0x00FF946E},
                null, Shader.TileMode.CLAMP));
        raw.drawCircle(1370,405,530,p);
        p.setShader(null);

        // Fine glowing dusk cloud bands.
        Random cloud = new Random(38);
        for (int i = 0; i < 58; i++) {
            float x = cloud.nextInt(2030) - 65;
            float y = 80 + cloud.nextInt(370);
            float width = 90 + cloud.nextInt(250);
            p.setColor(i % 3 == 0 ? 0x226F4D8A : 0x1F1E245A);
            p.setStrokeWidth(4 + cloud.nextInt(11));
            raw.drawLine(x, y, x+width, y-cloud.nextInt(19), p);
        }

        mountain(raw,0xFF32375E, 620, new float[]{
            0,635, 120,585, 280,611, 435,535, 556,572, 730,468, 882,558, 1038,461,
            1170,514, 1358,425, 1520,497, 1680,442, 1810,500, 1920,482
        });
        mountain(raw,0xFF1D3055, 650, new float[]{
            0,680,130,620,285,662,440,600,590,635,745,528,885,609,1035,560,
            1225,622,1360,540,1510,611,1680,558,1800,595,1920,560
        });
        mountain(raw,0xFF111E3C, 725, new float[]{
            0,718,125,670,295,711,475,657,640,702,790,652,960,698,1138,646,
            1295,701,1460,668,1610,716,1785,644,1920,690
        });

        // Mirror lake in navy and amber.
        p.setShader(new LinearGradient(0, 640, 0, 1080,
                new int[]{0xFF333956,0xFF192747,0xFF0A1732},
                null, Shader.TileMode.CLAMP));
        raw.drawRect(0, 640, 1920, 1080,p);
        p.setShader(null);
        p.setShader(new LinearGradient(0, 690, 0, 1080,
                new int[]{0x44FC9B83,0x18F2A57B,0x00FFB292},
                null, Shader.TileMode.CLAMP));
        raw.drawRect(950,655,1770,1080,p);
        p.setShader(null);

        Random reflection = new Random(117);
        for (int i=0;i<180;i++) {
            float x=reflection.nextInt(1920);
            float y=660+reflection.nextInt(420);
            float len=(18+reflection.nextInt(90))*(1f+(y-650)/850f);
            int alpha=14+reflection.nextInt(47);
            boolean warm=x>890 && x<1830 && reflection.nextInt(3)!=0;
            p.setColor((alpha<<24)|(warm?0x00F9BAA2:0x0092A2CF));
            p.setStrokeWidth(1+reflection.nextInt(3));
            raw.drawLine(x,y,x+len,y,p);
        }

        // Distant shore and silhouette trees frame the background.
        mountain(raw,0xFF0C2034, 870, new float[]{
            0,800,105,786,200,806,315,798,440,825,600,840,750,849,875,860,995,858,
            1160,864,1310,859,1450,869,1600,842,1730,827,1840,808,1920,815
        });
        for(int i=0;i<33;i++){
            float x=i*39f+8;
            float y=803+(i*17)%43;
            tree(raw,x,y,19+(i*13)%46,0xFF0C2033);
        }
        for(int i=0;i<30;i++){
            float x=1500+i*17f;
            float y=824+(i*23)%70;
            tree(raw,x,y,22+(i*11)%80,0xFF0B1B2D);
        }
        // Delicate settlement lights across the far shore.
        Random lights=new Random(82);
        for(int i=0;i<120;i++){
            float x=lights.nextInt(1860)+20;
            float y=800+lights.nextInt(30);
            p.setColor(i%3==0?0xAAFFAA6A:0x66FFCA96);
            raw.drawCircle(x,y,.7f+lights.nextFloat()*1.6f,p);
        }
        // Vignette improves readability of white type and frosted app tiles.
        p.setShader(new LinearGradient(0,0,0,1080,
                new int[]{0x42070823,0x18070D23,0x0A0A1227,0xB5091024},
                new float[]{0,.36f,.60f,1f},Shader.TileMode.CLAMP));
        raw.drawRect(0,0,1920,1080,p);
        p.setShader(null);
        raw.restore();
    }

    private void mountain(Canvas c,int color,float bottom,float[] points) {
        Path shape=new Path();
        shape.moveTo(0,bottom);
        for(int i=0;i<points.length;i+=2)shape.lineTo(points[i],points[i+1]);
        shape.lineTo(1920,bottom);
        shape.close();
        p.setShader(null);
        p.setColor(color);
        c.drawPath(shape,p);
    }

    private void tree(Canvas c,float x,float y,float height,int color) {
        p.setShader(null);p.setColor(color);
        Path t=new Path();
        t.moveTo(x,y-height);
        t.lineTo(x-height*.24f,y-height*.48f);
        t.lineTo(x-height*.12f,y-height*.48f);
        t.lineTo(x-height*.35f,y-height*.08f);
        t.lineTo(x+height*.35f,y-height*.08f);
        t.lineTo(x+height*.12f,y-height*.48f);
        t.lineTo(x+height*.24f,y-height*.48f);
        t.close();
        c.drawPath(t,p);
        p.setStrokeWidth(Math.max(1,height*.055f));
        c.drawLine(x,y-height*.1f,x,y+height*.07f,p);
    }
}
