import os

base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI"

def update_manifest():
    path = os.path.join(base_dir, r"app\src\main\AndroidManifest.xml")
    with open(path, "r", encoding="utf-8") as f:
        c = f.read()

    provider = """
        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.provider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/provider_paths" />
        </provider>
    """
    
    if "OnboardingActivity" not in c:
        c = c.replace("</application>", "    <activity android:name=\".OnboardingActivity\" android:exported=\"false\" />\n</application>")
    
    if "FileProvider" not in c:
        c = c.replace("</application>", provider + "\n</application>")

    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

def write_provider_paths():
    os.makedirs(os.path.join(base_dir, r"app\src\main\res\xml"), exist_ok=True)
    path = os.path.join(base_dir, r"app\src\main\res\xml\provider_paths.xml")
    c = """<?xml version="1.0" encoding="utf-8"?>
<paths>
    <cache-path name="cache" path="." />
</paths>
"""
    with open(path, "w", encoding="utf-8") as f:
        f.write(c)

update_manifest()
write_provider_paths()
print("Done Script 4")
