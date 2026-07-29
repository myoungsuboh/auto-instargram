<script setup>
import { computed, ref } from 'vue'
import NoticeBanner from '../components/NoticeBanner.vue'
import TokenGuideModal from '../components/TokenGuideModal.vue'
import { newIdempotencyKey, toDisplayError } from '../api/client'
import { reels, tokens } from '../api/endpoints'
import { isAdmin } from '../stores/session'

/**
 * SCREEN-04 릴스 업로드 제어 화면 — `/dashboard/reels`
 *
 * ⚠️ 1_spack.md §5 의 이 화면은 기재가 서로 어긋난다:
 *   이름·설명·Story(06.1) → 릴스 업로드 (API-04)
 *   호출 API 란          → API-05 POST /api/v1/tokens/refresh (토큰 갱신)
 * 3_architecture.md §5 매핑 표에서도 두 API 의 설명이 서로 바뀌어 있어 문서상 혼선으로 보인다.
 *
 * 사용자 확정 결정(2026-07-29): <b>둘 다 넣는다.</b>
 *   · 릴스 업로드를 주 기능으로 (화면 이름·설명·Story 를 따름)
 *   · 토큰 갱신도 같은 화면에 (체크리스트의 API-05 지정을 따름)
 * 실사용에서도 자연스럽다 — 릴스를 올리려면 인스타그램 토큰이 유효해야 한다.
 */

// ══ 릴스 업로드 (API-04) ══════════════════════════════════════════════

const upload = ref({ binaryPath: '', caption: '' })
const uploading = ref(false)
const uploadError = ref(null)
const uploadResult = ref(null)

/** 실패 시 같은 키를 유지해 재시도가 중복 게시를 만들지 않게 한다 (규칙 1·2). */
const uploadKey = ref(newIdempotencyKey())

const captionLength = computed(() => upload.value.caption.length)

async function submitUpload() {
  uploadError.value = null
  uploadResult.value = null
  uploading.value = true
  try {
    uploadResult.value = await reels.upload(
      { binaryPath: upload.value.binaryPath, caption: upload.value.caption },
      uploadKey.value,
    )
    upload.value = { binaryPath: '', caption: '' }
    uploadKey.value = newIdempotencyKey()
  } catch (caught) {
    uploadError.value = toDisplayError(caught)
  } finally {
    uploading.value = false
  }
}

// ══ 인스타그램 토큰 갱신 (API-05, 관리자 전용) ═════════════════════════

/**
 * 토큰 발급 방법 안내 모달.
 *
 * 별도 화면이 아니라 모달로 둔 이유: 토큰을 넣는 칸을 보면서 안내를 읽어야 하고,
 * 읽다가 화면을 떠나면 입력하던 값을 잃는다.
 */
const guideOpen = ref(false)

const shortLivedToken = ref('')
const refreshing = ref(false)
const refreshError = ref(null)
const refreshResult = ref(null)

async function submitRefresh() {
  refreshError.value = null
  refreshResult.value = null
  refreshing.value = true
  try {
    const result = await tokens.refresh(shortLivedToken.value)
    // ⚠️ 응답에는 장기 토큰 전문이 들어 있지만 <b>화면에 표시하지 않는다.</b>
    //    서버가 이미 암호화해 저장했으므로 사용자가 그 값을 볼 이유가 없고,
    //    화면에 띄우면 어깨너머·스크린샷·화면 공유로 새는 경로가 생긴다(POL-05 의 취지).
    //    만료까지 남은 기간만 알려준다.
    refreshResult.value = { expiresInDays: Math.floor(result.expiresIn / 86400) }
    shortLivedToken.value = ''
  } catch (caught) {
    refreshError.value = toDisplayError(caught)
  } finally {
    refreshing.value = false
  }
}
</script>

