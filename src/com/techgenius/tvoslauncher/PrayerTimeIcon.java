package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Gold sun/sunrise and silver crescent artwork, drawn at native display resolution. */
final class PrayerTimeIcon extends View {
    private final int kind;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    PrayerTimeIcon(Context c,int kind){super(c);this.kind=kind;}
    @Override protected void onDraw(Canvas c){
        float scale=Math.min(getWidth()/64f,getHeight()/44f);
        c.save();c.translate((getWidth()-64*scale)/2,(getHeight()-44*scale)/2);c.scale(scale,scale);
        if(kind==4){
            Path moon=new Path();moon.moveTo(36,4);moon.cubicTo(14,4,11,33,32,36);
            moon.cubicTo(43,38,49,31,50,25);moon.cubicTo(34,33,24,16,36,4);moon.close();
            p.setColor(0xFFE5EAF3);c.drawPath(moon,p);
        }else{
            boolean horizon=kind==0||kind==3;
            float cy=horizon?30:22;
            p.setShader(new android.graphics.RadialGradient(32,cy,23,new int[]{0x88FFD46A,0x00FFBE39},null,android.graphics.Shader.TileMode.CLAMP));
            c.drawCircle(32,cy,23,p);p.setShader(null);
            p.setColor(0xFFFFD46A);p.setStrokeWidth(1.4f);p.setStrokeCap(Paint.Cap.ROUND);
            for(int i=0;i<10;i++){
                double angle=Math.PI*2*i/10;float x=(float)Math.cos(angle),y=(float)Math.sin(angle);
                if(!horizon||y<-.1)c.drawLine(32+x*14,cy+y*14,32+x*18,cy+y*18,p);
            }
            if(horizon){c.save();c.clipRect(0,0,64,31);c.drawCircle(32,cy,10,p);c.restore();
                c.drawLine(8,32,56,32,p);p.setAlpha(90);c.drawLine(17,35,47,35,p);p.setAlpha(255);
            }else c.drawCircle(32,cy,10,p);
        }c.restore();
    }
}
