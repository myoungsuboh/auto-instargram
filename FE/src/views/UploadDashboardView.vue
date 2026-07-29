<script setup>
import { computed, onMounted, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { newIdempotencyKey, toDisplayError } from '../api/client'
import { queues } from '../api/endpoints'

/**
 * SCREEN-01 자동 업로드 대시보드 — `/dashboard/upload`
 *
 * 1_spack.md §5:
 *   설명: 단계별 구현 페이즈 제어 및 예약 큐 관리 화면
 *   호출 API: API-02 GET /api/v1/queues, API-01 POST /api/v1/queues
 *   다음 화면: /dashboard/history
 *
 * 목데이터를 쓰지 않는다 — 목록과 등록 모두 실제 백엔드를 호출한다.
 */

// 디자인 스킬 규칙 3: "한 화면에 6개 이하만 보여준다"
const PAGE_SIZE = 6

const items = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(true)
const loadError = ref(null)

const form = ref({ mediaPath: '', caption: '', scheduledAt: '' })
const submitting = ref(false)
const submitError = ref(null)
const submitSuccess = ref(null)

/**
 * 등록 요청마다 새 멱등성 키를 만들고, 실패 시에는 <b>같은 키를 유지</b>한다.
 *
 * 그래야 "서버는 처리했는데 응답만 유실된" 경우의 재시도가 중복 예약을 만들지 않는다.
 * 성공하면 키를 버려 다음 등록이 새 요청으로 취급되게 한다.
 */
const pendingKey = ref(newIdempotencyKey())

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))
const captionLength = computed(() => form.value.caption.length)

async function loadQueues() {
  loading.value = true
  loadError.value = null
  try {
    const data = await queues.list({ page: page.value, limit: PAGE_SIZE })
    items.value = data.items
    total.value = data.total
  } catch (caught) {
    loadError.value = toDisplayError(caught)
    items.value = []
  } finally {
    loading.value = false
  }
}

async function submit() {
  submitError.value = null
  submitSuccess.value = null
  submitting.value = true
  try {
    // datetime-local 은 타임존 없는 문자열("2026-12-01T10:00")을 준다.
    // 명세는 UTC datetime 을 요구하므로 브라우저 로컬시각으로 해석해 ISO(UTC)로 변환한다.
    //
    // 변환 전에 유효성을 확인해야 한다. 잘못된 값이면 toISOString() 이 RangeError 를 던지고,
    // 그 예외는 axios 오류가 아니라서 "서버에 연결할 수 없습니다"로 잘못 표시된다 —
    // 사용자가 원인을 날짜가 아닌 네트워크에서 찾게 만든다.
    const parsed = new Date(form.value.scheduledAt)
    if (Number.isNaN(parsed.getTime())) {
      submitError.value = {
        code: 'VALIDATION_ERROR',
        message: '발행 시각을 다시 확인해 주세요.',
        fields: { scheduledAt: '올바른 날짜와 시각을 선택해 주세요' },
      }
      return
    }
    const scheduledAt = parsed.toISOString()

    const created = await queues.create(
      {
        mediaPath: form.value.mediaPath,
        // 빈 캡션은 보내지 않는다 — 명세상 선택 항목이다
        caption: form.value.caption || undefined,
        scheduledAt,
      },
      pendingKey.value,
    )

    submitSuccess.value = `예약이 등록되었습니다 (예약 번호 ${created.queueId.slice(0, 8)}…)`
    form.value = { mediaPath: '', caption: '', scheduledAt: '' }
    pendingKey.value = newIdempotencyKey()

    // 방금 등록한 것이 보이도록 첫 페이지로 돌아가 다시 불러온다
    page.value = 0
    await loadQueues()
  } catch (caught) {
    submitError.value = toDisplayError(caught)
  } finally {
    submitting.value = false
  }
}

