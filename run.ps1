# ============================================================================
#  auto-instargram 실행 (Windows)
#
#  run.bat 이 이 파일을 호출한다. 사람이 읽는 메시지를 여기 두는 이유:
#  cmd.exe 는 .bat 을 시스템 ANSI 코드페이지로 읽어 UTF-8 한글이 깨지지만,
#  PowerShell 은 UTF-8 파일을 올바르게 읽는다.
#
#  ⚠️ 이 파일은 반드시 **UTF-8 BOM 포함**으로 저장해야 한다.
#     Windows PowerShell 5.1 은 BOM 이 없으면 .ps1 을 시스템 ANSI 코드페이지로 읽어
#     한글이 깨지고 파서가 죽는다("Unexpected token" / "string is missing the terminator").
#     편집기에서 "UTF-8 (BOM 없음)" 으로 저장하면 이 스크립트가 실행되지 않는다.
# ============================================================================

$ErrorActionPreference = 'Continue'
Set-Location $PSScriptRoot

# 콘솔에 한글이 제대로 나오게 한다
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

function Write-Step($n, $text) { Write-Host "[$n/5] $text" }
function Write-Detail($text)   { Write-Host "      $text" -ForegroundColor DarkGray }
function Write-Problem($text)  { Write-Host $text -ForegroundColor Yellow }

Write-Host '==============================================================='
Write-Host '  auto-instargram  실행'
Write-Host '==============================================================='
Write-Host ''

# ── 1. Docker 확인 ──────────────────────────────────────────────────────────
# Docker Desktop 은 사용자 폴더에 설치되기도 하는데, 그 경우 새 터미널을 열기 전까지
# PATH 에 잡히지 않는다. 알려진 설치 경로를 직접 확인해 보정한다.
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

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Problem @'
[!] Docker 를 찾을 수 없습니다.

    데이터베이스를 실행하려면 Docker Desktop 이 필요합니다:
      https://www.docker.com/products/docker-desktop/

    설치한 뒤 Docker Desktop 을 실행하고(상태바에 고래 아이콘),
    이 창을 닫고 run.bat 을 다시 실행해 주세요.
'@
    Read-Host '엔터를 누르면 닫힙니다'
    exit 1
}

docker info 2>&1 | Out-Null
if ($LASTEXITCODE -ne 0) {
    Write-Problem @'
[!] Docker 는 설치되어 있지만 실행 중이 아닙니다.

    시작 메뉴에서 "Docker Desktop" 을 실행하고,
    상태바에 고래 아이콘이 나타나면 run.bat 을 다시 실행해 주세요.
'@
    Read-Host '엔터를 누르면 닫힙니다'
    exit 1
}
Write-Step 1 'Docker 확인 완료'

# ── 2. 설정 파일 준비 ───────────────────────────────────────────────────────
# .env 는 비밀값이 담기므로 저장소에 없다(.gitignore). 없으면 예시에서 만든다.
#
# ⚠️ 단순 복사로는 안 된다. .env.example 의 비밀값은 "EXAMPLE_ONLY_..." 자리표시자이고,
#    서버는 그것을 fail-fast 로 거부한다(예: CREDENTIAL_ENCRYPTION_KEY 는 정확히 32바이트
#    base64 여야 한다). 그래서 여기서 실제 무작위 값을 만들어 채운다 —
#    그러지 않으면 저장소를 처음 받은 사람이 서버를 띄울 수 없다.

function New-Base64Secret([int]$byteCount) {
    $bytes = New-Object byte[] $byteCount
    $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes)
}

function New-AlphanumericSecret([int]$byteCount) {
    # DB 비밀번호는 JDBC URL·compose 보간에서 문제를 일으키지 않도록 영숫자만 쓴다
    return (New-Base64Secret ($byteCount * 2)) -replace '[^A-Za-z0-9]', '' |
        ForEach-Object { $_.Substring(0, [Math]::Min($byteCount, $_.Length)) }
}

if (-not (Test-Path '.env')) {
    # ⚠️ 반드시 UTF-8 로 읽어야 한다.
    #   Get-Content 를 -Encoding 없이 쓰면 PowerShell 5.1 은 시스템 기본 코드페이지
    #   (한국어 Windows 는 CP949)로 읽는다. .env.example 은 UTF-8 이므로 한글 주석이
    #   깨진 문자로 바뀌고, 아래 WriteAllText 가 그 깨진 문자를 "정상 UTF-8"로 저장해
    #   되돌릴 수 없게 된다(실제로 발생했던 문제 — 파일은 UTF-8 인데 내용만 깨짐).
    #   아래 WriteAllText 와 짝을 맞춰 .NET API 로 명시적으로 읽는다.
    $content = [IO.File]::ReadAllText((Join-Path $PSScriptRoot '.env.example'),
                                      [Text.UTF8Encoding]::new($false))

    $dbPassword = New-AlphanumericSecret 24
    $content = $content -replace 'change_me_local_only', $dbPassword
    $content = $content -replace 'EXAMPLE_ONLY_replace_me_with_a_random_48_byte_base64_secret_value',
                                  (New-Base64Secret 48)
    # AES-256 은 정확히 32바이트를 요구한다
    $content = $content -replace 'EXAMPLE_ONLY_replace_me_with_openssl_rand_base64_32==',
                                  (New-Base64Secret 32)

    # BOM 없이 써야 한다 — 첫 줄이 주석이라 BOM 이 있어도 동작하지만,
    # 스프링의 properties 파서가 값 앞에 보이지 않는 문자를 붙이지 않게 확실히 해 둔다.
    [IO.File]::WriteAllText((Join-Path $PSScriptRoot '.env'), $content,
                            [Text.UTF8Encoding]::new($false))

    Write-Step 2 '설정 파일 .env 를 만들고 비밀값을 새로 생성했습니다'
    Write-Detail 'DB 비밀번호 · 로그인 토큰 서명키 · 토큰 암호화 키를 무작위로 생성했습니다'
    Write-Host ''
    Write-Problem '      [!] 로그인 계정의 비밀번호는 예시값입니다 (README 참조).'
    Write-Problem '          인터넷에 공개하려면 .env 의 SEED_*_PASSWORD 를 바꾸세요.'
    Write-Host ''
} else {
    Write-Step 2 '설정 파일 .env 확인 완료'
}

