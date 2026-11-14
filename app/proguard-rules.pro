# Room entities / DAOs are handled by the Room compiler's generated keep rules.
# Glance widget receiver + workers are referenced from the manifest (kept by AAPT rules).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Strip verbose logging in release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
