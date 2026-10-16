#Requires -Version 7.0
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-SafeGit {
    param([string]$Root, [string[]]$Arguments, [int[]]$AllowedExitCodes = @(0))
    $info = [Diagnostics.ProcessStartInfo]::new('git')
    $info.UseShellExecute = $false
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.Environment['GIT_OPTIONAL_LOCKS'] = '0'
    $overrides = @()
    if ($Arguments[0] -in @('status','diff')) { $overrides = @(Get-FilterSuppressionArguments $Root) }
    foreach ($argument in (@('--no-optional-locks', '-c', 'core.fsmonitor=false', '-C', $Root) + $overrides + $Arguments)) { $info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    $buffer = [IO.MemoryStream]::new()
    try {
        if (-not $process.Start()) { throw 'Cannot start Git.' }
        $outputTask = $process.StandardOutput.BaseStream.CopyToAsync($buffer)
        $errorTask = $process.StandardError.ReadToEndAsync()
        $process.WaitForExit()
        $null = $outputTask.GetAwaiter().GetResult()
        $null = $errorTask.GetAwaiter().GetResult()
        if ($process.ExitCode -notin $AllowedExitCodes) { throw 'Git inspection failed; no protection was completed.' }
        $bytes = $buffer.ToArray()
        return [pscustomobject]@{ Bytes = $bytes; Text = [Text.Encoding]::UTF8.GetString($bytes); ExitCode = $process.ExitCode }
    } finally { $buffer.Dispose(); $process.Dispose() }
}

function Get-FilterSuppressionArguments([string]$Root) {
    # Discover names only; never execute or echo configured conversion commands.
    $keys = Invoke-SafeGit $Root @('config','--null','--name-only','--get-regexp','^filter\..*\.(clean|process|required)$') @(0,1)
    $drivers = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($key in $keys.Text.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)) {
        $match = [regex]::Match($key, '^filter\.(.+)\.(clean|process|required)$')
        if (-not $match.Success -or $match.Groups[1].Value -notmatch '^[A-Za-z0-9._/-]+$') {
            throw 'Cannot safely suppress configured conversion filter names.'
        }
        $null = $drivers.Add($match.Groups[1].Value)
    }
    foreach ($driver in ($drivers | Sort-Object -CaseSensitive)) {
        '-c'; "filter.$driver.clean="
        '-c'; "filter.$driver.process="
        '-c'; "filter.$driver.required=false"
    }
}
function Assert-NoLinkParents([string]$Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        if ([IO.File]::Exists($current) -or [IO.Directory]::Exists($current)) {
            if (([IO.File]::GetAttributes($current) -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw 'Linked paths or linked parent directories are unsupported.'
            }
        }
        $parent = [IO.Directory]::GetParent($current)
        $current = if ($null -eq $parent) { $null } else { $parent.FullName }
    }
}

