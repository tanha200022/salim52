package ir.orderbook.app;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import java.io.*;
import java.util.*;

/** Private, temporary PNGs for FileProvider sharing. Never writes to the gallery. */
public final class ImageReport {
    private static final int WIDTH=540,HEIGHT=1200,PAD=28,BOTTOM=1128;
    private static final long CACHE_LIFETIME_MS=24L*60*60*1000;
    private final List<File> files=new ArrayList<>();
    private final Store.Company company;
    private final File directory;
    private final int count;
    private final String reportDate=Values.today();
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap bitmap;
    private Canvas canvas;
    private float y,pageTop;
    private String activeTitle="",activeDate="";

    private ImageReport(Context context,Store.Company company,int count)throws IOException{
        this.company=company;this.count=count;
        directory=new File(new File(context.getCacheDir(),"reports"),UUID.randomUUID().toString());
        if(!directory.mkdirs())throw new IOException("Cannot create share cache");
    }
    public static List<File> create(Context context,Store.Company company,List<Store.Order> orders)throws IOException{
        cleanExpired(context);
        ImageReport r=new ImageReport(context,company,orders.size());
        boolean success=false;
        try{
            r.start();int index=0;
            for(Store.Order o:orders){
                r.activeTitle=fa("قلم "+(++index)+"  ·  ")+o.name;r.activeDate=o.created;
                List<Line> lines=new ArrayList<>();
                lines.add(new Line(r.activeTitle,24,Ui.INK,true,Ui.WHITE));
                lines.add(new Line("ثبت سفارش: "+fa(o.created)+(o.delivered?"  •  تحویل شد":"  •  در انتظار تحویل"),20,Ui.MUTED,false,Ui.WHITE));
                lines.add(new Line("قیمت خرید: "+fa(Values.money(o.buy))+" تومان",22,Ui.GREEN,true,Ui.MINT));
                lines.add(new Line("قیمت مصرف: "+fa(Values.money(o.retail))+" تومان",22,Ui.INK,true,Ui.WHITE));
                lines.add(new Line("اختلاف نسبت به خرید: "+fa(Values.percent(o.buy,o.retail)),20,o.retail>=o.buy?Ui.GREEN:Ui.RED,true,Ui.WHITE));
                lines.add(new Line("تعداد سفارش: "+fa(Values.money(o.cartons))+" "+o.unitLabel(),20,Ui.INK,false,Ui.WHITE));
                lines.add(new Line("تاریخ تحویل: "+fa(o.delivery),20,Ui.INK,false,Ui.WHITE));
                if(!o.note.isEmpty())lines.add(new Line("یادداشت: "+o.note,20,Ui.MUTED,false,Ui.WHITE));
                float height=0;for(Line line:lines)height+=r.layout(line).getHeight()+16;
                // Keep ordinary orders together; oversized notes can continue with an item header.
                float available=BOTTOM-r.pageTop;
                float opening=r.layout(lines.get(0)).getHeight()+r.layout(lines.get(1)).getHeight()+80;
                if(r.y>r.pageTop&&(r.y+height>BOTTOM&&(height<=available||r.y+opening>BOTTOM))){r.finish();r.start();}
                for(Line line:lines)r.block(line);
                r.y+=20;
            }
            r.finish();success=true;return r.files;
        }finally{
            if(r.bitmap!=null&&!r.bitmap.isRecycled())r.bitmap.recycle();
            if(!success)deleteTree(r.directory);
        }
    }
    /** Defer deletion so receiving messengers can finish reading shared URIs. */
    public static void cleanExpired(Context context){
        File root=new File(context.getCacheDir(),"reports");File[] entries=root.listFiles();
        if(entries==null)return;long cutoff=System.currentTimeMillis()-CACHE_LIFETIME_MS;
        for(File entry:entries)if(entry.lastModified()<cutoff)deleteTree(entry);
    }
    private static void deleteTree(File file){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);file.delete();}
    private static final class Line {
        final String text;final int size,color,background;final boolean bold;
        Line(String text,int size,int color,boolean bold,int background){this.text=text;this.size=size;this.color=color;this.bold=bold;this.background=background;}
    }
    static String fa(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(c>='0'&&c<='9'?(char)('۰'+c-'0'):c);return b.toString();}
    private StaticLayout layout(Line line){return layout(line.text,line.size,line.color,line.bold,WIDTH-2*PAD-32);}
    private StaticLayout layout(String text,int size,int color,boolean bold,int width){
        TextPaint p=new TextPaint(Paint.ANTI_ALIAS_FLAG);p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));
        return StaticLayout.Builder.obtain(text,0,text.length(),p,width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).setLineSpacing(5,1).setIncludePad(true).build();
    }
    private void place(StaticLayout l,float x,float yy){canvas.save();canvas.translate(x,yy);l.draw(canvas);canvas.restore();}
    private void start()throws IOException{
        bitmap=Bitmap.createBitmap(WIDTH*2,HEIGHT*2,Bitmap.Config.ARGB_8888);canvas=new Canvas(bitmap);canvas.scale(2,2);canvas.drawColor(Ui.BG);
        StaticLayout name=layout(company.name.replaceAll("\\s+"," "),30,Ui.WHITE,true,WIDTH-2*PAD-40);
        String contact="";
        if(!company.visitor.isEmpty())contact="ویزیتور: "+company.visitor.replaceAll("\\s+"," ")+"\n";
        if(!company.phone.isEmpty())contact+="تلفن: "+fa(company.phone)+"\n";
        contact+=company.settlementDays<0?"تسویه: تعیین‌نشده":company.settlementDays==0?"تسویه: نقدی":"تسویه: "+fa(Values.money(company.settlementDays))+" روز";
        StaticLayout visitor=layout(contact,20,0xFFD7EADD,false,WIDTH-2*PAD-40);
        float headerHeight=98+name.getHeight()+visitor.getHeight();
        RectF header=new RectF(PAD,24,WIDTH-PAD,24+headerHeight);Path clip=new Path();clip.addRoundRect(header,24,24,Path.Direction.CW);
        canvas.save();canvas.clipPath(clip);paint.setShader(new LinearGradient(WIDTH-PAD,24,PAD,24+headerHeight,new int[]{Ui.DARK,0xFF216B53},null,Shader.TileMode.CLAMP));canvas.drawRect(header,paint);paint.setShader(null);paint.setColor(0x0FFFFFFF);canvas.drawCircle(PAD+10,44,140,paint);canvas.restore();
        place(layout("سفارش‌یار  /  سفارش خرید",20,Ui.GOLD,true,WIDTH-2*PAD-40),PAD+20,44);
        place(name,PAD+20,86);place(visitor,PAD+20,100+name.getHeight());
        y=24+headerHeight+20;
        StaticLayout summary=layout(fa(count+" قلم سفارش")+"  •  مبالغ به تومان",20,Ui.MUTED,false,WIDTH-2*PAD);place(summary,PAD,y);y+=summary.getHeight()+20;
        pageTop=y;
        if(BOTTOM-pageTop<180)throw new IOException("Report header is too tall");
    }
    private void nextForItem()throws IOException{
        finish();start();
        StaticLayout continued=layout("ادامهٔ "+activeTitle+"\nثبت: "+fa(activeDate),20,Ui.GREEN,true,WIDTH-2*PAD-32);
        if(y+continued.getHeight()+70>BOTTOM)throw new IOException("Continuation header is too tall");
        paint.setColor(Ui.MINT);canvas.drawRoundRect(PAD,y,WIDTH-PAD,y+continued.getHeight()+16,12,12,paint);place(continued,PAD+16,y+8);y+=continued.getHeight()+28;
    }
    private void block(Line line)throws IOException{
        StaticLayout l=layout(line);int first=0;
        while(first<l.getLineCount()){
            int top=l.getLineTop(first),end=first;
            while(end<l.getLineCount()&&y+16+l.getLineBottom(end)-top<=BOTTOM)end++;
            if(end==first){nextForItem();if(y+16+l.getLineBottom(first)-top>BOTTOM)throw new IOException("Report line is too tall");continue;}
            int bottom=l.getLineBottom(end-1),h=bottom-top;
            paint.setColor(line.background);canvas.drawRoundRect(PAD,y,WIDTH-PAD,y+h+16,10,10,paint);
            canvas.save();canvas.clipRect(PAD+16,y+8,WIDTH-PAD-16,y+8+h);canvas.translate(PAD+16,y+8-top);l.draw(canvas);canvas.restore();y+=h+16;
            first=end;if(first<l.getLineCount())nextForItem();
        }
    }
    private void finish()throws IOException{
        paint.setColor(Ui.LINE);canvas.drawLine(PAD,1150,WIDTH-PAD,1150,paint);
        place(layout("سفارش‌یار  •  صفحهٔ "+fa(""+(files.size()+1)),20,Ui.MUTED,false,WIDTH-2*PAD),PAD,1160);
        File file=new File(directory,ReportNames.stem(company.name)+"_"+reportDate.replace('/','-')+"_صفحه-"+String.format(Locale.US,"%02d",files.size()+1)+".png");
        try(OutputStream out=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG failed");}finally{bitmap.recycle();}
        files.add(file);directory.setLastModified(System.currentTimeMillis());
    }
}
