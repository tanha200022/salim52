"""Validate actual SQL extracted from Store.java without Android SDK."""
from pathlib import Path
import re
import sqlite3
import unittest

class SchemaTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.execute('PRAGMA foreign_keys=ON')
        source = (Path(__file__).parents[1] / 'app/src/main/java/ir/orderbook/app/Store.java').read_text()
        for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)', source.split('public void onCreate',1)[1].split('@Override public void onUpgrade',1)[0]):
            self.db.execute(sql)
        self.db.execute("INSERT INTO companies(id,name,visitor,phone) VALUES(1,'الف','رضا','09123456789')")
        self.db.execute("INSERT INTO companies(id,name,visitor,phone) VALUES(2,'ب','علی','09123456780')")
    def add(self, company=1, buy=100, cartons=2, date='1405/07/04'):
        self.db.execute('INSERT INTO orders(company,name,buy,retail,days,cartons,delivery,note,created) VALUES(?,?,?,?,?,?,?,?,?)', (company,'کالا',buy,125,30,cartons,date,'',date))
    def test_company_isolation_and_order(self):
        self.add(date='1405/07/03'); self.add(company=2); self.add()
        rows = self.db.execute('SELECT company,created FROM orders WHERE company=1 ORDER BY created DESC,id DESC').fetchall()
        self.assertEqual(rows, [(1,'1405/07/04'),(1,'1405/07/03')])
    def test_invalid_values(self):
        for options in ({'buy':0}, {'cartons':0}, {'company':999}):
            with self.assertRaises(sqlite3.IntegrityError): self.add(**options)
    def test_duplicate_company(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO companies(name,visitor,phone) VALUES('الف','رضا','09123456789')")
    def test_edit_delete(self):
        self.add(); self.add(company=2)
        self.db.execute('UPDATE orders SET cartons=5 WHERE id=1 AND company=1')
        self.assertEqual(self.db.execute('SELECT cartons FROM orders WHERE id=1').fetchone()[0],5)
        self.db.execute('DELETE FROM orders WHERE id=1')
        self.assertEqual(self.db.execute('SELECT company FROM orders').fetchall(),[(2,)])

    def test_units_and_separate_totals(self):
        self.add(); self.add()
        self.db.execute("UPDATE orders SET unit='piece',cartons=7 WHERE id=2")
        self.assertEqual(self.db.execute('SELECT unit,SUM(cartons) FROM orders GROUP BY unit ORDER BY unit').fetchall(), [('carton',2),('piece',7)])
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute("UPDATE orders SET unit='invalid'")
    def test_company_edit_preserves_orders(self):
        self.add()
        before=self.db.execute('SELECT * FROM orders').fetchall()
        self.db.execute("UPDATE companies SET name='جدید',visitor='جدید',phone='09123456781',settlement_days=45 WHERE id=1")
        self.assertEqual(before,self.db.execute('SELECT * FROM orders').fetchall())
        self.assertEqual(self.db.execute('SELECT settlement_days FROM companies WHERE id=1').fetchone(),(45,))
        with self.assertRaises(sqlite3.IntegrityError): self.db.execute('UPDATE companies SET settlement_days=-1 WHERE id=1')

class MigrationTest(unittest.TestCase):
    def test_v1_data_preserved_and_settlement_inferred_only_when_unambiguous(self):
        db=sqlite3.connect(':memory:')
        db.execute('PRAGMA foreign_keys=ON')
        db.execute('CREATE TABLE companies(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, visitor TEXT NOT NULL, phone TEXT NOT NULL)')
        db.execute('CREATE TABLE orders(id INTEGER PRIMARY KEY, company INTEGER NOT NULL REFERENCES companies(id), name TEXT NOT NULL, buy INTEGER NOT NULL CHECK(buy>0), retail INTEGER NOT NULL CHECK(retail>=0), days INTEGER NOT NULL CHECK(days>=0), cartons INTEGER NOT NULL CHECK(cartons>0), delivery TEXT NOT NULL, note TEXT NOT NULL, created TEXT NOT NULL)')
        for cid in range(1,5): db.execute('INSERT INTO companies VALUES(?,?,?,?)',(cid,str(cid),'ویزیتور','09123456789'))
        for cid,days in [(1,30),(1,30),(2,10),(2,60),(4,0)]:
            db.execute("INSERT INTO orders(company,name,buy,retail,days,cartons,delivery,note,created) VALUES(?,'کالا',100,125,?,2,'1405/07/04','یادداشت','1405/07/04')",(cid,days))
        before=db.execute('SELECT * FROM orders').fetchall()
        source=(Path(__file__).parents[1]/'app/src/main/java/ir/orderbook/app/Store.java').read_text()
        upgrade=source.split('public void onUpgrade',1)[1].split('public List<Company>',1)[0]
        for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)',upgrade): db.execute(sql)
        after=db.execute('SELECT * FROM orders').fetchall()
        self.assertEqual(before,[row[:-1] for row in after])
        self.assertTrue(all(row[-1]=='carton' for row in after))
        self.assertEqual(db.execute('SELECT settlement_days FROM companies ORDER BY id').fetchall(),[(30,),(None,),(None,),(0,)])
        self.assertEqual(db.execute('PRAGMA foreign_key_check').fetchall(),[])
        # New and upgraded tables expose the same column order used by Store cursors.
        fresh=sqlite3.connect(':memory:')
        create=source.split('public void onCreate',1)[1].split('@Override public void onUpgrade',1)[0]
        for sql in re.findall(r'db.execSQL\("([^"\n]+)"\)',create): fresh.execute(sql)
        for table in ['companies','orders']:
            self.assertEqual(db.execute('PRAGMA table_info('+table+')').fetchall(),fresh.execute('PRAGMA table_info('+table+')').fetchall())

if __name__ == '__main__': unittest.main()

