# ===========================================================================
#  deploy/local-pipeline.ps1 —— 本地等价流水线（答辩演示用）
#
#  为什么需要它：
#    任务书要求《CI/CD 部署方案》含「配置文件可运行性」，答辩也明确要求演示
#    「测试 / 流水线运行结果截图」。真实流水线跑在 GitHub Actions 上，答辩现场
#    不便现场触发，因此提供本脚本：**按 ci.yml 的阶段顺序在本地执行一遍**，
#    输出可直接截图的结果。
#
#  【这不是伪造截图】本脚本执行的是真实命令、真实测试、真实构建产物。
#    答辩时如实说明：「镜像构建与冒烟阶段按本机环境跳过，完整流水线在
#    GitHub Actions 上执行。」诚实比好看更安全。
#
#  阶段 ↔ ci.yml 对应关系（一一对应，截图才站得住）：
#    [1/6] 版本信息        ← actions/checkout（打印本次构建对应的提交号）
#    [2/6] 后端构建与测试  ← job: backend  / mvn -B clean test
#    [3/6] 前端构建        ← job: frontend / yarn build:prod（= vite build）
#    [4/6] 打包产物        ← job: package  / mvn -B package -DskipTests
#    [5/6] 镜像构建        ← job: docker   / docker build（本机无 Docker → SKIPPED）
#    [6/6] 冒烟测试        ← 请求 /captchaImage（**只对流水线自己启动的服务判失败**，
#                             本机无 Docker 时一律 SKIPPED，观察值另行记录）
#
#  运行（任意目录均可）：
#    powershell -NoProfile -ExecutionPolicy Bypass -File deploy/local-pipeline.ps1
#
#  退出码：任一阶段 FAIL → 1；否则 0。**SKIPPED 不算失败**。
#
#  五个本机坑（改脚本时别踩回去，每条都真实翻过车）：
#    1. 【不要直接调 mvn】本机 mvn 启动脚本损坏（ClassNotFoundException:
#       plexus-classworlds.launcher），必须走 .workbuddy/tools/mvnx.ps1。
#    2. 【前端构建必须先切到 RuoYi-Vue3/】vite 的 root 取的是**当前工作目录**。
#       在仓库根直接跑 `node RuoYi-Vue3/node_modules/vite/bin/vite.js build` 会
#       12ms 报 `Could not resolve entry module "index.html"` —— 那是调用姿势问题，
#       不是代码问题。正确姿势：Push-Location 到前端目录再跑。
#    3. 【不要用 `cmd 2>&1 | Out-File` 抓 Maven 输出】PowerShell 5.1 会把原生命令的
#       stderr 包成 NativeCommandError 再吐回**本进程的 stderr**，于是外层若用管道
#       捕获本脚本，管道会因 RemoteException 中断、本脚本连同日志一起被干掉
#       （2026-09-17 22:01 那次就是这么丢掉后半段输出的）。
#       改用 Maven 自带的 `-l <file>` 落盘，主流程全程无管道。
#    4. 【中文与 ✓ 会乱码】PowerShell 解码原生命令输出走 [Console]::OutputEncoding，
#       本机默认 GBK，不改会把 node 吐的 UTF-8 解成「鉁?」。两个编码设置都要有。
#    5. 【日志要即时追加】PowerShell 的 stdout 有时不返回，而且并行会话可能在构建
#       中途 `mvn clean` 清空 target/，所以每行立刻 flush 落盘，进程被杀也不丢内容。
#    6. 【并行会话会互踩 target/】多会话共用 RuoYi-Vue-fast/target/，一方 clean 的瞬间
#       另一方正在写 class，会报 `Cannot create resource output directory` /
#       `Error assembling JAR` / `写入 xxx.class 时出错`。**不是代码缺陷**，重试即可
#       —— 所以 Maven 阶段走 Invoke-MavenStage（最多 3 次，并打印重试原因）。
#       CI 上每个 job 独占 runner，机制上不会出现该问题。
# ===========================================================================

$ErrorActionPreference = 'Continue'

# 【坑 4】编码：读原生命令输出 = [Console]::OutputEncoding；写原生 stdin = $OutputEncoding
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding          = [System.Text.Encoding]::UTF8
$script:AnsiPattern      = ([char]27) + '\[[0-9;]*[A-Za-z]'   # vite 输 ANSI 颜色码，落日志要剥掉

