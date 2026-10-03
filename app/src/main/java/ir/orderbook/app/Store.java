package ir.orderbook.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    public static class Company {
        long id, settlementDays=-1; String name, visitor, phone;
        public String toString() { return name; }
    }
    public static class Product {
        String name;long buy,retail;
        public String toString(){return name;}
    }
    public static class Order {
        long id, company, buy, retail, days, cartons;
        String name, delivery, note, created, unit="carton";
        boolean delivered;
        String unitLabel() { return "piece".equals(unit) ? "عدد" : "کارتن"; }
    }
    public Store(Context c) { super(c, "orders.db", null, 3); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE companies(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, visitor TEXT NOT NULL, phone TEXT NOT NULL, settlement_days INTEGER CHECK(settlement_days>=0))");
        db.execSQL("CREATE TABLE orders(id INTEGER PRIMARY KEY, company INTEGER NOT NULL REFERENCES companies(id), name TEXT NOT NULL, buy INTEGER NOT NULL CHECK(buy>0), retail INTEGER NOT NULL CHECK(retail>=0), days INTEGER NOT NULL CHECK(days>=0), cartons INTEGER NOT NULL CHECK(cartons>0), delivery TEXT NOT NULL, note TEXT NOT NULL, created TEXT NOT NULL, unit TEXT NOT NULL DEFAULT 'carton' CHECK(unit IN ('carton','piece')), delivered INTEGER NOT NULL DEFAULT 0 CHECK(delivered IN (0,1)))");
        db.execSQL("CREATE INDEX orders_company_date ON orders(company, created DESC, id DESC)");
        db.execSQL("CREATE TABLE products(company INTEGER NOT NULL REFERENCES companies(id), name TEXT NOT NULL COLLATE NOCASE, buy INTEGER NOT NULL CHECK(buy>0), retail INTEGER NOT NULL CHECK(retail>=0), PRIMARY KEY(company,name))");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if(oldVersion<2){
            db.execSQL("ALTER TABLE companies ADD COLUMN settlement_days INTEGER CHECK(settlement_days>=0)");
            db.execSQL("ALTER TABLE orders ADD COLUMN unit TEXT NOT NULL DEFAULT 'carton' CHECK(unit IN ('carton','piece'))");
            // Only infer settlement when all historical orders agree. Preserve every legacy order.
            db.execSQL("UPDATE companies SET settlement_days=(SELECT MIN(days) FROM orders WHERE orders.company=companies.id HAVING MIN(days)=MAX(days))");
        }
        if(oldVersion<3){
            db.execSQL("ALTER TABLE orders ADD COLUMN delivered INTEGER NOT NULL DEFAULT 0 CHECK(delivered IN (0,1))");
        db.execSQL("CREATE TABLE products(company INTEGER NOT NULL REFERENCES companies(id), name TEXT NOT NULL COLLATE NOCASE, buy INTEGER NOT NULL CHECK(buy>0), retail INTEGER NOT NULL CHECK(retail>=0), PRIMARY KEY(company,name))");
            db.execSQL("INSERT OR REPLACE INTO products(company,name,buy,retail) SELECT company,TRIM(name),buy,retail FROM orders ORDER BY created ASC,id ASC");
        }
    }
    public List<Company> companies() {
        List<Company> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM companies ORDER BY name", null)) {
            while (c.moveToNext()) {
                Company x = new Company(); x.id=c.getLong(0); x.name=c.getString(1); x.visitor=c.getString(2); x.phone=c.getString(3); x.settlementDays=c.isNull(4)?-1:c.getLong(4); list.add(x);
            }
        } return list;
    }
    public long saveCompany(long id,String name,String visitor,String phone,long settlementDays) {
        if(settlementDays<0||settlementDays>36500)throw new IllegalArgumentException("مدت تسویه معتبر نیست");
        ContentValues v=new ContentValues();v.put("name",name);v.put("visitor",visitor);v.put("phone",phone);v.put("settlement_days",settlementDays);
        if(id==0)return getWritableDatabase().insertOrThrow("companies",null,v);
        if(getWritableDatabase().update("companies",v,"id=?",new String[]{String.valueOf(id)})!=1)throw new IllegalArgumentException("شرکت پیدا نشد");
        return id;
    }
    public List<Order> orders(long company) {
        List<Order> list = new ArrayList<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT * FROM orders WHERE company=? ORDER BY created DESC,id DESC",new String[]{String.valueOf(company)})) {
            while(c.moveToNext()) {
                Order o=new Order(); o.id=c.getLong(0); o.company=c.getLong(1); o.name=c.getString(2); o.buy=c.getLong(3); o.retail=c.getLong(4); o.days=c.getLong(5); o.cartons=c.getLong(6); o.delivery=c.getString(7); o.note=c.getString(8); o.created=c.getString(9); o.unit=c.getString(10);o.delivered=c.getInt(11)!=0; list.add(o);
            }
        } return list;
    }
    public List<Product> products(long company){
        List<Product> list=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT name,buy,retail FROM products WHERE company=? ORDER BY name",new String[]{String.valueOf(company)})){
            while(c.moveToNext()){Product p=new Product();p.name=c.getString(0);p.buy=c.getLong(1);p.retail=c.getLong(2);list.add(p);}
        }return list;
    }
    public void save(Order o) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{
            o.name=o.name.trim();
            ContentValues v=new ContentValues();v.put("company",o.company);v.put("name",o.name);v.put("buy",o.buy);v.put("retail",o.retail);v.put("days",o.days);v.put("cartons",o.cartons);v.put("delivery",o.delivery);v.put("note",o.note);v.put("created",o.created);v.put("unit",o.unit);v.put("delivered",o.delivered?1:0);
            if(o.id==0)db.insertOrThrow("orders",null,v);
            else if(db.update("orders",v,"id=? AND company=?",new String[]{""+o.id,""+o.company})!=1)throw new IllegalArgumentException("سفارش پیدا نشد");
            ContentValues product=new ContentValues();product.put("company",o.company);product.put("name",o.name);product.put("buy",o.buy);product.put("retail",o.retail);
            if(db.insertWithOnConflict("products",null,product,SQLiteDatabase.CONFLICT_REPLACE)==-1)throw new IllegalStateException("Cannot save product history");
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void markDayDelivered(long company,String date,boolean delivered){
        ContentValues v=new ContentValues();v.put("delivered",delivered?1:0);
        getWritableDatabase().update("orders",v,"company=? AND created=?",new String[]{String.valueOf(company),date});
    }
    public void deleteDay(long company,String date){getWritableDatabase().delete("orders","company=? AND created=?",new String[]{String.valueOf(company),date});}
    public void deleteCompany(long company){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{String[] args={String.valueOf(company)};db.delete("orders","company=?",args);db.delete("products","company=?",args);db.delete("companies","id=?",args);db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public void delete(long id) { getWritableDatabase().delete("orders","id=?",new String[]{""+id}); }
}
