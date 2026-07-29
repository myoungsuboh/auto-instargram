# ============================================================================
#  auto-instargram 종료 (Windows)
#
#  stop.bat 이 이 파일을 호출한다 (한글 출력을 위해 — run.bat 주석 참조).
#
#  ⚠️ 이 파일은 반드시 **UTF-8 BOM 포함**으로 저장해야 한다 (run.ps1 주석 참조).
# ============================================================================

$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

Write-Host '==============================================================='
Write-Host '  auto-instargram  종료'
Write-Host '==============================================================='
Write-Host ''

# Docker 경로 보정 (run.ps1 과 동일한 이유)
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    foreach ($candidate in @(
            "$env:LOCALAPPDATA\Programs\DockerDesktop\resources\bin",
            "$env:ProgramFiles\Docker\Docker\resources\bin")) {
        if (Test-Path (Join-Path $candidate 'docker.exe')) {
            $env:Path = "$candidate;$env:Path"
            break
        }
    }
}

# ── 1. 서버 프로세스 종료 ───────────────────────────────────────────────────
# Gradle 로 띄운 백엔드는 자식 JVM 을 따로 만든다. 창만 닫으면 그 JVM 이 살아남아
# 포트 8080 을 계속 점유하고, 다음 실행이 "포트 사용 중" 으로 실패한다.
# 그래서 포트를 점유한 프로세스를 직접 찾아 끝낸다.
Write-Host '[1/2] 서버를 종료합니다...'

foreach ($port in @(8080, 5173)) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($processId in $pids) {
            $proc = Get-Process -Id $processId -ErrorAction SilentlyContinue
            if ($proc) {
                Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
                Write-Host "      포트 $port 사용 프로세스 종료 ($($proc.Name), PID $processId)" -ForegroundColor DarkGray
            }
        }
    } else {
        Write-Host "      포트 $port 사용 프로세스 없음" -ForegroundColor DarkGray
    }
}

# ── 2. 데이터베이스 정지 ────────────────────────────────────────────────────
# 기본은 데이터를 보존한다 (컨테이너만 정지).
Write-Host '[2/2] 데이터베이스를 정지합니다 (데이터는 보존)...'
docker compose -f docker-compose.dev.yml stop 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host '      정지 완료' -ForegroundColor DarkGray
} else {
    Write-Host '      Docker 가 실행 중이 아니거나 이미 정지되어 있습니다.' -ForegroundColor DarkGray
}

Write-Host ''
Write-Host '==============================================================='
Write-Host '  종료되었습니다.'
Write-Host '==============================================================='
Write-Host @'

  다시 시작: run.bat

  데이터까지 완전히 지우고 처음부터 시작하려면:
    docker compose -f docker-compose.dev.yml down -v
  (예약·이력·계정이 모두 삭제되고 다음 실행 때 새로 만들어집니다)

'@

if (-not $env:AUTO_INSTARGRAM_NONINTERACTIVE) {
    Read-Host '엔터를 누르면 닫힙니다'
}
