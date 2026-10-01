package ir.orderbook.app;

import java.text.Normalizer;

public final class ReportNames {
    private ReportNames() {}
    public static String stem(String name){
        String clean=Normalizer.normalize(name,Normalizer.Form.NFC)
            .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|\\u202A-\\u202E\\u2066-\\u2069]","_")
            .replaceAll("\\s+"," ").trim();
        if(clean.isEmpty()||clean.equals(".")||clean.equals(".."))clean="شرکت";
        int n=clean.codePointCount(0,clean.length());
        if(n>60)clean=clean.substring(0,clean.offsetByCodePoints(0,60));
        return clean;
    }
}
