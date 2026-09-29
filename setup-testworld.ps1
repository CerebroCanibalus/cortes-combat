# Provision the test world once, then it persists between sessions.
#
# The problem this solves: every time the run directory is reset you get a new random world,
# different spawn, different mob placement, so "it did not hit last time" and "it hit last
# time" are not comparable. This script creates a fixed world with fixed rules and a test
# arena, once.
#
# Safe to re-run: if the world already exists it does nothing and says so.

param(
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$run = Join-Path $root "run"
$worldName = "cortes-combat-test"
# The ModDevGradle run directory is the game dir itself, so the world sits directly in run/,
# not in run/saves/. Getting this wrong is how you end up wondering why the world "resets".
$world = Join-Path $run $worldName

if ((Test-Path $world) -and -not $Force) {
    Write-Host "El mundo de pruebas ya existe: $world" -ForegroundColor Green
    Write-Host "No se toca nada. Usá -Force para regenerarlo." -ForegroundColor DarkGray
    exit 0
}

Write-Host "Creando el mundo de pruebas..." -ForegroundColor Cyan

New-Item -ItemType Directory -Path $run -Force | Out-Null
Set-Content -Path (Join-Path $run "eula.txt") -Value "eula=true" -Encoding ASCII -NoNewline

# server.properties controls the seed, gamemode and difficulty. Plain text, so it is easy
# to keep stable; gamerules live in level.dat and are applied below through commands.
$props = @{
    "level-seed"      = "-3141592653"
    "level-name"      = $worldName
    "gamemode"        = "1"
    "difficulty"      = "2"
    "online-mode"     = "false"
    "max-players"     = "4"
    "spawn-protection"= "0"
    "view-distance"   = "10"
    "simulation-distance" = "10"
    "allow-flight"    = "true"
    "enable-command-block" = "true"
    "sync-chunk-writes" = "false"
    "server-port"     = "25599"
}
$propsText = ($props.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join "`r`n"
Set-Content -Path (Join-Path $run "server.properties") -Value $propsText -Encoding ASCII -NoNewline

# The arena and the rules, sent to the server console once it is up.
$commands = @(
    "gamerule doDaylightCycle false",
    "gamerule doWeatherCycle false",
    "gamerule doMobSpawning true",
    "gamerule mobSpawnRadius 40",
    "gamerule naturalRegeneration false",
    "gamerule keepInventory true",
    "gamerule fallDamage false",
    "gamerule mobGriefing false",
    "gamerule doFireTick false",
    "gamerule announceAdvancements false",
    "gamerule spawnRadius 10",
    "time set 6000",
    "weather clear",
    "difficulty normal",
    # Flat arena: a big platform at spawn so nothing blocks a cone test.
    "fill -60 78 -60 60 78 60 minecraft:polished_andesite",
    "fill -60 79 -60 60 79 60 minecraft:air",
    # Low wall behind the player to show the cone does not reach behind you.
    "fill -8 79 -8 8 80 8 minecraft:cobblestone",
    # A row of markers at increasing distance straight ahead, for reach tests.
    "setblock 0 79 2 minecraft:redstone_block",
    "setblock 0 79 3 minecraft:redstone_block",
    "setblock 0 79 4 minecraft:redstone_block",
    "setblock 0 79 5 minecraft:redstone_block",
    "setblock 0 79 6 minecraft:redstone_block",
    "setblock 0 79 7 minecraft:redstone_block",
    "say TESTWORLD_READY",
    "stop"
)

$commandsFile = Join-Path $run "setup-commands.txt"
$commands | Set-Content -Path $commandsFile -Encoding ASCII

Write-Host "Arrancando el servidor dedicado para provisionar (puede tardar un minuto)..." -ForegroundColor DarkGray

# RedirectStandardInput on Start-Process does not survive the gradlew -> java hop: cmd.exe
# does not forward the redirected handle to its child. Driving the process directly and
# writing to stdin once the server reports Done is what actually works.
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = Join-Path $root "gradlew.bat"
$psi.Arguments = "runServer"
$psi.WorkingDirectory = $root
$psi.UseShellExecute = $false
$psi.RedirectStandardInput = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$process = New-Object System.Diagnostics.Process
$process.StartInfo = $psi

$logPath = Join-Path $run "setup-out.log"
$null = $process.Start()

# Drain stdout in the background so the pipe never fills and blocks the server.
$readTask = $process.StandardOutput.ReadToEndAsync()

# Wait for the server to be up before sending anything: commands sent too early are dropped.
$deadline = (Get-Date).AddMinutes(6)
$up = $false
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3
    if ($process.HasExited) { break }
    $elapsed = ((Get-Date) - $process.StartTime).TotalSeconds
    if ($elapsed -gt 45) { $up = $true; break }
}

if ($up) {
    foreach ($cmd in $commands) {
        try { $process.StandardInput.WriteLine($cmd); $process.StandardInput.Flush() } catch { }
        Start-Sleep -Milliseconds 120
    }
    Start-Sleep -Seconds 8
    try { $process.StandardInput.WriteLine("stop"); $process.StandardInput.Flush() } catch { }
}

try { $process.WaitForExit(30000) | Out-Null } catch { }

$output = ""
try { $output = $readTask.GetAwaiter().GetResult() } catch { }
Set-Content -Path $logPath -Value $output -Encoding UTF8

# Stop only this server, never every java process on the machine.
$procs = Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
    Where-Object { $_.CommandLine -like "*cortes-combat*" }
foreach ($p in $procs) { Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue }
try { $process.Kill($true) } catch { }

$ready = ($output -match "TESTWORLD_READY") -and (Test-Path $world)

if ($ready) {
    Write-Host "Mundo de pruebas listo: $world" -ForegroundColor Green
    Write-Host "Arrancá el cliente con: .\gradlew.bat runClient" -ForegroundColor Cyan
} else {
    Write-Host "No se pudo confirmar. Mirá $logPath" -ForegroundColor Red
    Write-Host "Ultimas lineas:" -ForegroundColor DarkGray
    ($output -split "`n" | Select-Object -Last 8) -join "`n" | Write-Host -ForegroundColor DarkGray
    exit 1
}
