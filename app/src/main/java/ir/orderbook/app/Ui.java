package ir.orderbook.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Shared visual language for native screens and exports. */
public final class Ui {
    public static final int BG=Color.rgb(246,247,242), INK=Color.rgb(24,48,43),
        MUTED=Color.rgb(91,112,102), GREEN=Color.rgb(15,102,80), DARK=Color.rgb(14,49,41),
        MINT=Color.rgb(231,241,233), LINE=Color.rgb(223,232,223), GOLD=Color.rgb(239,209,143),
        RED=Color.rgb(174,60,55), WHITE=Color.WHITE;
    private final Context c;
    public Ui(Context c){this.c=c;}
    public int dp(float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    public LinearLayout col(){LinearLayout l=new LinearLayout(c);l.setOrientation(1);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    public LinearLayout row(){LinearLayout l=new LinearLayout(c);l.setGravity(Gravity.CENTER_VERTICAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    public TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(c);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setFontFeatureSettings("kern");t.setIncludeFontPadding(true);t.setLineSpacing(dp(2),1);t.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_RTL);t.setGravity(Gravity.START);t.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return t;}
    public GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    public GradientDrawable border(int color,int radius,int stroke){GradientDrawable d=bg(color,radius);d.setStroke(dp(1),stroke);return d;}
    public RippleDrawable ripple(int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x22116F5B),bg(color,radius),bg(WHITE,radius));}
    public void pad(View v,int p){v.setPadding(dp(p),dp(p),dp(p),dp(p));}
    public void gap(LinearLayout l,int h){View v=new View(c);l.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    public void weighted(LinearLayout row,View v){row.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    public void space(LinearLayout row,int w){row.addView(new View(c),new LinearLayout.LayoutParams(dp(w),1));}
    public void rule(LinearLayout l){View v=new View(c);v.setBackgroundColor(LINE);l.addView(v,new LinearLayout.LayoutParams(-1,dp(1)));}
    public LinearLayout card(){LinearLayout card=col();pad(card,18);card.setBackground(border(WHITE,24,LINE));return card;}
    public ImageView emblem(String icon,int color,int background,int size){ImageView v=new ImageView(c);v.setImageDrawable(new Icon(icon,color));v.setBackground(bg(background,16));v.setPadding(dp(12),dp(12),dp(12),dp(12));v.setLayoutParams(new LinearLayout.LayoutParams(dp(size),dp(size)));return v;}
    public Drawable hero(){return new Hero();}
    private final class Hero extends Drawable {
        private final Paint paint=new Paint(3);
        @Override public void draw(Canvas canvas){Rect b=getBounds();RectF area=new RectF(b);Path clip=new Path();clip.addRoundRect(area,dp(28),dp(28),Path.Direction.CW);canvas.save();canvas.clipPath(clip);
            paint.setShader(new LinearGradient(b.right,b.top,b.left,b.bottom,new int[]{DARK,0xFF216B53},null,Shader.TileMode.CLAMP));canvas.drawRect(b,paint);paint.setShader(null);
            paint.setColor(0x0FFFFFFF);canvas.drawCircle(b.left+dp(5),b.top+dp(20),dp(120),paint);paint.setColor(0x08FFFFFF);canvas.drawCircle(b.left+dp(32),b.top+dp(24),dp(175),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x20EFD18F);canvas.drawCircle(b.left+dp(5),b.top+dp(20),dp(120),paint);paint.setStyle(Paint.Style.FILL);canvas.restore();}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    public Button button(String title,String icon,boolean primary){Button b=new Button(c);b.setText(title);b.setTextSize(20);b.setAllCaps(false);b.setTypeface(Typeface.create("sans-serif-medium",0));b.setTextColor(primary?WHITE:GREEN);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(56));b.setMinimumHeight(dp(56));b.setPadding(dp(12),dp(10),dp(12),dp(10));b.setBackground(ripple(primary?GREEN:MINT,18));b.setStateListAnimator(null);
        if(icon!=null){Icon i=new Icon(icon,primary?WHITE:GREEN);i.setBounds(0,0,dp(22),dp(22));b.setCompoundDrawablesRelative(i,null,null,null);b.setCompoundDrawablePadding(dp(7));}return b;}
    public ImageButton iconButton(String icon,String label,int color){ImageButton b=new ImageButton(c);b.setImageDrawable(new Icon(icon,color));b.setBackground(ripple(Color.TRANSPARENT,16));b.setContentDescription(label);b.setPadding(dp(13),dp(13),dp(13),dp(13));b.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));return b;}
    public TextView tag(String s,int fg,int bg){TextView t=text(s,20,fg,true);t.setBackground(bg(bg,12));t.setPadding(dp(10),dp(5),dp(10),dp(5));return t;}
    /** Small vector icons; no emoji or downloaded image dependencies. */
    public static final class Icon extends Drawable {
        private final String type;private final Paint p=new Paint(3);private final int color;
        public Icon(String type,int color){this.type=type;this.color=color;}
        private void line(Canvas c,float a,float b,float x,float y){c.drawLine(a,b,x,y,p);}
        @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
            switch(type){
                case "plus":line(c,12,5,12,19);line(c,5,12,19,12);break;
                case "close":line(c,6,6,18,18);line(c,18,6,6,18);break;
                case "down":line(c,6,9,12,15);line(c,12,15,18,9);break;
                case "next":line(c,14,6,8,12);line(c,8,12,14,18);break;
                case "prev":line(c,10,6,16,12);line(c,16,12,10,18);break;
                case "share":c.drawCircle(18,5,3,p);c.drawCircle(6,12,3,p);c.drawCircle(18,19,3,p);line(c,9,10,15,6);line(c,9,14,15,18);break;
                case "image":c.drawRoundRect(3,3,21,21,3,3,p);c.drawCircle(8,8,1.5f,p);line(c,4,18,10,12);line(c,10,12,14,16);line(c,14,16,18,12);line(c,18,12,21,15);break;
                case "company":c.drawRoundRect(5,3,19,21,2,2,p);line(c,10,21,10,16);line(c,14,21,14,16);line(c,10,16,14,16);for(int y=7;y<=12;y+=5){line(c,8,y,9,y);line(c,15,y,16,y);}break;
                case "calendar":c.drawRoundRect(3,5,21,21,3,3,p);line(c,3,10,21,10);line(c,8,3,8,7);line(c,16,3,16,7);line(c,8,15,10,15);line(c,14,15,16,15);break;
                case "box":c.drawRoundRect(4,5,20,21,2,2,p);line(c,4,10,20,10);line(c,12,5,12,10);line(c,10,15,14,15);break;
                case "receipt":c.drawRoundRect(5,2,19,22,2,2,p);line(c,9,7,15,7);line(c,9,12,15,12);line(c,9,17,12,17);break;
                case "more":p.setStyle(Paint.Style.FILL);c.drawCircle(12,5,1.6f,p);c.drawCircle(12,12,1.6f,p);c.drawCircle(12,19,1.6f,p);break;
                case "check":line(c,5,12,10,17);line(c,10,17,19,6);break;
                case "save":line(c,12,3,12,15);line(c,7,10,12,15);line(c,17,10,12,15);line(c,4,16,4,21);line(c,4,21,20,21);line(c,20,21,20,16);break;
                case "edit":line(c,5,16,16,5);line(c,16,5,20,9);line(c,20,9,9,20);line(c,9,20,4,21);line(c,4,21,5,16);line(c,13,8,17,12);break;
                case "trash":line(c,4,6,20,6);line(c,9,3,15,3);line(c,6,6,7,21);line(c,7,21,17,21);line(c,17,21,18,6);line(c,10,10,10,17);line(c,14,10,14,17);break;
            }c.restore();
        }
        @Override public void setAlpha(int a){p.setAlpha(a);}
        @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
