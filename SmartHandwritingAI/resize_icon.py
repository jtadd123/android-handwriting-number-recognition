import os
from PIL import Image

src_img = r"C:\Users\admin\.gemini\antigravity\brain\562c03eb-8df8-4bbb-9899-3ddf584ced1c\ai_app_icon_1778854452091.png"
base_dir = r"d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI\app\src\main\res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

img = Image.open(src_img)

for folder, size in sizes.items():
    folder_path = os.path.join(base_dir, folder)
    if not os.path.exists(folder_path):
        os.makedirs(folder_path)
    
    # Standard icon
    resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
    resized_img.save(os.path.join(folder_path, "ic_launcher.png"))
    
    # Round icon (we can just use the same or circular crop, let's just make it circular)
    # Actually adaptive icons in Android 8.0+ handle this, but for older versions:
    # A quick trick is to leave it square or just reuse it
    resized_img.save(os.path.join(folder_path, "ic_launcher_round.png"))
    
    # Create foreground for adaptive icon (scale a bit to fit inside adaptive safe zone)
    # Safe zone is 66/108 = 61%
    fg_size = int(size * 0.8) 
    fg_img = img.resize((fg_size, fg_size), Image.Resampling.LANCZOS)
    
    # Paste on transparent canvas
    canvas = Image.new("RGBA", (size, size), (0,0,0,0))
    offset = ((size - fg_size) // 2, (size - fg_size) // 2)
    canvas.paste(fg_img, offset)
    canvas.save(os.path.join(folder_path, "ic_launcher_foreground.png"))

print("Icons generated successfully!")