<template>
  <div class="container">
    <header class="page-header">
      <p class="meta-label">
        SCREEN-04 · 릴스 업로드 제어 화면
      </p>
      <h1 class="display">
        릴스 업로드
      </h1>
      <p class="lede">
        영상을 지금 바로 인스타그램에 올립니다. 파일 검증을 통과하면 접수되고,
        실제 게시는 백그라운드에서 4단계로 진행됩니다.
      </p>
    </header>

    <div class="split">
      <!-- ══ 업로드 (API-04) ═══════════════════════════════════════ -->
      <section class="card" aria-labelledby="upload-heading">
        <h2 id="upload-heading" class="section-title">
          지금 업로드
        </h2>

        <NoticeBanner
          v-if="uploadResult"
          kind="success"
          :message="`접수되었습니다. 처리 번호 ${uploadResult.containerId.slice(0, 8)}… (상태 ${uploadResult.status})`"
          style="margin-top: 24px"
        />
        <NoticeBanner
          v-if="uploadError"
          kind="error"
          :message="uploadError.message"
          :code="uploadError.code"
          style="margin-top: 24px"
        />

        <form novalidate style="margin-top: 24px" @submit.prevent="submitUpload">
          <label class="field">
            <span class="meta-label">영상 파일</span>
            <input
              v-model.trim="upload.binaryPath"
              class="field-input"
              type="text"
              placeholder="reel-today.mp4"
              required
              maxlength="255"
              :aria-invalid="Boolean(uploadError?.fields?.binaryPath)"
              :disabled="uploading"
            >
            <span class="field-hint">
              <code>BE/storage/media/</code> 폴더의 파일명입니다. mp4 · mov · m4v 만
              허용되며, 확장자만 바꾼 파일은 내용 검사에서 거부됩니다.
            </span>
            <span v-if="uploadError?.fields?.binaryPath" class="field-error">
              {{ uploadError.fields.binaryPath }}
            </span>
          </label>

          <label class="field">
            <span class="meta-label">캡션</span>
            <textarea
              v-model="upload.caption"
              class="field-textarea"
              maxlength="2200"
              required
              placeholder="릴스에 함께 올릴 문구"
              :aria-invalid="Boolean(uploadError?.fields?.caption)"
              :disabled="uploading"
            />
            <span class="field-hint">{{ captionLength }} / 2200자 · 필수</span>
            <span v-if="uploadError?.fields?.caption" class="field-error">
              {{ uploadError.fields.caption }}
            </span>
          </label>

          <button
            class="button"
            type="submit"
            :disabled="uploading || !upload.binaryPath || !upload.caption"
          >
            {{ uploading ? '검증 중…' : '업로드 시작' }}
          </button>
        </form>

        <p class="field-hint" style="margin-top: 24px">
          접수 후 진행 상태는
          <RouterLink to="/dashboard/posts" class="nav-link">
            게시 관리
          </RouterLink>
          화면에서 같은 번호로 확인할 수 있습니다.
        </p>
      </section>

      <!-- ══ 토큰 갱신 (API-05, 관리자 전용) ══════════════════════ -->
      <section class="card" aria-labelledby="token-heading">
        <div class="spread">
          <h2 id="token-heading" class="section-title">
            인스타그램 토큰
          </h2>
          <!-- 토큰 발급은 Meta 개발자 콘솔을 거쳐야 해서 처음에는 막막하다.
               입력 칸 바로 옆에서 안내를 열 수 있게 둔다. -->
          <button type="button" class="button-text" @click="guideOpen = true">
            어떻게 받나요?
          </button>
        </div>
        <p class="lede" style="margin-top: 16px">
          단기 토큰을 60일짜리 장기 토큰으로 교환해 안전하게 저장합니다. 토큰이
          만료되면 릴스 게시가 실패합니다.
        </p>

        <!-- 권한이 없으면 폼을 감춘다. 단, 이것은 편의일 뿐 보안 장치가 아니다 —
             진짜 차단은 서버가 한다(SKL-AUTHN-AUTHZ 규칙 3). -->
        <NoticeBanner
          v-if="!isAdmin"
          kind="info"
          message="토큰 갱신은 관리자 계정만 할 수 있습니다. 관리자에게 요청해 주세요."
          style="margin-top: 24px"
        />

        <template v-else>
          <NoticeBanner
            v-if="refreshResult"
            kind="success"
            :message="`토큰이 갱신되어 안전하게 저장되었습니다. 약 ${refreshResult.expiresInDays}일 후 만료됩니다.`"
            style="margin-top: 24px"
          />
          <NoticeBanner
            v-if="refreshError"
            kind="error"
            :message="refreshError.message"
            :code="refreshError.code"
            style="margin-top: 24px"
          />

          <form novalidate style="margin-top: 24px" @submit.prevent="submitRefresh">
            <label class="field">
              <span class="meta-label">단기 토큰</span>
              <!-- type=password: 토큰이 화면에 그대로 보이지 않게 한다.
                   autocomplete=off: 브라우저가 토큰을 저장해 두지 않게 한다. -->
              <input
                v-model.trim="shortLivedToken"
                class="field-input"
                type="password"
                autocomplete="off"
                spellcheck="false"
                placeholder="EAAG…"
                required
                maxlength="1000"
                :aria-invalid="Boolean(refreshError?.fields?.shortLivedToken)"
                :disabled="refreshing"
              >
              <span class="field-hint">
                Meta 개발자 콘솔에서 발급한 단기 액세스 토큰입니다. 입력값은 화면에
                표시되지 않으며, 교환된 토큰도 화면에 노출하지 않습니다.
              </span>
              <span v-if="refreshError?.fields?.shortLivedToken" class="field-error">
                {{ refreshError.fields.shortLivedToken }}
              </span>
            </label>

            <button
              class="button button-quiet"
              type="submit"
              :disabled="refreshing || !shortLivedToken"
            >
              {{ refreshing ? '교환 중…' : '토큰 갱신' }}
            </button>
          </form>
        </template>
      </section>
    </div>

    <!-- 토큰 발급 안내 (Meta 공식 문서 기준). Teleport 로 body 에 붙어
         이 화면의 레이아웃·스크롤에 영향을 주지 않는다. -->
    <TokenGuideModal :open="guideOpen" @close="guideOpen = false" />
  </div>
</template>