function Get-ProjectInventory([string]$Root) {
    $files = [Collections.Generic.List[string]]::new()
    $directories = [Collections.Generic.List[string]]::new()
    $pending = [Collections.Generic.Stack[string]]::new()
    $pending.Push($Root)
    while ($pending.Count) {
        $directory = $pending.Pop()
        foreach ($path in [IO.Directory]::GetFileSystemEntries($directory)) {
            $attributes = [IO.File]::GetAttributes($path)
            if (($attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'Links in the repository cannot be protected safely.' }
            $relative = [IO.Path]::GetRelativePath($Root, $path).Replace('\', '/')
            if ([IO.Path]::GetFileName($path) -eq '.git' -and $relative -ne '.git') { throw 'Nested repositories are unsupported.' }
            if (($attributes -band [IO.FileAttributes]::Directory) -ne 0) {
                $directories.Add($relative)
                $pending.Push($path)
            } else { $files.Add($relative) }
        }
    }
    return [pscustomobject]@{ Files = @($files | Sort-Object -CaseSensitive); Directories = @($directories | Sort-Object -CaseSensitive) }
}

function Get-FileDigest([string]$Path) { return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash }

function Get-SanitizedRemotes([string]$Root) {
    $configured = Invoke-SafeGit $Root @('config','--null','--get-regexp','^remote\..*\.(url|pushurl)$') @(0,1)
    foreach ($entry in $configured.Text.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)) {
        $separator = $entry.IndexOf("`n")
        if ($separator -lt 0) { throw 'Cannot safely parse remote configuration.' }
        $key = $entry.Substring(0, $separator)
        $identity = $entry.Substring($separator + 1) -replace '[?#].*$', ''
        $match = [regex]::Match($key, '^remote\.(.+)\.(url|pushurl)$')
        if (-not $match.Success) { throw 'Cannot safely parse remote configuration names.' }
        $uri = $null
        if ([Uri]::TryCreate($identity, [UriKind]::Absolute, [ref]$uri) -and $identity -match '^[A-Za-z][A-Za-z0-9+.-]*://') {
            $builder = [UriBuilder]::new($uri)
            $builder.UserName = ''; $builder.Password = ''; $builder.Query = ''; $builder.Fragment = ''
            $identity = $builder.Uri.AbsoluteUri
        } elseif ($identity -match '^(?:[^@/\s]+@)?(?<host>[^:/\s]+):(?<path>.+)$') {
            $identity = $Matches.host + ':' + $Matches.path
        } elseif ($identity -match '[@\r\n]' -or $identity -match '://') {
            $identity = '[redacted-unrecognized-remote]'
        }
        [pscustomobject]@{ Name = $match.Groups[1].Value; Type = $match.Groups[2].Value; Identity = $identity }
    }
}
function Get-StateCore([string]$Root) {
    $head = Invoke-SafeGit $Root @('rev-parse', '--verify', '--quiet', 'HEAD') @(0,1)
    $branch = Invoke-SafeGit $Root @('symbolic-ref', '--quiet', '--short', 'HEAD') @(0,1)
    $status = Invoke-SafeGit $Root @('status', '--porcelain=v1', '-z', '--untracked-files=all')
    $tracked = Invoke-SafeGit $Root @('ls-files', '-z', '--cached', '--others', '--exclude-standard')
    return [pscustomobject][ordered]@{
        ProjectPath = $Root
        Head = if ($head.ExitCode -eq 0) { $head.Text.Trim() } else { $null }
        Branch = if ($branch.ExitCode -eq 0) { $branch.Text.Trim() } else { $null }
        Remotes = @(Get-SanitizedRemotes $Root)
        OperationState = 'None'
        ConversionFiltersSuppressed = $true
        StatusFormat = 'porcelain-v1 NUL-separated UTF-8, base64 encoded'
        StatusBase64 = [Convert]::ToBase64String($status.Bytes)
        Paths = @($tracked.Text.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries) | Sort-Object -Unique -CaseSensitive)
    }
}

