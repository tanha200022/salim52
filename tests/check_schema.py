"""Exercise the real schema/migration SQL with SQLite, without Android SDK."""
from pathlib import Path
import re
import sqlite3
import unittest

SOURCE=(Path(__file__).parents[1]/'app/src/main/java/ir/orderbook/app/Store.java').read_text()
def sqls(text):return re.findall(r'db.execSQL\("([^"\n]+)"\)',text)
CREATE=sqls(SOURCE.split('public void onCreate',1)[1].split('@Override public void onUpgrade',1)[0])
UPGRADE=SOURCE.split('public void onUpgrade',1)[1].split('public List<Company>',1)[0]
MIGRATIONS={int(v):sqls(body) for v,body in re.findall(r'if\(oldVersion<(\d+)\)\{(.*?)\n        }',UPGRADE,re.S)}
def connect():
    db=sqlite3.connect(':memory:');db.execute('PRAGMA foreign_keys=ON');return db

def add(db,company=1,name='کالا',buy=100,retail=125,days=30,date='1405/07/04',quantity=2):
    db.execute('INSERT INTO orders(company,name,buy,retail,days,cartons,delivery,note,created) VALUES(?,?,?,?,?,?,?,?,?)',(company,name,buy,retail,days,quantity,date,'یادداشت',date))

class SchemaTest(unittest.TestCase):
    def setUp(self):
        self.db=connect()
        for sql in CREATE:self.db.execute(sql)
        self.db.executemany('INSERT INTO companies(id,name,visitor,phone) VALUES(?,?,?,?)',[(1,'الف','',''),(2,'ب','علی','09123456789')])
    def test_optional_contact_and_unique_name(self):
        self.assertEqual(self.db.execute('SELECT visitor,phone FROM companies WHERE id=1').fetchone(),('',''))
        with self.assertRaises(sqlite3.IntegrityError):self.db.execute("INSERT INTO companies(name,visitor,phone) VALUES('الف','','')")
    def test_constraints(self):
        for kw in [{'buy':0},{'quantity':0},{'company':999},{'retail':-1}]:
            with self.assertRaises(sqlite3.IntegrityError):add(self.db,**kw)
        add(self.db)
        for sql in ["UPDATE orders SET unit='invalid'",'UPDATE orders SET delivered=2','UPDATE companies SET settlement_days=-1']:
            with self.assertRaises(sqlite3.IntegrityError):self.db.execute(sql)
    def test_units_stay_separate(self):
        add(self.db);add(self.db,quantity=7)
        self.db.execute("UPDATE orders SET unit='piece' WHERE id=2")
        self.assertEqual(self.db.execute('SELECT unit,SUM(cartons) FROM orders GROUP BY unit ORDER BY unit').fetchall(),[('carton',2),('piece',7)])
    def test_history_survives_day_deletion_and_is_company_scoped(self):
        add(self.db);add(self.db,company=2)
        self.db.executemany('INSERT INTO products VALUES(?,?,?,?)',[(1,'کالا',100,125),(2,'کالا',200,250)])
        self.db.execute('DELETE FROM orders WHERE company=? AND created=?',(1,'1405/07/04'))
        self.assertEqual(self.db.execute('SELECT * FROM products ORDER BY company').fetchall(),[(1,'کالا',100,125),(2,'کالا',200,250)])
        self.assertEqual(self.db.execute('SELECT company FROM orders').fetchall(),[(2,)])
        self.db.execute('INSERT OR REPLACE INTO products VALUES(?,?,?,?)',(1,'کالا',110,140))
        self.assertEqual(self.db.execute('SELECT buy,retail FROM products WHERE company=1').fetchall(),[(110,140)])
    def test_delivery_scoped_to_company_and_day_new_orders_pending(self):
        add(self.db);add(self.db,date='1405/07/05');add(self.db,company=2)
        self.db.execute('UPDATE orders SET delivered=1 WHERE company=? AND created=?',(1,'1405/07/04'))
        add(self.db)
        self.assertEqual(self.db.execute('SELECT delivered FROM orders ORDER BY id').fetchall(),[(1,),(0,),(0,),(0,)])
        self.db.execute('UPDATE orders SET delivered=0 WHERE company=? AND created=?',(1,'1405/07/04'))
        self.assertEqual(self.db.execute('SELECT SUM(delivered) FROM orders').fetchone(),(0,))
    def test_edit_company_preserves_orders_and_history(self):
        add(self.db);self.db.execute("INSERT INTO products VALUES(1,'کالا',100,125)")
        before=self.db.execute('SELECT * FROM orders').fetchall()
        self.db.execute("UPDATE companies SET name='نام جدید',settlement_days=45 WHERE id=1")
        self.assertEqual(before,self.db.execute('SELECT * FROM orders').fetchall())
        self.assertEqual(self.db.execute('SELECT buy FROM products WHERE company=1').fetchone(),(100,))
    def test_company_delete_is_isolated(self):
        add(self.db);add(self.db,company=2)
        self.db.executemany('INSERT INTO products VALUES(?,?,?,?)',[(1,'کالا',100,125),(2,'کالا',200,250)])
        for table,column in [('orders','company'),('products','company'),('companies','id')]:self.db.execute(f'DELETE FROM {table} WHERE {column}=?',(1,))
        self.assertEqual(self.db.execute('SELECT id FROM companies').fetchall(),[(2,)])
        self.assertEqual(self.db.execute('SELECT company FROM orders').fetchall(),[(2,)])
        self.assertEqual(self.db.execute('SELECT company FROM products').fetchall(),[(2,)])
        self.assertEqual(self.db.execute('PRAGMA foreign_key_check').fetchall(),[])

