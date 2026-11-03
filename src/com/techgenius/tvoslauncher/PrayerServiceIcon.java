package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small resolution-independent artwork; independent of the device's emoji font. */
final class PrayerServiceIcon extends View {
    private final int kind;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    PrayerServiceIcon(Context context, int kind) { super(context); this.kind = kind; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    private void color(int value) { paint.setColor(value); paint.setStyle(Paint.Style.FILL); paint.setStrokeWidth(2); }
    private void line(Canvas c, float x, float y, float a, float b, int color, float width) {
        color(color); paint.setStrokeWidth(width); paint.setStrokeCap(Paint.Cap.ROUND); c.drawLine(x, y, a, b, paint);
    }
    private void box(Canvas c, float l, float t, float r, float b, float radius, int color) {
        color(color); c.drawRoundRect(new RectF(l,t,r,b),radius,radius,paint);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float scale = Math.min(getWidth(), getHeight()) / 64f;
        c.save(); c.translate((getWidth()-64*scale)/2, (getHeight()-64*scale)/2); c.scale(scale,scale);
        if (kind == 0) quran(c); else if (kind == 1) beads(c); else if (kind == 2) radio(c); else calendar(c);
        c.restore();
    }
    private void quran(Canvas c) {
        line(c,13,55,49,34,0xFFB67A39,5); line(c,16,34,50,56,0xFFD39C55,5);
        line(c,16,55,26,58,0xFF8F582F,3); line(c,40,58,51,56,0xFF8F582F,3);
        c.save(); c.rotate(-17,32,27);
        box(c,9,9,55,41,3,0xFFE7D4A6); box(c,9,6,55,37,3,0xFF327B62);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.3f); paint.setColor(0xFFE8C56A);
        c.drawRoundRect(new RectF(12,9,52,34),2,2,paint);
        Path diamond=new Path();diamond.moveTo(32,12);diamond.lineTo(42,21);diamond.lineTo(32,31);diamond.lineTo(22,21);diamond.close();c.drawPath(diamond,paint);
        color(0xFFE8C56A);c.drawCircle(32,21,3,paint);c.restore();
    }
    private void beads(Canvas c) {
        for(int i=0;i<22;i++) {
            double angle = Math.PI*2*i/22;
            float x=33+(float)Math.cos(angle)*17,y=27+(float)Math.sin(angle)*21;
            color(0xFFE8EDF2); c.drawCircle(x,y,3.5f,paint);
            color(Color.WHITE);c.drawCircle(x-1,y-1,1.1f,paint);
        }
        line(c,25,46,18,56,0xFFE2E7ED,2);line(c,18,55,13,61,0xFFE2E7ED,3);
        line(c,19,55,18,61,0xFFE2E7ED,2);line(c,20,54,23,59,0xFFE2E7ED,2);
    }
    private void radio(Canvas c) {
        line(c,17,20,52,7,0xFFCDE7F7,3);
        box(c,7,20,58,55,5,0xFFA8C6DD);box(c,11,24,54,51,3,0xFF7193B3);
        color(0xFF294E70);c.drawCircle(25,39,12,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);paint.setColor(0xFFC6D8E9);
        c.drawCircle(25,39,8,paint);c.drawCircle(25,39,4,paint);
        box(c,40,27,51,31,1,0xFFDEEDF4);color(0xFF274969);c.drawCircle(46,42,5,paint);
        line(c,14,57,20,57,0xFFB8CFDF,3);line(c,45,57,51,57,0xFFB8CFDF,3);
    }
    private void calendar(Canvas c) {
        box(c,10,14,55,57,4,0xFFD9E1E9);box(c,13,25,52,54,2,0xFF30445B);
        line(c,20,9,20,21,0xFFF0F3F7,4);line(c,45,9,45,21,0xFFF0F3F7,4);
        color(0xFFEAC36B);c.drawCircle(26,36,6,paint);color(0xFF30445B);c.drawCircle(29,33,6,paint);
        for(int y=43;y<51;y+=6)for(int x=21;x<47;x+=8)box(c,x,y,x+4,y+3,1,0xFFE6EDF2);
        box(c,39,31,46,37,1,0xFFEAC36B);
    }
}
