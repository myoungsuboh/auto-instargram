<script setup>
import { computed, onMounted, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { toDisplayError } from '../api/client'
import { queues } from '../api/endpoints'

/**
 * SCREEN-02 자동 게시 관리 대시보드 — `/dashboard/posts`
 *
 * 1_spack.md §5:
 *   설명: 예약 관리 및 <b>실패 정책 상태 조회</b> 화면
 *   호출 API: API-02 GET /api/v1/queues
 *
 * SCREEN-01 과 같은 API 를 쓰지만 관점이 다르다.
 * 여기서는 API-02 설명이 규정한 "실패 상태 및 재시도 상태"에 초점을 둔다 —
 * 실패한 예약을 앞에 모아 보여주고, 재시도 횟수와 오류 코드를 드러낸다.
 *
 * 상태 요약을 서버가 아니라 여기서 계산하는 이유: 명세에 집계 API 가 없다.
 * 지어내지 않고, 이미 받은 목록으로 계산할 수 있는 것만 보여준다.
 */

const PAGE_SIZE = 6

const items = ref([])
const total = ref(0)
const page = ref(0)
const loading = ref(true)
const loadError = ref(null)
/** 실패한 항목만 볼지 여부 — "실패 정책 상태 조회"를 돕는 필터. */
const failedOnly = ref(false)

/**
 * ⚠️ 이 집계는 <b>현재 페이지</b> 기준이다.
 * 서버에 집계 API 가 없어 전체 통계를 낼 수 없으므로, 오해하지 않도록 화면에도 그렇게 표기한다.
 */
const pageSummary = computed(() => ({
  pending: items.value.filter((item) => item.status === 'PENDING').length,
  success: items.value.filter((item) => item.status === 'SUCCESS').length,
  failed: items.value.filter((item) => item.status === 'FAILED').length,
}))

const visibleItems = computed(() =>
  failedOnly.value ? items.value.filter((item) => item.status === 'FAILED') : items.value,
)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load() {
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

async function goToPage(next) {
  page.value = Math.min(Math.max(0, next), totalPages.value - 1)
  await load()
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

function fileNameOf(path) {
  return path?.split(/[\\/]/).pop() || path
}

onMounted(load)
</script>

<template>
  <div class="container">
    <header class="page-header">
      <p class="meta-label">
        SCREEN-02 · 자동 게시 관리 대시보드
      </p>
      <h1 class="display">
        게시 상태
      </h1>
      <p class="lede">
        예약된 게시물의 진행 상태와 실패·재시도 현황을 확인합니다. 실패한 예약은
        오류 코드와 재시도 횟수를 함께 보여줍니다.
      </p>
    </header>

    <NoticeBanner
      v-if="loadError"
      kind="error"
      :message="loadError.message"
      :code="loadError.code"
      style="margin-bottom: 32px"
    />

    <p v-else-if="loading" class="meta" role="status">
      불러오는 중…
    </p>

    <template v-else>
      <!-- ══ 현재 페이지 요약 ═══════════════════════════════════════ -->
      <section class="card" aria-labelledby="summary-heading" style="margin-bottom: 32px">
        <div class="spread">
          <h2 id="summary-heading" class="section-title">
            이 페이지 요약
          </h2>
          <span class="meta">전체 {{ total }}건 중 {{ items.length }}건 표시</span>
        </div>

        <div class="row" style="margin-top: 24px; gap: 32px">
          <div>
            <span class="meta-label">대기 중</span>
            <p class="section-title" style="margin: 0">
              {{ pageSummary.pending }}
            </p>
          </div>
          <div>
            <span class="meta-label">성공</span>
            <p class="section-title" style="margin: 0">
              {{ pageSummary.success }}
            </p>
          </div>
          <div>
            <span class="meta-label">실패</span>
            <p class="section-title" style="margin: 0">
              {{ pageSummary.failed }}
            </p>
          </div>
        </div>

        <p class="field-hint" style="margin-top: 16px">
          이 숫자는 지금 보고 있는 페이지({{ items.length }}건)만 센 것입니다. 전체 집계
          API 는 설계에 없어 제공하지 않습니다.
        </p>
      </section>

      <!-- ══ 목록 (API-02) ════════════════════════════════════════ -->
      <section aria-labelledby="list-heading">
        <div class="spread">
          <h2 id="list-heading" class="section-title">
            예약 목록
          </h2>
          <label class="meta" style="display: flex; align-items: center; gap: 8px">
            <input v-model="failedOnly" type="checkbox">
            실패한 항목만 보기
          </label>
        </div>

        <EmptyState
          v-if="visibleItems.length === 0 && failedOnly"
          title="실패한 예약이 없습니다"
          description="이 페이지의 모든 예약이 정상입니다."
          style="margin-top: 24px"
        />

        <!-- POL-03: 0건은 정상 응답이다 -->
        <EmptyState
          v-else-if="visibleItems.length === 0"
          title="등록된 예약이 없습니다"
          description="예약 등록 화면에서 첫 예약을 만들어 보세요."
          style="margin-top: 24px"
        >
          <template #action>
            <RouterLink to="/dashboard/upload" class="button">
              예약 등록하기
            </RouterLink>
          </template>
        </EmptyState>

        <div v-else class="card card-tight" style="margin-top: 24px">
          <article v-for="item in visibleItems" :key="item.queueId" class="record">
            <div>
              <h3 class="record-title" :title="item.mediaPath">
                {{ fileNameOf(item.mediaPath) }}
              </h3>
              <p class="record-meta">
                <span>발행 예정 {{ formatDateTime(item.scheduledAt) }}</span>
                <span>등록 {{ formatDateTime(item.createdAt) }}</span>
              </p>

              <!-- 실패 상태 및 재시도 상태 (API-02 설명이 요구하는 정보) -->
              <p
                v-if="item.retryCount > 0 || item.lastErrorCode"
                class="record-meta"
                style="margin-top: 8px"
              >
                <span v-if="item.retryCount > 0">재시도 {{ item.retryCount }}회</span>
                <span v-if="item.lastErrorCode">
                  마지막 오류 <code>{{ item.lastErrorCode }}</code>
                </span>
                <span v-if="item.lastFailedAt">
                  {{ formatDateTime(item.lastFailedAt) }}
                </span>
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
      </section>
    </template>
  </div>
</template>
