package ir.orderbook.app;

import android.icu.util.Calendar;
import android.icu.util.ULocale;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class Values {
    private Values() {}
    public static String ascii(String text) {
        StringBuilder s = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c >= '۰' && c <= '۹') s.append((char) ('0' + c - '۰'));
            else if (c >= '٠' && c <= '٩') s.append((char) ('0' + c - '٠'));
            else s.append(c);
        }
        return s.toString().trim();
    }
    public static long number(String text, long min, long max) {
        String s = ascii(text).replace(",", "").replace("٬", "");
        if (!s.matches("[0-9]+")) throw new IllegalArgumentException("عدد معتبر وارد کنید");
        long n;
        try { n = Long.parseLong(s); } catch (NumberFormatException e) { throw new IllegalArgumentException("عدد بیش از حد بزرگ است"); }
        if (n < min || n > max) throw new IllegalArgumentException("عدد باید بین " + money(min) + " و " + money(max) + " باشد");
        return n;
    }
    public static String money(long n) {
        return new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US)).format(n);
    }
    public static String percent(long buy, long retail) {
        if (buy <= 0) return "—";
        return BigDecimal.valueOf(retail).subtract(BigDecimal.valueOf(buy))
            .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(buy), 2, RoundingMode.HALF_UP).toPlainString() + "٪";
    }
    static Calendar calendar() { return Calendar.getInstance(new ULocale("fa_IR@calendar=persian")); }
    public static String today() {
        Calendar c = calendar();
        return String.format(Locale.US, "%04d/%02d/%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }
    public static String date(String input) {
        String s = ascii(input);
        if (!s.matches("[0-9]{4}/[0-9]{1,2}/[0-9]{1,2}")) throw new IllegalArgumentException("تاریخ را به صورت ۱۴۰۵/۰۷/۰۴ وارد کنید");
        String[] p = s.split("/");
        int y = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]), d = Integer.parseInt(p[2]);
        if (y < 1200 || y > 1600) throw new IllegalArgumentException("سال باید بین ۱۲۰۰ و ۱۶۰۰ باشد");
        Calendar c = calendar(); c.clear(); c.setLenient(false); c.set(y, m - 1, d);
        try { c.getTimeInMillis(); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("تاریخ شمسی معتبر نیست"); }
        return String.format(Locale.US, "%04d/%02d/%02d", y, m, d);
    }
}
