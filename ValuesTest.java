package ir.orderbook.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ValuesTest {
    @Test public void positiveAndNegativePercent(){assertEquals("25.00٪",Values.percent(100000,125000));assertEquals("-20.00٪",Values.percent(100000,80000));}
    @Test public void parsesPersianGroupedNumber(){assertEquals(123456,Values.number("۱۲۳٬۴۵۶",1,999999));}
    @Test(expected=IllegalArgumentException.class) public void rejectsZeroBuy(){Values.number("0",1,999999);}
    @Test public void formatsThousands(){assertEquals("1,234,567",Values.money(1234567));}
}
