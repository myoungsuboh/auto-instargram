#!/usr/bin/env bash
# auto-instargram 실행 (macOS / Linux)
# Windows 는 run.bat 을 쓰세요.
set -uo pipefail
cd "$(dirname "$0")"

echo "==============================================================="
echo "  auto-instargram  실행"
echo "==============================================================="
echo

# ── 1. Docker 확인 ────────────────────────────────────────────
if ! command -v docker >/dev/null 2>&1; then
  cat <<'MSG'
[!] Docker 를 찾을 수 없습니다.

    데이터베이스를 실행하려면 Docker Desktop 이 필요합니다:
      https://www.docker.com/products/docker-desktop/

    설치 후 Docker Desktop 을 실행하고 이 스크립트를 다시 실행해 주세요.
MSG
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  cat <<'MSG'
[!] Docker 는 설치되어 있지만 실행 중이 아닙니다.

    Docker Desktop 을 실행한 뒤 이 스크립트를 다시 실행해 주세요.
MSG
  exit 1
fi
echo "[1/5] Docker 확인 완료"

# ── 2. 설정 파일 준비 ─────────────────────────────────────────
# .env 는 비밀값이 담기므로 저장소에 없다(.gitignore). 없으면 예시에서 만든다.
#
# ⚠️ 단순 복사로는 안 된다. .env.example 의 비밀값은 "EXAMPLE_ONLY_..." 자리표시자이고,
#    서버는 그것을 fail-fast 로 거부한다(예: CREDENTIAL_ENCRYPTION_KEY 는 정확히 32바이트
#    base64 여야 한다). 그래서 여기서 실제 무작위 값을 만들어 채운다 —
#    그러지 않으면 저장소를 처음 받은 사람이 서버를 띄울 수 없다.

rand_base64() {  # $1 = 바이트 수
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 "$1" | tr -d '\n'
  else
    head -c "$1" /dev/urandom | base64 | tr -d '\n'
  fi
}

if [ ! -f .env ]; then
  # DB 비밀번호는 JDBC URL·compose 보간에서 문제를 일으키지 않도록 영숫자만 쓴다
  DB_PW=$(rand_base64 24 | tr -dc 'A-Za-z0-9' | cut -c1-24)
  JWT=$(rand_base64 48)
  ENC=$(rand_base64 32)   # AES-256 은 정확히 32바이트를 요구한다

  # 구분자로 | 를 쓴다 — base64 값에 / 가 들어갈 수 있어 sed 의 기본 구분자와 충돌한다
  sed -e "s|change_me_local_only|${DB_PW}|g" \
      -e "s|EXAMPLE_ONLY_replace_me_with_a_random_48_byte_base64_secret_value|${JWT}|g" \
      -e "s|EXAMPLE_ONLY_replace_me_with_openssl_rand_base64_32==|${ENC}|g" \
      .env.example > .env

  echo "[2/5] 설정 파일 .env 를 만들고 비밀값을 새로 생성했습니다"
  echo "      DB 비밀번호 · 로그인 토큰 서명키 · 토큰 암호화 키를 무작위로 생성했습니다"
  echo
  echo "      [!] 로그인 계정의 비밀번호는 예시값입니다 (README 참조)."
  echo "          인터넷에 공개하려면 .env 의 SEED_*_PASSWORD 를 바꾸세요."
  echo
else
  echo "[2/5] 설정 파일 .env 확인 완료"
fi

# ── 3. 데이터베이스 기동 ──────────────────────────────────────
echo "[3/5] 데이터베이스를 시작합니다..."
if ! docker compose -f docker-compose.dev.yml up -d; then
  echo
  echo "[!] 데이터베이스를 시작하지 못했습니다. 위 메시지를 확인해 주세요."
  exit 1
fi

# 준비될 때까지 기다린다. 바로 서버를 띄우면 DB 연결 실패로 종료된다.
echo "      데이터베이스가 준비될 때까지 기다립니다..."
for i in $(seq 1 60); do
  health=$(docker inspect --format='{{.State.Health.Status}}' auto-instargram-postgres 2>/dev/null || echo "")
  [ "$health" = "healthy" ] && break
  if [ "$i" -eq 60 ]; then
    echo
    echo "[!] 데이터베이스가 60초 안에 준비되지 않았습니다."
    echo "    docker compose -f docker-compose.dev.yml logs postgres"
    exit 1
  fi
  sleep 1
done
echo "      준비 완료"

# ── 4. 서버 시작 ──────────────────────────────────────────────
# 로그를 파일로 남긴다 — 문제가 생겼을 때 확인할 수 있어야 한다.
mkdir -p .run
echo "[4/5] 서버를 시작합니다 (로그: .run/backend.log, .run/frontend.log)"

( cd BE && ./gradlew bootRun ) > .run/backend.log 2>&1 &
echo $! > .run/backend.pid

( cd FE && npm install --no-fund --no-audit && npm run dev ) > .run/frontend.log 2>&1 &
echo $! > .run/frontend.pid

# ── 5. 준비 대기 ─────────────────────────────────────────────
echo "[5/5] 준비되면 브라우저가 열립니다 (처음 실행은 1~2분 걸립니다)"
opened=""
for i in $(seq 1 180); do
  if curl -s -o /dev/null http://localhost:5173 2>/dev/null; then
    opened="yes"
    break
  fi
  sleep 1
done

if [ -n "$opened" ]; then
  if command -v open >/dev/null 2>&1; then
    open http://localhost:5173          # macOS
  elif command -v xdg-open >/dev/null 2>&1; then
    xdg-open http://localhost:5173      # Linux
  fi
else
  echo "      화면이 아직 준비되지 않았습니다. .run/frontend.log 를 확인해 주세요."
fi

cat <<'MSG'

===============================================================
  준비 완료
===============================================================

  화면      http://localhost:5173
  서버 API  http://localhost:8080

  로그인 정보는 .env 파일의 SEED_ADMIN_USERNAME / SEED_ADMIN_PASSWORD 입니다.
  (기본값: admin / Admin!2026Local)

  끄려면:  ./stop.sh
MSG
