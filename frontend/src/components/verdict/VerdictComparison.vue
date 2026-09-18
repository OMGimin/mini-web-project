<script setup>
import { computed } from 'vue'
import { Scale, UsersRound } from '@lucide/vue'

const props = defineProps({
  aiResult: { type: Object, required: true },
  juryResult: { type: Object, required: true },
})

const resultsAgree = computed(() => {
  if (!props.aiResult.winnerSide || !props.juryResult.winnerSide) return null
  return props.aiResult.winnerSide === props.juryResult.winnerSide
})
</script>

<template>
  <section class="comparison-card" aria-labelledby="comparison-title">
    <header>
      <div>
        <span>두 판단을 독립적으로 확인하세요</span>
        <h2 id="comparison-title">판결 결과 비교 <small>(AI vs 배심원)</small></h2>
      </div>
      <span class="comparison-badge" :class="{ 'comparison-badge--agree': resultsAgree }">
        {{ resultsAgree === null ? '판단 비교 불가' : resultsAgree ? '판단 일치' : '판단 불일치' }}
      </span>
    </header>

    <div class="comparison-grid">
      <article class="result-column">
        <h3><Scale :size="18" /> AI 판결</h3>
        <div class="ai-winner" role="status">
          <span>AI 책임 비율</span>
          <strong>A {{ aiResult.sideA }}% · B {{ aiResult.sideB }}%</strong>
        </div>
        <p class="ai-note">{{ aiResult.winnerSide ? `${aiResult.winnerSide}측 승소 판단입니다.` : '승소 측을 정하지 않은 AI 판단입니다.' }}</p>
      </article>

      <article class="result-column jury-column">
        <h3><UsersRound :size="18" /> 배심원 투표 결과</h3>
        <div class="result-row">
          <div><span>A측 승소 <small v-if="juryResult.aVotes !== undefined">({{ juryResult.aVotes.toLocaleString('ko-KR') }}표)</small></span><strong>{{ juryResult.sideA }}%</strong></div>
          <div class="track"><span class="jury-side-a" :style="{ width: `${juryResult.sideA}%` }"></span></div>
        </div>
        <div class="result-row">
          <div><span>B측 승소 <small v-if="juryResult.bVotes !== undefined">({{ juryResult.bVotes.toLocaleString('ko-KR') }}표)</small></span><strong>{{ juryResult.sideB }}%</strong></div>
          <div class="track"><span class="jury-side-b" :style="{ width: `${juryResult.sideB}%` }"></span></div>
        </div>
        <p>총 참여 배심원 {{ juryResult.participantCount.toLocaleString('ko-KR') }}명</p>
      </article>
    </div>
  </section>
</template>

<style scoped>
.comparison-card {
  padding: 23px 26px 22px;
  border: 1px solid #edf1f7;
  border-radius: var(--ds-radius-lg);
  background: white;
  box-shadow: 0 14px 36px rgb(26 54 93 / 7%);
}

header {
  padding-bottom: 13px;
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--ds-color-card-border);
}

header > div > span {
  color: var(--ds-color-justice-blue);
  font-size: 0.9rem;
  font-weight: 700;
}

h2 {
  margin: 1px 0 0;
  color: var(--ds-color-primary);
  font-size: 1.35rem;
}

h2 small {
  font-size: 0.82em;
}

.comparison-badge {
  padding: 5px 9px;
  border-radius: var(--ds-radius-full);
  background: #fff1e9;
  color: #b65020;
  font-size: 0.9rem;
  font-weight: 700;
}

.comparison-badge--agree {
  background: #e8f7ed;
  color: #207443;
}

.comparison-grid {
  padding-top: 20px;
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.result-column {
  padding: 0 38px 0 20px;
}

.jury-column {
  padding: 0 20px 0 38px;
  border-left: 1px solid var(--ds-color-card-border);
}

h3 {
  margin: 0 0 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--ds-color-on-surface-variant);
  font-family: var(--ds-font-body);
  font-size: 1.15rem;
}

.result-row + .result-row {
  margin-top: 14px;
}

.result-row > div:first-child {
  margin-bottom: 6px;
  display: flex;
  justify-content: space-between;
  color: var(--ds-color-on-surface-variant);
  font-size: 1rem;
}

.result-row strong {
  color: var(--ds-color-primary);
  font-size: 1.15rem;
}

.result-row:first-of-type strong {
  color: var(--ds-color-justice-blue);
}

.track {
  height: 10px;
  border-radius: var(--ds-radius-full);
  background: #d4e3fa;
  overflow: hidden;
}

.track > span {
  display: block;
  height: 100%;
  border-radius: inherit;
}

.jury-side-a {
  background: var(--ds-color-justice-blue);
}

.jury-side-b {
  background: #8a95a5;
}

.ai-winner {
  min-height: 92px;
  padding: 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: var(--ds-radius-default);
  background: #eef4ff;
  color: var(--ds-color-on-surface-variant);
}

.ai-winner strong {
  color: var(--ds-color-justice-blue);
  font-size: 1.45rem;
}

.ai-note {
  margin: 14px 0 0;
  color: #7e8896;
  font-size: 0.9rem;
  line-height: 1.5;
}

.jury-column > p {
  margin: 15px 0 0;
  color: #7e8896;
  font-size: 0.9rem;
  text-align: right;
}

@media (max-width: 760px) {
  .comparison-grid {
    grid-template-columns: 1fr;
    gap: 26px;
  }

  .result-column,
  .jury-column {
    padding: 0;
  }

  .jury-column {
    padding-top: 24px;
    border-top: 1px solid var(--ds-color-card-border);
    border-left: 0;
  }
}
</style>
