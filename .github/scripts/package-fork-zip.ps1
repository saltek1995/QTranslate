param(
    [Parameter(Mandatory = $true)][string] $Version,
    [Parameter(Mandatory = $true)][string] $PortableArchive,
    [Parameter(Mandatory = $true)][string] $RuntimeImage,
    [Parameter(Mandatory = $true)][string] $OutputDirectory,
    [Parameter(Mandatory = $true)][string] $SourceRoot,
    [string] $ApprovedPluginArchive,
    [string] $ApprovedPluginArchiveSha256,
    [string] $ApprovedPluginSourceCommit
)
$ErrorActionPreference = 'Stop'
$archive = (Resolve-Path -LiteralPath $PortableArchive).Path
$runtime = (Resolve-Path -LiteralPath $RuntimeImage).Path
$source = (Resolve-Path -LiteralPath $SourceRoot).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Output directory already exists' }
New-Item -ItemType Directory -Path $output | Out-Null
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $output 'portable')
$distribution = Join-Path $output 'portable/QTranslate'
if ($ApprovedPluginArchive) {
    if ($ApprovedPluginSourceCommit -notmatch '^[0-9a-fA-F]{40}$' -or
        $ApprovedPluginArchiveSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw 'An approved plugin archive requires its SHA-256 and exact source commit'
    }
    $guardedPaths = @('api', 'plugins', 'buildSrc', 'gradle', 'build.gradle.kts', 'settings.gradle.kts',
        'gradle.properties', 'LICENSE', 'NOTICE.md', 'LICENSES', 'THIRD_PARTY_LICENSES')
    & git -C $source diff --quiet $ApprovedPluginSourceCommit -- @guardedPaths
    if ($LASTEXITCODE -ne 0) { throw 'Plugin sources or build inputs changed; cannot reuse the approved binaries' }
    $untracked = @(& git -C $source ls-files --others --exclude-standard -- @guardedPaths)
    if ($LASTEXITCODE -ne 0 -or $untracked.Count -gt 0) { throw 'Untracked plugin or build input files prevent approved binary reuse' }
    $approved = (Resolve-Path -LiteralPath $ApprovedPluginArchive).Path
    if ((Get-FileHash -LiteralPath $approved -Algorithm SHA256).Hash -ne $ApprovedPluginArchiveSha256) {
        throw 'Approved plugin archive SHA-256 mismatch'
    }
    $expected = @(Get-ChildItem -LiteralPath (Join-Path $distribution 'plugins') -Filter '*.jar' | ForEach-Object Name)
    $zipReader = [IO.Compression.ZipFile]::OpenRead($approved)
    try {
        $entries = @($zipReader.Entries | Where-Object { $_.FullName.Replace('\', '/') -match '^QTranslate/plugins/[A-Za-z0-9._-]+\.jar$' })
        $names = @($entries | ForEach-Object { [IO.Path]::GetFileName($_.FullName.Replace('\', '/')) })
        if ($names.Count -ne $expected.Count -or @($names | Select-Object -Unique).Count -ne $names.Count -or
            @(Compare-Object $expected $names).Count -ne 0) {
            throw 'Approved plugin archive inventory differs from the new distribution'
        }
        foreach ($entry in $entries) {
            $name = [IO.Path]::GetFileName($entry.FullName.Replace('\', '/'))
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $distribution "plugins/$name"), $true)
        }
        Write-Output "Preserved $($entries.Count) unchanged approved plugin JARs"
    } finally { $zipReader.Dispose() }
}
$inputDirectory = Join-Path $output 'package-input'
New-Item -ItemType Directory -Path $inputDirectory | Out-Null
Copy-Item -LiteralPath (Join-Path $distribution 'QTranslate.jar') -Destination $inputDirectory
$imageParent = Join-Path $output 'app-image'
$packageVersion = ($Version -split '[-+]')[0]
& jpackage --type app-image --name QTranslate --input $inputDirectory `
    --main-jar QTranslate.jar --main-class com.github.ahatem.qtranslate.app.MainKt `
    --app-version $packageVersion --icon (Join-Path $source 'ui-swing/src/main/resources/icons/app/icon.ico') `
    --runtime-image $runtime --dest $imageParent --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'jpackage app-image failed' }
$image = Join-Path $imageParent 'QTranslate'
foreach ($name in @('plugins', 'languages', 'themes', 'icons', 'LICENSES', 'THIRD_PARTY_LICENSES')) {
    Copy-Item -LiteralPath (Join-Path $distribution $name) -Destination (Join-Path $image $name) -Recurse
}
foreach ($name in @('LICENSE', 'NOTICE.md', 'portable-plugin-ids.txt')) {
    Copy-Item -LiteralPath (Join-Path $distribution $name) -Destination (Join-Path $image $name)
}
New-Item -ItemType File -Path (Join-Path $image 'portable.flag') | Out-Null
$zip = Join-Path $output "QTranslate-$Version-windows-x64.zip"
Compress-Archive -LiteralPath $image -DestinationPath $zip
Write-Output "Windows ZIP created: $zip"
