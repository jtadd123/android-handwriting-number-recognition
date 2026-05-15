import os

base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI"

def modify_xml():
    path = os.path.join(base_dir, r"app\src\main\res\layout\activity_settings.xml")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()
    
    new_ui = """
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:gravity="center_vertical"
            android:orientation="horizontal">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="Tiếng Anh (English)"
                android:textColor="@color/text_primary"
                android:textSize="16sp" />

            <com.google.android.material.materialswitch.MaterialSwitch
                android:id="@+id/switch_language"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        </LinearLayout>

        <com.google.android.material.button.MaterialButton
    """
    
    if "switch_language" not in c:
        c = c.replace("<com.google.android.material.button.MaterialButton", new_ui)
        with open(path, "w", encoding="utf-8") as f: f.write(c)

def modify_java():
    path = os.path.join(base_dir, r"app\src\main\java\dat\nguyenvan\smarthandwritingai\SettingsActivity.java")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()

    if "switchLanguage" not in c:
        c = c.replace("private MaterialSwitch switchFirebase;", "private MaterialSwitch switchFirebase;\n    private MaterialSwitch switchLanguage;")
        c = c.replace("switchFirebase = findViewById(R.id.switch_firebase);", "switchFirebase = findViewById(R.id.switch_firebase);\n        switchLanguage = findViewById(R.id.switch_language);")
        
        logic = """
        switchLanguage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isEnglish", isChecked).apply();
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(java.util.Locale.forLanguageTag(isChecked ? "en" : "vi"))
            );
        });
        """
        c = c.replace("findViewById(R.id.btn_clear_data).setOnClickListener", logic + "\n        findViewById(R.id.btn_clear_data).setOnClickListener")
        c = c.replace("switchFirebase.setChecked(sync);", "switchFirebase.setChecked(sync);\n        switchLanguage.setChecked(prefs.getBoolean(\"isEnglish\", false));")
        with open(path, "w", encoding="utf-8") as f: f.write(c)

modify_xml()
modify_java()
print("Done Script 5")
