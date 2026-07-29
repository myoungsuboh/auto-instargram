#!/usr/bin/env bash
# auto-instargram 종료 (macOS / Linux)
set -uo pipefail
cd "$(dirname "$0")"

echo "==============================================================="
echo "  auto-instargram  종료"
echo "==============================================================="
echo

# ── 1. 서버 프로세스 종료 ─────────────────────────────────────
# Gradle 로 띄운 백엔드는 자식 JVM 을 따로 만든다. 부모만 죽이면 그 JVM 이 살아남아
# 포트 8080 을 계속 점유하고 다음 실행이 "포트 사용 중" 으로 실패한다.
# 그래서 기록해 둔 PID 를 프로세스 그룹째 정리하고, 포트로도 한 번 더 확인한다.
echo "[1/2] 서버를 종료합니다..."

for name in backend frontend; do
  pidfile=".run/${name}.pid"
  if [ -f "$pidfile" ]; then
    pid=$(cat "$pidfile")
    if kill -0 "$pid" 2>/dev/null; then
      # 자식까지 함께 끝내기 위해 프로세스 그룹에 신호를 보낸다
      kill -TERM -"$pid" 2>/dev/null || kill -TERM "$pid" 2>/dev/null || true
      echo "      ${name} 종료 (PID ${pid})"
    fi
    rm -f "$pidfile"
  fi
done

# 포트를 아직 잡고 있는 프로세스가 있으면 정리한다 (자식 JVM 등)
for port in 8080 5173; do
  if command -v lsof >/dev/null 2>&1; then
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
      echo "$pids" | xargs kill -TERM 2>/dev/null || true
      echo "      포트 ${port} 사용 프로세스 종료"
    fi
  fi
done

# ── 2. 데이터베이스 정지 ──────────────────────────────────────
# 기본은 데이터를 보존한다 (컨테이너만 정지).
echo "[2/2] 데이터베이스를 정지합니다 (데이터는 보존)..."
if docker compose -f docker-compose.dev.yml stop >/dev/null 2>&1; then
  echo "      정지 완료"
else
  echo "      Docker 가 실행 중이 아니거나 이미 정지되어 있습니다."
fi

cat <<'MSG'

===============================================================
  종료되었습니다.
===============================================================

  다시 시작: ./run.sh

  데이터까지 완전히 지우고 처음부터 시작하려면:
    docker compose -f docker-compose.dev.yml down -v
  (예약·이력·계정이 모두 삭제되고 다음 실행 때 새로 만들어집니다)
MSG
