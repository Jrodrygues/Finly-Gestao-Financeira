# Rules for R8 / Proguard Code Shrinking

# MPAndroidChart
-keep class com.github.mikephil.charting.charts.** { *; }
-keep class com.github.mikephil.charting.data.** { *; }
-keep class com.github.mikephil.charting.components.** { *; }
-keep class com.github.mikephil.charting.formatter.** { *; }
-dontwarn com.github.mikephil.charting.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Firebase Models & Serialization
-keepclassmembers class com.jesse.finly.models.** {
    <fields>;
    <init>(...);
}
-keep class com.jesse.finly.models.** { *; }

# ViewBinding
-keep class com.jesse.finly.databinding.** { *; }
