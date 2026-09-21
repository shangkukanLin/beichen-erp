# Project-phase "complete all" verification (2026-09-21). ASCII ONLY - no Chinese in this file.
# Chinese labels come from ui-e2e-zh.json (key -> base64 -> JS TextDecoder), per project convention.
#
# Feature: the project-phase tab gains a "complete all" button that marks EVERY pending phase (NOT_STARTED /
# IN_PROGRESS) as FINISHED in one transaction. Terminal state equals completing phase by phase:
# all phases FINISHED and the project is auto-CLOSED (plus product status sync).
#
# Part 1 (API):
#   a. a fresh project gets one IN_PROGRESS phase + N-1 NOT_STARTED (generated from 14 templates)
#   b. PUT /dev/project/{id}/phase/complete-all -> 200 and reports completing every pending phase
#   c. afterwards every phase is FINISHED with an actualEnd, and plannedEnd is UNCHANGED
#   d. the project is automatically CLOSED and gets an actualEndDate
#   e. idempotent: a 2nd call returns 200 with 0 completed and the project stays CLOSED
#   f. a non-existent project is rejected (no silent OK)
#   g. a CANCELLED project is rejected and its phases stay untouched
# Part 2 (UI):
#   h. the project-phase tab shows the complete-all button
#   i. clicking it opens a confirmation dialog that mentions the auto-close side effect
#   j. confirming closes the project and the progress bar reaches 100%
#   k. no JS/API errors
# Part 3 (regression):
#   l. the single-phase machine is untouched: complete activates the next phase, skip marks SKIPPED and
#      activates the next, revert resets the phase to IN_PROGRESS and every later phase to NOT_STARTED
#      (the shared product-status-sync helper was split while adding complete-all)
param([int]$Part = 0)
$ErrorActionPreference = 'Continue'
# NOTE: do NOT name this $base -- ui-e2e-lib.ps1 sets $script:BASE and would clobber it (case-insensitive).
$apiBase = 'http://localhost:8080/api'
$zh = Get-Content (Join-Path $PSScriptRoot 'ui-e2e-zh.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$lg = Invoke-RestMethod -Uri "$apiBase/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"lin","password":"123","companyId":1}'
$h = @{ Authorization = $lg.data.token }
$script:PASS = 0
$script:FAIL = 0
function Ok([bool]$cond, [string]$msg) {
  if ($cond) { Write-Host ('PASS ' + $msg); $script:PASS++ } else { Write-Host ('FAIL ' + $msg); $script:FAIL++ }
}
function Summary([string]$title) {
  if ($script:FAIL -eq 0) { Write-Host ('RESULT PASS ' + $title + '  (PASS=' + $script:PASS + ' FAIL=0)') }
  else { Write-Host ('RESULT FAIL ' + $title + '  (PASS=' + $script:PASS + ' FAIL=' + $script:FAIL + ')'); exit 1 }
}
function Call([string]$method, [string]$uri, $body) {
  try {
    if ($null -eq $body) { return Invoke-RestMethod -Uri $uri -Method $method -Headers $h }
    return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 5)
  } catch {
    return @{ code = -1; msg = ('HTTP error: ' + $_.Exception.Message) }
  }
}
# Probe projects are created WITHOUT assemblyName on purpose: syncProduct only creates a product when the
# assembly name is present, so no product master data is polluted. The probes end up CLOSED (terminal
# state of the lifecycle) and are named PROBE-PHASE-* so they stay identifiable.
function NewProbe([string]$suffix) {
  return Call 'Post' "$apiBase/dev/project" @{ name = ('PROBE-PHASE-' + $suffix); remark = 'probe for the complete-all verification; safe to cancel' }
}

