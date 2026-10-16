# Requires PowerShell 7. Tests retain their unique temporary repositories for review.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ('git-safety-tests-' + [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($testRoot) | Out-Null
# Isolate fixture configuration from workstation-wide conversion drivers/identity.
$previousGlobalConfig = $env:GIT_CONFIG_GLOBAL
$previousNoSystem = $env:GIT_CONFIG_NOSYSTEM
$env:GIT_CONFIG_GLOBAL = Join-Path $testRoot 'empty-global.config'
[IO.File]::WriteAllText($env:GIT_CONFIG_GLOBAL, '')
$env:GIT_CONFIG_NOSYSTEM = '1'
try {
function Assert($condition, [string]$message) { if (-not $condition) { throw "ASSERT: $message" } }
function Invoke-FixtureGit([string]$repo, [string[]]$arguments) {
    $result = & git --no-optional-locks -C $repo @arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'Test fixture Git operation failed.' }
    return $result
}
function Reject([scriptblock]$action, [string]$message) {
    $rejected = $false
    try { & $action | Out-Null } catch { $rejected = $true }
    Assert $rejected $message
}
function New-Repo([string]$name) {
    $path = Join-Path $testRoot $name
    [IO.Directory]::CreateDirectory($path) | Out-Null
    Invoke-FixtureGit $path @('init', '-q') | Out-Null
    Invoke-FixtureGit $path @('config','user.name','Isolated test') | Out-Null
    Invoke-FixtureGit $path @('config','user.email','isolated@example.invalid') | Out-Null
    return $path
}
Write-Host "Retained fixtures: $testRoot"
Assert (Test-Path (Join-Path $PSScriptRoot 'GitSafety.psm1')) 'Safety module must exist (expected initial RED).'
Import-Module (Join-Path $PSScriptRoot 'GitSafety.psm1') -Force
$repo = New-Repo '中文 空格'
[IO.File]::WriteAllText((Join-Path $repo '.gitignore'), "ignored/`n")
[IO.File]::WriteAllText((Join-Path $repo '中文 空格.txt'), 'base')
[IO.File]::WriteAllBytes((Join-Path $repo 'binary.bin'), [byte[]](0,1,2,255))
Invoke-FixtureGit $repo @('add','--','.gitignore','中文 空格.txt','binary.bin') | Out-Null
Invoke-FixtureGit $repo @('commit','-qm','fixture') | Out-Null
[IO.File]::WriteAllText((Join-Path $repo '中文 空格.txt'), 'staged')
[IO.File]::WriteAllBytes((Join-Path $repo 'binary.bin'), [byte[]](0,7,2,255))
Invoke-FixtureGit $repo @('add','--','中文 空格.txt','binary.bin') | Out-Null
[IO.File]::WriteAllText((Join-Path $repo '中文 空格.txt'), 'unstaged')
[IO.File]::WriteAllBytes((Join-Path $repo 'binary.bin'), [byte[]](0,8,2,255))
[IO.File]::WriteAllText((Join-Path $repo '未跟踪 文件.txt'), 'untracked')
[IO.Directory]::CreateDirectory((Join-Path $repo 'sub dir')) | Out-Null
[IO.File]::WriteAllText((Join-Path $repo 'sub dir/深层.txt'), 'nested file')
Invoke-FixtureGit $repo @('add','--','sub dir/深层.txt') | Out-Null
[IO.Directory]::CreateDirectory((Join-Path $repo 'ignored')) | Out-Null
[IO.File]::WriteAllText((Join-Path $repo 'ignored/private.txt'), 'ignored fixture')
$backups = Join-Path $testRoot 'backups'
$indexBefore = (Get-FileHash -LiteralPath (Join-Path $repo '.git/index')).Hash
$stateBefore = Get-GitProjectState -ProjectPath $repo | ConvertTo-Json -Depth 10 -Compress
$work = New-ProjectProtection -ProjectPath $repo -BackupRoot $backups -Mode Worktree
Assert ($work.Kind -eq 'Worktree') 'Worktree kind'
Assert (Test-Path (Join-Path $work.BackupPath 'SUCCESS.json')) 'Success marker'
Assert ((Get-Content -Raw -LiteralPath (Join-Path $work.BackupPath 'staged.patch')) -match 'GIT binary patch') 'Staged binary patch'
Assert ((Get-Content -Raw -LiteralPath (Join-Path $work.BackupPath 'unstaged.patch')) -match 'GIT binary patch') 'Unstaged binary patch'
Assert ((Get-Content -Raw -LiteralPath (Join-Path $work.BackupPath 'files/中文 空格.txt')) -eq 'unstaged') 'Chinese space filename'
Assert (Test-Path (Join-Path $work.BackupPath 'files/未跟踪 文件.txt')) 'Untracked protected'
Assert (Test-Path (Join-Path $work.BackupPath 'files/sub dir/深层.txt')) 'Nested tracked file protected'
Assert (-not (Test-Path (Join-Path $work.BackupPath 'files/ignored/private.txt'))) 'Worktree excludes ignored'
$full = New-ProjectProtection -ProjectPath $repo -BackupRoot $backups -Mode Full
Assert (Test-Path (Join-Path $full.BackupPath 'files/ignored/private.txt')) 'Full includes ignored'
Assert (Test-Path (Join-Path $full.BackupPath 'files/.git/index')) 'Full includes metadata'
$manifest = Get-Content -Raw -LiteralPath (Join-Path $full.BackupPath 'manifest.json') | ConvertFrom-Json
foreach ($entry in $manifest.Files) {
    Assert ((Get-FileHash -LiteralPath (Join-Path $full.BackupPath $entry.BackupFile)).Hash -eq $entry.SHA256) 'SHA256 verified file'
}
Assert ($indexBefore -eq (Get-FileHash -LiteralPath (Join-Path $repo '.git/index')).Hash) 'Index unchanged'
Assert ($stateBefore -eq (Get-GitProjectState -ProjectPath $repo | ConvertTo-Json -Depth 10 -Compress)) 'State unchanged'
Assert ((Get-Content -Raw -LiteralPath (Join-Path $repo '中文 空格.txt')) -eq 'unstaged') 'Worktree unchanged'
$again = New-ProjectProtection -ProjectPath $repo -BackupRoot $backups -Mode Worktree
Assert ($again.BackupPath -ne $work.BackupPath) 'Unique no-overwrite backup'
Reject { New-ProjectProtection -ProjectPath $repo -BackupRoot (Join-Path $repo 'inside') -Mode Full } 'Reject backup inside repository'
Reject { New-ProjectProtection -ProjectPath $testRoot -BackupRoot $backups -Mode Full } 'Reject nonrepository'
$nested = New-Repo 'nested-parent'
Invoke-FixtureGit $nested @('init','-q','child') | Out-Null
Reject { New-ProjectProtection -ProjectPath $nested -BackupRoot $backups -Mode Full } 'Reject nested repository'
$special = New-Repo 'special'
[IO.File]::WriteAllText((Join-Path $special '.git/MERGE_HEAD'), ('0' * 40))
Reject { New-ProjectProtection -ProjectPath $special -BackupRoot $backups -Mode Full } 'Reject special state'
$linkRepo = New-Repo 'link-repository'
New-Item -ItemType Junction -Path (Join-Path $linkRepo 'linked') -Target $repo | Out-Null
Reject { New-ProjectProtection -ProjectPath $linkRepo -BackupRoot $backups -Mode Full } 'Reject link in repository'
New-Item -ItemType Junction -Path (Join-Path $testRoot 'backup-link') -Target $repo | Out-Null
Reject { New-ProjectProtection -ProjectPath $repo -BackupRoot (Join-Path $testRoot 'backup-link/inside') -Mode Full } 'Reject resolved backup link parent'
$unborn = New-Repo 'unborn'
[IO.File]::WriteAllText((Join-Path $unborn 'new.txt'), 'new')
Invoke-FixtureGit $unborn @('add','--','new.txt') | Out-Null
Assert ((New-ProjectProtection -ProjectPath $unborn -BackupRoot $backups -Mode Worktree).Kind -eq 'Worktree') 'Unborn index protected'
$conflict = New-Repo 'conflict'
[IO.File]::WriteAllText((Join-Path $conflict 'f.txt'), 'base')
Invoke-FixtureGit $conflict @('add','--','f.txt') | Out-Null
Invoke-FixtureGit $conflict @('commit','-qm','base') | Out-Null
Invoke-FixtureGit $conflict @('checkout','-qb','other') | Out-Null
[IO.File]::WriteAllText((Join-Path $conflict 'f.txt'), 'other')
Invoke-FixtureGit $conflict @('commit','-qam','other') | Out-Null
Invoke-FixtureGit $conflict @('checkout','-q','-') | Out-Null
[IO.File]::WriteAllText((Join-Path $conflict 'f.txt'), 'main')
Invoke-FixtureGit $conflict @('commit','-qam','main') | Out-Null
& git -C $conflict merge other 2>&1 | Out-Null
Assert ($LASTEXITCODE -ne 0) 'Fixture has merge conflict'
Reject { New-ProjectProtection -ProjectPath $conflict -BackupRoot $backups -Mode Worktree } 'Reject conflicts'
$external = New-Repo 'external-objects'
[IO.File]::WriteAllText((Join-Path $external '.git/objects/info/alternates'), ((Join-Path $repo '.git/objects').Replace('\','/') + "`n"))
Reject { New-ProjectProtection -ProjectPath $external -BackupRoot $backups -Mode Full } 'Reject external object store'
Invoke-FixtureGit $repo @('update-index','--assume-unchanged','--','中文 空格.txt') | Out-Null
Reject { New-ProjectProtection -ProjectPath $repo -BackupRoot $backups -Mode Worktree } 'Reject index flags hiding changes'
Invoke-FixtureGit $repo @('update-index','--no-assume-unchanged','--','中文 空格.txt') | Out-Null
$head = (Invoke-FixtureGit $repo @('rev-parse','HEAD')).Trim()
Invoke-FixtureGit $repo @('update-index','--add','--cacheinfo',"160000,$head,submodule") | Out-Null
Reject { New-ProjectProtection -ProjectPath $repo -BackupRoot $backups -Mode Full } 'Reject gitlink index entry'
$hookRepo = New-Repo 'hook-repository'
$sentinel = Join-Path $testRoot 'hook-was-run'
$hookFile = Join-Path $testRoot 'fsmonitor-hook.sh'
[IO.File]::WriteAllText($hookFile, ("#!/bin/sh`necho invoked > '" + $sentinel.Replace('\','/') + "'`necho token`n"))
Invoke-FixtureGit $hookRepo @('config','core.fsmonitor',$hookFile.Replace('\','/')) | Out-Null
Get-GitProjectState -ProjectPath $hookRepo | Out-Null
Assert (-not (Test-Path -LiteralPath $sentinel)) 'Inspection must not execute configured fsmonitor hook'
$previousIndex = $env:GIT_INDEX_FILE
try {
    $env:GIT_INDEX_FILE = Join-Path $testRoot 'external-index'
    Reject { New-ProjectProtection -ProjectPath $hookRepo -BackupRoot $backups -Mode Full } 'Reject external Git environment overrides'
} finally { $env:GIT_INDEX_FILE = $previousIndex }
$partial = New-Repo 'partial-clone'
Invoke-FixtureGit $partial @('config','extensions.partialClone','origin') | Out-Null
Reject { New-ProjectProtection -ProjectPath $partial -BackupRoot $backups -Mode Full } 'Reject partial clone requiring missing external objects'
$filterFailures = [Collections.Generic.List[string]]::new()
foreach ($driver in @('clean','process')) {
    foreach ($operation in @('Inspect','Protect')) {
        $filterRepo = New-Repo ("filter-$driver-$operation")
        [IO.File]::WriteAllText((Join-Path $filterRepo 'tracked.txt'), 'base')
        Invoke-FixtureGit $filterRepo @('add','--','tracked.txt') | Out-Null
        Invoke-FixtureGit $filterRepo @('commit','-qm','filter baseline') | Out-Null
        [IO.File]::WriteAllText((Join-Path $filterRepo 'tracked.txt'), 'staged raw content')
        Invoke-FixtureGit $filterRepo @('add','--','tracked.txt') | Out-Null
        [IO.File]::WriteAllText((Join-Path $filterRepo '.gitattributes'), "*.txt filter=sentinel`n")
        [IO.File]::WriteAllText((Join-Path $filterRepo 'tracked.txt'), 'changed content with different length')
        $filterSentinel = Join-Path $testRoot ("filter-$driver-$operation-executed")
        $filterScript = Join-Path $testRoot ("filter-$driver-$operation.sh")
        [IO.File]::WriteAllText($filterScript, ("#!/bin/sh`necho invoked > '" + $filterSentinel.Replace('\','/') + "'`ncat`n"))
        Invoke-FixtureGit $filterRepo @('config',"filter.sentinel.$driver",$filterScript.Replace('\','/')) | Out-Null
        Invoke-FixtureGit $filterRepo @('config','filter.sentinel.required','true') | Out-Null
        $configHash = (Get-FileHash -LiteralPath (Join-Path $filterRepo '.git/config')).Hash
        $filterIndexHash = (Get-FileHash -LiteralPath (Join-Path $filterRepo '.git/index')).Hash
        $rawHash = (Get-FileHash -LiteralPath (Join-Path $filterRepo 'tracked.txt')).Hash
        $succeeded = $true
        try {
            if ($operation -eq 'Inspect') {
                $filterState = Get-GitProjectState -ProjectPath $filterRepo
                Assert $filterState.ConversionFiltersSuppressed 'Inspection discloses filter suppression'
            } else {
                $filterProtection = New-ProjectProtection -ProjectPath $filterRepo -BackupRoot $backups -Mode Worktree
                Assert ((Get-FileHash -LiteralPath (Join-Path $filterProtection.BackupPath 'files/tracked.txt')).Hash -eq $rawHash) 'Raw attributed worktree bytes authoritative'
                Assert ((Get-Content -Raw -LiteralPath (Join-Path $filterProtection.BackupPath 'staged.patch')) -match 'staged raw content') 'Attributed staged change protected'
                Assert ((Get-Content -Raw -LiteralPath (Join-Path $filterProtection.BackupPath 'unstaged.patch')) -match 'changed content') 'Attributed unstaged change recorded'
                Assert ($filterProtection.Scope -match 'filters suppressed' -and $filterProtection.Scope -match 'informational' -and $filterProtection.Scope -match 'authoritative') 'Filter and patch limitations disclosed'
            }
        } catch { $succeeded = $false }
        if (-not $succeeded -or (Test-Path -LiteralPath $filterSentinel)) { $filterFailures.Add("$driver/$operation") }
        Assert ((Get-FileHash -LiteralPath (Join-Path $filterRepo '.git/config')).Hash -eq $configHash) 'User filter configuration unchanged'
        Assert ((Get-FileHash -LiteralPath (Join-Path $filterRepo '.git/index')).Hash -eq $filterIndexHash) 'Attributed index unchanged'
        Assert ((Get-FileHash -LiteralPath (Join-Path $filterRepo 'tracked.txt')).Hash -eq $rawHash) 'Attributed source unchanged'
    }
}
Assert ($filterFailures.Count -eq 0) ('Configured conversion filters must be suppressed without execution and operations succeed: ' + ($filterFailures -join ', '))
Assert ($full.Scope -match 'regular-file contents only' -and $full.Scope -match 'ACL' -and $full.Scope -match 'ADS' -and $full.Scope -match 'VSS' -and $full.Scope -match 'atomic') 'Returned Full scope exposes filesystem limitations'
Assert ($manifest.Scope -eq $full.Scope) 'Manifest and returned Full limitations agree'
$remoteRepo = New-Repo 'remote-redaction'
Invoke-FixtureGit $remoteRepo @('remote','add','origin','https://fixture-user:fixture-password@example.invalid/org/repo.git?token=fixture-query#fixture-fragment') | Out-Null
Invoke-FixtureGit $remoteRepo @('config','remote.origin.pushurl','git@push.example.invalid:org/repo.git?fixture-query#fixture-fragment') | Out-Null
$remoteState = Get-GitProjectState -ProjectPath $remoteRepo
$remoteJson = $remoteState | ConvertTo-Json -Depth 10 -Compress
Assert ($remoteState.PSObject.Properties.Name -contains 'Remotes') 'Inspection must expose sanitized Remotes'
Assert (@($remoteState.Remotes).Count -eq 2) 'Inspection includes fetch and push remotes'
Assert ($remoteJson -notmatch 'fixture-user|fixture-password|fixture-query|fixture-fragment|git@') 'Remote credentials and query/fragment redacted'
Assert ($remoteState.Remotes[0].Name -eq 'origin') 'Remote name included'
Assert ($remoteJson -match 'example.invalid/org/repo.git' -and $remoteJson -match 'push.example.invalid:org/repo.git') 'Sanitized remote identity retained'
$operationMessage = ''
try { Get-GitProjectState -ProjectPath $special | Out-Null } catch { $operationMessage = $_.Exception.Message }
Assert ($operationMessage -match 'MERGE_HEAD') 'Inspection names operation marker'
$lockRepo = New-Repo 'locked-repository'
[IO.File]::WriteAllText((Join-Path $lockRepo '.git/index.lock'), 'fixture lock')
$lockMessage = ''
try { Get-GitProjectState -ProjectPath $lockRepo | Out-Null } catch { $lockMessage = $_.Exception.Message }
Assert ($lockMessage -match 'index.lock') 'Inspection names lock state'
Write-Host 'PASS: isolated Git protection, preservation, binary patches, SHA256, scope and refusal cases.'

} finally {
    $env:GIT_CONFIG_GLOBAL = $previousGlobalConfig
    $env:GIT_CONFIG_NOSYSTEM = $previousNoSystem
}