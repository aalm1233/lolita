# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in the
# http://developer.android.com/guide/developing/tools/proguard.html

# === 调试信息 ===
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# === Room ===
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# === Gson ===
-keep class com.lolita.app.data.file.BackupData { *; }
-keep class com.lolita.app.data.local.entity.** { *; }
-keep class com.lolita.app.data.remote.** { *; }
-keepattributes Signature,*Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# === Apache POI ===
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.apache.commons.**
-dontwarn org.openxmlformats.**
-dontwarn org.apache.logging.log4j.**
-dontwarn aQute.bnd.annotation.**
-dontwarn java.awt.**
-dontwarn org.osgi.framework.**
-dontwarn com.graphbuilder.**
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }

# === Kotlin Coroutines ===
-dontwarn kotlinx.coroutines.**

# === Enum 保护 ===
-keepclassmembers enum * { *; }
