<script setup>
/**
 * 목록이 0건일 때 보여주는 안내.
 *
 * POL-03: 서버는 결과가 0건이어도 200 + 빈 배열을 준다. 즉 <b>0건은 오류가 아니다.</b>
 * 그래서 오류 배너가 아니라 별도의 안내 상태로 표시하고, 다음에 할 일을 알려준다.
 */
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
})
</script>

<template>
  <div class="empty">
    <p class="empty-title">
      {{ title }}
    </p>
    <p v-if="description" class="meta" style="margin: 0">
      {{ description }}
    </p>
    <div v-if="$slots.action" style="margin-top: 24px">
      <slot name="action" />
    </div>
  </div>
</template>
