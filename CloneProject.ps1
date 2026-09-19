# ==========================================================
# Project Cloning Configuration Variables
# ==========================================================
$projectPath     = "E:\Projects\Google\"
$newProjectPath  = "E:\Temp\"
$projectName     = "Ulenspigel"
$newProjectName  = "Ulenspigel"
$oldPackageName  = "com.KonstantinShramko.Audiobook"  # Main source package to refactor
$newPackageName  = "com.KonstantinShramko.Ulenspigel" # Target package for cloned project

# Classes or files that MUST retain their original package name
# (Required for precompiled JNI .so native libraries such as liblyra_decoder.so)
$preserveFiles    = @("LyraDecoder.kt")
$preservePackage  = "com.KonstantinShramko.Audiobook"

# Derived lower-case variables for preference keys, storage names, etc.
$projectNameLower    = $projectName.ToLower()
$newProjectNameLower = $newProjectName.ToLower()

# ==========================================================
# Calculated Paths
# ==========================================================
$projectFolder    = Join-Path $projectPath $projectName
$newProjectFolder = Join-Path $newProjectPath $newProjectName

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting project cloning: $projectName -> $newProjectName" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "Source Project : $projectFolder"
Write-Host "New Project    : $newProjectFolder"
Write-Host "Old Package    : $oldPackageName"
Write-Host "New Package    : $newPackageName"
Write-Host "----------------------------------------------------------"

# Verify source project existence
if (-not (Test-Path $projectFolder)) {
    Write-Error "Error: Source directory '$projectFolder' not found!"
    exit 1
}

# 1. Create target folder and copy source files excluding cache/build folders
# ==========================================================
Write-Host "[1/6] Copying source files..." -ForegroundColor Yellow

if (Test-Path $newProjectFolder) {
    Remove-Item -Path $newProjectFolder -Recurse -Force -ErrorAction SilentlyContinue
}
New-Item -ItemType Directory -Path $newProjectFolder -Force | Out-Null

$excludeDirs = @('.gradle', '.idea', 'build', '.artifacts', '.navigation', '.cxx', 'captures')

Get-ChildItem -Path $projectFolder -Recurse | ForEach-Object {
    $relativePath = $_.FullName.Substring($projectFolder.Length)

    # Check if relative path contains an excluded directory
    $isExcluded = $false
    foreach ($ex in $excludeDirs) {
        if ($relativePath -split '[\\/]' -contains $ex) {
            $isExcluded = $true
            break
        }
    }

    if (-not $isExcluded) {
        $targetPath = Join-Path $newProjectFolder $relativePath
        if ($_.PSIsContainer) {
            if (-not (Test-Path $targetPath)) {
                New-Item -ItemType Directory -Path $targetPath -Force | Out-Null
            }
        } else {
            $targetDir = Split-Path $targetPath
            if (-not (Test-Path $targetDir)) {
                New-Item -ItemType Directory -Path $targetDir -Force | Out-Null
            }
            Copy-Item -Path $_.FullName -Destination $targetPath -Force
        }
    }
}

Write-Host "    Files copied successfully (cache and build folders skipped)." -ForegroundColor Green

# 2. Update settings.gradle.kts
# ==========================================================
Write-Host "[2/6] Updating settings.gradle.kts..." -ForegroundColor Yellow
$settingsFile = Join-Path $newProjectFolder "settings.gradle.kts"
if (Test-Path $settingsFile) {
    $content = Get-Content -Path $settingsFile -Raw -Encoding UTF8
    $content = $content -replace 'rootProject\.name\s*=\s*"[^"]*"', "rootProject.name = `"$newProjectName`""
    Set-Content -Path $settingsFile -Value $content -Encoding UTF8
    Write-Host "    rootProject.name updated to '$newProjectName'." -ForegroundColor Green
}

# 3. Update app/build.gradle.kts
# ==========================================================
Write-Host "[3/6] Updating app/build.gradle.kts..." -ForegroundColor Yellow
$appGradleFile = Join-Path $newProjectFolder "app\build.gradle.kts"
if (Test-Path $appGradleFile) {
    $content = Get-Content -Path $appGradleFile -Raw -Encoding UTF8
    $content = $content -replace 'namespace\s*=\s*"[^"]*"', "namespace = `"$newPackageName`""
    $content = $content -replace 'applicationId\s*=\s*"[^"]*"', "applicationId = `"$newPackageName`""
    $content = $content -replace 'archivesName\.set\("[^"]*"\)', "archivesName.set(`"$newProjectName`")"
    Set-Content -Path $appGradleFile -Value $content -Encoding UTF8
    Write-Host "    namespace and applicationId updated to '$newPackageName'." -ForegroundColor Green
}

# 4. Refactor package directory structures (main, test, androidTest, etc.)
# ==========================================================
Write-Host "[4/6] Refactoring package directories across all source sets (main, test, androidTest)..." -ForegroundColor Yellow