function Assert-SafeProject([string]$ProjectPath) {
    foreach ($name in @('GIT_DIR','GIT_WORK_TREE','GIT_COMMON_DIR','GIT_INDEX_FILE','GIT_OBJECT_DIRECTORY','GIT_ALTERNATE_OBJECT_DIRECTORIES','GIT_CONFIG','GIT_CONFIG_COUNT','GIT_CONFIG_PARAMETERS','GIT_SHALLOW_FILE','GIT_NAMESPACE','GIT_REPLACE_REF_BASE')) {
        if ([Environment]::GetEnvironmentVariable($name)) { throw 'Git environment overrides are unsupported; use a clean session.' }
    }
    $root = [IO.Path]::GetFullPath($ProjectPath).TrimEnd([IO.Path]::DirectorySeparatorChar)
    Assert-NoLinkParents $root
    if (-not [IO.Directory]::Exists($root)) { throw 'Project directory does not exist.' }
    $top = (Invoke-SafeGit $root @('rev-parse', '--show-toplevel')).Text.Trim()
    if (-not $root.Equals([IO.Path]::GetFullPath($top), [StringComparison]::OrdinalIgnoreCase)) { throw 'Use the repository root.' }
    if (-not [IO.Directory]::Exists((Join-Path $root '.git'))) { throw 'Only standalone repositories with an in-root .git directory are supported.' }
    $gitDir = (Invoke-SafeGit $root @('rev-parse', '--absolute-git-dir')).Text.Trim()
    if (-not [IO.Path]::GetFullPath($gitDir).Equals((Join-Path $root '.git'), [StringComparison]::OrdinalIgnoreCase)) { throw 'External Git metadata is unsupported.' }
    foreach ($marker in @('MERGE_HEAD','CHERRY_PICK_HEAD','REVERT_HEAD','REBASE_HEAD','BISECT_LOG','rebase-apply','rebase-merge','sequencer','commondir','worktrees','objects/info/alternates','objects/info/http-alternates','info/grafts','info/sparse-checkout')) {
        if (Test-Path -LiteralPath (Join-Path $gitDir $marker)) { throw ('Repository operation/special state: ' + $marker) }
    }
    $partial = Invoke-SafeGit $root @('config','--get-regexp','^(extensions\.partialclone|remote\..*\.promisor)$') @(0,1)
    if ($partial.ExitCode -eq 0) { throw 'Partial clones and promisor object stores are unsupported.' }
    $inventory = Get-ProjectInventory $root
    $locks = @($inventory.Files | Where-Object { $_ -like '.git/*.lock' })
    if ($locks.Count) { throw ('Repository lock state: ' + ($locks -join ', ')) }
    if ((Invoke-SafeGit $root @('ls-files','-u','-z')).Bytes.Length) { throw 'Repository conflict state: unmerged index entries.' }
    $flags = (Invoke-SafeGit $root @('ls-files','-v','-z')).Text
    foreach ($entry in $flags.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)) {
        if ($entry[0] -ceq 'S' -or [char]::IsLower($entry[0])) { throw 'Index flags hiding worktree changes are unsupported.' }
    }
    $stage = (Invoke-SafeGit $root @('ls-files','--stage','-z')).Text
    foreach ($entry in $stage.Split([char]0, [StringSplitOptions]::RemoveEmptyEntries)) {
        if ($entry.StartsWith('160000 ') -or $entry.StartsWith('120000 ')) { throw 'Submodules and tracked symbolic links are unsupported.' }
    }
    return [pscustomobject]@{ Root = $root; Inventory = $inventory }
}

function Get-GitProjectState {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$ProjectPath)
    $project = Assert-SafeProject $ProjectPath
    Get-StateCore $project.Root
}