function Clear-Ansi {
    param([string]$Text)
    return ($Text -replace $script:AnsiPattern, '')
}

# ---------------------------------------------------------------------------
# 0. 环境定位
# ---------------------------------------------------------------------------
$Root     = Split-Path -Parent $PSScriptRoot                       # 仓库根目录
$LogDir   = Join-Path $Root '.workbuddy\logs'
$Stamp    = Get-Date -Format 'yyyyMMdd-HHmmss'
$Base     = Join-Path $LogDir "local-pipeline-$Stamp"
$RunLog   = "$Base.log"                                            # 等同控制台输出（即时落盘）
$MVNX     = Join-Path $Root '.workbuddy\tools\mvnx.ps1'
$FrontDir = Join-Path $Root 'RuoYi-Vue3'
$BackDir  = Join-Path $Root 'RuoYi-Vue-fast'
$JarPath  = Join-Path $BackDir 'target\ruoyi.jar'
$JarCache = Join-Path (Split-Path -Parent $Root) '_Manager_system_build_cache'

if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Force -Path $LogDir | Out-Null }

# 【坑 5】即时追加的日志书写器（UTF-8 无 BOM，AutoFlush）
$script:LogWriter = New-Object System.IO.StreamWriter($RunLog, $false, (New-Object System.Text.UTF8Encoding($false)))
$script:LogWriter.AutoFlush = $true

$script:Results = New-Object System.Collections.Generic.List[object]
$script:Failed  = 0

function Say {
    param([string]$Text = '')
    Write-Host $Text
    $script:LogWriter.WriteLine($Text)
}

# ---------------------------------------------------------------------------
# 输出工具：中文按 2 列宽对齐（不然汇总表会歪）
# ---------------------------------------------------------------------------
function Get-DisplayWidth {
    param([string]$Text)
    $w = 0
    foreach ($ch in $Text.ToCharArray()) {
        $c = [int][char]$ch
        if (($c -ge 0x1100 -and $c -le 0x115F) -or ($c -ge 0x2E80 -and $c -le 0xA4CF) -or
            ($c -ge 0xAC00 -and $c -le 0xD7A3) -or ($c -ge 0xF900 -and $c -le 0xFAFF) -or
            ($c -ge 0xFE30 -and $c -le 0xFE6F) -or ($c -ge 0xFF00 -and $c -le 0xFF60) -or
            ($c -ge 0xFFE0 -and $c -le 0xFFE6)) { $w += 2 } else { $w += 1 }
    }
    return $w
}

function Format-Cell {
    param([string]$Text, [int]$Width, [switch]$Right)
    $pad = $Width - (Get-DisplayWidth $Text)
    if ($pad -lt 0) { $pad = 0 }
    if ($Right) { return (' ' * $pad) + $Text }
    return $Text + (' ' * $pad)
}

# ---------------------------------------------------------------------------
# Maven 阶段封装：带「并行构建冲突」自愈重试
#
#   背景（2026-09-17 实测踩坑）：本项目在多个会话里并行开发，各会话都会跑
#   `mvnx.ps1 -o -B clean test`，而它们**共用同一个 RuoYi-Vue-fast/target/**。
#   一方 clean 掉 target/ 的瞬间，另一方正在写 class → 报
#     `Cannot create resource output directory: ...\target\test-classes`
#     `Error assembling JAR: ...\target\classes\Xxx.class`
#     `写入 ...\target\classes\Xxx.class 时出错`
#   这不是代码缺陷、也不是流水线缺陷，重试即可。CI（GitHub Actions）上每个 job
#   独占一台 runner，从机制上不会出现这个问题。
# ---------------------------------------------------------------------------
function Test-TargetConflict {
    param([string]$StageLog)
    if (-not (Test-Path $StageLog)) { return $false }
    $patterns = 'Cannot create resource output directory|Error assembling JAR|时出错|' +
                'Error while writing|Failed to copy|target[\\/]classes|target[\\/]test-classes'
    return (@(Get-Content -Encoding UTF8 $StageLog | Where-Object { $_ -match $patterns }).Count -gt 0)
}

