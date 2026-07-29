<script setup>
/**
 * 성공·실패·안내 메시지 배너.
 *
 * 메시지는 <b>서버가 준 문구를 그대로</b> 넘겨받는다. 화면에서 문구를 다시 만들면
 * 1_spack.md 가 규정한 메시지("인증이 필요합니다" 등)와 어긋난다.
 *
 * 접근성: 오류는 role="alert" 로 즉시 읽히게 하고, 성공·안내는 role="status" 로
 * 현재 작업을 방해하지 않게 알린다.
 */
defineProps({
  kind: {
    type: String,
    default: 'info',
    validator: (value) => ['error', 'success', 'info'].includes(value),
  },
  message: { type: String, required: true },
  /** 에러 코드 (있으면 작게 병기 — 문의·조사에 도움이 된다) */
  code: { type: String, default: '' },
})
</script>

<template>
  <div
    class="notice"
    :class="`notice-${kind}`"
    :role="kind === 'error' ? 'alert' : 'status'"
  >
    <!-- v-html 을 쓰지 않는다: 사용자·서버 문자열을 그대로 삽입하면 XSS 경로가 된다
         (SKL-OWASP-TOP10 규칙 2). Vue 의 {{ }} 는 자동으로 이스케이프한다. -->
    {{ message }}
    <span v-if="code" class="meta" style="margin-left: 8px">({{ code }})</span>
  </div>
</template>