if ($oldPackageName -and $newPackageName -and ($oldPackageName -ne $newPackageName)) {
    $oldRelPath = $oldPackageName -replace '\.', '\'
    $newRelPath = $newPackageName -replace '\.', '\'

    $srcFolder = Join-Path $newProjectFolder "app\src"
    if (Test-Path $srcFolder) {
        Get-ChildItem -Path $srcFolder -Recurse -Directory | ForEach-Object {
            if ($_.FullName.EndsWith($oldRelPath)) {
                $oldPkgDir = $_.FullName
                $parentDir = $oldPkgDir.Substring(0, $oldPkgDir.Length - $oldRelPath.Length)
                $newPkgDir = Join-Path $parentDir $newRelPath

                Write-Host "    Moving package: '$oldPkgDir' -> '$newPkgDir'..."
                if (-not (Test-Path $newPkgDir)) {
                    New-Item -ItemType Directory -Path $newPkgDir -Force | Out-Null
                }
                Get-ChildItem -Path $oldPkgDir | ForEach-Object {
                    # Skip files that are preserved for JNI bindings (e.g. LyraDecoder.kt)
                    if ($preserveFiles -contains $_.Name) {
                        Write-Host "    Preserving JNI file in original package: $($_.Name)" -ForegroundColor Cyan
                        return
                    }

                    $dest = Join-Path $newPkgDir $_.Name
                    if ($_.PSIsContainer) {
                        Move-Item -Path $_.FullName -Destination $dest -Force
                    } else {
                        Move-Item -Path $_.FullName -Destination $dest -Force
                    }
                }
            }
        }
    }
}

# 5. Rename files and directories matching old project name
# ==========================================================
Write-Host "[5/6] Renaming files and directories containing '$projectName'..." -ForegroundColor Yellow

if ($projectName -ne $newProjectName) {
    Get-ChildItem -Path $newProjectFolder -Recurse | Where-Object { $_.Name -like "*$projectName*" } | ForEach-Object {
        $newName = $_.Name.Replace($projectName, $newProjectName)
        Rename-Item -Path $_.FullName -NewName $newName -Force -ErrorAction SilentlyContinue
        Write-Host "    Renamed file/dir: $_.Name -> $newName" -ForegroundColor Gray
    }
}

# 6. Replace package, project, theme, and string references in source files
# ==========================================================
Write-Host "[6/6] Replacing package, project, theme, and string references in source files..." -ForegroundColor Yellow

$extensionsToUpdate = @('*.kt', '*.java', '*.xml', '*.gradle', '*.kts', '*.properties', '*.pro', '*.txt', '*.json', '*.md')

Get-ChildItem -Path $newProjectFolder -Recurse -Include $extensionsToUpdate | ForEach-Object {
    # Skip updating files that are explicitly preserved for JNI bindings
    if ($preserveFiles -contains $_.Name) {
        Write-Host "    Skipping refactoring for preserved JNI file: $($_.Name)" -ForegroundColor Cyan
        return
    }

    $fileContent = Get-Content -Path $_.FullName -Raw -Encoding UTF8
    if ($fileContent) {
        $updatedContent = $fileContent
        $isModified = $false

        # 1. Replace Package Name (com.KonstantinShramko.Audiobook -> com.KonstantinShramko.Ulenspigel)
        if ($oldPackageName -and ($oldPackageName -ne $newPackageName) -and $updatedContent.Contains($oldPackageName)) {
            $updatedContent = $updatedContent.Replace($oldPackageName, $newPackageName)
            $isModified = $true
        }

        # 2. Replace Lowercase Project Name
        if (($projectNameLower -ne $newProjectNameLower) -and $updatedContent.Contains($projectNameLower)) {
            $updatedContent = $updatedContent.Replace($projectNameLower, $newProjectNameLower)
            $isModified = $true
        }

        # 3. Replace Project Name
        if (($projectName -ne $newProjectName) -and $updatedContent.Contains($projectName)) {
            $updatedContent = $updatedContent.Replace($projectName, $newProjectName)
            $isModified = $true
        }

        # 4. If this file references LyraDecoder, ensure import for preserved package is present
        if ($updatedContent.Contains("LyraDecoder") -and -not $updatedContent.Contains("import $preservePackage.LyraDecoder")) {
            $updatedContent = $updatedContent -replace "(package\s+[^\r\n]+)", "`$1`r`n`r`nimport $preservePackage.LyraDecoder"
            $isModified = $true
        }

        if ($isModified) {
            Set-Content -Path $_.FullName -Value $updatedContent -Encoding UTF8
            Write-Host "    Updated content: $($_.FullName.Substring($newProjectFolder.Length))" -ForegroundColor Gray
        }
    }
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Cloning finished successfully! Project '$newProjectName' is ready." -ForegroundColor Green
Write-Host "==========================================================" -ForegroundColor Cyan