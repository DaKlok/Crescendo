# Pravidlá pre release build (R8).
# Väčšina knižníc (Room, WorkManager, Compose, Google Play služby, ML Kit) má vlastné pravidlá.

# Čitateľné chyby v Play Console (čísla riadkov; mapping.txt sa nahrá automaticky s .aab)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# PdfBox-Android: písma, kódovania a filtre sa hľadajú podľa názvu (reflexia, zdroje v JAR)
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# Widgety a prijímače sú v manifeste (R8 ich ponechá samo); triedy widgetu pre istotu celé
-keep class com.daklok.crescendo.widget.** extends android.appwidget.AppWidgetProvider

# WorkManager vytvára workerov podľa názvu triedy
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
