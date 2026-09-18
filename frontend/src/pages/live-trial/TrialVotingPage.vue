<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye } from '@lucide/vue'
import { CONNECTION_STATUS, VOTE_STATUS } from '@/consts/liveTrialUiStatus.js'
import { TRIAL_STATUS } from '@/consts/trialStatus.js'
import { submitVote as submitVoteRequest } from '@/apis/trialApi.js'
import { useLiveTrialSession } from '@/composables/useLiveTrialSession.js'
import { useTrialCountdown } from '@/composables/useTrialCountdown.js'
import TrialChatPanel from '@/components/chat/TrialChatPanel.vue'
import TrialConnectionStatus from '@/components/trial/TrialConnectionStatus.vue'
import FinalVerdictVote from '@/components/vote/FinalVerdictVote.vue'
import { finalVoteMock } from '@/mock/vote/finalVoteMock.js'

const route = useRoute()
const router = useRouter()
const trialId = computed(() => route.params.trialId)
const session = useLiveTrialSession(trialId)

const trialEndsAt = computed(() => session.currentSnapshot.value?.phaseEndsAt)
const { formattedRemainingTime } = useTrialCountdown(trialEndsAt)
const trialEnded = computed(
  () => session.status.value === TRIAL_STATUS.ENDED || session.currentSnapshot.value?.ended,
)
const generationStatus = computed(() => session.currentSnapshot.value?.generationStatus)
const generationInProgress = computed(() => generationStatus.value === 'GENERATING')
const generationFailed = computed(() => generationStatus.value === 'FAILED')
const interactionsDisabled = computed(
  () => session.connection.value.status !== CONNECTION_STATUS.CONNECTED || trialEnded.value || generationInProgress.value || generationFailed.value,
)
const interactionDisabledMessage = computed(() => {
  if (trialEnded.value) return '재판이 종료되었습니다.'
  if (generationInProgress.value) return 'AI 변론이 생성 중입니다. 완료된 뒤 투표가 시작됩니다.'
  if (generationFailed.value) return 'AI 생성 문제가 해결된 뒤 투표를 진행할 수 있습니다.'
  if (session.connection.value.status !== CONNECTION_STATUS.CONNECTED) {
    return '재판 연결을 복구한 뒤 다시 시도해 주세요.'
  }
  return ''
})
const submittedVote = ref(null)
const selectedChoice = ref(null)
const votePending = ref(false)
const voteError = ref('')
const duplicateVote = ref(false)
const votePresentation = computed(() => {
  const detail = session.detail.value
  const aDisplayName = detail?.aParty?.displayName || '박건우'
  const bDisplayName = detail?.bParty?.displayName || '김지민'

  return {
    ...finalVoteMock,
    title: detail?.title || finalVoteMock.title,
    subtitle: `A측: ${aDisplayName} vs B측: ${bDisplayName}`,
    choices: [
      {
        ...finalVoteMock.choices[0],
        description: `${aDisplayName}의 주장이 옳다`,
      },
      {
        ...finalVoteMock.choices[1],
        description: `${bDisplayName}의 주장이 옳다`,
      },
    ],
  }
})
const voteStatus = computed(() =>
  submittedVote.value
    ? VOTE_STATUS.SUBMITTED
    : session.currentSnapshot.value?.voteOpen
      ? VOTE_STATUS.OPEN
      : VOTE_STATUS.WAITING,
)
const voteDisabled = computed(
  () =>
    interactionsDisabled.value ||
    votePending.value ||
    duplicateVote.value ||
    voteStatus.value !== VOTE_STATUS.OPEN,
)
const voteDisabledMessage = computed(() => {
  if (interactionsDisabled.value) return interactionDisabledMessage.value
  if (duplicateVote.value) return '이 브라우저에서는 이미 투표를 제출했습니다.'
  if (votePending.value) return '투표를 제출하고 있습니다.'
  if (voteStatus.value === VOTE_STATUS.WAITING) return '최종 투표가 아직 시작되지 않았습니다.'
  if (voteStatus.value === VOTE_STATUS.SUBMITTED) return '투표 제출이 완료되었습니다.'
  return ''
})

watch(
  [() => session.status.value, () => session.restoring.value, generationStatus],
  ([status, restoring, generation]) => {
    if (restoring) return

    if (generation === 'GENERATING' || generation === 'FAILED') {
      router.replace({ name: 'live-trial', params: { trialId: route.params.trialId } })
      return
    }

    if (status === TRIAL_STATUS.ENDED) {
      router.replace({ name: 'trial-result', params: { trialId: route.params.trialId } })
      return
    }

    if (status && ![TRIAL_STATUS.VOTING, TRIAL_STATUS.VERDICT].includes(status)) {
      router.replace({ name: 'live-trial', params: { trialId: route.params.trialId } })
    }
  },
  { immediate: true },
)

function selectChoice(choiceId) {
  if (voteDisabled.value) return
  voteError.value = ''
  selectedChoice.value = choiceId
}

async function submitVote() {
  if (voteDisabled.value || !selectedChoice.value) return

  votePending.value = true
  voteError.value = ''

  try {
    submittedVote.value = Object.freeze(
      await submitVoteRequest(
        trialId.value,
        selectedChoice.value.replace('SIDE_', ''),
      ),
    )
  } catch (error) {
    duplicateVote.value = error?.code === 'ALREADY_VOTED'
    voteError.value = error?.message || '투표를 제출하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  } finally {
    votePending.value = false
  }
}
</script>

