# Keep Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Hilt
-keep class * extends dagger.hilt.internal.GeneratedComponent
-keep class * extends dagger.hilt.internal.ComponentManager

# Keep Models
-keep class com.clipvault.android.data.local.entity.** { *; }
-keep class com.clipvault.android.ui.viewmodel.** { *; }
