#Requires -Version 7.0
[CmdletBinding()]
param([Parameter(Mandatory)][string]$ProjectPath)
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'GitSafety.psm1') -Force
Get-GitProjectState -ProjectPath $ProjectPath | ConvertTo-Json -Depth 10
