<script setup>
defineProps({
  ratio: {
    type: Object,
    required: true,
  },
})
</script>

<template>
  <section class="fault-card" aria-labelledby="fault-ratio-title">
    <div class="card-copy">
      <span>AI 판결 결과</span>
      <h2 id="fault-ratio-title">AI 책임 비율</h2>
      <p>양측의 진술과 변론 내용을 바탕으로 산정한 AI 판단입니다.</p>
    </div>

    <div
      class="ratio-bar"
      :aria-label="`A측 책임 ${ratio.sideA}퍼센트, B측 책임 ${ratio.sideB}퍼센트`"
    >
      <span class="side-a" :style="{ width: `${ratio.sideA}%` }">
        A측 {{ ratio.sideA }}%
      </span>
      <span class="side-b" :style="{ width: `${ratio.sideB}%` }">
        B측 {{ ratio.sideB }}%
      </span>
    </div>

    <div class="ratio-labels">
      <div>
        <strong>A측</strong>
        <span><b>A측 개선 행동:</b> {{ ratio.aRecommendation }}</span>
      </div>
      <div>
        <strong>B측</strong>
        <span><b>B측 개선 행동:</b> {{ ratio.bRecommendation }}</span>
      </div>
    </div>
  </section>
</template>

<style scoped>
.fault-card {
  min-height: 342px;
  padding: 42px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border: 1px solid #edf1f7;
  border-radius: var(--ds-radius-lg);
  background: white;
  box-shadow: 0 14px 36px rgb(26 54 93 / 7%);
}

.card-copy {
  text-align: center;
}

.card-copy > span {
  color: var(--ds-color-justice-blue);
  font-size: 0.9rem;
  font-weight: 700;
  letter-spacing: 0.06em;
}

h2 {
  margin: 7px 0 5px;
  color: var(--ds-color-primary);
  font-size: 1.45rem;
}

.card-copy p {
  margin: 0;
  color: #7d8796;
  font-size: 1rem;
}

.ratio-bar {
  height: 34px;
  margin-top: 26px;
  display: flex;
  border-radius: var(--ds-radius-full);
  background: #d9e7fb;
  box-shadow: inset 0 1px 2px rgb(0 32 69 / 12%);
  color: white;
  font-size: 1rem;
  font-weight: 700;
  overflow: hidden;
}

.ratio-bar span {
  display: flex;
  align-items: center;
  padding: 0 14px;
  white-space: nowrap;
}

.side-a {
  justify-content: flex-start;
  background: var(--ds-color-justice-blue);
}

.side-b {
  justify-content: flex-end;
  background: var(--ds-color-primary);
}

.ratio-labels {
  margin-top: 18px;
  display: flex;
  justify-content: space-between;
  gap: 24px;
}

.ratio-labels div {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ratio-labels div:last-child {
  text-align: right;
}

.ratio-labels strong {
  color: var(--ds-color-justice-blue);
  font-size: 1rem;
}

.ratio-labels div:last-child strong {
  color: var(--ds-color-primary);
}

.ratio-labels span {
  color: var(--ds-color-on-surface-variant);
  font-size: 1.05rem;
}

@media (max-width: 620px) {
  .fault-card {
    min-height: auto;
    padding: 30px 20px;
  }

  .ratio-bar {
    font-size: 0.85rem;
  }

  .ratio-bar span {
    padding: 0 8px;
  }
}
</style>
