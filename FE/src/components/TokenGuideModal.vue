<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'

/**
 * 인스타그램 토큰 발급 안내 모달.
 *
 * 내용 출처 — Meta 공식 문서 (2026-07 확인):
 *   · 설정·토큰 발급   https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/get-started
 *   · 토큰 수명·교환   https://developers.facebook.com/docs/instagram-platform/reference/access_token/
 *   · 게시 권한·한도   https://developers.facebook.com/docs/instagram-platform/content-publishing
 *
 * 이 앱은 `graph.instagram.com` + `grant_type=ig_exchange_token` 을 쓴다.
 * 즉 "Instagram API with Instagram Login" 경로다 — 안내도 그 경로로 맞췄다.
 * (Facebook Login 경로는 권한 이름과 토큰 종류가 달라 섞으면 동작하지 않는다.)
 */
const props = defineProps({
  open: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])

const dialog = ref(null)

/** ESC 로 닫기 — 모달의 기본 기대 동작이다. */
function onKeydown(event) {
  if (event.key === 'Escape' && props.open) emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  // 컴포넌트가 사라질 때 스크롤 잠금이 남지 않게 반드시 해제한다
  document.body.style.overflow = ''
})

watch(
  () => props.open,
  (isOpen) => {
    // 모달이 열려 있을 때 뒤 배경이 스크롤되면 어디를 보고 있는지 잃는다
    document.body.style.overflow = isOpen ? 'hidden' : ''
    if (isOpen) {
      // 열리면 모달로 초점을 옮긴다 (키보드 사용자가 바로 읽을 수 있게)
      requestAnimationFrame(() => dialog.value?.focus())
    }
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="open" class="modal-backdrop" @click.self="emit('close')">
        <div
          ref="dialog"
          class="modal"
          role="dialog"
          aria-modal="true"
          aria-labelledby="token-guide-title"
          tabindex="-1"
        >
          <header class="modal-header">
            <div>
              <p class="meta-label">
                Meta 공식 문서 기준 · 2026년 7월 확인
              </p>
              <h2 id="token-guide-title" class="section-title">
                인스타그램 토큰 받는 방법
              </h2>
            </div>
            <button
              type="button"
              class="modal-close"
              aria-label="닫기"
              @click="emit('close')"
            >
              ✕
            </button>
          </header>

          <div class="modal-body">
            <p class="lede" style="margin-top: 0">
              인스타그램에 자동으로 게시하려면 <strong>액세스 토큰</strong>이 필요합니다.
              토큰은 "이 프로그램이 내 인스타그램에 글을 올려도 된다"는 허가증입니다.
              아래 순서대로 하면 15~20분 정도 걸립니다.
            </p>

            <!-- ── 준비물 ─────────────────────────────────────────── -->
            <section class="guide-section">
              <h3 class="guide-heading">
                먼저 준비해야 하는 것
              </h3>
              <p class="meta" style="margin-bottom: 12px">
                이 두 가지가 없으면 토큰을 발급할 수 없습니다. 둘 다 무료입니다.
              </p>
              <ul class="guide-list">
                <li>
                  <strong>인스타그램 프로페셔널 계정</strong> — 비즈니스 또는 크리에이터 계정이어야
                  합니다. 개인 계정은 API 로 게시할 수 없습니다. 또한 <strong>공개 계정</strong>이어야
                  합니다(비공개는 연결 불가).
                  <span class="guide-note">
                    바꾸는 곳: 인스타그램 앱 → 설정 → 계정 유형 및 도구 → 프로페셔널 계정으로 전환
                  </span>
                </li>
                <li>
                  <strong>Meta 개발자 계정</strong> — 페이스북 계정으로 가입합니다.
                  <span class="guide-note">
                    developers.facebook.com 에서 우측 상단 "시작하기"
                  </span>
                </li>
              </ul>
              <p class="guide-warn">
                <strong>페이스북 페이지는 필요 없습니다.</strong> 문서 원문:
                "이 API 설정은 Facebook 페이지를 Instagram 프로페셔널 계정에 연결할 필요가
                없습니다." 인터넷의 인스타그램 API 자료 다수가 페이스북 페이지를 요구하는데,
                그건 <em>Facebook 로그인</em> 방식 설명입니다 — 이 앱은
                <em>Instagram 로그인</em> 방식을 쓰므로 해당하지 않습니다.
              </p>
            </section>

            <!-- ── 단계 ───────────────────────────────────────────── -->
            <section class="guide-section">
              <h3 class="guide-heading">
                토큰 발급 순서
              </h3>

              <ol class="guide-steps">
                <li>
                  <strong>앱 만들기 — 여기서 고르는 값이 중요합니다</strong>
                  <p>
                    developers.facebook.com → 내 앱 → <em>앱 만들기</em>. 순서대로 이렇게 고릅니다.
                  </p>
                  <ul class="guide-list">
                    <li><strong>이용 사례</strong>: <strong>기타(Other)</strong></li>
                    <li><strong>앱 유형</strong>: <strong>비즈니스</strong></li>
                    <li><strong>앱 이름</strong>: 아무거나 (예: <code>내 릴스 자동화</code>)</li>
                  </ul>
                  <p class="guide-warn">
                    이용 사례에서 <strong>"비즈니스용 Facebook"</strong> 같은 다른 항목을 고르면
                    다음 단계의 <strong>Instagram 제품이 목록에 나타나지 않습니다.</strong>
                    반드시 <strong>기타(Other)</strong> 를 고르세요. 문서 원문:
                    "Instagram 제품에 액세스할 수 있는 앱을 만들려면 Other 이용 사례를 선택합니다."
                  </p>
                </li>

                <li>
                  <strong>Instagram 제품 추가</strong>
                  <p>
                    왼쪽 메뉴가 아니라 <strong>대시보드 화면 본문</strong>에 있습니다.
                    왼쪽 맨 위 <strong>대시보드</strong> 를 누르고, 그 페이지를
                    <strong>아래로 쭉 스크롤</strong>하면 추가할 수 있는 제품 카드들이 나옵니다.
                    거기서 <strong>Instagram</strong> 카드의 <strong>설정</strong>(Set up) 을 누릅니다.
                  </p>
                  <p class="guide-warn">
                    <strong>대시보드에 제품 카드가 없고 "앱 맞춤 설정 및 요건" 목록만 보인다면</strong>,
                    그 앱은 이미 다른 용도(광고·Threads 등)로 설정된 앱입니다.
                    제품 카드는 <em>갓 만든 앱</em>의 대시보드에만 나옵니다. 이 경우
                    오른쪽 위 <strong>이용 사례 추가</strong> 를 누르고, 목록에서
                    <strong>"Instagram에서 메시지 및 콘텐츠 관리"</strong> 를 체크한 뒤
                    <strong>저장</strong>하면 됩니다 (목록 아래쪽에 있어 스크롤해야 보입니다).
                    이것이 Meta 문서가 말하는 그 이용 사례입니다.
                  </p>
                  <p class="guide-warn">
                    <strong>이미 있는 이용 사례(광고 등)를 눌러 들어가면 안 됩니다</strong> —
                    그 안의 권한 목록에는 인스타그램 권한이 없습니다.
                  </p>
                  <p class="guide-warn">
                    왼쪽 메뉴에 Instagram 이 생기면 설정 방식이 두 개 보입니다.
                    반드시 <strong>"Instagram 로그인으로 API 설정"</strong>
                    (<em>API setup with Instagram business login</em>) 을 쓰세요.
                    <strong>"Facebook 로그인으로 API 설정" 은 쓰지 마세요</strong> —
                    이 프로그램은 <code>graph.instagram.com</code> 방식이라 토큰 종류가 달라
                    작동하지 않습니다.
                  </p>
                  <p>
                    그러면 "Instagram 로그인을 통한 API 설정"이 자동으로 추가되고,
                    <strong>그때 비로소 왼쪽 메뉴에 Instagram 이 생깁니다</strong>
                    (<strong>Instagram → Instagram 로그인으로 API 설정</strong>,
                    영어 화면에서는 <em>API setup with Instagram business login</em>).
                  </p>
                  <p class="guide-warn">
                    <strong>이미 만든 앱에 추가하려는 경우</strong>도 이 단계부터 하면 됩니다
                    (문서: "기존 앱에 Instagram을 추가하려면 6단계부터 시작하세요").
                    단, 제품 카드 목록에 <strong>Instagram 이 아예 없다면</strong> 그 앱은
                    비즈니스 유형이 아니라서 쓸 수 없습니다 — 위 1단계대로 앱을 새로 만드세요.
                  </p>
                  <p>
                    <strong>내 앱에 Instagram 이 들어 있는지 확인하는 법</strong>:
                    왼쪽 메뉴 <strong>게시</strong> 를 누르면 <strong>"이 앱의 이용 사례"</strong>
                    목록이 나옵니다. 거기에 Instagram 관련 항목이 없고 광고·Threads 같은 것만
                    있으면 그 앱으로는 릴스를 올릴 수 없습니다.
                  </p>
                  <p class="guide-warn">
                    <strong>권한을 손으로 찾아 추가하지 마세요.</strong> 광고 같은 다른 이용 사례의
                    권한 목록에는 <code>instagram_business_content_publish</code> 가 아예 없습니다.
                    Instagram 제품을 추가하면 필요한 권한이 자동으로 함께 붙습니다.
                  </p>
                </li>

                <li>
                  <strong>계정 연결하고 토큰 생성</strong>
                  <p>
                    그 메뉴에서 내 인스타그램 계정을 연결한 뒤, 계정 옆의
                    <strong>Generate token</strong>(토큰 생성) 을 누릅니다.
                    인스타그램 로그인 창이 뜨고 권한을 허용하면 <strong>토큰 문자열</strong>이 나옵니다.
                  </p>
                  <p>
                    연결할 계정은 <strong>공개 상태</strong>여야 합니다 — 비공개 계정은
                    연결되지 않습니다 (문서: "이 계정은 공개 상태여야 합니다").
                  </p>
                  <p class="guide-warn">
                    이렇게 받은 토큰은 <strong>1시간만</strong> 유효한 "단기 토큰"입니다.
                    바로 다음 단계로 넘어가세요.
                  </p>
                </li>

                <li>
                  <strong>앱 시크릿 복사</strong>
                  <p>
                    왼쪽 메뉴 <strong>앱 설정 → 기본 설정</strong> 에서
                    <strong>앱 시크릿 코드</strong>의 <em>표시</em> 를 눌러 값을 복사합니다.
                  </p>
                  <p>
                    이 값을 프로젝트 폴더의 <code>.env</code> 파일에 넣습니다:
                  </p>
                  <pre class="guide-code">INSTAGRAM_CLIENT_SECRET=복사한_앱_시크릿</pre>
                </li>

                <li>
                  <strong>내 인스타그램 계정 번호 확인</strong>
                  <p>
                    브라우저 주소창에 아래 주소를 넣고 <code>단기토큰</code> 부분만 3단계에서
                    받은 토큰으로 바꿔 실행합니다.
                  </p>
                  <pre class="guide-code">https://graph.instagram.com/v25.0/me?fields=user_id,username&amp;access_token=단기토큰</pre>
                  <p>
                    화면에 나오는 <code>user_id</code> 숫자를 <code>.env</code> 에 넣습니다:
                  </p>
                  <pre class="guide-code">INSTAGRAM_USER_ID=나온_숫자
INSTAGRAM_PUBLISH_ENABLED=true</pre>
                </li>

                <li>
                  <strong>서버 다시 시작</strong>
                  <p>
                    <code>stop.bat</code> → <code>run.bat</code> 순서로 실행해 바뀐 설정을 반영합니다.
                  </p>
                </li>

                <li>
                  <strong>이 화면에서 장기 토큰으로 바꾸기</strong>
                  <p>
                    아래 <strong>인스타그램 토큰</strong> 칸에 3단계의 단기 토큰을 붙여넣고
                    <em>토큰 갱신</em> 을 누릅니다. 서버가 이것을
                    <strong>60일짜리 장기 토큰</strong>으로 바꿔 암호화해 보관합니다.
                  </p>
                  <p class="guide-warn">
                    단기 토큰은 1시간 뒤 만료되므로, 3단계 직후에 이 단계까지 마치는 게 좋습니다.
                    만료되면 3단계를 다시 하면 됩니다.
                  </p>
                </li>
              </ol>
            </section>

            <!-- ── 알아두면 좋은 것 ────────────────────────────────── -->
            <section class="guide-section">
              <h3 class="guide-heading">
                알아두면 좋은 것
              </h3>
              <ul class="guide-list">
                <li>
                  <strong>60일마다 갱신해야 합니다.</strong> 장기 토큰은 60일 뒤 만료되고
                  자동으로 연장되지 않습니다. 만료되면 게시가 실패하고 이력에 오류로 남습니다.
                </li>
                <li>
                  <strong>하루 100건까지</strong> API 로 게시할 수 있습니다 (24시간 이동 기준).
                  이 앱은 그 한도를 넘기기 전에 미리 막습니다.
                </li>
                <li>
                  <strong>앱이 "개발 모드"면</strong> 본인 계정에만 게시됩니다.
                  다른 사람 계정에 쓰려면 Meta 의 앱 검수를 받아야 합니다.
                </li>
                <li>
                  <strong>토큰은 비밀번호와 같습니다.</strong> 남에게 보여주거나 화면을 공유하지
                  마세요. 이 앱은 토큰을 암호화해 저장하고 화면·기록에 절대 표시하지 않습니다.
                </li>
              </ul>
            </section>

            <!-- ── 출처 ───────────────────────────────────────────── -->
            <section class="guide-section">
              <h3 class="guide-heading">
                이 안내의 출처
              </h3>
              <p class="meta" style="margin-bottom: 8px">
                추측이 아니라 Meta 공식 문서를 확인해 작성했습니다. 화면 구성이 바뀌었다면
                아래 문서가 최신입니다.
              </p>
              <ul class="guide-list guide-sources">
                <li>
                  <a
                    href="https://developers.facebook.com/docs/instagram-platform/create-an-instagram-app/"
                    target="_blank"
                    rel="noopener noreferrer"
                  >앱 만들기 — 이용 사례 선택과 Instagram 제품 추가 (1·2단계의 출처)</a>
                </li>
                <li>
                  <a
                    href="https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login"
                    target="_blank"
                    rel="noopener noreferrer"
                  >Instagram 로그인 방식 개요 — 페이스북 페이지 불필요 근거</a>
                </li>
                <li>
                  <a
                    href="https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/get-started"
                    target="_blank"
                    rel="noopener noreferrer"
                  >설정 및 토큰 발급 (Get Started)</a>
                </li>
                <li>
                  <a
                    href="https://developers.facebook.com/docs/instagram-platform/reference/access_token/"
                    target="_blank"
                    rel="noopener noreferrer"
                  >토큰 수명과 교환 방법 (Access Token)</a>
                </li>
                <li>
                  <a
                    href="https://developers.facebook.com/docs/instagram-platform/content-publishing"
                    target="_blank"
                    rel="noopener noreferrer"
                  >게시 권한과 하루 100건 한도 (Content Publishing)</a>
                </li>
              </ul>
            </section>
          </div>

          <footer class="modal-footer">
            <button type="button" class="button" @click="emit('close')">
              닫기
            </button>
          </footer>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
