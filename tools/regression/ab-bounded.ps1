# =====================================================================================
# ab-bounded.ps1  (2026-09-30)   bounded agent-browser execution
#
# WHY: UI scripts used to call `agent-browser` as a bare external command. When the CLI
# stalled (observed: one silent 14-minute block inside an eval while mainline-seed.ps1 was
# running), the whole run hung with no output and the stall could not be attributed to any
# step. All calls are now bounded: after $AB_TIMEOUT_SEC the call is abandoned and a loud
# AB-TIMEOUT line is printed instead of hanging forever.
#
# HOW: dot-sourcing this file defines
#   AbExe()       - resolves the CLI entry point (prefers the *.cmd shim)
#   AbCli(...)    - runs the CLI inside a child job with a timeout
#   agent-browser - a FUNCTION with the same name as the CLI. PowerShell prefers a
#                   function over an external command, so every existing call site
#                   (`agent-browser wait 2000`, `@(agent-browser eval $js)`, ...) becomes
#                   bounded WITHOUT editing the calling script.
#
# NOTE: the *.cmd shim is used on purpose. `agent-browser` (no extension) also resolves to
# a *.ps1 shim, and a freshly spawned child process refuses to load it under the default
# execution policy ("running scripts is disabled on this system").
# =====================================================================================
$script:AB_TIMEOUT_SEC = 45
$script:AB_EXE = ''

function AbExe() {
    if ($script:AB_EXE) { return $script:AB_EXE }
    # -CommandType Application skips both our own wrapper function and the *.ps1 shim
    $c = Get-Command agent-browser -ErrorAction SilentlyContinue -CommandType Application | Select-Object -First 1
    if ($c) { $script:AB_EXE = $c.Source }
    else {
        $c2 = Get-Command agent-browser -ErrorAction SilentlyContinue -CommandType ExternalScript | Select-Object -First 1
        if ($c2) {
            $cmd = [System.IO.Path]::ChangeExtension($c2.Source, '.cmd')
            if (Test-Path $cmd) { $script:AB_EXE = $cmd } else { $script:AB_EXE = $c2.Source }
        }
        else { $script:AB_EXE = 'agent-browser.cmd' }
    }
    return $script:AB_EXE
}

function AbCli([object[]]$abArgs, [int]$timeoutSec = 0) {
    if ($timeoutSec -le 0) { $timeoutSec = $script:AB_TIMEOUT_SEC }
    $exe = AbExe
    # AB_FAST=1: call the CLI directly (no child job). ~2s faster per call, which matters for
    # bulk seeding (hundreds of calls), at the cost of losing the timeout guard. Only use it in
    # runners that enforce their own overall timeout (see tools/regression/seed-bulk.ps1).
    if ($env:AB_FAST -eq '1') {
        return @(& $exe @abArgs 2>&1 | ForEach-Object { "$_" })
    }
    $out = $null
    try {
        $j = Start-Job -ScriptBlock {
            param($e, $a)
            & $e @a 2>&1 | ForEach-Object { "$_" }
        } -ArgumentList $exe, @($abArgs)
        if (Wait-Job $j -Timeout $timeoutSec) {
            $out = @(Receive-Job $j)
        }
        else {
            Stop-Job $j -ErrorAction SilentlyContinue
            $brief = ((@($abArgs) | ForEach-Object { "$_" }) -join ' ')
            if ($brief.Length -gt 80) { $brief = $brief.Substring(0, 80) + '...' }
            Write-Host ('  [AB-TIMEOUT ' + $timeoutSec + 's] ' + $brief)
            $out = @()
        }
        Remove-Job $j -Force -ErrorAction SilentlyContinue
    }
    catch {
        # jobs unavailable => fall back to a direct (unbounded) call so the script still works
        $out = @(& $exe @abArgs 2>&1 | ForEach-Object { "$_" })
    }
    if ($null -eq $out) { $out = @() }
    return $out
}

# Same name as the CLI on purpose: a function shadows the external command, which is what
# makes every pre-existing `agent-browser ...` call in the suite bounded.
# NOTE: `@(AbCli ...)` is required. AbCli returns through PowerShell, which UNROLLS a
# 1-element array into a bare string; indexing that with [0] then yields the string's first
# CHARACTER (observed: `get url` returned "h" instead of the URL, and every eval-returning
# script silently got 1 char => "page could not be read"). Force an array here.
function agent-browser {
    $out = @(AbCli @($args))
    if ($out.Count -eq 0) { return $null }
    if ($out.Count -eq 1) { return $out[0] }
    return $out
}
