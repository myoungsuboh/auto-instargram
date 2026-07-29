<script setup>
import { computed } from 'vue'

/**
 * 상태 표시. 서버가 보내는 API enum 값을 사람이 읽는 한국어로 바꾼다.
 *
 * 서버는 ADR-0003 에 따라 내부 상태(4가지/3가지)를 명세의 API enum 으로 변환해 보낸다.
 * 화면은 그 <b>변환된 값</b>만 본다 — 여기서 다시 내부 상태를 추측하지 않는다.
 *
 * 접근성: 색만으로 상태를 전달하지 않는다. 배지 안에 항상 텍스트가 들어간다.
 */
const props = defineProps({
  /** 서버가 준 값: PENDING | SUCCESS | FAILED */
  status: { type: String, required: true },
})

const LABELS = {
  PENDING: '대기 중',
  SUCCESS: '성공',
  FAILED: '실패',
}

const CLASSES = {
  PENDING: 'badge-pending',
  SUCCESS: 'badge-success',
  FAILED: 'badge-failed',
}

// 서버가 예상 밖의 값을 보내도 화면이 깨지지 않게 원문을 그대로 보여준다
const label = computed(() => LABELS[props.status] ?? props.status)
const className = computed(() => CLASSES[props.status] ?? 'badge-pending')
</script>

<template>
  <span class="badge" :class="className">{{ label }}</span>
</template>
