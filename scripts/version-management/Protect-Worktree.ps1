#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$ProjectPath,
    [Parameter(Mandatory)][string]$BackupRoot,
    [Parameter(Mandatory)][ValidateSet('Worktree','Full')][string]$Mode
)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'GitSafety.psm1') -Force
New-ProjectProtection -ProjectPath $ProjectPath -BackupRoot $BackupRoot -Mode $Mode | ConvertTo-Json -Depth 10