function Invoke-MavenStage {
    param(
        [string[]]$MavenArgs,
        [string]$StageLog,
        [int]$MaxAttempts = 3
    )
    $code    = 1
    $attempt = 0
    $notes   = @()
    while ($attempt -lt $MaxAttempts) {
        $attempt++
        # 用 Maven 自带 -l 落盘：不套管道，避免 PowerShell 把原生命令 stderr 包成
        # NativeCommandError 再吐回本进程 stderr（见文件头「坑 3」）
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $MVNX @MavenArgs -l $StageLog
        $code = $LASTEXITCODE
        if ($code -eq 0) { break }
        if (-not (Test-TargetConflict -StageLog $StageLog) -or $attempt -ge $MaxAttempts) { break }
        $notes += "并行构建冲突：本机另有会话在跑 Maven，target/ 被并发清理 → 第 $($attempt + 1) 次尝试"
        Start-Sleep -Seconds 5
    }
    return @{ Code = $code; Attempts = $attempt; Notes = $notes; StageLog = $StageLog }
}

# 从 Maven 日志里摘出**真正有信息量**的 [ERROR] 行（剔掉 Maven 的模板尾行）
function Get-MavenErrors {
    param([string]$StageLog, [int]$Count = 3)
    if (-not (Test-Path $StageLog)) { return @() }
    $all = @(Get-Content -Encoding UTF8 $StageLog | Where-Object { $_ -match '\[ERROR\]' })
    $real = @($all | Where-Object {
        $_ -notmatch 'To see the full stack trace|Re-run Maven using|For more information|' +
                     'read the following articles|\[Help 1\]|^\[ERROR\]\s*$'
    })
    if ($real.Count -eq 0) { $real = $all }
    return @($real | Select-Object -First $Count | ForEach-Object { (Clear-Ansi "$_").Trim() })
}

# ---------------------------------------------------------------------------
# 阶段执行器
#   阶段体返回字符串数组；数组里出现 'SKIPPED' / 'FAIL' 即表示该阶段的结论，
#   其余元素作为明细行打印在结论行下方。
# ---------------------------------------------------------------------------
function Invoke-Stage {
    param(
        [string]$Index,
        [string]$Name,
        [scriptblock]$Body
    )
    $label = "[$Index/6] $Name"
    Write-Host (Format-Cell $label 30) -NoNewline      # 控制台先占位，长任务看起来才像在跑
    Write-Host ' ... ' -NoNewline

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $status = 'PASS'
    $detail = @()
    try {
        $r = & $Body
        if ($r -is [array]) { $detail = $r } elseif ($r) { $detail = @($r) }
        foreach ($d in $detail) {
            if ("$d" -eq 'SKIPPED') { $status = 'SKIPPED' }
            elseif ("$d" -eq 'FAIL') { $status = 'FAIL' }
        }
    } catch {
        $status = 'FAIL'
        $detail = @('!! ' + $_.Exception.Message)
    }
    if ($status -eq 'FAIL') { $script:Failed++ }
    $sw.Stop()
    $secs = [math]::Round($sw.Elapsed.TotalSeconds, 1)

    switch ($status) {
        'PASS'    { Write-Host "PASS ($secs s)" -ForegroundColor Green }
        'SKIPPED' { Write-Host "SKIPPED ($secs s)" -ForegroundColor DarkYellow }
        'FAIL'    { Write-Host "FAIL ($secs s)" -ForegroundColor Red }
    }
    $script:LogWriter.WriteLine((Format-Cell $label 30) + ' ... ' + $status + " ($secs s)")

    foreach ($d in $detail) {
        if ("$d" -ne 'SKIPPED' -and "$d" -ne 'FAIL') { Say "      $d" }
    }
    $script:Results.Add([pscustomobject]@{
        Stage  = $label
        Status = $status
        Secs   = $secs
    })
}

# ---------------------------------------------------------------------------
# 开跑
# ---------------------------------------------------------------------------
Say ''
Say '=============================================================='
Say '  高校实验室资产与报修管理平台 · 本地等价流水线'
Say '  对应配置  .github/workflows/ci.yml   （job: backend / frontend / package）'
Say "  开始时间  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Say '=============================================================='
Say ''