function New-ProjectProtection {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$ProjectPath,
        [Parameter(Mandatory)][string]$BackupRoot,
        [Parameter(Mandatory)][ValidateSet('Worktree','Full')][string]$Mode
    )
    $project = Assert-SafeProject $ProjectPath
    $root = $project.Root
    $destinationRoot = [IO.Path]::GetFullPath($BackupRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
    # Reject linked ancestors rather than trust lexical containment through junctions.
    Assert-NoLinkParents $destinationRoot
    if ($destinationRoot.Equals($root, [StringComparison]::OrdinalIgnoreCase) -or
        $destinationRoot.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'The backup root must be outside the repository.'
    }
    $state = Get-StateCore $root
    $before = @{}
    foreach ($relative in $project.Inventory.Files) { $before[$relative] = Get-FileDigest (Join-Path $root $relative) }
    [IO.Directory]::CreateDirectory($destinationRoot) | Out-Null
    Assert-NoLinkParents $destinationRoot
    $backup = Join-Path $destinationRoot ((Get-Date -Format 'yyyyMMddTHHmmssfff') + '-' + $Mode + '-' + [guid]::NewGuid().ToString('N'))
    if (Test-Path -LiteralPath $backup) { throw 'Backup path already exists.' }
    [IO.Directory]::CreateDirectory($backup) | Out-Null
    # SUCCESS.json is written last. On any failure retain the incomplete directory.
    $state | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $backup 'state.json') -Encoding utf8
    $null = Invoke-SafeGit $root @('diff','--cached','--binary','--full-index','--no-ext-diff','--no-textconv',('--output=' + (Join-Path $backup 'staged.patch')))
    $null = Invoke-SafeGit $root @('diff','--binary','--full-index','--no-ext-diff','--no-textconv',('--output=' + (Join-Path $backup 'unstaged.patch')))
    $selected = if ($Mode -eq 'Full') { $project.Inventory.Files } else { @($state.Paths | Where-Object { $before.ContainsKey($_) }) }
    $fileRoot = Join-Path $backup 'files'
    [IO.Directory]::CreateDirectory($fileRoot) | Out-Null
    if ($Mode -eq 'Full') {
        foreach ($relative in $project.Inventory.Directories) { [IO.Directory]::CreateDirectory((Join-Path $fileRoot $relative)) | Out-Null }
    }
    $manifest = [Collections.Generic.List[object]]::new()
    foreach ($relative in $selected) {
        $source = Join-Path $root $relative
        $target = Join-Path $fileRoot $relative
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
        [IO.File]::Copy($source, $target, $false)
        $digest = Get-FileDigest $target
        if ($digest -ne $before[$relative]) { throw 'A source file changed or backup verification failed.' }
        $manifest.Add([pscustomobject]@{ Path = $relative; BackupFile = 'files/' + $relative; SHA256 = $digest })
    }
    # Detect concurrent edits, metadata changes, new files, links, operations or removals.
    $after = Assert-SafeProject $root
    if (($after.Inventory.Files -join [char]0) -cne ($project.Inventory.Files -join [char]0) -or
        ($after.Inventory.Directories -join [char]0) -cne ($project.Inventory.Directories -join [char]0)) { throw 'Repository inventory changed during protection.' }
    foreach ($relative in $after.Inventory.Files) {
        if ((Get-FileDigest (Join-Path $root $relative)) -ne $before[$relative]) { throw 'Repository content changed during protection.' }
    }
    if ((Get-StateCore $root | ConvertTo-Json -Depth 10 -Compress) -cne ($state | ConvertTo-Json -Depth 10 -Compress)) { throw 'Git state changed during protection.' }
    $scope = if ($Mode -eq 'Full') { 'Regular-file contents only, including ignored files, empty directories and in-root .git metadata; no ACL or ADS preservation, VSS snapshot or atomic snapshot guarantee.' } else { 'Tracked and nonignored untracked regular files, staged and unstaged binary patches, HEAD and branch. Ignored untracked files and Git metadata are excluded. No automatic restore is provided.' }
    $scope += ' Conversion filters suppressed per invocation; patches are informational and raw file copies are authoritative. Stop editors/background writers; restoration has not been exercised. Worktree needs a matching base repository.'
    $artifacts = foreach ($name in @('state.json','staged.patch','unstaged.patch')) {
        [pscustomobject]@{ BackupFile = $name; SHA256 = Get-FileDigest (Join-Path $backup $name) }
    }
    [pscustomobject]@{ Kind = $Mode; Scope = $scope; Files = @($manifest); Artifacts = @($artifacts) } |
        ConvertTo-Json -Depth 10 | Set-Content -LiteralPath (Join-Path $backup 'manifest.json') -Encoding utf8
    [pscustomobject]@{ Kind = $Mode; ManifestSHA256 = Get-FileDigest (Join-Path $backup 'manifest.json') } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backup 'SUCCESS.json') -Encoding utf8
    return [pscustomobject]@{ BackupPath = $backup; Kind = $Mode; Scope = $scope }
}

Export-ModuleMember -Function Get-GitProjectState, New-ProjectProtection