# ── 3. 데이터베이스 기동 ────────────────────────────────────────────────────
Write-Step 3 '데이터베이스를 시작합니다...'
# docker compose 는 진행 상황을 "커서를 위로 올려 그 줄을 다시 그리는" 방식으로 출력한다.
# 그 제어문자를 그대로 콘솔에 흘리면 <b>바로 위에 찍은 우리 메시지가 덮여 잘린다</b>
# (실제로 "[3/5] 데이터베이스를 시작합니다..." 가 "스를 시작합니다..." 로 잘려 보였다).
# --progress 플래그는 compose 버전마다 위치가 달라 의존하지 않고, 제어문자만 직접 지운다.
$ansiEscape = "$([char]27)\[[0-9;?]*[a-zA-Z]"
docker compose -f docker-compose.dev.yml up -d 2>&1 |
    ForEach-Object { Write-Detail (($_ -replace $ansiEscape, '').TrimEnd()) }
if ($LASTEXITCODE -ne 0) {
    Write-Problem '[!] 데이터베이스를 시작하지 못했습니다. 위 메시지를 확인해 주세요.'
    Read-Host '엔터를 누르면 닫힙니다'
    exit 1
}

# 준비될 때까지 기다린다. 바로 서버를 띄우면 DB 연결 실패로 즉시 종료된다.
Write-Detail '데이터베이스가 준비될 때까지 기다립니다...'
$ready = $false
foreach ($i in 1..60) {
    $health = (docker inspect --format '{{.State.Health.Status}}' auto-instargram-postgres 2>$null | Out-String).Trim()
    if ($health -eq 'healthy') { $ready = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $ready) {
    Write-Problem @'
[!] 데이터베이스가 60초 안에 준비되지 않았습니다.
    아래 명령으로 원인을 확인할 수 있습니다:
      docker compose -f docker-compose.dev.yml logs postgres
'@
    Read-Host '엔터를 누르면 닫힙니다'
    exit 1
}
Write-Detail '준비 완료'

# ── 4. 서버 시작 ────────────────────────────────────────────────────────────
# 각각 별도 창으로 띄운다 — 비개발자가 로그를 눈으로 보고 창을 닫을 수 있게.
Write-Step 4 '서버를 시작합니다 (새 창 2개가 열립니다)'

Start-Process -FilePath 'cmd.exe' -ArgumentList @(
    '/k', "title auto-instargram : 서버(백엔드) && cd /d `"$PSScriptRoot\BE`" && gradlew.bat bootRun"
)
Start-Process -FilePath 'cmd.exe' -ArgumentList @(
    '/k', "title auto-instargram : 화면(프론트엔드) && cd /d `"$PSScriptRoot\FE`" && npm install --no-fund --no-audit && npm run dev"
)

# ── 5. 준비 대기 후 브라우저 열기 ───────────────────────────────────────────
Write-Step 5 '준비되면 브라우저가 열립니다 (처음 실행은 1~2분 걸립니다)'

$feReady = $false
$beReady = $false
foreach ($i in 1..180) {
    if (-not $beReady) {
        try {
            $r = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 2 -UseBasicParsing
            if ($r.StatusCode -eq 200) { $beReady = $true; Write-Detail '서버 준비 완료' }
        } catch { }
    }
    if (-not $feReady) {
        try {
            $r = Invoke-WebRequest -Uri 'http://localhost:5173' -TimeoutSec 2 -UseBasicParsing
            if ($r.StatusCode -eq 200) { $feReady = $true; Write-Detail '화면 준비 완료' }
        } catch { }
    }
    if ($beReady -and $feReady) { break }
    Start-Sleep -Seconds 1
}

if ($feReady) {
    Start-Process 'http://localhost:5173'
} else {
    Write-Problem '      화면이 아직 준비되지 않았습니다. 새로 열린 두 창의 메시지를 확인해 주세요.'
}
if (-not $beReady) {
    Write-Problem '      서버가 아직 준비되지 않았습니다. "서버(백엔드)" 창의 메시지를 확인해 주세요.'
}

Write-Host ''
Write-Host '==============================================================='
if ($beReady -and $feReady) {
    Write-Host '  준비 완료' -ForegroundColor Green
} else {
    Write-Host '  일부가 준비되지 않았습니다' -ForegroundColor Yellow
}
Write-Host '==============================================================='
Write-Host @'

  화면      http://localhost:5173
  서버 API  http://localhost:8080

  로그인 정보는 .env 파일의 SEED_ADMIN_USERNAME / SEED_ADMIN_PASSWORD 입니다.
  (기본값: admin / Admin!2026Local)

  끄려면: stop.bat 을 실행하세요.
          (새로 열린 창을 그냥 닫으면 서버가 백그라운드에 남을 수 있습니다)

'@

# 자동 검증(무인 실행)에서는 입력 대기가 걸리지 않도록 건너뛴다
if (-not $env:AUTO_INSTARGRAM_NONINTERACTIVE) {
    Read-Host '엔터를 누르면 이 창이 닫힙니다 (서버는 계속 실행됩니다)'
}

if ($beReady -and $feReady) { exit 0 } else { exit 1 }
