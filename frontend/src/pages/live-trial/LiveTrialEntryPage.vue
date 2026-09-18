<script setup>
import { computed, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Clock3, Eye, UsersRound } from "@lucide/vue";
import { CONNECTION_STATUS } from "@/consts/liveTrialUiStatus.js";
import { TRIAL_STATUS } from "@/consts/trialStatus.js";
import { retryAiGeneration } from "@/apis/trialApi.js";
import { useLiveTrialSession } from "@/composables/useLiveTrialSession.js";
import { useTrialCountdown } from "@/composables/useTrialCountdown.js";
import TrialChatPanel from "@/components/chat/TrialChatPanel.vue";
import LawyerDebatePanel from "@/components/trial/LawyerDebatePanel.vue";
import TrialConnectionStatus from "@/components/trial/TrialConnectionStatus.vue";
import TrialStage from "@/components/trial/TrialStage.vue";
import { liveTrialMock } from "@/mock/trial/liveTrialMock.js";
import { getTrialPhaseLabel } from "@/mock/trial/liveTrialPresentation.js";
import {
  normalizeSpeakerKey,
  toTrialConversationEvents,
} from "@/utils/trialEvent.js";

const route = useRoute();
const router = useRouter();
const trialId = computed(() => route.params.trialId);
const session = useLiveTrialSession(trialId);
const retryPending = ref(false);
const retryError = ref("");

const trialTitle = computed(
  () => session.detail.value?.title ?? "재판 정보를 불러오는 중입니다",
);
const trialParticipants = computed(() =>
  liveTrialMock.participants.map((participant) => {
    if (participant.position === "left" && session.detail.value?.aParty) {
      return {
        ...participant,
        name: `${session.detail.value.aParty.displayName} AI 변호사`,
      };
    }
    if (participant.position === "right" && session.detail.value?.bParty) {
      return {
        ...participant,
        name: `${session.detail.value.bParty.displayName} AI 변호사`,
      };
    }
    return participant;
  }),
);
const audienceCount = computed(() => {
  const snap = session.currentSnapshot.value;
  const detailValue = session.detail.value;
  if (snap && typeof snap.audienceCount === "number") {
    return snap.audienceCount;
  }
  if (detailValue && typeof detailValue.audienceCount === "number")
    return detailValue.audienceCount;
  if (detailValue && detailValue.viewerCount)
    return Number(String(detailValue.viewerCount).replace(/,/g, ""));

  try {
    const ids = new Set(
      session.messages.value
        .map((m) => m?.sender?.demoUserId || m?.sender?.demoKey)
        .filter(Boolean),
    );
    const count = ids.size;
    return count || liveTrialMock.audienceCount || 0;
  } catch {
    return liveTrialMock.audienceCount || 0;
  }
});
const activeSpeaker = computed(() => {
  if (session.currentSnapshot.value?.generationStatus === "GENERATING") {
    return normalizeSpeakerKey(session.currentSnapshot.value?.nextSpeaker);
  }
  if (
    session.status.value === TRIAL_STATUS.INTRODUCTION ||
    session.status.value === TRIAL_STATUS.VERDICT
  )
    return "JUDGE";
  if (session.status.value === TRIAL_STATUS.A_ARGUMENT) return "A_LAWYER";
  if (session.status.value === TRIAL_STATUS.B_ARGUMENT) return "B_LAWYER";
  if (session.status.value === TRIAL_STATUS.DEBATE) {
    const lastSpeaker = normalizeSpeakerKey(
      session.events.value.at(-1)?.speaker,
    );
    return lastSpeaker;
  }
  return "";
});
const generation = computed(() => session.currentSnapshot.value ?? {});
const isGenerating = computed(() => generation.value.generationStatus === "GENERATING");
const generationFailed = computed(() => generation.value.generationStatus === "FAILED");
const generationLabel = computed(() => {
  if (generation.value.generationStage === "VERDICT") return "AI 판결 준비 중";
  return normalizeSpeakerKey(generation.value.nextSpeaker) === "B_LAWYER"
    ? "B측 반박 준비중"
    : "A측 반박 준비중";
});
const generationTimerLabel = computed(() => {
  if (isGenerating.value) return '생성 완료 대기';
  if (generationFailed.value) return '재시도 대기';
  return '';
});
const providerLabel = computed(() => {
  if (generation.value.aiProvider === "mock") return "모의 응답";
  if (generation.value.aiProvider === "langchain") return "LangChain 연결 모드";
  return "AI 진행";
});
const phaseLabel = computed(() => getTrialPhaseLabel(session.status.value));
const trialConversationEvents = computed(() =>
  toTrialConversationEvents(session.events.value),
);
const phaseEndsAt = computed(() => session.currentSnapshot.value?.phaseEndsAt);
const { formattedRemainingTime } = useTrialCountdown(phaseEndsAt);
const trialEnded = computed(
  () =>
    session.status.value === TRIAL_STATUS.ENDED ||
    session.currentSnapshot.value?.ended,
);
const chatAllowed = computed(() =>
  [
    TRIAL_STATUS.INTRODUCTION,
    TRIAL_STATUS.A_ARGUMENT,
    TRIAL_STATUS.B_ARGUMENT,
    TRIAL_STATUS.DEBATE,
    TRIAL_STATUS.VOTING,
    TRIAL_STATUS.VERDICT,
  ].includes(session.status.value),
);
const interactionsDisabled = computed(
  () =>
    session.connection.value.status !== CONNECTION_STATUS.CONNECTED ||
    !chatAllowed.value,
);
const interactionDisabledMessage = computed(() => {
  if (trialEnded.value) return "재판이 종료되었습니다.";
  if (session.connection.value.status !== CONNECTION_STATUS.CONNECTED) {
    return "재판 연결을 복구한 뒤 다시 시도해 주세요.";
  }
  return "현재 단계에서는 채팅을 사용할 수 없습니다.";
});

