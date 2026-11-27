# Vendor a release of the shared SDK test suite into conformance/suite.
#
#     ./tools/update-suite.ps1 <path to a checkout of the suite at a release tag>
#
# The suite is copied, not fetched at test time, for the same reason the API description is: the
# tests must run from this repository alone, and a new case reaches this SDK only when the pin in
# conformance/suite-version.txt moves, in a reviewed pull request.
#
# The suite's fake hub is Python. Its exact dependencies, with their hashes, are written to
# conformance/hub-requirements.txt from the suite's own lock file, so CI installs the same packages
# the suite was released with. That step needs uv (https://docs.astral.sh/uv/).
param([Parameter(Mandatory)][string]$Suite)
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$here = Join-Path $root 'conformance'
$target = Join-Path $here 'suite'
# What an SDK needs from the suite: the cases, what they refer to, the fake hub, and the SDK
# reference's schema, page list and checker (sdkref/, run on docs/sdkref.json in CI).
$taken = @('cases', 'fixtures.json', 'vocabulary.json', 'sdkref', 'src/mfx_fake_hub', 'LICENSE')

$pyproject = Get-Content -Raw (Join-Path $Suite 'pyproject.toml')
if ($pyproject -notmatch '(?m)^version\s*=\s*"([^"]+)"') { throw "the suite's pyproject.toml names no version" }
$version = $Matches[1]
$commit = (git -C $Suite rev-parse HEAD).Trim()

if (Test-Path $target) { Remove-Item -Recurse -Force $target }
New-Item -ItemType Directory -Force $target | Out-Null
foreach ($part in $taken) {
    $name = Split-Path -Leaf $part
    Copy-Item -Recurse (Join-Path $Suite $part) (Join-Path $target $name)
}
Get-ChildItem -Recurse -Force $target -Include '__pycache__', '*.pyc' | Remove-Item -Recurse -Force

Push-Location $Suite
try {
    $requirements = (uv export --frozen --no-dev --no-emit-project --no-header --format requirements-txt) -join "`n"
} finally { Pop-Location }
$header = "# The fake hub's dependencies, exported from mfx-sdk-conformance $version's lock file by`n" +
    "# tools/update-suite.ps1. Installed with --require-hashes. Do not edit by hand.`n"
[IO.File]::WriteAllText((Join-Path $here 'hub-requirements.txt'), $header + $requirements + "`n")

$pin = "mfx-sdk-conformance $version ($commit)"
[IO.File]::WriteAllText((Join-Path $here 'suite-version.txt'), $pin + "`n")
Write-Output $pin
