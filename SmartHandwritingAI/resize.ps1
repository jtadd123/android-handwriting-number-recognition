Add-Type -AssemblyName System.Drawing

$srcImgPath = "C:\Users\admin\.gemini\antigravity\brain\562c03eb-8df8-4bbb-9899-3ddf584ced1c\ai_app_icon_1778854452091.png"
$baseDir = "d:\MoBai\android-handwriting-number-recognition\SmartHandwritingAI\app\src\main\res"

$sizes = @{
    "mipmap-mdpi" = 48
    "mipmap-hdpi" = 72
    "mipmap-xhdpi" = 96
    "mipmap-xxhdpi" = 144
    "mipmap-xxxhdpi" = 192
}

$srcBmp = New-Object System.Drawing.Bitmap($srcImgPath)

foreach ($key in $sizes.Keys) {
    $size = $sizes[$key]
    $folderPath = Join-Path $baseDir $key
    if (-not (Test-Path $folderPath)) {
        New-Item -ItemType Directory -Force -Path $folderPath | Out-Null
    }

    # Standard
    $newBmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($newBmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.DrawImage($srcBmp, 0, 0, $size, $size)
    $g.Dispose()
    $newBmp.Save((Join-Path $folderPath "ic_launcher.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $newBmp.Save((Join-Path $folderPath "ic_launcher_round.png"), [System.Drawing.Imaging.ImageFormat]::Png)

    # Foreground
    $fgSize = [math]::Floor($size * 0.8)
    $fgBmp = New-Object System.Drawing.Bitmap($size, $size)
    $g2 = [System.Drawing.Graphics]::FromImage($fgBmp)
    $g2.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $offset = [math]::Floor(($size - $fgSize) / 2)
    $g2.DrawImage($srcBmp, $offset, $offset, $fgSize, $fgSize)
    $g2.Dispose()
    $fgBmp.Save((Join-Path $folderPath "ic_launcher_foreground.png"), [System.Drawing.Imaging.ImageFormat]::Png)

    $newBmp.Dispose()
    $fgBmp.Dispose()
}
$srcBmp.Dispose()
Write-Host "Done!"
