# RuoYi-Vue-fast 构建辅助脚本（WorkBuddy 用，PowerShell 版）
#
# 为什么需要它：
#   1. 本机 mvn 启动脚本损坏（ClassNotFoundException: plexus-classworlds.launcher），
#      只能直接调用 classworlds 启动器。
#   2. Git Bash 环境有时不可用（PATH 被破坏，ls/dirname/head 全部 command not found），
#      此时 mvnx.sh 连外层 bash 都起不来，用本脚本兜底。
#
# 用法（任意目录）：
#   powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/mvnx.ps1 -o -B clean test
#   不带参数时默认执行： -o -B clean compile
#
# 两个坑（已规避，改脚本时别踩回去）：
#   1. 【不要加 param 块】。一旦声明了 param + [Parameter]，PowerShell 会启用通用参数，
#      Maven 的 -o / -v / -e 会被当成 -OutBuffer / -Verbose / -ErrorAction 拦截或报"参数歧义"。
#      因此本脚本一律用 $args 直通。
#   2. 【给 java.exe 传参必须用数组】。若直接写 -Dclassworlds.conf=D:\... ，
#      PowerShell 会把它拆坏，把 ".conf=D:\..." 当成主类。

$ErrorActionPreference = 'Stop'

$MVN_HOME = 'D:\IDEA\apache-maven-3.9.4'
$JAVA_EXE = 'D:\Study_Running\JDK_Warehouse\jdk-17.0.19\bin\java.exe'
$PROJECT_DIR = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) 'RuoYi-Vue-fast'

$MavenArgs = @($args)
if ($MavenArgs.Count -eq 0) { $MavenArgs = @('-o', '-B', 'clean', 'compile') }

if (-not (Test-Path $JAVA_EXE)) { throw "找不到 JDK: $JAVA_EXE" }
if (-not (Test-Path $PROJECT_DIR)) { throw "找不到后端项目目录: $PROJECT_DIR" }

Set-Location $PROJECT_DIR

$argList = @(
    '-classpath', (Join-Path $MVN_HOME 'boot\plexus-classworlds-2.7.0.jar'),
    "-Dclassworlds.conf=$(Join-Path $MVN_HOME 'bin\m2.conf')",
    "-Dmaven.home=$MVN_HOME",
    "-Dmaven.multiModuleProjectDirectory=$PROJECT_DIR",
    'org.codehaus.plexus.classworlds.launcher.Launcher'
) + $MavenArgs

& $JAVA_EXE @argList
exit $LASTEXITCODE
