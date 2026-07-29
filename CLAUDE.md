# AI 에이전트 지침 — auto-instargram

당신은 이 폴더 안에서 작업하는 AI 코딩 에이전트입니다. 이것은 Harness 가 생성한 **바이브 코딩 명세 패키지**입니다 — 당신이 구현할 구조화된 설계입니다. 이 파일은 당신의 도구가 자동으로 읽습니다(Claude Code 는 `CLAUDE.md`, Cursor / Codex / Antigravity / Windsurf 는 `AGENTS.md`). 따라서 사용자가 긴 지시문을 붙여넣을 필요가 없습니다.

## ▶ 마스터 플랜
**`00-ORCHESTRATOR.md`** 를 읽고 그대로 실행하세요 — 그것이 권위 있는, 단계별(phase-by-phase) 마스터 워크플로우입니다.
**한 번에 한 phase 씩 진행하고, 각 phase 가 끝나면 사용자의 확인을 받기 위해 STOP 하세요.** 앞서 나가지 마세요.

## 📂 명세 파일
- `1_spack.md` — APIs / Entities / Policies (무엇을 만들지)
- `2_ddd.md` — 도메인 모델: Aggregates / Domain Events / Services (어떻게 동작하는지)
- `3_architecture.md` — services / databases / connections 와 **Tech Stack** (어떻게 배포할지; spec 이 충돌할 때 최종 권위)
- `skills/` — 코딩 규칙. 코드를 작성하기 전(BEFORE) 관련 규칙을 읽고, 절대 위반하지 마세요.
- `IMPLEMENTATION-CHECKLIST.md` — 만들어야 할 모든 것의 기계 생성 전수 목록(거기서 빠진 것은 없음). 모든 항목이 체크될 때까지 당신의 작업은 끝나지 않습니다.

## ✅ 필수 규칙
1. **Tech stack 은 `3_architecture.md` 에서 가져옵니다.** 거기에 명시된 Tech Stack 을 사용하세요. 명시되지 않은 경우에만 사용자에게 선택을 요청하고 — 절대 조용히 기본값을 쓰지 마세요.
2. **각 phase 마다 멈추고(Stop after every phase)** 계속하기 전에 사용자의 OK 를 기다리세요.
3. **요구사항을 절대 지어내지 마세요(Never invent requirements).** 불명확한 점이 있으면 사용자에게 물어보세요.
4. 모든 코딩 행위에 대해 **`skills/` 규칙을 따르세요(Follow `skills/` rules).** 위반을 고칠 때는 어떤(WHICH) `skills/` 파일의 어떤(WHICH) 규칙을 위반했는지 명시하세요(cite).
5. **완료 = `IMPLEMENTATION-CHECKLIST.md` 의 모든 항목이 실제 파일 경로와 함께 `[x]` 로 체크됨.** 완료를 보고하기 전에 체크리스트를 항목별로 코드와 대조 감사하고; 빠진 것을 구현한 뒤 **모두 통과할 때까지 반복(loop)** 하세요.

## 🙋 사람용 시작 가이드
사용자용 "어떻게 시작하나요" 가이드는 `0_START_HERE.md` 에 있습니다.
