# Phase templates split into two sets (2026-09-21). ASCII ONLY - Chinese data comes from ui-e2e-zh.json.
#
# User requirement: a project whose spec is "matched" only goes through 7 phases
# (setup / cable sample / back-film+cover sample / assembly sample / test / small batch / close),
# while "modified" keeps the original 14. So the template admin page must hold TWO sets and a new
# project must pick the set that matches its spec.
#
# Part 1 (API):
#   a. per-spec template counts: matched=7, modified=14, no filter=21 (backward compatible)
#   b. the matched set holds EXACTLY the 7 expected phase names, in order
#   c. every matched name also exists in the modified set (matched is a subset)
#   d. a project created with spec=MATCHED gets 7 phases that mirror the matched templates
#      (name / sortOrder / defaultDays / templateId), first IN_PROGRESS and the rest NOT_STARTED
#   e. a project created with spec=MODIFIED gets 14 phases
#   f. a project created WITHOUT a spec still gets 14 phases (NULL means modified - unchanged behaviour)
#   g. a duplicate name inside the SAME spec is rejected, but the same name in the OTHER spec is allowed
#   h. F7-94 regression: renaming a template must NOT break the phase -> template link
#      (complete the phase after a rename: still 200, and the product really flips to NORMAL)
# Part 2 (UI):
#   i. the template panel switches between the two sets (7 rows / 14 rows)
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
    # NOTE: PowerShell 5.1's Invoke-RestMethod does NOT encode a string body as UTF-8 by default, so any
    # Chinese value (e.g. a Chinese phase name) would arrive mangled. Send explicit UTF-8 bytes and
    # declare the charset. (Verified the hard way: a rename probe wrote "???" into the DB.)
    $json = $body | ConvertTo-Json -Depth 6
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    return Invoke-RestMethod -Uri $uri -Method $method -Headers $h -ContentType 'application/json; charset=utf-8' -Body $bytes
  } catch {
    return @{ code = -1; msg = ('HTTP error: ' + $_.Exception.Message) }
  }
}
function TplList([string]$spec) {
  if ($spec) { return @((Call 'Get' "$apiBase/dev/phase-template/list?specType=$spec" $null).data) }
  return @((Call 'Get' "$apiBase/dev/phase-template/list" $null).data)
}
# Probe projects are named PROBE-* so they stay identifiable; each gets a unique product name so the
# duplicate-name confirm dialog never triggers.
function NewProbeProject([string]$suffix, $specType) {
  $body = @{ name = ('PROBE-PJ-' + $suffix); productName = ('PROBE-PJNAME-' + $suffix) }
  if ($specType) { $body.specType = $specType }
  $body.productSku = ('NS-' + (Get-Random -Minimum 100000 -Maximum 999999))
  return Call 'Post' "$apiBase/dev/project" $body
}

