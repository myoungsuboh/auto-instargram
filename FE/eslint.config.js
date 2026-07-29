import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'

/**
 * ESLint 설정.
 *
 * 근거: skills/frontEnd/eslint-coding-styles.md
 *   규칙 3 — "포맷은 도구로 강제한다: 사람이 눈으로 맞추지 말고 린터로 자동 정렬한다"
 *   규칙 2 — "명명은 의도를 드러낸다: 종류별로 정해진 케이스를 일관되게 적용한다"
 *
 * Vue 공식 권장 규칙을 기반으로 하고, 이 프로젝트에서 실수하기 쉬운 것만 추가로 막는다.
 */
export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    files: ['**/*.{js,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
      },
    },
    rules: {
      // ── 규칙 2: 명명 케이스를 일관되게 ──────────────────────────────
      // 컴포넌트 파일·이름은 PascalCase, 템플릿에서도 PascalCase 로 쓴다
      'vue/component-name-in-template-casing': ['error', 'PascalCase'],
      'vue/component-definition-name-casing': ['error', 'PascalCase'],
      // 커스텀 이벤트는 camelCase 로 정의하고 템플릿에서 kebab-case 로 듣는다
      'vue/custom-event-name-casing': ['error', 'camelCase'],

      // ── 보안: XSS 경로를 코드 차원에서 막는다 ───────────────────────
      // SKL-OWASP-TOP10 규칙 2("innerHTML 에 미검증 데이터를 삽입하지 않는다").
      // v-html 은 Vue 의 자동 이스케이프를 우회하므로 아예 금지한다.
      'vue/no-v-html': 'error',

      // ── 실수 방지 ─────────────────────────────────────────────────
      // props 를 직접 수정하면 부모와 상태가 어긋난다
      'vue/no-mutating-props': 'error',
      // 미사용 변수는 지운 코드의 잔재이거나 오타다
      'no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      // 디버깅 흔적을 커밋하지 않는다
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-debugger': 'error',
      // == 은 예상 밖의 형변환을 만든다
      eqeqeq: ['error', 'always'],
      // 재할당하지 않는 변수는 const 로 (의도를 드러낸다)
      'prefer-const': 'error',

      // ── 가독성 ────────────────────────────────────────────────────
      // 템플릿 속성이 많아지면 한 줄에 몰지 않고 줄바꿈한다
      'vue/max-attributes-per-line': ['warn', { singleline: 4, multiline: 1 }],
    },
  },
]
