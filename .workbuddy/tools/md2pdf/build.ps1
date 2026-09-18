# ASCII-only wrapper: Windows PowerShell 5.1 reads non-BOM .ps1 as ANSI/GBK,
# which corrupts Chinese paths and breaks string literals.
# The real orchestrator therefore lives in build.mjs (Node, UTF-8 safe).
#
# Usage: powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/md2pdf/build.ps1
#
# Note: the managed node path below is built from char codes so this file stays pure ASCII.

$node = 'C:\Users\' + [char]0x738B + [char]0x65FB + [char]0x8F89 +
        '\.workbuddy\binaries\node\versions\22.22.2-3\node.exe'
if (-not (Test-Path -LiteralPath $node)) {
  $node = (Get-Command node -ErrorAction SilentlyContinue).Source
}
$script = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) 'build.mjs'
& $node $script
exit $LASTEXITCODE
