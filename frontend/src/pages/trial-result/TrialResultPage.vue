<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Check, Share2 } from '@lucide/vue'
import { getResults, getSnapshot, getTrial } from '@/apis/trialApi.js'
import { TRIAL_STATUS } from '@/consts/trialStatus.js'
import FaultRatioCard from '@/components/verdict/FaultRatioCard.vue'
import JudgmentGrounds from '@/components/verdict/JudgmentGrounds.vue'
import VerdictComparison from '@/components/verdict/VerdictComparison.vue'

const shareCompleted = ref(false)
const route = useRoute()
const router = useRouter()
const trialDetail = ref(null)
const trialResult = ref(null)
const resultPending = ref(true)
const resultError = ref('')
const caseTitle = computed(() => trialDetail.value?.title ?? '')
const caseNumber = computed(() => `사건 #${route.params.trialId}`)
const providerLabel = computed(() => {
  if (trialResult.value?.aiProvider === 'mock') return '모의 응답'
  if (trialResult.value?.aiProvider === 'langchain') return 'LangChain 연결 모드'
  return ''
})
const aiResult = computed(() => {
  const verdict = trialResult.value?.verdict
  if (!verdict) return null

  return {
    winnerSide: verdict.winnerSide,
    sideA: verdict.aFaultRatio,
    sideB: verdict.bFaultRatio,
    judgment: verdict.summary,
    grounds: (verdict.grounds || []).map((description) => ({ description })),
    aRecommendation: verdict.aRecommendation,
    bRecommendation: verdict.bRecommendation,
  }
})
const juryResult = computed(() => {
  const publicVote = trialResult.value?.publicVote
  if (!publicVote) return null

  const totalVotes = publicVote.totalVotes || 0
  const sideAPercentage = totalVotes ? Math.round((publicVote.aVotes / totalVotes) * 100) : 0
  return {
    sideA: sideAPercentage,
    sideB: totalVotes ? 100 - sideAPercentage : 0,
    aVotes: publicVote.aVotes,
    bVotes: publicVote.bVotes,
    participantCount: totalVotes,
    winnerSide: publicVote.aVotes === publicVote.bVotes
      ? null
      : publicVote.aVotes > publicVote.bVotes ? 'A' : 'B',
  }
})

onMounted(async () => {
  try {
    const [detail, snapshot] = await Promise.all([
      getTrial(route.params.trialId),
      getSnapshot(route.params.trialId),
    ])
    trialDetail.value = detail
    trialResult.value = { aiProvider: snapshot.aiProvider }
    if (
      snapshot.status !== TRIAL_STATUS.ENDED ||
      ['GENERATING', 'FAILED'].includes(snapshot.generationStatus)
    ) {
      await router.replace({ name: 'live-trial', params: { trialId: route.params.trialId } })
      return
    }
    const result = await getResults(route.params.trialId)
    trialResult.value = { ...result, aiProvider: result.aiProvider ?? snapshot.aiProvider }
  } catch (error) {
    resultError.value = error?.message || '대중 투표 결과를 불러오지 못했습니다.'
  } finally {
    resultPending.value = false
  }
})

async function shareResult() {
  const shareData = {
    title: `사랑과 전쟁터 판결 ${caseNumber.value}`,
    text: caseTitle.value,
    url: window.location.href,
  }

  try {
    if (navigator.share) {
      await navigator.share(shareData)
    } else {
      await navigator.clipboard.writeText(window.location.href)
    }
    shareCompleted.value = true
  } catch {
    shareCompleted.value = false
  }
}
</script>

