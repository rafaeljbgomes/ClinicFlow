[CmdletBinding()]
param([Parameter(Mandatory)][string[]]$Artifact)
# Downloads main's latest security evidence so a PR gate blocks only findings the PR adds (ADR-030). Only artifacts
# from push, schedule or dispatch runs of main in this repository are trusted. Any failure leaves no reference, and
# the gate then treats every High/Critical finding as new.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$Root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$ReferenceRoot = Join-Path $Root 'reports/reference'
if (-not $env:CHANGE_ID) { Write-Host 'Not a pull request; the full gate applies.'; return }
if (-not $env:GH_TOKEN -or -not $env:GITHUB_REPOSITORY -or -not $env:GITHUB_REPOSITORY_ID) {
    Write-Warning 'GitHub API context unavailable; no main security reference.'
    return
}
$Repository = $env:GITHUB_REPOSITORY
foreach ($Name in $Artifact) {
    try {
        if ($Name -notmatch '^[a-z0-9-]+$') { throw "Invalid artifact name: $Name" }
        $Listing = & gh api "repos/$Repository/actions/artifacts?name=$Name&per_page=100" | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0) { throw 'Artifact listing failed.' }
        $Candidates = @($Listing.artifacts | Where-Object {
            -not $_.expired -and $_.workflow_run.head_branch -eq 'main' -and "$($_.workflow_run.head_repository_id)" -eq $env:GITHUB_REPOSITORY_ID
        } | Sort-Object { [DateTimeOffset]$_.created_at } -Descending)
        $Selected = $null
        foreach ($Candidate in $Candidates) {
            $Run = & gh api "repos/$Repository/actions/runs/$($Candidate.workflow_run.id)" | ConvertFrom-Json
            if ($LASTEXITCODE -eq 0 -and $Run.event -in @('push','schedule','workflow_dispatch') -and $Run.head_branch -eq 'main') { $Selected = $Candidate; break }
        }
        if (-not $Selected) { Write-Warning "No main run has published $Name; its findings all count as new."; continue }
        $Directory = Join-Path $ReferenceRoot $Name
        New-Item -ItemType Directory -Force $Directory | Out-Null
        $Archive = Join-Path $ReferenceRoot "$Name.zip"
        # The API redirects to a signed blob URL; PowerShell drops the token on that cross-host redirect.
        Invoke-WebRequest -Uri "https://api.github.com/repos/$Repository/actions/artifacts/$($Selected.id)/zip" -OutFile $Archive `
            -Headers @{ Authorization = "Bearer $env:GH_TOKEN"; Accept = 'application/vnd.github+json' }
        Expand-Archive -LiteralPath $Archive -DestinationPath $Directory -Force
        Remove-Item -LiteralPath $Archive
        [ordered]@{ artifact = $Name; id = $Selected.id; runId = $Selected.workflow_run.id; headSha = $Selected.workflow_run.head_sha; createdAt = $Selected.created_at } |
            ConvertTo-Json | Set-Content (Join-Path $Directory 'reference.json')
        Write-Host "Main security reference ${Name}: run $($Selected.workflow_run.id) at $($Selected.workflow_run.head_sha)"
    }
    catch {
        Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $ReferenceRoot $Name)
        Write-Warning "Main security reference ${Name} unavailable: $($_.Exception.Message)"
    }
}
