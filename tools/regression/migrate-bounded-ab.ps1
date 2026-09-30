# =====================================================================================
# migrate-bounded-ab.ps1  (2026-09-30)  make every UI script use bounded agent-browser
#
# What it does, per script that calls `agent-browser`:
#   - already pulls ab-bounded.ps1                  -> nothing to do
#   - dot-sources ui-e2e-lib.ps1 (which pulls it in) -> nothing to do
#   - otherwise                                      -> splice in ONE line, right after the
#        param(...) block (or before the first statement) :
#          . (Join-Path $PSScriptRoot 'ab-bounded.ps1')
#     so the `agent-browser` FUNCTION shadows the external CLI and every existing call in
#     that script becomes bounded by AbCli's timeout.
#
# Safety: dry-run by default (-Apply to write); byte-level encoding + BOM state and the
# file's own EOL style are preserved; each modified file is backed up to %TEMP% and is
# re-parsed afterwards - on a parse error the original bytes are restored.
# =====================================================================================
param([switch]$Apply)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$bak = Join-Path $env:TEMP ('ab-migrate-bak-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$self = @('ab-bounded.ps1', 'ui-e2e-lib.ps1', 'migrate-bounded-ab.ps1')
$null = $null

function Read-Source([string]$path) {
    $bytes = [System.IO.File]::ReadAllBytes($path)
    $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $off = 0; if ($hasBom) { $off = 3 }
    $len = $bytes.Length - $off
    $utf8 = New-Object System.Text.UTF8Encoding($false, $true)
    try { $text = $utf8.GetString($bytes, $off, $len); $kind = 'utf8' }
    catch { $text = [System.Text.Encoding]::Default.GetString($bytes, $off, $len); $kind = 'gbk' }
    return [pscustomobject]@{ Text = $text; HasBom = $hasBom; Kind = $kind; Bytes = $bytes }
}

function Write-Source([string]$path, [string]$text, [bool]$hasBom, [string]$kind) {
    if ($kind -eq 'gbk') { $enc = [System.Text.Encoding]::Default }
    else { $enc = New-Object System.Text.UTF8Encoding($hasBom) }
    [System.IO.File]::WriteAllText($path, $text, $enc)
}

$plan = New-Object System.Collections.Generic.List[object]
foreach ($f in (Get-ChildItem $root -Recurse -Filter *.ps1 -File)) {
    if ($self -contains $f.Name) { continue }
    $src = Read-Source $f.FullName
    if ($src.Text -notmatch 'agent-browser') { continue }
    $rel = $f.FullName.Substring($root.Length).TrimStart('\')
    if ($src.Text -match 'ab-bounded') { $plan.Add([pscustomobject]@{ File = $rel; Action = 'skip: already bounded' }); continue }
    if ($src.Text -match 'ui-e2e-lib') { $plan.Add([pscustomobject]@{ File = $rel; Action = 'skip: gets it via ui-e2e-lib' }); continue }

    $err = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseInput($src.Text, [ref]$null, [ref]$err)
    if (@($err).Count -gt 0) { $plan.Add([pscustomobject]@{ File = $rel; Action = ('SKIP: source has ' + @($err).Count + ' parse error(s) already') }); continue }
    if (-not $ast.EndBlock -or -not $ast.EndBlock.Statements -or $ast.EndBlock.Statements.Count -eq 0) {
        $plan.Add([pscustomobject]@{ File = $rel; Action = 'SKIP: no top-level statement found' }); continue
    }
    if ($ast.ParamBlock) { $after = $ast.ParamBlock.Extent.EndLineNumber } else { $after = $ast.EndBlock.Statements[0].Extent.StartLineNumber - 1 }
    if ($after -lt 1) { $after = 0 }
    $plan.Add([pscustomobject]@{ File = $rel; Action = ('INSERT after line ' + $after) ; After = $after; Src = $src })
}

Write-Host '=== migration plan ==='
foreach ($p in $plan) { Write-Host ('  ' + $p.File.PadRight(52) + $p.Action) }
$todo = @($plan | Where-Object { $_.Action -like 'INSERT*' })
# NOTE: @($genericList).Count throws "Argument types do not match" in PS 5.1 - use .Count directly
Write-Host ('  files with agent-browser = ' + $plan.Count + ' ; to insert = ' + $todo.Count)
if (-not $Apply) { Write-Host 'DRY RUN (no files written). Re-run with -Apply.'; return }

if ($todo.Count -gt 0) { New-Item -ItemType Directory -Force -Path $bak | Out-Null }
$changed = 0
foreach ($p in $todo) {
    $full = Join-Path $root $p.File
    $src = $p.Src
    $eol = if ($src.Text.Contains("`r`n")) { "`r`n" } else { "`n" }
    # relative path from the script's own folder to ab-bounded.ps1 (all live in tools\regression)
    $dirDepth = ($p.File -split '\\').Count - 1
    $up = ('..\' * $dirDepth)
    $target = if ($dirDepth -eq 0) { 'ab-bounded.ps1' } else { ($up + 'ab-bounded.ps1') }
    $ins = $eol + '. (Join-Path $PSScriptRoot ''' + $target + ''')'
    $lines = New-Object System.Collections.Generic.List[string]
    $lines.AddRange([string[]]($src.Text -split "`r`n|`n"))
    # splice AFTER the anchor line (1-based)
    $idx = [Math]::Min($p.After, $lines.Count)
    $lines.Insert($idx, $ins.TrimStart("`r", "`n"))
    $newText = ($lines -join $eol)
    if (-not $newText.EndsWith($eol)) { $newText += $eol }
    Copy-Item $full (Join-Path $bak ($p.File -replace '[\\/]', '__')) -Force
    Write-Source $full $newText $src.HasBom $src.Kind
    # verify it parses; restore on failure.
    # NOTE: ParseFile() assumes UTF-8 and therefore mis-flags a plain ANSI/GBK file (which
    # PowerShell itself runs fine via -File) as a syntax error => decode the bytes we just
    # wrote with the SAME encoding, then parse the text (same way the pre-check does).
    $chk = Read-Source $full
    $err2 = $null
    [void][System.Management.Automation.Language.Parser]::ParseInput($chk.Text, [ref]$null, [ref]$err2)
    if (@($err2).Count -gt 0) {
        [System.IO.File]::WriteAllBytes($full, $src.Bytes)
        Write-Host ('  FAIL (restored) ' + $p.File + ' : ' + @($err2)[0].Message)
    }
    else { $changed++; Write-Host ('  OK   ' + $p.File) }
}
Write-Host ('changed = ' + $changed + ' / ' + $todo.Count + '  (backups: ' + $bak + ')')
