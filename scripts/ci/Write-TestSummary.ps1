[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Title,
    [string[]]$JUnit = @(),
    [string]$JaCoCo,
    [string]$Lcov,
    [string]$Pit
)

# Publishes JUnit, JaCoCo and LCOV results to the GitHub Actions job summary without third-party actions.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$Lines = [System.Collections.Generic.List[string]]::new()
$Lines.Add("### $Title")
$Lines.Add('')

Push-Location $RepositoryRoot
try {
    $Files = @($JUnit | ForEach-Object { Get-ChildItem -Path $_ -File -ErrorAction SilentlyContinue })
    if ($Files.Count) {
        $Totals = [ordered]@{ Tests = 0; Failures = 0; Errors = 0; Skipped = 0 }
        $Failed = [System.Collections.Generic.List[string]]::new()
        foreach ($File in $Files) {
            [xml]$Document = Get-Content -Raw -LiteralPath $File.FullName
            foreach ($Suite in @($Document.SelectNodes('//testsuite'))) {
                foreach ($Key in @('Tests','Failures','Errors','Skipped')) {
                    $Value = $Suite.GetAttribute($Key.ToLowerInvariant())
                    if ($Value) { $Totals[$Key] += [int]$Value }
                }
            }
            foreach ($Case in @($Document.SelectNodes('//testcase[failure or error]'))) {
                $Name = "$($Case.GetAttribute('classname')).$($Case.GetAttribute('name'))"
                $Failed.Add($Name)
                Write-Host "::error title=Test failed::$Name"
            }
        }
        $Lines.Add('| Tests | Failures | Errors | Skipped |')
        $Lines.Add('| ---: | ---: | ---: | ---: |')
        $Lines.Add("| $($Totals.Tests) | $($Totals.Failures) | $($Totals.Errors) | $($Totals.Skipped) |")
        if ($Failed.Count) {
            $Lines.Add('')
            $Lines.Add('Failed tests:')
            $Failed | Select-Object -First 50 | ForEach-Object { $Lines.Add("- ``$_``") }
        }
    } elseif ($JUnit.Count) {
        $Lines.Add('No JUnit reports were produced.')
    }

    if ($Pit) {
        if (Test-Path -LiteralPath $Pit) {
            [xml]$Mutations = Get-Content -Raw -LiteralPath $Pit
            $All = @($Mutations.SelectNodes('//mutation'))
            $Detected = @($All | Where-Object { $_.GetAttribute('detected') -eq 'true' }).Count
            $Score = if ($All.Count) { '{0:P1}' -f ($Detected / $All.Count) } else { 'n/a' }
            $Lines.Add("Mutation score: $Detected / $($All.Count) detected ($Score)")
        } else {
            $Lines.Add('No PIT mutation report was produced.')
        }
    }

    if ($JaCoCo -and (Test-Path -LiteralPath $JaCoCo)) {
        $Settings = [System.Xml.XmlReaderSettings]::new()
        $Settings.DtdProcessing = [System.Xml.DtdProcessing]::Ignore
        $Reader = [System.Xml.XmlReader]::Create((Resolve-Path -LiteralPath $JaCoCo).Path, $Settings)
        try { $Report = [xml]::new(); $Report.Load($Reader) } finally { $Reader.Dispose() }
        $Lines.Add('')
        $Lines.Add('| Coverage | Covered | Missed | Ratio |')
        $Lines.Add('| --- | ---: | ---: | ---: |')
        foreach ($Counter in @($Report.report.counter | Where-Object { $_.type -in @('LINE','BRANCH','INSTRUCTION') })) {
            $Covered = [int]$Counter.covered; $Missed = [int]$Counter.missed
            $Ratio = if ($Covered + $Missed) { '{0:P1}' -f ($Covered / ($Covered + $Missed)) } else { 'n/a' }
            $Lines.Add("| $($Counter.type.ToLowerInvariant()) | $Covered | $Missed | $Ratio |")
        }
    }

    if ($Lcov -and (Test-Path -LiteralPath $Lcov)) {
        $Found = 0; $Hit = 0
        foreach ($Line in [IO.File]::ReadLines((Resolve-Path -LiteralPath $Lcov).Path)) {
            if ($Line.StartsWith('LF:')) { $Found += [int]$Line.Substring(3) }
            elseif ($Line.StartsWith('LH:')) { $Hit += [int]$Line.Substring(3) }
        }
        $Ratio = if ($Found) { '{0:P1}' -f ($Hit / $Found) } else { 'n/a' }
        $Lines.Add('')
        $Lines.Add("Line coverage: $Hit / $Found ($Ratio)")
    }
}
finally { Pop-Location }

$Markdown = ($Lines -join "`n") + "`n"
if ($env:GITHUB_STEP_SUMMARY) { Add-Content -LiteralPath $env:GITHUB_STEP_SUMMARY -Value $Markdown }
else { Write-Host $Markdown }