class MigrationTest(unittest.TestCase):
    def migrate(self,start):
        db=connect()
        db.execute('CREATE TABLE companies(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, visitor TEXT NOT NULL, phone TEXT NOT NULL)')
        db.execute('CREATE TABLE orders(id INTEGER PRIMARY KEY, company INTEGER NOT NULL REFERENCES companies(id), name TEXT NOT NULL, buy INTEGER NOT NULL CHECK(buy>0), retail INTEGER NOT NULL CHECK(retail>=0), days INTEGER NOT NULL CHECK(days>=0), cartons INTEGER NOT NULL CHECK(cartons>0), delivery TEXT NOT NULL, note TEXT NOT NULL, created TEXT NOT NULL)')
        for cid in range(1,5):db.execute('INSERT INTO companies VALUES(?,?,?,?)',(cid,str(cid),'',''))
        for cid,days,price,date in [(1,30,100,'1405/07/03'),(1,30,110,'1405/07/04'),(1,30,120,'1405/07/04'),(2,10,200,'1405/07/04'),(2,60,210,'1405/07/05'),(4,0,400,'1405/07/04')]:add(db,company=cid,days=days,buy=price,date=date)
        original=db.execute('SELECT * FROM orders').fetchall()
        for sql in MIGRATIONS[2]:db.execute(sql)
        if start==2:
            db.execute("UPDATE orders SET unit='piece' WHERE id=1")
            db.execute('UPDATE companies SET settlement_days=90 WHERE id=1')
        for sql in MIGRATIONS[3]:db.execute(sql)
        rows=db.execute('SELECT * FROM orders').fetchall()
        self.assertEqual([row[:10] for row in rows],original)
        self.assertTrue(all(row[-1]==0 for row in rows))
        self.assertEqual(rows[0][10],'piece' if start==2 else 'carton')
        self.assertEqual(db.execute('SELECT settlement_days FROM companies ORDER BY id').fetchall(),[(90 if start==2 else 30,),(None,),(None,),(0,)])
        self.assertEqual(db.execute('SELECT company,buy FROM products ORDER BY company').fetchall(),[(1,120),(2,210),(4,400)])
        fresh=connect()
        for sql in CREATE:fresh.execute(sql)
        for table in ['companies','orders','products']:
            self.assertEqual(db.execute(f'PRAGMA table_info({table})').fetchall(),fresh.execute(f'PRAGMA table_info({table})').fetchall())
        self.assertEqual(db.execute('PRAGMA foreign_key_check').fetchall(),[])
    def test_upgrade_from_v1(self):self.migrate(1)
    def test_upgrade_from_v2(self):self.migrate(2)

if __name__=='__main__':unittest.main()
