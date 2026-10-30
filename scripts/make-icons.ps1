# Draws the project icon (docs/icon.png, 512x512) and derives the launcher icons and TV banner from it.
# Flat art: a TV with a globe on screen and a puzzle piece for add-ons.
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot
$res = Join-Path $root 'app\app\src\main\res'

function C([int]$r, [int]$g, [int]$b) { [System.Drawing.Color]::FromArgb(255, $r, $g, $b) }
function RoundRect([float]$x, [float]$y, [float]$w, [float]$h, [float]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddArc($x, $y, 2 * $r, 2 * $r, 180, 90)
    $p.AddArc($x + $w - 2 * $r, $y, 2 * $r, 2 * $r, 270, 90)
    $p.AddArc($x + $w - 2 * $r, $y + $h - 2 * $r, 2 * $r, 2 * $r, 0, 90)
    $p.AddArc($x, $y + $h - 2 * $r, 2 * $r, 2 * $r, 90, 90)
    $p.CloseFigure()
    $p
}

# Draws the icon into a square of side $s at ($ox, $oy).
function DrawIcon($g, [float]$ox, [float]$oy, [float]$s, [bool]$background) {
    $u = $s / 512
    if ($background) {
        $g.FillPath((New-Object System.Drawing.SolidBrush (C 24 30 54)), (RoundRect $ox $oy $s $s (96 * $u)))
    }
    # stand
    $stand = New-Object System.Drawing.SolidBrush (C 160 170 190)
    $g.FillPath($stand, (RoundRect ($ox + 196 * $u) ($oy + 392 * $u) (120 * $u) (22 * $u) (11 * $u)))
    $g.FillRectangle($stand, $ox + 244 * $u, $oy + 360 * $u, 24 * $u, 40 * $u)
    # TV body and screen
    $g.FillPath((New-Object System.Drawing.SolidBrush (C 225 230 240)), (RoundRect ($ox + 56 * $u) ($oy + 104 * $u) (400 * $u) (264 * $u) (34 * $u)))
    $g.FillPath((New-Object System.Drawing.SolidBrush (C 30 136 229)), (RoundRect ($ox + 76 * $u) ($oy + 124 * $u) (360 * $u) (224 * $u) (20 * $u)))
    # globe
    $cx = $ox + 256 * $u; $cy = $oy + 236 * $u; $r = 84 * $u
    $pen = New-Object System.Drawing.Pen ([System.Drawing.Color]::White), (12 * $u)
    $g.DrawEllipse($pen, $cx - $r, $cy - $r, 2 * $r, 2 * $r)
    $g.DrawEllipse($pen, $cx - $r * 0.45, $cy - $r, $r * 0.9, 2 * $r)
    $g.DrawLine($pen, $cx, $cy - $r, $cx, $cy + $r)
    $g.DrawLine($pen, $cx - $r, $cy, $cx + $r, $cy)
    $g.DrawLine($pen, $cx - $r * 0.86, $cy - $r * 0.5, $cx + $r * 0.86, $cy - $r * 0.5)
    $g.DrawLine($pen, $cx - $r * 0.86, $cy + $r * 0.5, $cx + $r * 0.86, $cy + $r * 0.5)
    # puzzle piece (add-ons), bottom-right corner of the screen
    $pc = New-Object System.Drawing.SolidBrush (C 255 167 38)
    $px = $ox + 352 * $u; $py = $oy + 286 * $u; $pw = 92 * $u; $knob = 30 * $u
    $piece = New-Object System.Drawing.Drawing2D.GraphicsPath
    $piece.AddPath((RoundRect $px $py $pw $pw (12 * $u)), $false)
    $piece.AddEllipse($px + $pw / 2 - $knob / 2, $py - $knob * 0.7, $knob, $knob)
    $piece.AddEllipse($px - $knob * 0.7, $py + $pw / 2 - $knob / 2, $knob, $knob)
    $piece.FillMode = 'Winding'
    $outline = New-Object System.Drawing.Pen (C 24 30 54), (10 * $u)
    $g.DrawPath($outline, $piece)
    $g.FillPath($pc, $piece)
}

function NewCanvas([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::Transparent)
    @($bmp, $g)
}

function Save($bmp, [string]$path) {
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

# Project icon
$bmp, $g = NewCanvas 512 512
DrawIcon $g 0 0 512 $true
Save $bmp (Join-Path $root 'docs\icon.png')

# Launcher icons
foreach ($d in @(@('mipmap-mdpi', 48), @('mipmap-hdpi', 72), @('mipmap-xhdpi', 96), @('mipmap-xxhdpi', 144), @('mipmap-xxxhdpi', 192))) {
    $b, $gg = NewCanvas $d[1] $d[1]
    $gg.InterpolationMode = 'HighQualityBicubic'
    $gg.DrawImage($bmp, 0, 0, $d[1], $d[1])
    Save $b (Join-Path $res "$($d[0])\ic_launcher.png")
}

# TV banner 320x180: icon on the left, name on the right
$bn, $gb = NewCanvas 320 180
$gb.FillRectangle((New-Object System.Drawing.SolidBrush (C 24 30 54)), 0, 0, 320, 180)
DrawIcon $gb 8 26 128 $false
$font = New-Object System.Drawing.Font 'Segoe UI', 34, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$sf = New-Object System.Drawing.StringFormat
$sf.LineAlignment = 'Center'
$gb.DrawString("Zero`nBrowser", $font, [System.Drawing.Brushes]::White, [System.Drawing.RectangleF]::new(144, 0, 176, 180), $sf)
Save $bn (Join-Path $res 'drawable-xhdpi\banner.png')
