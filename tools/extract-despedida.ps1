$ErrorActionPreference = 'Stop'
$skill = 'C:\Users\Admin\.config\opencode\skills\crear-repo\SKILL.md'
$text = [System.IO.File]::ReadAllText($skill, [System.Text.Encoding]::UTF8)
$lines = $text -split "`r?`n"

$start = -1
$end = -1
for ($i = 0; $i -lt $lines.Count; $i++) {
  if ($start -lt 0 -and $lines[$i] -like '*poiesis*') { $start = $i }
  if ($lines[$i] -like '*Gustavo Bueno*') { $end = $i; break }
}
if ($start -lt 0 -or $end -lt 0) { throw "markers not found start=$start end=$end" }

$artStart = $start + 1
while ($artStart -lt $end -and $lines[$artStart] -notmatch ([char]0x2588)) { $artStart++ }

$art = New-Object System.Collections.Generic.List[string]
for ($i = $artStart; $i -le $end; $i++) { $art.Add($lines[$i].TrimEnd()) }

$root = Split-Path -Parent $PSScriptRoot
$artFile = Join-Path $root 'tools/despedida-art.txt'
[System.IO.File]::WriteAllLines($artFile, $art, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ("art lines: " + $art.Count)

foreach ($readme in @('README.md', 'README.es.md')) {
  $path = Join-Path $root $readme
  $t = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
  $ls = $t -split "`r?`n"

  $q = -1
  for ($i = 0; $i -lt $ls.Count; $i++) { if ($ls[$i] -like '*poiesis*') { $q = $i; break } }
  if ($q -lt 0) { Write-Output ("no quote in " + $readme); continue }

  $f = -1
  for ($i = $q; $i -lt $ls.Count; $i++) { if ($ls[$i].Trim() -eq '```') { $f = $i; break } }
  if ($f -lt 0) { Write-Output ("no fence in " + $readme); continue }

  $f2 = -1
  for ($i = $f + 1; $i -lt $ls.Count; $i++) { if ($ls[$i].Trim() -eq '```') { $f2 = $i; break } }
  if ($f2 -lt 0) { Write-Output ("no closing fence in " + $readme); continue }

  $out = New-Object System.Collections.Generic.List[string]
  for ($i = 0; $i -le $f; $i++) { $out.Add($ls[$i]) }
  foreach ($a in $art) { $out.Add($a) }
  for ($i = $f2; $i -lt $ls.Count; $i++) { $out.Add($ls[$i]) }

  [System.IO.File]::WriteAllLines($path, $out, (New-Object System.Text.UTF8Encoding($false)))
  Write-Output ("spliced " + $readme)
}