async function goToPage(next) {
  page.value = Math.min(Math.max(0, next), totalPages.value - 1)
  await loadQueues()
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

/** 긴 절대 경로에서 파일명만 뽑아 보여준다 (전체 경로는 title 로 확인 가능). */
function fileNameOf(path) {
  return path?.split(/[\\/]/).pop() || path
}

onMounted(loadQueues)
</script>

<template>
  <div class="container">
    <header class="page-header">
      <p class="meta-label">
        SCREEN-01 · 자동 업로드 대시보드
      </p>
      <h1 class="display">
        예약 등록
      </h1>
      <p class="lede">
        업로드할 영상과 발행 시각을 지정해 예약합니다. 등록된 예약은 발행 시각이 되면
        자동으로 인스타그램에 게시됩니다.
      </p>
    </header>

    <div class="split">
      <!-- ══ 등록 폼 (API-01) ══════════════════════════════════════ -->
      <section class="card" aria-labelledby="new-queue-heading">
        <h2 id="new-queue-heading" class="section-title">
          새 예약
        </h2>

        <NoticeBanner
          v-if="submitSuccess"
          kind="success"
          :message="submitSuccess"
          style="margin-top: 24px"
        />
        <NoticeBanner
          v-if="submitError"
          kind="error"
          :message="submitError.message"
          :code="submitError.code"
          style="margin-top: 24px"
        />

        <form novalidate style="margin-top: 24px" @submit.prevent="submit">
          <label class="field">
            <span class="meta-label">영상 파일</span>
            <input
              v-model.trim="form.mediaPath"
              class="field-input"
              type="text"
              placeholder="reel-morning.mp4"
              required
              maxlength="255"
              :aria-invalid="Boolean(submitError?.fields?.mediaPath)"
              :disabled="submitting"
            >
            <span class="field-hint">
              서버의 미디어 폴더(<code>BE/storage/media/</code>)에 있는 파일명을
              입력하세요. 폴더 밖 경로는 보안상 거부됩니다.
            </span>
            <span v-if="submitError?.fields?.mediaPath" class="field-error">
              {{ submitError.fields.mediaPath }}
            </span>
          </label>

          <label class="field">
            <span class="meta-label">캡션 (선택)</span>
            <textarea
              v-model="form.caption"
              class="field-textarea"
              maxlength="2200"
              placeholder="게시물에 함께 올릴 문구"
              :aria-invalid="Boolean(submitError?.fields?.caption)"
              :disabled="submitting"
            />
            <span class="field-hint">{{ captionLength }} / 2200자</span>
            <span v-if="submitError?.fields?.caption" class="field-error">
              {{ submitError.fields.caption }}
            </span>
          </label>

          <label class="field">
            <span class="meta-label">발행 시각</span>
            <input
              v-model="form.scheduledAt"
              class="field-input"
              type="datetime-local"
              required
              :aria-invalid="Boolean(submitError?.fields?.scheduledAt)"
              :disabled="submitting"
            >
            <span class="field-hint">
              입력한 시각은 이 컴퓨터의 시간대 기준이며, 서버에는 UTC 로 저장됩니다.
            </span>
            <span v-if="submitError?.fields?.scheduledAt" class="field-error">
              {{ submitError.fields.scheduledAt }}
            </span>
          </label>

          <button
            class="button"
            type="submit"
            :disabled="submitting || !form.mediaPath || !form.scheduledAt"
          >
            {{ submitting ? '등록 중…' : '예약 등록' }}
          </button>
        </form>
      </section>

      <!-- ══ 예약 목록 (API-02) ═══════════════════════════════════ -->
      <section aria-labelledby="queue-list-heading">
        <div class="spread">
          <h2 id="queue-list-heading" class="section-title">
            등록된 예약
          </h2>
          <span class="meta">전체 {{ total }}건</span>
        </div>

        <NoticeBanner
          v-if="loadError"
          kind="error"
          :message="loadError.message"
          :code="loadError.code"
          style="margin-top: 24px"
        />

        <p v-else-if="loading" class="meta" style="margin-top: 24px" role="status">
          불러오는 중…
        </p>

        <!-- POL-03: 0건은 오류가 아니라 정상 응답이다 -->
        <EmptyState
          v-else-if="items.length === 0"
          title="아직 예약이 없습니다"
          description="왼쪽에서 첫 예약을 등록해 보세요."
          style="margin-top: 24px"
        />

        <div v-else class="card card-tight" style="margin-top: 24px">
          <article v-for="item in items" :key="item.queueId" class="record">
            <div>
              <h3 class="record-title" :title="item.mediaPath">
                {{ fileNameOf(item.mediaPath) }}
              </h3>
              <p class="record-meta">
                <span>발행 {{ formatDateTime(item.scheduledAt) }}</span>
                <span v-if="item.retryCount > 0">재시도 {{ item.retryCount }}회</span>
              </p>
              <p v-if="item.caption" class="meta" style="margin: 8px 0 0">
                {{ item.caption }}
              </p>
            </div>
            <StatusBadge :status="item.status" />
          </article>
        </div>

        <div v-if="totalPages > 1" class="pager">
          <button
            type="button"
            class="button button-quiet"
            :disabled="page === 0 || loading"
            @click="goToPage(page - 1)"
          >
            이전
          </button>
          <span class="meta">{{ page + 1 }} / {{ totalPages }}</span>
          <button
            type="button"
            class="button button-quiet"
            :disabled="page >= totalPages - 1 || loading"
            @click="goToPage(page + 1)"
          >
            다음
          </button>
        </div>

        <!-- 1_spack.md SCREEN-01 "다음 화면: /dashboard/history" -->
        <p class="meta" style="margin-top: 32px">
          <RouterLink to="/dashboard/history" class="nav-link">
            게시 이력 보기 →
          </RouterLink>
        </p>
      </section>
    </div>
  </div>
</template>