<template>
  <div class="voting-page">
    <main class="voting-shell">
      <TrialConnectionStatus
        :connection="session.connection.value"
        :ended="trialEnded"
        @retry="session.reconnect"
      />

      <p v-if="session.chatError.value" class="realtime-error" role="alert">
        {{ session.chatError.value?.message || '실시간 요청을 처리하지 못했습니다.' }}
      </p>

      <p v-if="voteError" class="vote-request-error" role="alert">
        {{ voteError }}
      </p>

      <div class="voting-layout">
        <div class="voting-main">
          <section class="courtroom-stream" aria-labelledby="stream-title">
            <img src="/images/final-vote-courtroom-pixel.png" alt="최종 투표를 진행 중인 AI 재판장" />
            <div class="live-indicator" :class="{ ended: trialEnded }">
              <i aria-hidden="true"></i>
              <span>{{ trialEnded ? 'ENDED' : 'LIVE' }}</span>
              <Eye :size="14" />
              <strong>{{ finalVoteMock.viewerCount.toLocaleString('ko-KR') }}</strong>
            </div>
            <div class="stream-caption">
              <span>FINAL VOTE · 사랑과 전쟁터</span>
              <h1 id="stream-title">사건 #{{ votePresentation.caseNumber }}: {{ votePresentation.title }}</h1>
              <p>{{ votePresentation.subtitle }}</p>
            </div>
          </section>

          <FinalVerdictVote
            :choices="votePresentation.choices"
            :remaining-time="formattedRemainingTime"
            :selected-choice="selectedChoice"
            :status="voteStatus"
            :disabled="interactionsDisabled || votePending || duplicateVote"
            :disabled-message="voteDisabledMessage"
            @select="selectChoice"
            @submit="submitVote"
          />
        </div>

        <div class="trial-chat-column">
          <TrialChatPanel
            :messages="session.messages.value"
            :current-user-id="session.demoUserId"
            :audience-count="finalVoteMock.viewerCount"
            :header-label="trialEnded ? '종료' : '실시간'"
            :disabled="interactionsDisabled"
            :loading="session.chatRestoring.value"
            :sending="session.chatSending.value"
            :disabled-message="interactionDisabledMessage"
            :on-send="session.sendChat"
          />
        </div>
      </div>
    </main>

  </div>
</template>

<style scoped>
.voting-page {
  min-height: calc(100dvh - 72px);
  background: var(--ds-color-page-background);
}

.voting-shell {
  width: min(calc(100% - 32px), var(--ds-container-max));
  min-height: calc(100dvh - 72px);
  margin: 0 auto;
  padding: 24px 0 34px;
  display: grid;
  grid-template-rows: auto auto auto 1fr;
  gap: 14px;
}

.voting-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  align-items: stretch;
  gap: 20px;
  min-height: 0;
}

.voting-main {
  min-height: 0;
}

.vote-request-error {
  margin: 0 0 14px;
  padding: 12px 14px;
  border: 1px solid var(--ds-color-error);
  border-radius: var(--ds-radius-default);
  background: var(--ds-color-error-container);
  color: var(--ds-color-on-error-container);
  font-size: 1rem;
}

.voting-main {
  display: grid;
  align-content: start;
  gap: 18px;
}

.courtroom-stream {
  position: relative;
  aspect-ratio: 16 / 9;
  border: 1px solid var(--ds-color-outline-variant);
  border-radius: var(--ds-radius-md);
  background: #dce7f3;
  box-shadow: var(--ds-shadow-interactive);
  overflow: hidden;
}

.courtroom-stream::after {
  content: '';
  position: absolute;
  inset: 45% 0 0;
  background: linear-gradient(transparent, rgb(4 18 38 / 86%));
  pointer-events: none;
}

.courtroom-stream img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.live-indicator {
  position: absolute;
  z-index: 2;
  top: 12px;
  left: 12px;
  min-height: 28px;
  padding: 0 13px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  border-radius: var(--ds-radius-full);
  background: #c92532;
  color: white;
  font-size: 0.88rem;
}

.live-indicator i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: white;
}

.live-indicator.ended {
  background: #687486;
}

.live-indicator span,
.live-indicator strong {
  font-weight: 700;
}

.stream-caption {
  position: absolute;
  z-index: 2;
  right: 0;
  bottom: 0;
  left: 0;
  padding: 22px 18px 16px;
  color: white;
}

.stream-caption span {
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  opacity: 0.76;
}

.stream-caption h1 {
  margin: 5px 0 3px;
  color: white;
  font-size: clamp(1.45rem, 2.6vw, 2rem);
  text-shadow: 0 2px 12px rgb(0 0 0 / 36%);
}

.stream-caption p {
  margin: 0;
  color: rgb(255 255 255 / 80%);
  font-size: 0.95rem;
}

.trial-chat-column {
  position: relative;
  min-width: 0;
  min-height: 0;
}

.trial-chat-column > :deep(.chat-panel) {
  position: absolute;
  inset: 0;
  height: auto;
  max-height: none;
}

@media (max-width: 960px) {
  .voting-layout {
    grid-template-columns: 1fr;
  }

  .trial-chat-column {
    height: 30rem;
  }
}

@media (max-width: 620px) {
  .voting-shell {
    width: min(calc(100% - 24px), var(--ds-container-max));
    padding-top: 14px;
  }

  .courtroom-stream {
    aspect-ratio: 4 / 3;
  }

  .stream-caption p {
    display: none;
  }
}
</style>