# ---- [1/6] 版本信息 --------------------------------------------------------
Invoke-Stage -Index 1 -Name '版本信息' -Body {
    $head = 'local（未初始化 git 或无提交）'
    if (Get-Command git -ErrorAction SilentlyContinue) {
        $h = (& git -C $Root rev-parse --short HEAD 2>$null)
        if ($LASTEXITCODE -eq 0 -and $h) {
            $b = (& git -C $Root rev-parse --abbrev-ref HEAD 2>$null)
            $head = "$($h.ToString().Trim())  (branch: $($b.ToString().Trim()))"
        }
    }
    @("提交: $head", "工作目录: $Root")
}

# ---- [2/6] 后端构建与测试 --------------------------------------------------
Invoke-Stage -Index 2 -Name '后端构建与测试' -Body {
    $stageLog = "$Base.stage2.log"
    $out = @('命令: mvnx.ps1 -o -B clean test   <- job: backend')
    # 【坑 1】不能直接调 mvn；【坑 3】用 Maven 自带 -l 落盘，不套管道
    $res = Invoke-MavenStage -MavenArgs @('-o', '-B', 'clean', 'test') -StageLog $stageLog
    $code = $res.Code
    foreach ($n in $res.Notes) { $out += ('>> ' + $n) }

    $log = @()
    if (Test-Path $stageLog) { $log = @(Get-Content -Encoding UTF8 $stageLog) }
    $tests = $log | Where-Object { $_ -match 'Tests run:\s*\d+.*Failures' } | Select-Object -Last 1
    $build = $log | Where-Object { $_ -match 'BUILD (SUCCESS|FAILURE)' } | Select-Object -Last 1

    if ($tests) { $out += ('>> ' + (Clear-Ansi "$tests").Trim()) }   # 答辩 PPT 要用这一行
    if ($build) { $out += ('>> ' + (Clear-Ansi "$build").Trim()) }

    if ($code -ne 0 -or -not $tests) {
        foreach ($e in (Get-MavenErrors -StageLog $stageLog -Count 3)) { $out += ('!! ' + $e) }
        $out += "!! 后端构建/测试未通过（exit=$code，尝试 $($res.Attempts) 次）；完整输出见 $stageLog"
        $out += 'FAIL'
        return $out
    }
    $out
}

