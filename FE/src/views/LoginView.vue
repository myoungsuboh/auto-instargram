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
    await login(username.value, password.value)
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