if ($Part -eq 0 -or $Part -eq 1) {
  Write-Host '--- STEP 1  two phase-template sets + per-spec project phases (API)'
  $stamp = Get-Date -Format 'HHmmss'
  $expectedNames = @(([string]$zh.phase_matched_names) -split '\|')

  # a) per-spec counts
  $matched = TplList 'MATCHED'
  $modified = TplList 'MODIFIED'
  $all = TplList ''
  Write-Host ('  templates: matched=' + $matched.Count + ' modified=' + $modified.Count + ' no-filter=' + $all.Count)
  # 2026-10-05 F7-292: were 7 / 14 / 21 absolute snapshots -- every template added turned this guard red.
  # Assert the STRUCTURE instead: both sets are non-empty and together they are exactly the unfiltered list.
  Ok ($matched.Count -gt 0 -and $modified.Count -gt 0) ('both spec types return templates (matched=' + $matched.Count + ' modified=' + $modified.Count + ')')
  Ok ($all.Count -eq ($matched.Count + $modified.Count)) ('omitting specType returns exactly the union of both sets (' + $all.Count + ' == ' + $matched.Count + ' + ' + $modified.Count + ')')

  # b) matched set holds exactly the 7 expected names, in order
  $matchedNames = @($matched | ForEach-Object { [string]$_.name })
  Write-Host ('  matched names = ' + ($matchedNames -join ' / '))
  Ok ($matchedNames.Count -eq $expectedNames.Count) 'the matched set has 7 names'
  for ($i = 0; $i -lt [Math]::Min($matchedNames.Count, $expectedNames.Count); $i++) {
    Ok ($matchedNames[$i] -eq $expectedNames[$i]) ('matched phase #' + ($i + 1) + ' is as expected')
  }
  $sortOk = $true
  for ($i = 0; $i -lt $matched.Count; $i++) { if ([int]$matched[$i].sortOrder -ne ($i + 1)) { $sortOk = $false } }
  Ok $sortOk 'the matched set is re-numbered 1..7 (not the old 1/6/10/11/12/13/14)'

  # c) subset check
  $modNames = @($modified | ForEach-Object { [string]$_.name })
  $missing = @($matchedNames | Where-Object { $modNames -notcontains $_ })
  Ok ($missing.Count -eq 0) 'every matched name also exists in the modified set (matched is a subset)'

  # d) matched project -> 7 phases mirroring the matched templates
  $sfxM = 'M-' + $stamp
  $cM = NewProbeProject $sfxM 'MATCHED'
  $pidM = $cM.data.id
  Write-Host ('  create(spec=MATCHED) -> code=' + $cM.code + ' projectId=' + $pidM)
  Ok ([int]$cM.code -eq 200) 'POST /dev/project with spec=MATCHED returns 200'
  Ok ($pidM -ne $null -and [int]$pidM -gt 0) 'matched probe project id resolved (guards against vacuous PASS)'

  if ($pidM -ne $null -and [int]$pidM -gt 0) {
    $phM = @((Call 'Get' "$apiBase/dev/project/$pidM/phase" $null).data)
    Write-Host ('  matched project phases=' + $phM.Count + ' -> ' + (($phM | ForEach-Object { [string]$_.phaseName }) -join ' / '))
    Ok ($phM.Count -eq 7) 'a MATCHED project is generated with exactly 7 phases'
    $mirrorOk = $true; $tidOk = $true; $daysOk = $true
    for ($i = 0; $i -lt [Math]::Min($phM.Count, $matched.Count); $i++) {
      if ([string]$phM[$i].phaseName -ne [string]$matched[$i].name) { $mirrorOk = $false }
      if ([string]$phM[$i].templateId -ne [string]$matched[$i].id) { $tidOk = $false }
      if ([int]$phM[$i].defaultDays -ne [int]$matched[$i].defaultDays) { $daysOk = $false }
    }
    Ok $mirrorOk 'each phase mirrors the matched template order (name for name)'
    Ok $tidOk 'each phase records the MATCHED template id (not the modified one)'
    Ok $daysOk 'each phase copies the matched template default days'
    $ipM = @($phM | Where-Object { $_.status -eq 'IN_PROGRESS' }).Count
    $nsM = @($phM | Where-Object { $_.status -eq 'NOT_STARTED' }).Count
    Ok ($ipM -eq 1) 'exactly one matched phase starts as IN_PROGRESS'
    Ok ($nsM -eq 6) 'the other 6 matched phases start as NOT_STARTED'

    # e/f) modified + no-spec projects still get 14 phases
    $cMod = NewProbeProject ('D-' + $stamp) 'MODIFIED'
    $phMod = @((Call 'Get' "$apiBase/dev/project/$($cMod.data.id)/phase" $null).data)
    Write-Host ('  create(spec=MODIFIED) -> code=' + $cMod.code + ' phases=' + $phMod.Count)
    Ok ([int]$cMod.code -eq 200) 'POST /dev/project with spec=MODIFIED returns 200'
    Ok ($phMod.Count -eq 14) 'a MODIFIED project still gets 14 phases'

    $cNull = NewProbeProject ('N-' + $stamp) $null
    $phNull = @((Call 'Get' "$apiBase/dev/project/$($cNull.data.id)/phase" $null).data)
    Write-Host ('  create(spec omitted) -> code=' + $cNull.code + ' phases=' + $phNull.Count)
    Ok ([int]$cNull.code -eq 200) 'POST /dev/project without a spec returns 200'
    Ok ($phNull.Count -eq 14) 'a spec-less project still gets 14 phases (NULL means modified)'
    $nullTidOk = $true
    foreach ($p in $phNull) { if ([string]$p.templateId -eq '') { $nullTidOk = $false } }
    Ok $nullTidOk 'every phase of the spec-less project also records a (MODIFIED) template id'

    # g) uniqueness is scoped to (company, spec, name)
    $newName = 'PROBE-TPL-' + $stamp
    $t1 = Call 'Post' "$apiBase/dev/phase-template" @{ name = $newName; specType = 'MATCHED'; defaultDays = 1; sortOrder = 99; remark = 'probe' }
    Write-Host ('  add template (MATCHED ' + $newName + ') -> code=' + $t1.code)
    Ok ([int]$t1.code -eq 200) 'adding a template to the matched set works'
    $t2 = Call 'Post' "$apiBase/dev/phase-template" @{ name = $newName; specType = 'MATCHED'; defaultDays = 1; sortOrder = 98; remark = 'probe dup' }
    Write-Host ('  add SAME name in SAME spec -> code=' + $t2.code + ' msg=' + $t2.msg)
    Ok ([int]$t2.code -ne 200) 'the same name inside the SAME spec is rejected'
    $t3 = Call 'Post' "$apiBase/dev/phase-template" @{ name = $newName; specType = 'MODIFIED'; defaultDays = 1; sortOrder = 99; remark = 'probe cross' }
    Write-Host ('  add SAME name in OTHER spec -> code=' + $t3.code)
    Ok ([int]$t3.code -eq 200) 'the same name in the OTHER spec is allowed (two sets may share names)'
    if ($t1.data.id) { Call 'Delete' "$apiBase/dev/phase-template/$($t1.data.id)" $null | Out-Null }
    if ($t3.data.id) { Call 'Delete' "$apiBase/dev/phase-template/$($t3.data.id)" $null | Out-Null }

    # h) F7-94 regression: a template rename must not break the phase -> template link.
    # Use the matched "small batch" phase (its template has productStatusSync=1), rename the template,
    # then complete the phase: the product must still flip to NORMAL.
    $syncTpl = $matched | Where-Object { [int]$_.productStatusSync -eq 1 } | Select-Object -First 1
    if ($null -ne $syncTpl) {
      $syncPhase = $phM | Where-Object { [string]$_.phaseName -eq [string]$syncTpl.name } | Select-Object -First 1
      $prj = Call 'Get' "$apiBase/dev/project/$pidM" $null
      $prodBefore = Call 'Get' "$apiBase/product/$($prj.data.productId)" $null
      Write-Host ('  sync-on template=' + $syncTpl.name + ' product before=' + $prodBefore.data.status)
      Ok ([string]$prodBefore.data.status -eq 'DEVELOPING') 'the auto-built product starts as DEVELOPING'

      $renamed = ([string]$syncTpl.name) + 'REN'
      $ren = Call 'Put' "$apiBase/dev/phase-template" @{ id = $syncTpl.id; name = $renamed; specType = 'MATCHED'; defaultDays = $syncTpl.defaultDays; sortOrder = $syncTpl.sortOrder; remark = $syncTpl.remark; productStatusSync = 1 }
      Write-Host ('  rename template -> code=' + $ren.code + ' newName=' + $renamed)
      Ok ([int]$ren.code -eq 200) 'renaming a matched template returns 200'
      $done = Call 'Put' "$apiBase/dev/project/phase/$($syncPhase.id)/complete" $null
      Write-Host ('  complete phase after rename -> code=' + $done.code)
      Ok ([int]$done.code -eq 200) 'completing a phase after its template was renamed still returns 200'
      $prodAfter = Call 'Get' "$apiBase/product/$($prj.data.productId)" $null
      Write-Host ('  product after=' + $prodAfter.data.status)
      Ok ([string]$prodAfter.data.status -eq 'NORMAL') 'the product still flips to NORMAL (link survived the rename)'
      # restore the template name
      Call 'Put' "$apiBase/dev/phase-template" @{ id = $syncTpl.id; name = $syncTpl.name; specType = 'MATCHED'; defaultDays = $syncTpl.defaultDays; sortOrder = $syncTpl.sortOrder; remark = $syncTpl.remark; productStatusSync = 1 } | Out-Null
      $rstr = TplList 'MATCHED'
      Ok (@($rstr | Where-Object { [string]$_.name -eq [string]$syncTpl.name }).Count -eq 1) 'the template name was restored after the probe'
    }
  }
  Summary 'two phase-template sets (API)'
}

