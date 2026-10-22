$root = Split-Path -Parent $PSScriptRoot
foreach ($readme in @('README.md','README.es.md')) {
  $p = Join-Path $root $readme
  $t = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8)
  Write-Output ("=== " + $readme + " ===")

  # images
  $m = [regex]::Matches($t, 'src="([^"]+)"')
  $missing = 0
  foreach ($x in $m) {
    $rel = $x.Groups[1].Value
    if ($rel -like 'http*') { continue }
    $full = Join-Path $root $rel
    if (-not (Test-Path $full)) { Write-Output ("  MISSING IMG: " + $rel); $missing++ }
  }
  Write-Output ("  images: " + $m.Count + ", missing: " + $missing)

  # markdown links
  $m2 = [regex]::Matches($t, '\]\(([^)#][^)]*)\)')
  $missing2 = 0
  foreach ($x in $m2) {
    $rel = $x.Groups[1].Value
    if ($rel -like 'http*') { continue }
    $full = Join-Path $root $rel
    if (-not (Test-Path $full)) { Write-Output ("  MISSING LINK: " + $rel); $missing2++ }
  }
  Write-Output ("  links: " + $m2.Count + ", missing: " + $missing2)

  # style rules from the skill: em dash outside of quotes, colons in prose
  $body = ($t -split "`r?`n") | Where-Object { $_ -notmatch '^\s*█' -and $_ -notmatch '^\s*[*_\-]{3,}\s*$' }
  $em = @($body | Where-Object { $_ -match '—' })
  Write-Output ("  em dashes: " + $em.Count)
  foreach ($e in $em) { Write-Output ("    " + $e.Trim()) }
}
