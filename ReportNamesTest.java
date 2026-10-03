package ir.orderbook.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReportNamesTest {
    @Test public void preservesPersianCompanyName(){assertEquals("شرکت پخش بهار",ReportNames.stem("شرکت پخش بهار"));}
    @Test public void removesPathSeparators(){String n=ReportNames.stem("شرکت/بهار\\تهران:مرکزی");assertFalse(n.contains("/"));assertFalse(n.contains("\\"));assertFalse(n.contains(":"));}
    @Test public void rejectsDotOnlyName(){assertEquals("شرکت",ReportNames.stem(".."));assertEquals("شرکت",ReportNames.stem("  "));}
    @Test public void limitsUnicodeWithoutSplittingSurrogates(){String n=ReportNames.stem("🌷".repeat(100));assertEquals(60,n.codePointCount(0,n.length()));assertEquals(120,n.length());}
    @Test public void removesBidiOverrides(){assertFalse(ReportNames.stem("بهار\u202Egnp").contains("\u202E"));}
}
