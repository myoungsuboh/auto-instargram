<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NoticeBanner from '../components/NoticeBanner.vue'
import { toDisplayError } from '../api/client'
import { login } from '../stores/session'

/**
 * 로그인 화면 (명세 외 추가 — ADR-0005).
 *
 * 1_spack.md 의 API 5개가 모두 인증을 요구하지만 로그인 창구가 명세에 없어,
 * 사용자 확정 결정으로 추가했다.
 *
 * 토큰은 이 화면이 만지지 않는다 — 서버가 httpOnly 쿠키로 심는다(ADR-0006).
 */
const router = useRouter()
const route = useRoute()

const username = ref('')
const password = ref('')
const submitting = ref(false)
const error = ref(null)

async function handleSubmit() {
  error.value = null
  submitting.value = true
  try {
    // 비밀번호의 앞뒤 공백을 제거한다.
    //
    // 왜 필요한가: 비밀번호를 붙여넣을 때 공백이나 줄바꿈이 딸려 오는 일이 흔하고,
    // 그 문자는 입력란에서 <b>보이지 않는다</b>. 그러면 사용자는 올바른 비밀번호를 넣었다고
    // 믿는데 "아이디 또는 비밀번호가 올바르지 않습니다"만 보게 된다(실제로 겪은 문제다).
    //
    // 왜 안전한가: 이 시스템의 계정 비밀번호는 환경변수(SEED_*_PASSWORD)로만 만들어지고,
    // 스프링의 properties 파서가 앞뒤 공백을 이미 제거한다 — 즉 앞뒤 공백이 <b>의미를 갖는</b>
    // 비밀번호는 애초에 존재할 수 없다. 따라서 제거해도 로그인 가능한 조합이 줄지 않는다.
    await login(username.value, password.value.trim())
    // 로그인 전에 가려던 곳이 있으면 그곳으로, 없으면 기본 화면으로
    await router.push(route.query.redirect || { name: 'upload' })
  } catch (caught) {
    error.value = toDisplayError(caught)
    // 실패해도 아이디는 남긴다 — 비밀번호만 지워 다시 입력하게 한다
    password.value = ''
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="container" style="max-width: 460px">
    <header class="page-header">
      <p class="meta-label">
        auto-instargram
      </p>
      <h1 class="display" style="font-size: clamp(32px, 5vw, 48px)">
        로그인
      </h1>
      <p class="lede">
        인스타그램 자동 업로드 관리 대시보드입니다. 운영자 또는 관리자 계정으로
        로그인해 주세요.
      </p>
    </header>

    <form class="card" novalidate @submit.prevent="handleSubmit">
      <!-- 서버가 준 메시지를 그대로 보여준다. 어느 쪽이 틀렸는지는 서버도 알려주지 않는다
           (계정 존재 여부 노출 방지). -->
      <NoticeBanner
        v-if="error"
        :kind="error.code === 'TOO_MANY_ATTEMPTS' ? 'info' : 'error'"
        :message="error.message"
        :code="error.code"
        style="margin-bottom: 24px"
      />

      <!-- 자가 진단 안내.
           서버 메시지만으로는 무엇을 고쳐야 할지 알 수 없어 사용자가 같은 실패를 반복한다.
           계정 존재 여부를 알려주지 않는 <b>일반적인</b> 확인 항목만 제시한다. -->
      <ul
        v-if="error?.code === 'INVALID_CREDENTIALS'"
        class="meta"
        style="margin: -8px 0 24px; padding-left: 20px"
      >
        <li>아이디는 대소문자를 구분합니다.</li>
        <li>한글 입력 상태(한/영)와 Caps Lock 을 확인해 주세요.</li>
        <li>붙여넣기했다면 앞뒤에 공백이 섞이지 않았는지 확인해 주세요.</li>
      </ul>

      <label class="field">
        <span class="meta-label">아이디</span>
        <input
          v-model.trim="username"
          class="field-input"
          type="text"
          name="username"
          autocomplete="username"
          required
          :aria-invalid="Boolean(error?.fields?.username)"
          :disabled="submitting"
        >
        <span v-if="error?.fields?.username" class="field-error">
          {{ error.fields.username }}
        </span>
      </label>

      <label class="field">
        <span class="meta-label">비밀번호</span>
        <input
          v-model="password"
          class="field-input"
          type="password"
          name="password"
          autocomplete="current-password"
          required
          :aria-invalid="Boolean(error?.fields?.password)"
          :disabled="submitting"
        >
        <span v-if="error?.fields?.password" class="field-error">
          {{ error.fields.password }}
        </span>
      </label>

      <button
        class="button"
        type="submit"
        style="width: 100%"
        :disabled="submitting || !username || !password"
      >
        {{ submitting ? '확인 중…' : '로그인' }}
      </button>

      <p class="field-hint" style="margin-top: 24px">
        비밀번호를 5회 틀리면 15분간 로그인이 제한됩니다.
      </p>
    </form>
  </div>
</template>
