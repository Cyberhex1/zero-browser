# Draws the Arcade-style Zero logo (a neon cyan circle crossed by a pink slash) and every icon made from it:
#   docs/icon.png (512x512), legacy square and round launcher icons, adaptive-icon foreground layers,
#   and the 320x180 Fire TV banner.
Add-Type -AssemblyName System.Drawing
$root = Split-Path $PSScriptRoot
$res = Join-Path $root 'app\app\src\main\res'
$fonts = New-Object System.Drawing.Text.PrivateFontCollection
$fonts.AddFontFile((Join-Path $res '..\assets\fonts\SpaceGrotesk-Bold.ttf'))
$family = $fonts.Families[0]

function C([int]$a, [int]$r, [int]$g, [int]$b) { [System.Drawing.Color]::FromArgb($a, $r, $g, $b) }
$ground = C 255 7 7 11
$cyan = @(34, 211, 238)
$pink = @(244, 114, 182)

# Strokes a path several times, wide and faint first, for a neon glow.
function Glow($g, $path, $rgb, [float]$width) {
    foreach ($step in @(@(3.4, 26), @(2.3, 48), @(1.6, 90), @(1.0, 255))) {
        $pen = New-Object System.Drawing.Pen (C $step[1] $rgb[0] $rgb[1] $rgb[2]), ($width * $step[0])
        $pen.StartCap = 'Round'; $pen.EndCap = 'Round'
        $g.DrawPath($pen, $path)
    }
}

# The logo, centred on (cx, cy), fitting a circle of radius r (glow included).
function DrawLogo($g, [float]$cx, [float]$cy, [float]$r) {
    $ring = $r * 0.64
    $w = $r * 0.12
    $circle = New-Object System.Drawing.Drawing2D.GraphicsPath
    $circle.AddEllipse($cx - $ring, $cy - $ring, 2 * $ring, 2 * $ring)
    Glow $g $circle $cyan $w
    $slash = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 0.62
    $slash.AddLine($cx - $d, $cy + $d, $cx + $d, $cy - $d)
    Glow $g $slash $pink $w
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

function RoundRect([float]$s, [float]$rad) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $p.AddArc(0, 0, 2 * $rad, 2 * $rad, 180, 90)
    $p.AddArc($s - 2 * $rad, 0, 2 * $rad, 2 * $rad, 270, 90)
    $p.AddArc($s - 2 * $rad, $s - 2 * $rad, 2 * $rad, 2 * $rad, 0, 90)
    $p.AddArc(0, $s - 2 * $rad, 2 * $rad, 2 * $rad, 90, 90)
    $p.CloseFigure()
    $p
}

# Square icon on a black rounded tile (docs/icon.png and the legacy launcher icon).
function SquareIcon([int]$s) {
    $bmp, $g = NewCanvas $s $s
    $g.FillPath((New-Object System.Drawing.SolidBrush $ground), (RoundRect $s ($s * 0.18)))
    DrawLogo $g ($s / 2) ($s / 2) ($s * 0.40)
    $bmp
}

# Round icon on a black disc.
function RoundIcon([int]$s) {
    $bmp, $g = NewCanvas $s $s
    $g.FillEllipse((New-Object System.Drawing.SolidBrush $ground), 0, 0, $s, $s)
    DrawLogo $g ($s / 2) ($s / 2) ($s * 0.38)
    $bmp
}

# Adaptive-icon foreground: transparent 108dp canvas, logo inside the 66dp safe zone.
function Foreground([int]$s) {
    $bmp, $g = NewCanvas $s $s
    DrawLogo $g ($s / 2) ($s / 2) ($s * 0.29)
    $bmp
}

Save (SquareIcon 512) (Join-Path $root 'docs\icon.png')

$densities = @(@('mdpi', 1.0), @('hdpi', 1.5), @('xhdpi', 2.0), @('xxhdpi', 3.0), @('xxxhdpi', 4.0))
foreach ($d in $densities) {
    $dir = Join-Path $res "mipmap-$($d[0])"
    Save (SquareIcon ([int](48 * $d[1]))) (Join-Path $dir 'ic_launcher.png')
    Save (RoundIcon ([int](48 * $d[1]))) (Join-Path $dir 'ic_launcher_round.png')
    Save (Foreground ([int](108 * $d[1]))) (Join-Path $dir 'ic_launcher_foreground.png')
}

# Fire TV banner 320x180: logo on the left, ZERO/BROWSER on the right.
$bn, $gb = NewCanvas 320 180
$gb.FillRectangle((New-Object System.Drawing.SolidBrush $ground), 0, 0, 320, 180)
DrawLogo $gb 74 90 58
$font = New-Object System.Drawing.Font $family, 30, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
$gb.DrawString('ZERO', $font, (New-Object System.Drawing.SolidBrush (C 255 245 245 247)), 146, 50)
$gb.DrawString('/', $font, (New-Object System.Drawing.SolidBrush (C 255 $pink[0] $pink[1] $pink[2])), 226, 50)
$gb.DrawString('BROWSER', $font, (New-Object System.Drawing.SolidBrush (C 255 $cyan[0] $cyan[1] $cyan[2])), 146, 88)
Save $bn (Join-Path $res 'drawable-xhdpi\banner.png')