if ($Part -eq 0 -or $Part -eq 1) {
  Write-Host '--- STEP 1  complete-all (API)'
  $stamp = Get-Date -Format 'HHmmss'

  $c = NewProbe $stamp
  $pidX = $c.data.id
  Write-Host ('  create probe -> code=' + $c.code + ' id=' + $pidX)
  Ok ([int]$c.code -eq 200) 'POST /dev/project creates a probe project'
  # guard: a missing id would make every assertion below pass vacuously
  Ok ($pidX -ne $null -and [int]$pidX -gt 0) 'probe project id resolved (guards against vacuous PASS)'

  if ($pidX -ne $null -and [int]$pidX -gt 0) {
    # a) initial shape + plannedEnd snapshot
    $p1 = Call 'Get' "$apiBase/dev/project/$pidX/phase" $null
    $rows1 = @($p1.data)
    $total = $rows1.Count
    $planned = @{}
    foreach ($r in $rows1) { $planned[[string]$r.id] = [string]$r.plannedEnd }
    $ip = @($rows1 | Where-Object { $_.status -eq 'IN_PROGRESS' }).Count
    $ns = @($rows1 | Where-Object { $_.status -eq 'NOT_STARTED' }).Count
    Write-Host ('  initial phases=' + $total + ' inProgress=' + $ip + ' notStarted=' + $ns)
    Ok ($total -gt 0) 'the probe project has phases (generated from the templates)'
    Ok ($ip -eq 1) 'exactly one phase starts as IN_PROGRESS'
    Ok ($ns -eq ($total - 1)) 'all remaining phases start as NOT_STARTED'

    # b) complete-all
    $r1 = Call 'Put' "$apiBase/dev/project/$pidX/phase/complete-all" $null
    Write-Host ('  complete-all -> code=' + $r1.code + ' done=' + $r1.data + ' (expect ' + $total + ')')
    Ok ([int]$r1.code -eq 200) 'PUT phase/complete-all returns 200'
    Ok ([int]$r1.data -eq $total) 'it reports completing every pending phase'

    # c) phases + plannedEnd untouched
    $p2 = Call 'Get' "$apiBase/dev/project/$pidX/phase" $null
    $rows2 = @($p2.data)
    $notDone = @($rows2 | Where-Object { $_.status -ne 'FINISHED' }).Count
    $noActual = @($rows2 | Where-Object { -not $_.actualEnd }).Count
    Ok ($notDone -eq 0) 'every phase is now FINISHED'
    Ok ($noActual -eq 0) 'every phase got an actualEnd date'
    $plannedDiff = 0
    foreach ($r2 in $rows2) {
      $old = $planned[[string]$r2.id]
      if ([string]$r2.plannedEnd -ne $old) { $plannedDiff++; Write-Host ('    planned changed id=' + $r2.id + ' ' + $old + ' -> ' + $r2.plannedEnd) }
    }
    Ok ($plannedDiff -eq 0) 'plannedEnd is NOT modified (the plan snapshot is preserved)'

    # d) project auto-closed
    $pr = Call 'Get' "$apiBase/dev/project/$pidX" $null
    Write-Host ('  project status=' + $pr.data.status + ' actualEndDate=' + $pr.data.actualEndDate)
    Ok ([string]$pr.data.status -eq 'CLOSED') 'the project is automatically CLOSED'
    Ok ($pr.data.actualEndDate -ne $null) 'the project got an actualEndDate'

    # e) idempotent
    $r2c = Call 'Put' "$apiBase/dev/project/$pidX/phase/complete-all" $null
    Write-Host ('  2nd call -> code=' + $r2c.code + ' done=' + $r2c.data)
    Ok ([int]$r2c.code -eq 200) 'calling it again still returns 200 (idempotent, not an error)'
    Ok ([int]$r2c.data -eq 0) 'the 2nd call completes 0 phases'
    $pr2 = Call 'Get' "$apiBase/dev/project/$pidX" $null
    Ok ([string]$pr2.data.status -eq 'CLOSED') 'the project stays CLOSED'

    # f) non-existent project -> rejected
    $r3 = Call 'Put' "$apiBase/dev/project/99999999/phase/complete-all" $null
    Write-Host ('  missing project -> code=' + $r3.code + ' msg=' + $r3.msg)
    Ok ([int]$r3.code -ne 200) 'a non-existent project is rejected (not silently OK)'

    # g) cancelled project -> rejected and untouched
    $c2 = NewProbe ('C-' + $stamp)
    $pid2 = $c2.data.id
    Write-Host ('  cancelled-probe id=' + $pid2)
    Ok ($pid2 -ne $null -and [int]$pid2 -gt 0) 'cancelled-probe id resolved (guards against vacuous PASS)'
    if ($pid2 -ne $null -and [int]$pid2 -gt 0) {
      Call 'Put' "$apiBase/dev/project/$pid2/cancel" $null | Out-Null
      $r4 = Call 'Put' "$apiBase/dev/project/$pid2/phase/complete-all" $null
      Write-Host ('  cancelled project -> code=' + $r4.code + ' msg=' + $r4.msg)
      Ok ([int]$r4.code -ne 200) 'a CANCELLED project is rejected'
      Ok ([string]$r4.msg -like ('*' + $zh.msg_project_cancelled + '*')) 'the rejection carries the expected business message'
      $p3 = Call 'Get' "$apiBase/dev/project/$pid2/phase" $null
      $finished = @($p3.data | Where-Object { $_.status -eq 'FINISHED' }).Count
      Ok ($finished -eq 0) 'the cancelled project phases were NOT touched'
    }
  }
  Summary 'complete-all (API)'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Write-Host '--- STEP 2  complete-all (UI)'
  . (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
  WatchErrors
  EnsureLogin | Out-Null
  $B_BTN = B64 (ZH 'btn_complete_all')
  $B_TAB = B64 (ZH 'tab_project_phase')
  $B_KW = B64 (ZH 'phase_confirm_keyword')

  $c = NewProbe ('UI-' + (Get-Date -Format 'HHmmss'))
  $pid3 = $c.data.id
  Write-Host ('  UI probe project id=' + $pid3)
  Ok ($pid3 -ne $null -and [int]$pid3 -gt 0) 'UI probe project id resolved (guards against vacuous PASS)'

  if ($pid3 -ne $null -and [int]$pid3 -gt 0) {
    Open "/dev/project/edit/$pid3" 6000
    ClearErrs | Out-Null
    Start-Sleep -Milliseconds 3000

    # open the project-phase tab
    $jsTab = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$B_TAB');" +
      "var ts=[].slice.call(document.querySelectorAll('.el-tabs__item')).filter(function(t){return (t.innerText||'').trim()===L;});" +
      "if(!ts.length)return 'notab';ts[0].click();return 'ok';})()"
    $tb = (EvalJs $jsTab).Trim()
    Start-Sleep -Milliseconds 1500

    # h) the button exists (and we click it)
    $jsBtn = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const B=T('$B_BTN');" +
      "var bs=[].slice.call(document.querySelectorAll('button')).filter(function(b){return b.getClientRects().length>0 && (b.innerText||'').trim()===B;});" +
      "if(!bs.length)return JSON.stringify({found:false});bs[0].click();return JSON.stringify({found:true});})()"
    $braw = (EvalJs $jsBtn).Trim()
    Write-Host ('  button probe = ' + $braw + '  (tab=' + $tb + ')')
    $bd = $null
    try { $bd = $braw | ConvertFrom-Json } catch { }
    if ($null -ne $bd) { Ok ($bd.found -eq $true) 'the project-phase tab shows the complete-all button' }

    # i) confirmation dialog mentions the auto-close side effect
    Start-Sleep -Milliseconds 1000
    $jsDlg = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const K=T('$B_KW');" +
      "var ms=[].slice.call(document.querySelectorAll('.el-message-box')).filter(function(m){return m.getClientRects().length>0;});" +
      "if(!ms.length)return JSON.stringify({open:false});" +
      "var txt=(ms[0].innerText||'');return JSON.stringify({open:true,hit:txt.indexOf(K)>=0});})()"
    $draw = (EvalJs $jsDlg).Trim()
    Write-Host ('  confirm dialog = ' + $draw)
    $dd = $null
    try { $dd = $draw | ConvertFrom-Json } catch { }
    if ($null -ne $dd) {
      Ok ($dd.open -eq $true) 'clicking the complete-all button opens a confirmation dialog'
      Ok ($dd.hit -eq $true) 'the confirmation text explains the auto-close side effect'
    }

    # j) confirm -> project closed + progress 100%
    $jsOk = "(()=>{var ms=[].slice.call(document.querySelectorAll('.el-message-box')).filter(function(m){return m.getClientRects().length>0;});" +
      "if(!ms.length)return 'nomsg';var bs=[].slice.call(ms[0].querySelectorAll('button'));" +
      "var p=bs.filter(function(b){return b.classList.contains('el-button--primary');})[0];if(!p)return 'nobtn';p.click();return 'ok';})()"
    $ok = (EvalJs $jsOk).Trim()
    Start-Sleep -Milliseconds 3000
    $pc = Call 'Get' "$apiBase/dev/project/$pid3" $null
    Write-Host ('  after confirm -> project status=' + $pc.data.status + '  (confirm click=' + $ok + ')')
    Ok ([string]$pc.data.status -eq 'CLOSED') 'confirming the dialog closes the project (auto close)'
    $jsProg = "(()=>{var i=document.querySelector('.el-progress-bar__inner');if(!i)return JSON.stringify({pct:-1});" +
      "var w=(i.style.width||'');return JSON.stringify({w:w,pct:parseInt(w)||0});})()"
    $praw = (EvalJs $jsProg).Trim()
    Write-Host ('  progress = ' + $praw)
    $pd = $null
    try { $pd = $praw | ConvertFrom-Json } catch { }
    if ($null -ne $pd) { Ok ([int]$pd.pct -eq 100) 'the progress bar reaches 100%' }

    Write-Host ('  errs=' + (Errs))
    Ok ((Errs) -eq '[]') 'no JS/API errors on the phase tab'
  }
  Summary 'complete-all (UI)'
}

if ($Part -eq 0 -or $Part -eq 3) {
  Write-Host '--- STEP 3  single-phase regression'
  # The shared product-status-sync helper was split into needsProductStatusSync + checkProductStatusSync
  # while adding complete-all, so the single-phase state machine must be re-proved end to end.
  $c = NewProbe ('S-' + (Get-Date -Format 'HHmmss'))
  $pidS = $c.data.id
  Write-Host ('  regression probe id=' + $pidS)
  Ok ($pidS -ne $null -and [int]$pidS -gt 0) 'regression probe id resolved (guards against vacuous PASS)'

  if ($pidS -ne $null -and [int]$pidS -gt 0) {
    $ph = @((Call 'Get' "$apiBase/dev/project/$pidS/phase" $null).data)
    $id1 = $ph[0].id; $id2 = $ph[1].id; $id3 = $ph[2].id
    Ok ($ph.Count -ge 3) 'the probe project has at least 3 phases'

    # complete the 1st phase -> it is FINISHED and the NEXT one becomes IN_PROGRESS
    $r1 = Call 'Put' "$apiBase/dev/project/phase/$id1/complete" $null
    $s1 = @((Call 'Get' "$apiBase/dev/project/$pidS/phase" $null).data)
    $a1 = $s1 | Where-Object { $_.id -eq $id1 }
    $a2 = $s1 | Where-Object { $_.id -eq $id2 }
    Write-Host ('  complete #1 -> code=' + $r1.code + ' p1=' + $a1.status + ' p2=' + $a2.status)
    Ok ([int]$r1.code -eq 200) 'single-phase complete still returns 200'
    Ok ([string]$a1.status -eq 'FINISHED') 'the completed phase is FINISHED'
    Ok ([string]$a1.actualEnd -ne '') 'the completed phase got an actualEnd'
    Ok ([string]$a2.status -eq 'IN_PROGRESS') 'completing a phase still activates the NEXT phase'

    # skip the 2nd phase -> SKIPPED and the 3rd activates
    $r2 = Call 'Put' "$apiBase/dev/project/phase/$id2/skip" $null
    $s2 = @((Call 'Get' "$apiBase/dev/project/$pidS/phase" $null).data)
    $b2 = $s2 | Where-Object { $_.id -eq $id2 }
    $b3 = $s2 | Where-Object { $_.id -eq $id3 }
    Write-Host ('  skip #2 -> code=' + $r2.code + ' p2=' + $b2.status + ' p3=' + $b3.status)
    Ok ([string]$b2.status -eq 'SKIPPED') 'skipping a phase still marks it SKIPPED'
    Ok ([string]$b3.status -eq 'IN_PROGRESS') 'skipping still activates the next phase'

    # revert the 1st phase -> IN_PROGRESS and every later phase resets to NOT_STARTED
    $r3 = Call 'Put' "$apiBase/dev/project/phase/$id1/revert" $null
    $s3 = @((Call 'Get' "$apiBase/dev/project/$pidS/phase" $null).data)
    $c1 = $s3 | Where-Object { $_.id -eq $id1 }
    $c2 = $s3 | Where-Object { $_.id -eq $id2 }
    $c3 = $s3 | Where-Object { $_.id -eq $id3 }
    Write-Host ('  revert #1 -> code=' + $r3.code + ' p1=' + $c1.status + ' p2=' + $c2.status + ' p3=' + $c3.status)
    Ok ([string]$c1.status -eq 'IN_PROGRESS') 'revert still restores the phase to IN_PROGRESS'
    Ok ([string]$c2.status -eq 'NOT_STARTED') 'revert still resets the following phase'
    Ok ([string]$c3.status -eq 'NOT_STARTED') 'revert still resets ALL following phases'

    # finish the probe so it ends in the terminal CLOSED state
    Call 'Put' "$apiBase/dev/project/$pidS/phase/complete-all" $null | Out-Null
    $pr = Call 'Get' "$apiBase/dev/project/$pidS" $null
    Ok ([string]$pr.data.status -eq 'CLOSED') 'complete-all still closes the project after a revert'
  }
  Summary 'single-phase regression'
}
