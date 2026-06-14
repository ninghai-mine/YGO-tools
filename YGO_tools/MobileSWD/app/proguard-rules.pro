# SmallWorldDrawer ProGuard Rules
# SQLite is used via android.database.sqlite — no extra rules needed
# Keep all model classes
-keep class com.smallworld.drawer.data.** { *; }
-keep class com.smallworld.drawer.engine.** { *; }
