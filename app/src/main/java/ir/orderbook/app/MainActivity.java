package ir.orderbook.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.*;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

public class MainActivity extends Activity {
    private Ui ui;private Store db;private LinearLayout root,content;private ScrollView scroll;
    private List<Store.Company> companies=new ArrayList<>();private final List<Store.Order> visible=new ArrayList<>();
    private long selected=-1;private String dateFilter="همهٔ تاریخ‌ها";private boolean exporting;
    private ExportJob exportJob;private ArrayList<String> previewPaths;private long previewCompany=-1;private int previewIndex;
    private Button exportButton;private AlertDialog busyDialog;private Dialog activeDialog;
    private Store.Company editingCompany;private String selectedUnit="carton";private RadioGroup unitPicker;
    private Bundle draft;private String draftKind;private Store.Order editing;private final List<EditText> fields=new ArrayList<>();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new Ui(this);db=new Store(this);
        if(state!=null){selected=state.getLong("company",-1);dateFilter=state.getString("filter","همهٔ تاریخ‌ها");draft=state.getBundle("draft");previewPaths=state.getStringArrayList("previewPaths");previewCompany=state.getLong("previewCompany",-1);previewIndex=state.getInt("previewIndex",0);}
        else selected=getPreferences(0).getLong("company",-1);
        root=ui.col();root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(android.os.Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        setContentView(root);root.requestApplyInsets();
        LinearLayout toolbar=ui.row();toolbar.setPadding(ui.dp(20),ui.dp(16),ui.dp(20),ui.dp(14));
        toolbar.addView(ui.emblem("receipt",Ui.WHITE,Ui.GREEN,50));ui.space(toolbar,12);
        LinearLayout brand=ui.col();brand.addView(ui.text("سفارش‌یار",26,Ui.INK,true));brand.addView(ui.text("دفتر سفارش‌های شما",20,Ui.MUTED,false));ui.weighted(toolbar,brand);root.addView(toolbar);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=ui.col();content.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),ui.dp(24));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dock=ui.col();dock.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(16));dock.setBackgroundColor(Ui.WHITE);dock.setElevation(ui.dp(8));
        Button add=ui.button("سفارش جدید","plus",false);exportButton=ui.button("اشتراک عکس","share",true);
        if(wide()){LinearLayout actions=ui.row();ui.weighted(actions,exportButton);ui.space(actions,10);ui.weighted(actions,add);dock.addView(actions);}
        else{dock.addView(exportButton,new LinearLayout.LayoutParams(-1,-2));ui.gap(dock,8);dock.addView(add,new LinearLayout.LayoutParams(-1,-2));}
        root.addView(dock);
        add.setOnClickListener(v->{if(company()==null)companyDialog();else orderDialog(null);});exportButton.setOnClickListener(v->export());
        refresh();
        exportJob=(ExportJob)getLastNonConfigurationInstance();
        if(exportJob!=null){exporting=true;exportButton.setEnabled(false);showProgress();}
        else if(previewPaths!=null){ArrayList<String> paths=new ArrayList<>(previewPaths);root.post(()->restorePreview(paths));}
        Context cacheContext=getApplicationContext();new Thread(()->ImageReport.cleanExpired(cacheContext),"report-cache-cleanup").start();
        if(draft!=null){Bundle restored=draft;root.post(()->{if("company".equals(restored.getString("kind"))){long cid=restored.getLong("companyId",0);Store.Company oldCompany=null;for(Store.Company candidate:companies)if(candidate.id==cid)oldCompany=candidate;if(cid==0||oldCompany!=null)companyDialog(oldCompany);}else{Store.Order old=null;long id=restored.getLong("id",0);for(Store.Order o:db.orders(selected))if(o.id==id)old=o;if(id==0||old!=null)orderDialog(old);}ArrayList<String> values=restored.getStringArrayList("values");if(values!=null)for(int i=0;i<Math.min(values.size(),fields.size());i++)fields.get(i).setText(values.get(i));if(unitPicker!=null){selectedUnit=restored.getString("unit","carton");unitPicker.check("piece".equals(selectedUnit)?102:101);}draft=null;});}
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putLong("company",selected);b.putString("filter",dateFilter);b.putStringArrayList("previewPaths",previewPaths);b.putLong("previewCompany",previewCompany);b.putInt("previewIndex",previewIndex);
        if(activeDialog!=null&&activeDialog.isShowing()&&draftKind!=null){Bundle d=new Bundle();d.putString("kind",draftKind);d.putLong("id",editing==null?0:editing.id);d.putLong("companyId",editingCompany==null?0:editingCompany.id);d.putString("unit",selectedUnit);ArrayList<String> values=new ArrayList<>();for(EditText e:fields)values.add(e.getText().toString());d.putStringArrayList("values",values);b.putBundle("draft",d);}}
    private static String fa(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(c>='0'&&c<='9'?(char)('۰'+c-'0'):c);return b.toString();}
    private TextView text(String s,boolean bold){return ui.text(s,20,Ui.INK,bold);}
    private Store.Company company(){for(Store.Company c:companies)if(c.id==selected)return c;return null;}
    private void refresh(){companies=db.companies();if(company()==null)selected=companies.isEmpty()?-1:companies.get(0).id;getPreferences(0).edit().putLong("company",selected).apply();render();}
    private boolean wide(){return getResources().getConfiguration().screenWidthDp>=400&&getResources().getConfiguration().fontScale<1.2f;}
    private void render(){int oldY=scroll.getScrollY();content.removeAllViews();visible.clear();Store.Company c=company();
        LinearLayout today=ui.row();ui.weighted(today,ui.text("نمای کلی",22,Ui.INK,true));today.addView(ui.tag(fa(Values.today()),Ui.MUTED,Ui.WHITE));content.addView(today);ui.gap(content,14);
        LinearLayout companyCard=ui.col();ui.pad(companyCard,22);companyCard.setBackground(ui.hero());
        LinearLayout head=ui.row();ui.weighted(head,ui.text("شرکت انتخاب‌شده",20,0xFFD2E7DA,false));ImageButton plus=ui.iconButton("plus","افزودن شرکت",Ui.GOLD);head.addView(plus);plus.setOnClickListener(v->companyDialog());if(c!=null){ImageButton edit=ui.iconButton("edit","ویرایش شرکت",Ui.GOLD);head.addView(edit);edit.setOnClickListener(v->companyDialog(c));}companyCard.addView(head);ui.gap(companyCard,10);
        companyCard.addView(ui.text(c==null?"از اولین شرکت شروع کنید":c.name,28,Ui.WHITE,true));ui.gap(companyCard,16);
        if(c==null)companyCard.addView(ui.text("شرکت و ویزیتور را ثبت کنید؛\nسفارش‌ها اینجا مرتب می‌مانند.",20,0xFFDCEBE4,false));
        else{companyCard.addView(ui.text("ویزیتور  ·  "+c.visitor,20,0xFFDCEBE4,false));TextView phone=ui.text(fa(c.phone),22,Ui.GOLD,true);phone.setTextDirection(View.TEXT_DIRECTION_LTR);phone.setGravity(Gravity.RIGHT);companyCard.addView(phone);ui.gap(companyCard,10);companyCard.addView(ui.text(settlementText(c),20,Ui.GOLD,true));}
        ui.gap(companyCard,20);Button choose=ui.button(c==null?"افزودن شرکت":"انتخاب شرکت","company",false);choose.setTextColor(Ui.WHITE);choose.setBackground(ui.ripple(0x26FFFFFF,16));choose.setCompoundDrawables(null,null,null,null);choose.setOnClickListener(v->{if(c==null)companyDialog();else companyPicker();});companyCard.addView(choose,new LinearLayout.LayoutParams(-1,-2));
        content.addView(companyCard);ui.gap(content,18);
        List<Store.Order> all=db.orders(selected);List<String> dates=new ArrayList<>();dates.add("همهٔ تاریخ‌ها");for(Store.Order o:all)if(!dates.contains(o.created))dates.add(o.created);
        if(!dates.contains(dateFilter))dateFilter=dates.get(0);for(Store.Order o:all)if(dateFilter.equals(dates.get(0))||o.created.equals(dateFilter))visible.add(o);
        BigInteger cartonsTotal=BigInteger.ZERO,piecesTotal=BigInteger.ZERO;for(Store.Order o:visible){if("piece".equals(o.unit))piecesTotal=piecesTotal.add(BigInteger.valueOf(o.cartons));else cartonsTotal=cartonsTotal.add(BigInteger.valueOf(o.cartons));}
        LinearLayout stats=ui.row();ui.weighted(stats,stat(fa(""+visible.size()),"قلم سفارش","receipt"));ui.space(stats,12);ui.weighted(stats,stat(fa(cartonsTotal.toString())+" کارتن\n"+fa(piecesTotal.toString())+" عدد","مجموع سفارش","box"));content.addView(stats);ui.gap(content,26);
        LinearLayout section=ui.row();ui.weighted(section,ui.text("سفارش‌های شما",24,Ui.INK,true));section.addView(ui.tag(fa(""+visible.size()),Ui.GREEN,Ui.MINT));content.addView(section);ui.gap(content,12);
        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);LinearLayout chips=ui.row();
        for(String date:dates){boolean on=date.equals(dateFilter);TextView chip=ui.tag(date.equals(dates.get(0))?"همهٔ تاریخ‌ها":fa(date),on?Ui.WHITE:Ui.MUTED,on?Ui.GREEN:Ui.WHITE);chip.setMinHeight(ui.dp(48));chip.setGravity(Gravity.CENTER);chip.setOnClickListener(v->{dateFilter=date;render();});chips.addView(chip);ui.space(chips,8);}hs.addView(chips);content.addView(hs);ui.gap(content,18);
        if(visible.isEmpty()){LinearLayout empty=ui.card();ui.pad(empty,24);ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Icon("box",Ui.GREEN));LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(ui.dp(64),ui.dp(64));ilp.gravity=Gravity.CENTER;empty.addView(icon,ilp);ui.gap(empty,16);TextView title=text("جای اولین سفارش شما",true);title.setGravity(Gravity.CENTER);empty.addView(title);ui.gap(empty,8);TextView hint=ui.text("از دکمهٔ پایین شروع کنید؛\nبقیهٔ کارها مرتب می‌ماند.",20,Ui.MUTED,false);hint.setGravity(Gravity.CENTER);empty.addView(hint);content.addView(empty);}
        String last="";int sequence=0;for(Store.Order o:visible){if(!last.equals(o.created)){last=o.created;LinearLayout day=ui.row();ImageView cal=new ImageView(this);cal.setImageDrawable(new Ui.Icon("calendar",Ui.MUTED));day.addView(cal,new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));ui.space(day,8);day.addView(ui.text("ثبت در "+fa(last),20,Ui.MUTED,false));content.addView(day);ui.gap(content,10);}content.addView(orderCard(o,++sequence));ui.gap(content,14);}
        exportButton.setEnabled(!visible.isEmpty()&&!exporting);exportButton.setAlpha(visible.isEmpty()?0.45f:1f);scroll.post(()->scroll.scrollTo(0,oldY));
    }
    private LinearLayout stat(String n,String label,String icon){LinearLayout l=ui.card();l.addView(ui.emblem(icon,Ui.GREEN,Ui.MINT,46));ui.gap(l,12);l.addView(ui.text(n,32,Ui.GREEN,true));l.addView(ui.text(label,20,Ui.MUTED,false));return l;}
    private LinearLayout orderCard(Store.Order o,int index){LinearLayout card=ui.card();LinearLayout top=ui.row();ui.weighted(top,ui.text("قلم "+fa(String.valueOf(index)),20,Ui.MUTED,false));ImageButton more=ui.iconButton("more","گزینه‌های "+o.name,Ui.MUTED);top.addView(more);card.addView(top);more.setOnClickListener(v->orderMenu(o));
        card.addView(ui.text(o.name,24,Ui.INK,true));ui.gap(card,18);
        boolean columns=wide()&&o.buy<100000000&&o.retail<100000000;LinearLayout prices=columns?ui.row():ui.col();LinearLayout buy=price("قیمت خرید",o.buy,true),retail=price("قیمت مصرف",o.retail,false);
        if(columns){ui.weighted(prices,buy);ui.space(prices,10);ui.weighted(prices,retail);}else{prices.addView(buy);ui.gap(prices,8);prices.addView(retail);}card.addView(prices);ui.gap(card,12);
        card.addView(ui.tag("اختلاف نسبت به خرید  "+fa(Values.percent(o.buy,o.retail)),o.retail>=o.buy?Ui.GREEN:Ui.RED,o.retail>=o.buy?Ui.MINT:0xFFFBEDE9));ui.gap(card,18);ui.rule(card);ui.gap(card,16);
        detail(card,"تعداد سفارش",fa(Values.money(o.cartons))+" "+o.unitLabel());detail(card,"تاریخ تحویل",fa(o.delivery));
        if(!o.note.isEmpty()){ui.gap(card,12);LinearLayout note=ui.col();ui.pad(note,14);note.setBackground(ui.bg(Ui.BG,16));note.addView(ui.text("یادداشت سفارش",20,Ui.GREEN,true));ui.gap(note,4);note.addView(ui.text(o.note,20,Ui.MUTED,false));card.addView(note);}return card;}
    private LinearLayout price(String label,long n,boolean primary){LinearLayout l=ui.col();ui.pad(l,14);l.setBackground(ui.bg(primary?Ui.MINT:Ui.BG,16));l.addView(ui.text(label+" · تومان",20,Ui.MUTED,false));ui.gap(l,6);l.addView(ui.text(fa(Values.money(n)),26,primary?Ui.GREEN:Ui.INK,true));return l;}
    private void detail(LinearLayout l,String label,String value){LinearLayout row=wide()?ui.row():ui.col();if(wide()){ui.weighted(row,ui.text(label,20,Ui.MUTED,false));row.addView(text(value,true));}else{row.addView(ui.text(label,20,Ui.MUTED,false));row.addView(text(value,true));}l.addView(row);ui.gap(l,10);}
    private void companyPicker(){LinearLayout items=ui.col();for(Store.Company c:companies){LinearLayout item=ui.card();LinearLayout r=ui.row();ui.weighted(r,text(c.name,true));if(c.id==selected)r.addView(ui.tag("فعال",Ui.GREEN,Ui.MINT));item.addView(r);item.addView(ui.text(c.visitor,20,Ui.MUTED,false));item.addView(ui.text(settlementText(c),20,Ui.GREEN,false));ImageButton edit=ui.iconButton("edit","ویرایش "+c.name,Ui.GREEN);r.addView(edit);edit.setOnClickListener(v->{activeDialog.dismiss();companyDialog(c);});item.setOnClickListener(v->{selected=c.id;dateFilter="همهٔ تاریخ‌ها";activeDialog.dismiss();refresh();scroll.smoothScrollTo(0,0);});items.addView(item);ui.gap(items,10);}Sheet s=sheet("شرکت‌های شما","برای دیدن سفارش‌ها، شرکت را انتخاب کنید.",items,"افزودن شرکت",false);s.save.setOnClickListener(v->{s.dialog.dismiss();companyDialog();});}
    private void orderMenu(Store.Order o){LinearLayout body=ui.col();Button edit=ui.button("ویرایش سفارش","edit",false),delete=ui.button("حذف سفارش","trash",false);delete.setTextColor(Ui.RED);body.addView(edit);ui.gap(body,12);body.addView(delete);Sheet s=sheet(o.name,"مدیریت این سفارش",body,null,false);edit.setOnClickListener(v->{s.dialog.dismiss();orderDialog(o);});delete.setOnClickListener(v->{s.dialog.dismiss();confirmDelete(o);});}
    private void confirmDelete(Store.Order o){LinearLayout b=ui.col();b.addView(text("سفارش «"+o.name+"» حذف شود؟",false));Sheet s=sheet("حذف سفارش","این کار قابل بازگشت نیست.",b,"حذف سفارش",false);s.save.setTextColor(Ui.WHITE);s.save.setBackground(ui.ripple(Ui.RED,18));s.save.setOnClickListener(v->{db.delete(o.id);s.dialog.dismiss();refresh();toast("سفارش حذف شد");});}
    private final class Sheet{Dialog dialog;Button save;}
    private Sheet sheet(String title,String subtitle,LinearLayout body,String action,boolean form){
        if(!form){draftKind=null;fields.clear();}Sheet s=new Sheet();s.dialog=new Dialog(this);s.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=ui.col();panel.setBackground(ui.bg(Ui.BG,28));
        View handle=new View(this);handle.setBackground(ui.bg(0xFFCBD6CF,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(ui.dp(42),ui.dp(5));hp.gravity=Gravity.CENTER;hp.topMargin=ui.dp(12);hp.bottomMargin=ui.dp(12);panel.addView(handle,hp);
        LinearLayout heading=ui.row();heading.setPadding(ui.dp(20),0,ui.dp(20),0);ui.weighted(heading,ui.text(title,24,Ui.INK,true));ImageButton close=ui.iconButton("close","بستن",Ui.MUTED);heading.addView(close);close.setOnClickListener(v->s.dialog.dismiss());panel.addView(heading);
        if(subtitle!=null){TextView sub=ui.text(subtitle,20,Ui.MUTED,false);sub.setPadding(ui.dp(24),0,ui.dp(24),ui.dp(12));panel.addView(sub);}
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.setVerticalScrollBarEnabled(false);body.setPadding(ui.dp(22),ui.dp(8),ui.dp(22),ui.dp(20));sc.addView(body);panel.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        if(action!=null){LinearLayout footer=ui.col();footer.setPadding(ui.dp(20),ui.dp(10),ui.dp(20),ui.dp(18));s.save=ui.button(action,form?"check":null,true);footer.addView(s.save,new LinearLayout.LayoutParams(-1,-2));panel.addView(footer);}
        panel.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),0,insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        s.dialog.setContentView(panel);Window w=s.dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=w.getAttributes();attrs.dimAmount=.35f;w.setAttributes(attrs);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        s.dialog.show();if(w!=null){int width=Math.min(getResources().getDisplayMetrics().widthPixels,ui.dp(620));w.setLayout(width,(int)(getResources().getDisplayMetrics().heightPixels*.9f));}panel.requestApplyInsets();activeDialog=s.dialog;return s;
    }
    private void beginForm(String kind,Store.Order old){draftKind=kind;editing=old;editingCompany=null;unitPicker=null;selectedUnit="carton";fields.clear();}
    private EditText field(LinearLayout form,String label,String value,int type,int max,String hint){form.addView(ui.text(label,20,Ui.MUTED,false));ui.gap(form,6);EditText e=new EditText(this);e.setTextSize(20);e.setTextColor(Ui.INK);e.setHintTextColor(0xFF8C9C93);e.setHint(hint);e.setInputType(type);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});e.setBackground(ui.border(Ui.WHITE,16,Ui.LINE));e.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));e.setMinHeight(ui.dp(58));e.setText(value);e.setSelectAllOnFocus(false);e.setOnFocusChangeListener((v,focused)->e.setBackground(ui.border(Ui.WHITE,16,focused?Ui.GREEN:Ui.LINE)));form.addView(e,new LinearLayout.LayoutParams(-1,-2));ui.gap(form,18);fields.add(e);return e;}
    private void grouping(EditText e){e.addTextChangedListener(new TextWatcher(){boolean busy;public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable t){if(busy)return;String raw=Values.ascii(t.toString()).replace(",", "").replace("٬", "");if(raw.isEmpty()||!raw.matches("[0-9]+"))return;try{String fmt=Values.money(Long.parseLong(raw));if(!fmt.equals(t.toString())){int old=e.getSelectionStart(),digits=0;for(int i=0;i<Math.min(old,t.length());i++)if(Character.isDigit(t.charAt(i)))digits++;busy=true;e.setText(fmt);int p=0,n=0;while(p<fmt.length()&&n<digits){if(Character.isDigit(fmt.charAt(p)))n++;p++;}e.setSelection(p);busy=false;}}catch(NumberFormatException ignored){busy=false;}}});}
    private String required(EditText e,String message){String s=e.getText().toString().trim();if(s.isEmpty()){e.setError(message);e.requestFocus();throw new IllegalArgumentException(message);}return s;}
    private String settlementText(Store.Company c){return c.settlementDays<0?"تسویه: تعیین‌نشده":c.settlementDays==0?"تسویه: نقدی":"تسویه: "+fa(Values.money(c.settlementDays))+" روز";}
    private void companyDialog(){companyDialog(null);}
    private void companyDialog(Store.Company old){beginForm("company",null);editingCompany=old;LinearLayout f=ui.col();
        EditText name=field(f,"نام شرکت",old==null?"":old.name,1,100,"مثلاً شرکت پخش بهار"),visitor=field(f,"نام ویزیتور",old==null?"":old.visitor,1,100,"نام و نام خانوادگی"),phone=field(f,"تلفن ویزیتور",old==null?"":old.phone,3,20,"09…");phone.setTextDirection(View.TEXT_DIRECTION_LTR);
        EditText settlement=field(f,"مدت تسویه · روز",old==null||old.settlementDays<0?"":Values.money(old.settlementDays),2,8,"مثلاً ۳۰؛ برای نقدی ۰");grouping(settlement);
        Sheet s=sheet(old==null?"شرکت جدید":"ویرایش شرکت","مدت تسویه در سربرگ عکس سفارش‌ها نمایش داده می‌شود.",f,old==null?"ثبت شرکت":"ذخیرهٔ تغییرات",true);
        s.save.setOnClickListener(v->{try{String n=required(name,"نام شرکت را وارد کنید"),vis=required(visitor,"نام ویزیتور را وارد کنید"),p=Values.ascii(required(phone,"تلفن را وارد کنید")).replace(" ","").replace("-","");if(!p.matches("\\+?[0-9]{7,15}")){phone.setError("شماره معتبر وارد کنید");return;}long days=readNumber(settlement,0,36500);long id=db.saveCompany(old==null?0:old.id,n,vis,p,days);if(old==null){selected=id;dateFilter="همهٔ تاریخ‌ها";}endForm(s);refresh();toast(old==null?"شرکت اضافه شد":"اطلاعات شرکت به‌روز شد");}catch(SQLiteConstraintException e){name.setError("این نام برای شرکت دیگری ثبت شده است");}catch(IllegalArgumentException e){toast(e.getMessage());}});
    }
    private void orderDialog(Store.Order old){beginForm("order",old);final long companyId=selected;LinearLayout f=ui.col();
        EditText name=field(f,"نام کالا",old==null?"":old.name,1,100,"نام کامل محصول");
        EditText buy=field(f,"قیمت خرید · تومان",old==null?"":Values.money(old.buy),2,18,"۰"),retail=field(f,"قیمت مصرف · تومان",old==null?"":Values.money(old.retail),2,18,"۰");grouping(buy);grouping(retail);
        TextView percent=ui.tag("اختلاف نسبت به خرید: —",Ui.GREEN,Ui.MINT);f.addView(percent);ui.gap(f,20);
        TextWatcher update=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable e){try{percent.setText("اختلاف نسبت به خرید: "+fa(Values.percent(Values.number(buy.getText().toString(),1,999999999999L),Values.number(retail.getText().toString(),0,999999999999L))));}catch(IllegalArgumentException x){percent.setText("اختلاف نسبت به خرید: —");}}};buy.addTextChangedListener(update);retail.addTextChangedListener(update);update.afterTextChanged(buy.getText());
        EditText cartons=field(f,"تعداد سفارش",old==null?"":Values.money(old.cartons),2,10,"مثلاً ۱۲");grouping(cartons);
        f.addView(ui.text("واحد سفارش",20,Ui.MUTED,false));ui.gap(f,8);selectedUnit=old==null?"carton":old.unit;unitPicker=new RadioGroup(this);unitPicker.setOrientation(RadioGroup.HORIZONTAL);unitPicker.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        RadioButton piece=new RadioButton(this),carton=new RadioButton(this);piece.setId(102);carton.setId(101);piece.setText("عدد");carton.setText("کارتن");
        for(RadioButton option:new RadioButton[]{piece,carton}){option.setTextSize(20);option.setTextColor(Ui.GREEN);option.setMinHeight(ui.dp(52));unitPicker.addView(option,new RadioGroup.LayoutParams(0,-2,1));}unitPicker.setBackground(ui.bg(Ui.MINT,16));unitPicker.check("piece".equals(selectedUnit)?102:101);unitPicker.setOnCheckedChangeListener((group,id)->selectedUnit=id==102?"piece":"carton");f.addView(unitPicker);ui.gap(f,18);
        EditText delivery=field(f,"تاریخ تحویل · شمسی",old==null?Values.today():old.delivery,InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE,10,"۱۴۰۵/۰۷/۰۴");delivery.setTextDirection(View.TEXT_DIRECTION_LTR);
        EditText note=field(f,"توضیحات · اختیاری",old==null?"":old.note,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,500,"نکته‌ای برای این سفارش…");note.setMinLines(3);note.setGravity(Gravity.TOP|Gravity.START);
        Sheet s=sheet(old==null?"سفارش جدید":"ویرایش سفارش",company()==null?"":company().name,f,old==null?"ثبت سفارش":"ذخیرهٔ تغییرات",true);
        s.save.setOnClickListener(v->{try{Store.Order o=new Store.Order();o.company=companyId;o.id=old==null?0:old.id;o.created=old==null?Values.today():old.created;o.name=required(name,"نام کالا را وارد کنید");o.buy=readNumber(buy,1,999999999999L);o.retail=readNumber(retail,0,999999999999L);o.days=old==null?Math.max(0,company().settlementDays):old.days;o.unit=selectedUnit;o.cartons=readNumber(cartons,1,1000000);try{o.delivery=Values.date(delivery.getText().toString());}catch(IllegalArgumentException x){delivery.setError(x.getMessage());delivery.requestFocus();throw x;}o.note=note.getText().toString().trim();db.save(o);dateFilter="همهٔ تاریخ‌ها";endForm(s);refresh();toast("سفارش ذخیره شد");}catch(IllegalArgumentException x){toast(x.getMessage());}});
    }
    private void endForm(Sheet s){draftKind=null;fields.clear();editing=null;editingCompany=null;unitPicker=null;View focused=s.dialog.getCurrentFocus();if(focused!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);s.dialog.dismiss();}
    private long readNumber(EditText e,long min,long max){try{return Values.number(e.getText().toString(),min,max);}catch(IllegalArgumentException x){e.setError(x.getMessage());e.requestFocus();throw x;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void showProgress(){LinearLayout progress=ui.col();ui.pad(progress,24);progress.addView(new ProgressBar(this));ui.gap(progress,16);progress.addView(text("در حال آماده‌سازی عکس برای اشتراک…",false));busyDialog=new AlertDialog.Builder(this).setView(progress).setCancelable(false).create();busyDialog.show();}
    private void export(){if(exporting||visible.isEmpty())return;exporting=true;exportButton.setEnabled(false);showProgress();exportJob=new ExportJob(getApplicationContext(),company(),new ArrayList<>(visible));exportJob.attach(this);exportJob.start();}
    /** Keeps only application context while the activity is recreated. */
    private static final class ExportJob extends Thread {
        final Context app;final Store.Company company;final List<Store.Order> orders;
        final android.os.Handler main=new android.os.Handler(android.os.Looper.getMainLooper());
        MainActivity owner;List<File> files;boolean complete;
        ExportJob(Context app,Store.Company company,List<Store.Order> orders){super("share-report");this.app=app;this.company=company;this.orders=orders;}
        void attach(MainActivity activity){owner=activity;deliver();}
        void detach(){owner=null;}
        void deliver(){if(complete&&owner!=null){MainActivity target=owner;owner=null;target.reportReady(this);}}
        @Override public void run(){List<File> result;try{result=ImageReport.create(app,company,orders);}catch(Exception|OutOfMemoryError e){result=null;}final List<File> ready=result;main.post(()->{files=ready;complete=true;deliver();});}
    }
    private void reportReady(ExportJob job){exportJob=null;exporting=false;if(isFinishing()||isDestroyed())return;if(busyDialog!=null)busyDialog.dismiss();exportButton.setEnabled(!visible.isEmpty());if(job.files==null){toast("آماده‌سازی عکس انجام نشد؛ یک تاریخ را انتخاب کنید و دوباره تلاش کنید.");return;}previewIndex=0;preview(job.company,job.files);}
    @Override public Object onRetainNonConfigurationInstance(){if(exportJob!=null)exportJob.detach();return exportJob;}
    @Override protected void onResume(){super.onResume();if(exportJob!=null)exportJob.attach(this);}
    @Override protected void onPause(){if(exportJob!=null)exportJob.detach();super.onPause();}
    private void restorePreview(List<String> paths){Store.Company c=null;for(Store.Company candidate:companies)if(candidate.id==previewCompany)c=candidate;List<File> files=new ArrayList<>();for(String path:paths){File f=new File(path);if(!f.isFile()){previewPaths=null;return;}files.add(f);}if(c!=null&&!files.isEmpty())preview(c,files);else previewPaths=null;}
    private void preview(Store.Company company,List<File> files){previewCompany=company.id;previewPaths=new ArrayList<>();for(File f:files)previewPaths.add(f.getAbsolutePath());previewIndex=Math.max(0,Math.min(previewIndex,files.size()-1));
        LinearLayout body=ui.col();TextView caption=ui.text("آمادهٔ ارسال با نام "+company.name,20,Ui.GREEN,true);body.addView(caption);ui.gap(body,8);TextView filename=ui.text("",20,Ui.MUTED,false);body.addView(filename);ui.gap(body,14);
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(ui.border(Ui.WHITE,16,Ui.LINE));body.addView(image,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,16);
        LinearLayout nav=ui.row();ImageButton next=ui.iconButton("next","صفحهٔ بعد",Ui.GREEN),prev=ui.iconButton("prev","صفحهٔ قبل",Ui.GREEN);TextView page=text("",true);page.setGravity(Gravity.CENTER);nav.addView(prev);ui.weighted(nav,page);nav.addView(next);body.addView(nav);ui.gap(body,12);
        Button shareOne=ui.button("اشتراک همین صفحه","share",false);if(files.size()>1){body.addView(shareOne,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,12);}body.addView(ui.text("پیام‌رسان دلخواه را در مرحلهٔ بعد انتخاب کنید. عکس در گالری ذخیره نمی‌شود.",20,Ui.MUTED,false));
        final Bitmap[] thumb={null};Runnable show=()->{BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;Bitmap b=BitmapFactory.decodeFile(files.get(previewIndex).getAbsolutePath(),opts);image.setImageBitmap(b);Bitmap previous=thumb[0];thumb[0]=b;if(previous!=null)previous.recycle();filename.setText(files.get(previewIndex).getName());page.setText(fa((previewIndex+1)+" / "+files.size()));prev.setEnabled(previewIndex>0);next.setEnabled(previewIndex<files.size()-1);prev.setAlpha(prev.isEnabled()?1:.35f);next.setAlpha(next.isEnabled()?1:.35f);};
        Sheet s=sheet("پیش‌نمایش اشتراک",company.name,body,files.size()>1?"اشتراک همهٔ عکس‌ها":"انتخاب پیام‌رسان",false);s.save.setOnClickListener(v->share(company,files));shareOne.setOnClickListener(v->share(company,Collections.singletonList(files.get(previewIndex))));prev.setOnClickListener(v->{if(previewIndex>0){previewIndex--;show.run();}});next.setOnClickListener(v->{if(previewIndex<files.size()-1){previewIndex++;show.run();}});s.dialog.setOnDismissListener(d->{previewPaths=null;image.setImageDrawable(null);if(thumb[0]!=null){thumb[0].recycle();thumb[0]=null;}});show.run();
    }
    private void share(Store.Company c,List<File> files){try{for(File file:files){if(!file.isFile())throw new IllegalArgumentException("Expired share image");File directory=file.getParentFile();if(directory!=null)directory.setLastModified(System.currentTimeMillis());}ArrayList<Uri> uris=new ArrayList<>();for(File file:files)uris.add(FileProvider.getUriForFile(this,getPackageName()+".reports",file));Intent send=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);send.setType("image/png");send.putExtra(Intent.EXTRA_SUBJECT,"سفارش‌های "+c.name);send.putExtra(Intent.EXTRA_TEXT,"لیست سفارش‌های "+c.name);if(uris.size()==1)send.putExtra(Intent.EXTRA_STREAM,uris.get(0));else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        ClipData clip=ClipData.newUri(getContentResolver(),"سفارش‌های "+c.name,uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));send.setClipData(clip);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);Intent chooser=Intent.createChooser(send,"اشتراک سفارش‌های "+c.name);chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(chooser);
        }catch(ActivityNotFoundException e){toast("برنامه‌ای برای اشتراک عکس پیدا نشد");}catch(IllegalArgumentException e){toast("عکس در دسترس نیست؛ خروجی را دوباره بسازید");}}
    @Override public void onDestroy(){if(exportJob!=null)exportJob.detach();if(busyDialog!=null&&busyDialog.isShowing())busyDialog.dismiss();if(activeDialog!=null&&activeDialog.isShowing())activeDialog.dismiss();db.close();super.onDestroy();}
}
