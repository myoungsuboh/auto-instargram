<script setup>
import { computed, onMounted, ref } from 'vue'
import EmptyState from '../components/EmptyState.vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { toDisplayError } from '../api/client'
import { history } from '../api/endpoints'

/**
 * SCREEN-03 CLI 인터페이스 및 로그 대시보드 — `/dashboard/history`
 *
 * 1_spack.md §5:
 *   설명: 게시 이력 및 history.json 조회 화면
 *   호출 API: API-03 GET /api/v1/history
 *
 * 이력은 원래 파일(history.json)이었으나 3_architecture.md 의 DB-01 로 이관됐다
 * (POL-02 원자적 쓰기를 파일로는 보장할 수 없었기 때문).
 */

const records = ref([])
const loading = ref(true)
const loadError = ref(null)
const filters = ref({ startDate: '', endDate: '' })

/** 서버가 준 값으로만 집계한다 — 없는 통계 API 를 지어내지 않는다. */
const summary = computed(() => ({
  total: records.value.length,
  success: records.value.filter((record) => record.status === 'SUCCESS').length,
  failed: records.value.filter((record) => record.status === 'FAILED').length,
}))

const rangeInvalid = computed(
  () =>
    Boolean(filters.value.startDate && filters.value.endDate) &&
    filters.value.startDate > filters.value.endDate,
)

async function load() {
  loading.value = true
  loadError.value = null
  try {
    const data = await history.list({
      startDate: filters.value.startDate,
      endDate: filters.value.endDate,
    })
    records.value = data.history
  } catch (caught) {
    loadError.value = toDisplayError(caught)
    records.value = []
  } finally {
    loading.value = false
  }
}

async function resetFilters() {
  filters.value = { startDate: '', endDate: '' }
  await load()
}

function formatDateTime(value) {
  if (!value) return '—'
  return new Date(value).toLocaleString('ko-KR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
}

onMounted(load)
</script>

<template>
  <div class="container">
    <header class="page-header">
      <p class="meta-label">
        SCREEN-03 · CLI 인터페이스 및 로그 대시보드
      </p>
      <h1 class="display">
        게시 이력
      </h1>
      <p class="lede">
        지금까지의 게시 결과입니다. 같은 영상은 한 줄로 유지되며 최신 결과를 보여줍니다
        — 중복 업로드를 막기 위해 영상별로 하나의 기록만 남습니다.
      </p>
    </header>

    <!-- ══ 기간 필터 ═════════════════════════════════════════════ -->
    <section class="card" aria-labelledby="filter-heading" style="margin-bottom: 32px">
      <h2 id="filter-heading" class="section-title">
        기간 선택
      </h2>

      <form
        class="row"
        style="margin-top: 24px; align-items: flex-end"
        novalidate
        @submit.prevent="load"
      >
        <label class="field" style="margin: 0">
          <span class="meta-label">시작일</span>
          <input
            v-model="filters.startDate"
            class="field-input"
            type="date"
            :disabled="loading"
          >
        </label>

        <label class="field" style="margin: 0">
          <span class="meta-label">종료일</span>
          <input
            v-model="filters.endDate"
            class="field-input"
            type="date"
            :aria-invalid="rangeInvalid"
            :disabled="loading"
          >
        </label>

        <button class="button" type="submit" :disabled="loading || rangeInvalid">
          조회
        </button>
        <button
          type="button"
          class="button-text"
          :disabled="loading"
          @click="resetFilters"
        >
          전체 보기
        </button>
      </form>

      <p v-if="rangeInvalid" class="field-error" style="margin-top: 8px">
        시작일이 종료일보다 늦습니다. 순서를 바꿔 주세요.
      </p>
      <p v-else class="field-hint" style="margin-top: 8px">
        비워 두면 최근 90일을 조회합니다. 종료일은 그 날 하루 전체를 포함합니다.
      </p>
    </section>

    <NoticeBanner
      v-if="loadError"
      kind="error"
      :message="loadError.message"
      :code="loadError.code"
      style="margin-bottom: 32px"
    />

    <p v-else-if="loading" class="loading-block" role="status">
      불러오는 중…
    </p>

    <template v-else>
      <!-- ══ 요약 ════════════════════════════════════════════════ -->
      <section
        v-if="summary.total > 0"
        class="card"
        aria-labelledby="history-summary"
        style="margin-bottom: 32px"
      >
        <h2 id="history-summary" class="section-title">
          조회 결과 요약
        </h2>
        <div class="row" style="margin-top: 24px; gap: 32px">
          <div>
            <span class="meta-label">전체</span>
            <p class="section-title" style="margin: 0">
              {{ summary.total }}
            </p>
          </div>
          <div>
            <span class="meta-label">성공</span>
            <p class="section-title" style="margin: 0">
              {{ summary.success }}
            </p>
          </div>
          <div>
            <span class="meta-label">실패</span>
            <p class="section-title" style="margin: 0">
              {{ summary.failed }}
            </p>
          </div>
        </div>
      </section>

      <!-- ══ 이력 목록 (API-03) ═══════════════════════════════════ -->
      <section aria-labelledby="history-list">
        <h2 id="history-list" class="section-title">
          기록
        </h2>

        <!-- POL-03: 0건이어도 200 이다. 오류가 아니라 "기록 없음"으로 안내한다. -->
        <EmptyState
          v-if="records.length === 0"
          title="해당 기간에 기록이 없습니다"
          description="기간을 넓혀 보거나 전체 보기를 눌러 주세요."
          style="margin-top: 24px"
        />

        <div v-else class="card card-tight" style="margin-top: 24px">
          <article v-for="record in records" :key="record.recordId" class="record">
            <div>
              <p class="record-title" style="font-weight: 400">
                {{ formatDateTime(record.timestamp) }}
              </p>
              <p class="record-meta">
                <!-- 콘텐츠 해시는 중복 판별의 근거다. 전체 값은 title 로 확인 가능. -->
                <span class="hash" :title="record.contentHash">
                  {{ record.contentHash.slice(0, 16) }}…
                </span>
                <span v-if="record.errorCode">
                  오류 <code>{{ record.errorCode }}</code>
                </span>
              </p>
            </div>
            <StatusBadge :status="record.status" />
          </article>
        </div>
      </section>
    </template>
  </div>
</template>
