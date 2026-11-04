package com.techgenius.tvoslauncher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Native high-resolution section symbols, never used as invented article photographs. */
final class GlobalNewsIcon extends View {
    private final int kind;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    GlobalNewsIcon(Context context,int kind){super(context);this.kind=kind;setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    private void stroke(float width){p.setColor(0xFFF3FAFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    private void line(Canvas c,float x,float y,float a,float b){c.drawLine(x,y,a,b,p);}
    @Override protected void onDraw(Canvas c){
        float s=Math.min(getWidth(),getHeight())/64f;c.save();c.translate((getWidth()-64*s)/2,(getHeight()-64*s)/2);c.scale(s,s);stroke(2.7f);
        if(kind==0){c.drawCircle(32,32,25,p);c.drawOval(new RectF(19,7,45,57),p);c.drawOval(new RectF(8,22,56,42),p);line(c,7,32,57,32);}
        else if(kind==1){p.setColor(0xFFD9FFE3);line(c,32,11,32,40);for(int i=0;i<4;i++){Path leaf=new Path();leaf.moveTo(32,16+i*3);leaf.quadTo(18-i*3,10+i*4,11,23+i*4);c.drawPath(leaf,p);Path right=new Path();right.moveTo(32,16+i*3);right.quadTo(46+i*3,10+i*4,53,23+i*4);c.drawPath(right,p);}Path sword=new Path();sword.moveTo(12,46);sword.quadTo(36,58,51,39);c.drawPath(sword,p);Path second=new Path();second.moveTo(13,39);second.quadTo(29,58,52,46);c.drawPath(second,p);line(c,10,43,17,50);line(c,47,50,54,43);}
        else if(kind==2){c.drawRoundRect(new RectF(16,16,48,48),4,4,p);c.drawRect(23,23,41,41,p);for(int a=22;a<47;a+=8){line(c,a,7,a,14);line(c,a,50,a,57);line(c,7,a,14,a);line(c,50,a,57,a);}}
        else if(kind==3){p.setStyle(Paint.Style.FILL);c.drawRoundRect(new RectF(10,36,20,55),2,2,p);c.drawRoundRect(new RectF(27,23,37,55),2,2,p);c.drawRoundRect(new RectF(44,8,54,55),2,2,p);}
        else if(kind==4){c.drawCircle(32,32,26,p);Path pentagon=new Path();for(int i=0;i<5;i++){double angle=-Math.PI/2+i*Math.PI*2/5;float x=32+(float)Math.cos(angle)*11,y=32+(float)Math.sin(angle)*11;if(i==0)pentagon.moveTo(x,y);else pentagon.lineTo(x,y);}pentagon.close();p.setStyle(Paint.Style.FILL);c.drawPath(pentagon,p);stroke(2.4f);for(int i=0;i<5;i++){double angle=-Math.PI/2+i*Math.PI*2/5;line(c,32+(float)Math.cos(angle)*11,32+(float)Math.sin(angle)*11,32+(float)Math.cos(angle)*25,32+(float)Math.sin(angle)*25);}}
        else{c.drawRoundRect(new RectF(9,8,52,55),3,3,p);line(c,16,17,26,17);line(c,16,23,26,23);c.drawRect(33,15,45,28,p);for(int y=36;y<50;y+=7)line(c,16,y,45,y);line(c,53,18,59,18);line(c,59,18,59,52);}
        c.restore();
    }
}
