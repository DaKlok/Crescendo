# Pravidlá pre release build (minifikácia je predvolene vypnutá).

# PdfBox-Android (ak by sa minifikácia zapla)
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