# ---- [3/6] 前端构建 --------------------------------------------------------
Invoke-Stage -Index 3 -Name '前端构建' -Body {
    $stageLog = "$Base.stage3.log"
    $vite = Join-Path $FrontDir 'node_modules\vite\bin\vite.js'
    if (-not (Test-Path $vite)) { throw "找不到 $vite —— 请先在 RuoYi-Vue3 目录执行 yarn install" }

    # node 优先取 PATH；本机 PATH 不稳时回落到 WorkBuddy 托管版本
    $nodeExe = (Get-Command node -ErrorAction SilentlyContinue).Source
    if (-not $nodeExe) {
        $cand = Get-ChildItem -Path (Join-Path $env:USERPROFILE '.workbuddy\binaries\node\versions') `
                              -Filter 'node.exe' -Recurse -ErrorAction SilentlyContinue |
                Select-Object -First 1
        if ($cand) { $nodeExe = $cand.FullName }
    }
    if (-not $nodeExe) { throw '找不到 node，请确认 Node 已安装并在 PATH 中' }

    $out = @('命令: vite build --mode production   <- job: frontend')
    Push-Location $FrontDir      # 【坑 2】vite 的 root = 当前工作目录，不切目录必失败
    try {
        & $nodeExe $vite build --mode production 2>&1 | Out-File -Encoding utf8 $stageLog
        $code = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    $log = @()
    if (Test-Path $stageLog) { $log = @(Get-Content -Encoding UTF8 $stageLog) }
    $done = @($log | Where-Object { $_ -match 'modules transformed|built in' } | Select-Object -Last 2)
    foreach ($d in $done) { $out += ('>> ' + (Clear-Ansi "$d").Trim()) }
    $dist = Join-Path $FrontDir 'dist'
    if (Test-Path $dist) {
        $n = (Get-ChildItem -Recurse -File $dist -ErrorAction SilentlyContinue).Count
        $out += ">> dist/ 产物文件数: $n"
    }

    if ($code -ne 0) {
        $out += "!! 前端构建失败（exit=$code）；完整输出见 $stageLog"
        $out += 'FAIL'
        return $out
    }
    $out
}

# ---- [4/6] 打包产物 --------------------------------------------------------
Invoke-Stage -Index 4 -Name '打包产物' -Body {
    $stageLog = "$Base.stage4.log"
    $out = @('命令: mvnx.ps1 -o -B package -DskipTests   <- job: package')
    # 测试已在 [2/6] 跑过且通过，这里只出可运行 jar，不重复跑测试
    $res = Invoke-MavenStage -MavenArgs @('-o', '-B', 'package', '-DskipTests') -StageLog $stageLog
    $code = $res.Code
    foreach ($n in $res.Notes) { $out += ('>> ' + $n) }

    $log = @()
    if (Test-Path $stageLog) { $log = @(Get-Content -Encoding UTF8 $stageLog) }
    $build = $log | Where-Object { $_ -match 'BUILD (SUCCESS|FAILURE)' } | Select-Object -Last 1
    if ($build) { $out += ('>> ' + (Clear-Ansi "$build").Trim()) }

    if ($code -ne 0 -or -not (Test-Path $JarPath)) {
        foreach ($e in (Get-MavenErrors -StageLog $stageLog -Count 3)) { $out += ('!! ' + $e) }
        $out += "!! 打包未通过（exit=$code，尝试 $($res.Attempts) 次）；完整输出见 $stageLog"
        $out += 'FAIL'
        return $out
    }
    $mb = [math]::Round((Get-Item $JarPath).Length / 1MB, 1)
    $out += ">> 产物: RuoYi-Vue-fast/target/ruoyi.jar  ($mb MB)"

    # 本机防护：并行会话的 mvn clean 会清掉 target/，所以立刻备份到仓库外
    if (-not (Test-Path $JarCache)) { New-Item -ItemType Directory -Force -Path $JarCache | Out-Null }
    Copy-Item $JarPath (Join-Path $JarCache 'ruoyi.jar') -Force
    $out += ">> 已备份到仓库外: $JarCache\ruoyi.jar（防并行会话清空 target/）"
    $out
}

# ---- [5/6] 镜像构建（无 Docker 则跳过） ------------------------------------
Invoke-Stage -Index 5 -Name '镜像构建' -Body {
    # 与 ci.yml 的 job: docker 逐项对齐：后端镜像 → 前端镜像 → compose 语法门禁。
    # 全程只 build 不推送 registry（仓库 Token 已吊销，答辩用本地镜像足够）。
    $backDockerfile  = Join-Path $BackDir 'Dockerfile'
    $frontDockerfile = Join-Path $FrontDir 'Dockerfile'

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        return @('SKIPPED',
                 '跳过原因: 本机未安装 Docker —— 镜像构建在 GitHub Actions 的 job: docker 上执行',
                 '注: 容器化交付物的静态校验由工具脚本另行完成，不依赖 Docker')
    }
    if (-not (Test-Path $backDockerfile)) {
        return @('SKIPPED', '跳过原因: 缺少 RuoYi-Vue-fast/Dockerfile（T4「容器化交付」卡交付物）')
    }
    if (-not (Test-Path $frontDockerfile)) {
        return @('SKIPPED', '跳过原因: 缺少 RuoYi-Vue3/Dockerfile（T4「容器化交付」卡交付物）')
    }

    $log = "$Base.stage5.log"
    $out = @()
    Push-Location $Root
    try {
        foreach ($img in @(@('lab-asset-backend:ci',  './RuoYi-Vue-fast'),
                           @('lab-asset-frontend:ci', './RuoYi-Vue3'))) {
            $out += "命令: docker build -t $($img[0]) $($img[1])"
            & docker build -t $img[0] $img[1] 2>&1 | Out-File -Encoding utf8 -Append $log
            if ($LASTEXITCODE -ne 0) {
                throw "docker build $($img[0]) 失败（exit=$LASTEXITCODE）；完整输出见 $log"
            }
            $out += ">> 镜像: $($img[0])"
        }
        & docker compose -f docker-compose.yml config --quiet 2>&1 | Out-File -Encoding utf8 -Append $log
        if ($LASTEXITCODE -ne 0) { throw "docker compose config 校验失败；完整输出见 $log" }
        $out += '>> docker compose -f docker-compose.yml config --quiet  通过'
    } finally {
        Pop-Location
    }
    $out
}

# ---- [6/6] 冒烟测试（连不上或非本流水线启动的服务则跳过） ------------------
Invoke-Stage -Index 6 -Name '冒烟测试' -Body {
    # /captchaImage 是若依的匿名接口，且它会读 Redis —— 一个探针同时验证「后端起来了」+「Redis 通了」
    #
    # 判据设计（重要）：**只对本流水线自己启动的服务判失败**。
    #   本机没有 Docker，阶段 5 是 SKIPPED，也就意味着本流水线**没有**启动这个服务；
    #   此时 8080 上若恰好有别的后端（人或别的会话起的）在响应，
    #   它的健康与否不是本流水线的责任 —— 顶多作为观察值记录，不计入失败。
    #   反之若容器由本流水线拉起（docker 可用），非 200 就是真失败。
    $selfHosted = (Get-Command docker -ErrorAction SilentlyContinue) -and (Test-Path (Join-Path $BackDir 'Dockerfile'))

    if (-not (Get-Command curl.exe -ErrorAction SilentlyContinue)) {
        return @('SKIPPED', '跳过原因: 找不到 curl.exe')
    }
    $code = (& curl.exe --max-time 3 -s -o NUL -w '%{http_code}' 'http://localhost:8080/captchaImage' 2>$null)
    $code = "$code".Trim()

    if ($code -eq '' -or $code -eq '000') {
        return @('SKIPPED',
                 '跳过原因: http://localhost:8080 无响应 —— 后端未启动（需 MySQL + Redis）',
                 '本机未装 Docker，无法由流水线拉起容器；冒烟在 GitHub Actions / 部署环境上执行')
    }
    if ($code -eq '200') {
        return @(">> GET /captchaImage -> HTTP 200（后端与 Redis 均正常）")
    }
    if (-not $selfHosted) {
        return @('SKIPPED',
                 "跳过原因: 探测到 http://localhost:8080 返回 HTTP $code，但该服务不是本流水线启动的",
                 '本机未装 Docker（阶段 5 SKIPPED）→ 不对非本流水线启动的服务判失败',
                 '若要真实冒烟：先手工起后端（MySQL + Redis），再重跑本脚本')
    }
    throw "冒烟失败: GET /captchaImage -> HTTP $code（400/500 通常意味着 Redis 未启动）"
}

# ---------------------------------------------------------------------------
# 汇总
# ---------------------------------------------------------------------------
$pass  = @($script:Results | Where-Object { $_.Status -eq 'PASS' }).Count
$skip  = @($script:Results | Where-Object { $_.Status -eq 'SKIPPED' }).Count
$total = 0.0
foreach ($r in $script:Results) { $total += [double]$r.Secs }   # 不用 Measure-Object：本机对 List[object] 会抛 ArgumentException
$total = [math]::Round($total, 1)

Say ''
Say '=============================================================='
Say '  流水线汇总'
Say '=============================================================='
Say ('  ' + (Format-Cell '阶段' 30) + ' ' + (Format-Cell '结果' 9) + ' ' + (Format-Cell '耗时(秒)' 9 -Right))
Say ('  ' + ('-' * 30) + ' ' + ('-' * 9) + ' ' + ('-' * 9))
foreach ($r in $script:Results) {
    Say ('  ' + (Format-Cell $r.Stage 30) + ' ' + (Format-Cell $r.Status 9) + ' ' + (Format-Cell ("$($r.Secs)") 9 -Right))
}
Say '=============================================================='
Say ("  PASS $pass / SKIPPED $skip / FAIL $($script:Failed)      总耗时 $total s")
Say "  完整输出: $RunLog"
Say '=============================================================='
Say ''

if ($script:Failed -gt 0) {
    Say "[FAIL] 流水线未通过：$($script:Failed) 个阶段 FAIL"
    $script:LogWriter.Flush(); $script:LogWriter.Close()
    exit 1
}

Say '流水线通过（本输出即答辩「流水线运行结果」截图的采集对象）'
$script:LogWriter.Flush(); $script:LogWriter.Close()
exit 0