<template>
  <div class="result-page">
    <main class="result-shell">
      <section v-if="aiResult" class="result-hero" aria-labelledby="result-title">
        <span class="final-badge"><i aria-hidden="true"></i>최종 판결</span>
        <p>AI 판사의 책임 비율 판단이 확정되었습니다</p>
        <h1 id="result-title">{{ caseNumber }}</h1>
        <p class="case-title">“{{ caseTitle }}”</p>
        <p v-if="providerLabel" class="provider-label">{{ providerLabel }}</p>
        <div v-if="aiResult?.winnerSide" class="winner-badge"><Check :size="16" /> {{ aiResult.winnerSide }}측 승소</div>
        <div v-else-if="aiResult" class="winner-badge winner-badge--neutral">승소 측을 정하지 않은 판단입니다</div>
      </section>

      <p v-if="resultPending" class="result-status" role="status">저장된 AI 판결과 대중 투표 결과를 불러오는 중입니다.</p>
      <p v-else-if="resultError" class="result-status result-status--error" role="alert">{{ resultError }}</p>
      <p v-else-if="!aiResult" class="result-status" role="status">표시할 판결 결과가 없습니다.</p>

      <template v-else>
        <div class="judgment-grid">
          <FaultRatioCard :ratio="aiResult" />
          <JudgmentGrounds :grounds="aiResult.grounds" :judgment="aiResult.judgment" />
        </div>

        <VerdictComparison
          v-if="juryResult"
          :ai-result="aiResult"
          :jury-result="juryResult"
        />
        <p v-else class="result-status" role="status">대중 투표 결과가 없습니다.</p>
      </template>

      <div class="result-actions">
        <button class="share-button" type="button" @click="shareResult">
          <Check v-if="shareCompleted" :size="17" />
          <Share2 v-else :size="17" />
          {{ shareCompleted ? '링크가 복사되었습니다' : '판결문 공유하기' }}
        </button>
        <RouterLink
          class="list-button"
          :to="{ name: 'live-trial', params: { trialId: $route.params.trialId } }"
        >
          <ArrowLeft :size="17" /> Live 재판으로 돌아가기
        </RouterLink>
      </div>

      <p class="legal-notice">
        본 결과는 갈등 해결을 돕기 위한 AI 의견이며 실제 법률 판결이나 법률 상담이 아닙니다.
      </p>
    </main>

  </div>
</template>

<style scoped>
.result-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at 50% 10%, rgb(219 232 255 / 48%), transparent 28%),
    var(--ds-color-page-background);
}

.result-shell {
  width: min(calc(100% - 32px), var(--ds-container-max));
  margin: 0 auto;
  padding: 40px 0 52px;
}

.result-hero {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

.final-badge,
.winner-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  border-radius: var(--ds-radius-full);
  font-size: 0.95rem;
  font-weight: 700;
}

.final-badge {
  min-height: 25px;
  padding: 0 13px;
  background: #c92532;
  color: white;
}

.final-badge i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: white;
}

.result-hero > p:first-of-type {
  margin: 14px 0 4px;
  color: #788395;
  font-size: 0.95rem;
}

.result-hero h1 {
  margin: 0;
  color: var(--ds-color-primary);
  font-size: clamp(2.25rem, 4vw, 3rem);
  letter-spacing: 0.02em;
}

.case-title {
  margin: 8px 0 0;
  color: var(--ds-color-on-surface-variant);
  font-size: 1.15rem;
}

.provider-label {
  margin: 10px 0 0;
  padding: 4px 10px;
  border-radius: var(--ds-radius-full);
  background: #eef2f7;
  color: #52627a;
  font-size: 0.86rem;
  font-weight: 700;
}

.winner-badge {
  margin-top: 14px;
  min-height: 30px;
  padding: 0 14px;
  background: #e8f7ed;
  color: #207443;
}

.winner-badge--neutral {
  background: #eef2f7;
  color: #52627a;
}

.judgment-grid {
  margin-top: 38px;
  display: grid;
  grid-template-columns: 1fr 1.18fr;
  gap: 22px;
}

.result-shell > :deep(.comparison-card) {
  margin-top: 24px;
}

.result-actions {
  margin-top: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.result-status {
  margin: 24px 0 0;
  padding: 18px;
  border-radius: var(--ds-radius-default);
  background: white;
  color: var(--ds-color-on-surface-variant);
  text-align: center;
}

.result-status--error {
  border: 1px solid var(--ds-color-error);
  background: var(--ds-color-error-container);
  color: var(--ds-color-on-error-container);
}

.share-button,
.list-button {
  min-width: 180px;
  min-height: 46px;
  padding: 0 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--ds-radius-default);
  font-size: 1rem;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}

.share-button {
  border: 1px solid var(--ds-color-justice-blue);
  background: white;
  color: var(--ds-color-justice-blue);
}

.list-button {
  border: 1px solid var(--ds-color-justice-blue);
  background: var(--ds-color-justice-blue);
  color: white;
}

.share-button:hover,
.list-button:hover {
  box-shadow: 0 7px 18px rgb(26 54 93 / 14%);
  transform: translateY(-1px);
}

.share-button:focus-visible,
.list-button:focus-visible {
  outline: 3px solid rgb(37 99 235 / 22%);
  outline-offset: 2px;
}

.legal-notice {
  margin: 18px 0 0;
  color: #8b95a4;
  font-size: 0.9rem;
  text-align: center;
}

@media (max-width: 900px) {
  .judgment-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .result-shell {
    width: min(calc(100% - 24px), var(--ds-container-max));
    padding-top: 28px;
  }

  .judgment-grid {
    margin-top: 28px;
  }

  .result-actions {
    flex-direction: column;
  }

  .share-button,
  .list-button {
    width: 100%;
  }
}
</style>