watch(
  [() => session.status.value, () => session.restoring.value],
  ([status, restoring]) => {
    if (restoring) return;

    if (isGenerating.value || generationFailed.value) return;

    if (status === TRIAL_STATUS.ENDED) {
      router.replace({
        name: "trial-result",
        params: { trialId: route.params.trialId },
      });
      return;
    }

    if (status === TRIAL_STATUS.VOTING) {
      router.replace({
        name: "trial-voting",
        params: { trialId: route.params.trialId },
      });
    }
  },
  { immediate: true },
);

async function retryGeneration() {
  if (!generation.value.retryable || retryPending.value) return;
  retryPending.value = true;
  retryError.value = "";
  try {
    const nextSnapshot = await retryAiGeneration(trialId.value);
    session.snapshot.value = nextSnapshot;
  } catch (error) {
    retryError.value = error?.status === 403
      ? "재판을 만든 사용자만 AI 생성을 다시 시도할 수 있습니다."
      : error?.message || "AI 생성 재시도를 요청하지 못했습니다.";
  } finally {
    retryPending.value = false;
  }
}
</script>

<template>
  <div id="live-trial" class="live-trial-page">
    <main class="page-shell">
      <TrialConnectionStatus
        :connection="session.connection.value"
        :ended="trialEnded"
        @retry="session.reconnect"
      />

      <p v-if="session.chatError.value" class="realtime-error" role="alert">
        {{
          session.chatError.value?.message ||
          "실시간 요청을 처리하지 못했습니다."
        }}
      </p>
      <p v-if="retryError" class="realtime-error" role="alert">{{ retryError }}</p>

      <section class="trial-summary" aria-labelledby="trial-title">
        <div class="summary-copy">
          <div class="summary-badges">
            <span class="live-badge" :class="{ ended: trialEnded }">
              <i aria-hidden="true"></i>{{ trialEnded ? "종료" : "실시간" }}
            </span>
            <span class="provider-badge">{{ providerLabel }}</span>
            <span class="view-badge"
              ><Eye :size="14" />{{ liveTrialMock.viewCount }}</span
            >
          </div>
          <h1 id="trial-title">“{{ trialTitle }}”</h1>
        </div>

        <div class="summary-stats">
          <div class="stat-card timer-card">
            <Clock3 :size="21" />
            <span>
              <small>{{ phaseLabel }}</small>
              <strong>{{ generationTimerLabel || formattedRemainingTime }}</strong>
            </span>
          </div>
          <div class="stat-card audience-card">
            <UsersRound :size="21" />
            <span>
              <small>참여 배심원</small>
              <strong>{{ audienceCount.toLocaleString("ko-KR") }}명</strong>
            </span>
          </div>
        </div>
      </section>

      <div class="trial-layout">
        <div class="trial-main-column">
          <TrialStage
            :participants="trialParticipants"
            :active-speaker="activeSpeaker"
            :speaking-label="isGenerating ? generationLabel : ''"
          />
          <LawyerDebatePanel
            :events="trialConversationEvents"
            :remaining-time="formattedRemainingTime"
            :waiting-label="generationTimerLabel"
            :generation-label="generationLabel"
            :generation-status="generation.generationStatus"
            :generation-error="generation.generationError"
            :retryable="Boolean(generation.retryable)"
            :retry-pending="retryPending"
            @retry="retryGeneration"
          />
        </div>

        <div class="trial-chat-column">
          <TrialChatPanel
            :messages="session.messages.value"
            :current-user-id="session.demoUserId"
            :audience-count="audienceCount"
            :header-label="trialEnded ? '종료' : ''"
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
.live-trial-page {
  min-height: calc(100dvh - 72px);
  background: var(--ds-color-page-background);
}