if ($Part -eq 0 -or $Part -eq 2) {
  Write-Host '--- STEP 2  template panel switches between the two sets (UI)'
  . (Join-Path $PSScriptRoot 'ui-e2e-lib.ps1')
  WatchErrors
  EnsureLogin | Out-Null
  $B_MATCHED = B64 (ZH 'pj_opt_spec_matched')
  $B_MODIFIED = B64 (ZH 'pj_opt_spec_modified')

  Open '/template?tab=phase' 6000
  ClearErrs | Out-Null
  Start-Sleep -Milliseconds 3500

  # Count the phase rows currently rendered. NOTE: /template keeps BOTH panels mounted (the contract
  # panel holds 1 row), so the count must be scoped to the panel that owns the spec radio group --
  # a document-wide selector returns 8/15 instead of 7/14.
  $jsRows = "(()=>{const g=[].slice.call(document.querySelectorAll('.el-radio-group')).filter(function(x){return x.querySelector('.el-radio-button');})[0];" +
    "const root=g?(g.closest('.panel-body')||g.closest('.el-tab-pane')||document):document;" +
    "return String(root.querySelectorAll('.el-table__body-wrapper tbody tr').length);})()"
  # click the radio-button whose label matches
  function PickSet([string]$b64) {
    $js = "(()=>{const T=x=>new TextDecoder().decode(Uint8Array.from(atob(x),c=>c.charCodeAt(0)));const L=T('$b64');" +
      "const rs=[].slice.call(document.querySelectorAll('.el-radio-button')).filter(function(r){return r.getClientRects().length>0;});" +
      "const t=rs.filter(function(r){return (r.innerText||'').trim()===L;})[0];if(!t)return 'nobtn';const i=t.querySelector('input')||t;t.click();return 'ok';})()"
    return (EvalJs $js).Trim()
  }

  $pick1 = PickSet $B_MATCHED
  Start-Sleep -Milliseconds 1600
  $rows1 = (EvalJs $jsRows).Trim()
  Write-Host ('  picked matched (pick=' + $pick1 + ') -> table rows=' + $rows1)

  $pick2 = PickSet $B_MODIFIED
  Start-Sleep -Milliseconds 1600
  $rows2 = (EvalJs $jsRows).Trim()
  Write-Host ('  picked modified (pick=' + $pick2 + ') -> table rows=' + $rows2)

  Ok ($pick1 -eq 'ok') 'the matched radio button is clickable'
  Ok ($pick2 -eq 'ok') 'the modified radio button is clickable'
  Ok ($rows1 -eq '7') 'the matched tab shows exactly 7 template rows'
  Ok ($rows2 -eq '14') 'the modified tab shows exactly 14 template rows'

  Write-Host ('  errs=' + (Errs))
  Ok ((Errs) -eq '[]') 'no JS/API errors on the template page'
  Summary 'template panel switch (UI)'
}
