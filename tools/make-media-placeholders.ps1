# Genera los placeholders de docs/media/ (una tarjeta PNG por cada slot de captura).
#
# Cada tarjeta es un .gif de una sola imagen: el README se ve bien hoy y, cuando se grabe
# el capturín, se reemplaza el archivo con el mismo nombre y nada más hay que tocar.
#
#   powershell -ExecutionPolicy Bypass -File tools/make-media-placeholders.ps1
#
# Las capturas de verdad se graban en docs/media/_raw/ y se convierten con
# docs/media/CAPTURE-GUIDE.md, que dice el comando exacto.

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$media = Join-Path $root 'docs/media'
New-Item -ItemType Directory -Force -Path $media | Out-Null

$bg = [System.Drawing.Color]::FromArgb(18, 14, 13)
$panel = [System.Drawing.Color]::FromArgb(28, 22, 20)
$accent = [System.Drawing.Color]::FromArgb(224, 175, 71)
$text = [System.Drawing.Color]::FromArgb(238, 232, 226)
$muted = [System.Drawing.Color]::FromArgb(150, 142, 136)

$slots = @(
  @{ file = '01-reach-cone.gif';      title = 'Reach is a cone';        hint = 'hit the far edge of the arc' }
  @{ file = '02-hard-cooldown.gif';   title = 'The cooldown is a wall';  hint = 'no half cooldown attacks' }
  @{ file = '03-charge.gif';          title = 'Charge pays';             hint = 'hold, release, land it' }
  @{ file = '04-guard-stamina.gif';   title = 'The guard has a price';   hint = 'blocking drains stamina' }
  @{ file = '05-specials.gif';        title = 'Right click is another weapon'; hint = 'sweep, parry, lunge' }
  @{ file = '06-hunger.gif';          title = 'Food heals, food returns'; hint = 'healing spends, the clock gives back' }
  @{ file = '07-weapons.gif';         title = 'Five new melee families'; hint = 'ropera, alabarda, war hammer, dagger, shotel' }
  @{ file = '08-arcabuz.gif';         title = 'The Arcabuz';             hint = 'fuse, blast, reload' }
  @{ file = '09-enchantments.gif';    title = '48 enchantments';         hint = 'new ones and reworked vanilla' }
  @{ file = '10-reactor.gif';         title = 'The Nether Reactor';      hint = 'load it past its ceiling' }
  @{ file = '11-fx.gif';              title = 'Impact feedback';         hint = 'flash, shake, particles, sound' }
  @{ file = '12-third-person.gif';    title = 'Third person';            hint = 'the body moves too' }
  @{ file = '13-weapon-packs.gif';    title = 'Your own weapons';        hint = 'yaml packs, no code' }
  @{ file = '14-arcabuz-shots.gif';   title = 'Every weapon has a range'; hint = 'the tooltip says it in numbers' }
)

function New-Card([string]$path, [string]$title, [string]$hint) {
  $w = 640; $h = 360
  $bmp = New-Object System.Drawing.Bitmap($w, $h)
  $g = [System.Drawing.Graphics]::FromImage($bmp)
  $g.SmoothingMode = 'AntiAlias'
  $g.TextRenderingHint = 'AntiAliasGridFit'

  $g.Clear($bg)

  # Marco punteado, para que se lea como un marco esperando foto.
  $pen = New-Object System.Drawing.Pen($accent, 2)
  $pen.DashStyle = 'Dash'
  $g.DrawRectangle($pen, 14, 14, $w - 29, $h - 29)

  $fill = New-Object System.Drawing.SolidBrush($panel)
  $g.FillRectangle($fill, 14, 14, $w - 29, 6)

  $fTitle = New-Object System.Drawing.Font('Segoe UI', 26, [System.Drawing.FontStyle]::Bold)
  $fHint  = New-Object System.Drawing.Font('Segoe UI', 13)
  $fMark  = New-Object System.Drawing.Font('Consolas', 11)
  $bTitle = New-Object System.Drawing.SolidBrush($text)
  $bHint  = New-Object System.Drawing.SolidBrush($muted)
  $bMark  = New-Object System.Drawing.SolidBrush($accent)

  $g.DrawString($title, $fTitle, $bTitle, 42, 130)
  $g.DrawString($hint,  $fHint,  $bHint,  42, 178)
  $g.DrawString('PLACEHOLDER', $fMark, $bMark, 42, 246)
  $g.DrawString('replace this file with the capture, same name', $fMark, $bMark, 42, 266)

  $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Gif)

  $g.Dispose(); $bmp.Dispose()
  $pen.Dispose(); $fill.Dispose()
  $fTitle.Dispose(); $fHint.Dispose(); $fMark.Dispose()
  $bTitle.Dispose(); $bHint.Dispose(); $bMark.Dispose()
}

foreach ($s in $slots) {
  $target = Join-Path $media $s.file
  New-Card $target $s.title $s.hint
  Write-Output ("wrote " + $s.file)
}

# El logo y el banner se copian desde la raiz, que es donde los guardo mientras se dibujan.
$copies = @{
  'cortescombatlogo.png' = 'logo.png'
}
foreach ($k in $copies.Keys) {
  $src = Join-Path $root $k
  if (Test-Path $src) {
    Copy-Item $src (Join-Path $media $copies[$k]) -Force
    Write-Output ("copied " + $k + " -> docs/media/" + $copies[$k])
  }
}