.page-shell {
  width: min(calc(100% - 32px), var(--ds-container-max));
  min-height: calc(100dvh - 72px);
  margin: 0 auto;
  padding: 18px 0 20px;
  display: grid;
  grid-template-rows: auto auto 1fr;
  gap: 14px;
}

.trial-summary {
  min-height: 94px;
  padding: 18px 18px 18px 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  border: 1px solid var(--ds-color-outline-variant);
  border-radius: var(--ds-radius-md);
  background: white;
}

.summary-badges {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.live-badge,
.view-badge,
.provider-badge {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 24px;
  padding: 0 10px;
  border-radius: var(--ds-radius-full);
  font-size: 0.9rem;
  font-weight: 700;
}

.live-badge {
  background: #ca2330;
  color: white;
}

.live-badge.ended {
  background: #687486;
}

.live-badge i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: white;
  box-shadow: 0 0 0 4px rgb(255 255 255 / 16%);
}

.view-badge {
  background: #edf2f9;
  color: var(--ds-color-on-surface-variant);
}

.provider-badge {
  min-height: 24px;
  padding: 0 10px;
  border-radius: var(--ds-radius-full);
  background: #edf2f9;
  color: var(--ds-color-on-surface-variant);
  font-size: 0.85rem;
  font-weight: 700;
}

h1 {
  margin: 0;
  color: var(--ds-color-primary);
  font-size: clamp(1.5rem, 2.4vw, 2rem);
  line-height: 1.35;
}

.summary-stats {
  display: flex;
  align-items: stretch;
  gap: 12px;
}

.stat-card {
  min-width: 142px;
  min-height: 54px;
  padding: 9px 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  border: 1px solid #dae3f0;
  border-radius: var(--ds-radius-default);
  background: #f3f7ff;
  box-shadow: 0 2px 5px rgb(26 54 93 / 7%);
  color: #6e7887;
}

.stat-card span {
  display: flex;
  flex-direction: column;
}

.stat-card small {
  color: var(--ds-color-on-surface-variant);
  font-size: 0.88rem;
}

.stat-card strong {
  color: var(--ds-color-primary);
  font-size: 1.05rem;
}

.timer-card strong {
  color: var(--ds-color-justice-blue);
  letter-spacing: 0.05em;
}

.trial-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(300px, 1fr);
  align-items: stretch;
  min-height: 0;
}

.trial-main-column {
  display: grid;
  gap: 14px;
}

.trial-main-column > :deep(*) {
  border-top-right-radius: 0;
  border-bottom-right-radius: 0;
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
  margin-left: -1px;
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

@media (max-width: 960px) {
  .trial-summary {
    align-items: flex-start;
  }

  .trial-layout {
    grid-template-columns: 1fr;
    gap: 14px;
  }

  .trial-main-column > :deep(*) {
    border-radius: var(--ds-radius-md);
  }

  .trial-chat-column {
    height: 30rem;
  }

  .trial-chat-column > :deep(.chat-panel) {
    margin-left: 0;
    border-radius: var(--ds-radius-md);
  }
}

@media (max-width: 680px) {
  .page-shell {
    width: min(calc(100% - 24px), var(--ds-container-max));
    padding-top: 12px;
  }

  .trial-summary {
    flex-direction: column;
    padding: 16px;
  }

  .summary-stats {
    width: 100%;
  }

  .stat-card {
    flex: 1;
    min-width: 0;
    padding: 8px;
  }

  .audience-card {
    display: none;
  }
}
</style>
