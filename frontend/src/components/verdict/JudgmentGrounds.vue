<script setup>
import { Gavel } from '@lucide/vue'

defineProps({
  grounds: { type: Array, required: true },
  judgment: { type: String, required: true },
})
</script>

<template>
  <section class="grounds-card" aria-labelledby="grounds-title">
    <header>
      <span class="icon"><Gavel :size="20" /></span>
      <div>
        <span>AI 판사의 최종 판단</span>
        <h2 id="grounds-title">판결 근거 <small>(Grounds for Judgment)</small></h2>
      </div>
    </header>

    <ol>
      <li v-for="(ground, index) in grounds" :key="`${index}-${ground.description}`">
        <p>{{ ground.description }}</p>
      </li>
    </ol>

    <div class="judgment-box">
      <span>최종 주문</span>
      <h3>주문 <small>(Judgment)</small></h3>
      <p>{{ judgment }}</p>
    </div>
  </section>
</template>

<style scoped>
.grounds-card {
  min-height: 342px;
  padding: 28px 30px 24px;
  border: 1px solid #edf1f7;
  border-radius: var(--ds-radius-lg);
  background: white;
  box-shadow: 0 14px 36px rgb(26 54 93 / 7%);
}

header {
  padding-bottom: 13px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--ds-color-card-border);
}

.icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex: 0 0 auto;
  border-radius: 11px;
  background: #eaf2ff;
  color: var(--ds-color-justice-blue);
}

header > div > span {
  color: var(--ds-color-justice-blue);
  font-size: 0.9rem;
  font-weight: 700;
}

h2 {
  margin: 1px 0 0;
  color: var(--ds-color-primary);
  font-size: 1.4rem;
}

h2 small,
h3 small {
  font-size: 0.86em;
  font-weight: inherit;
}

ol {
  margin: 19px 0 18px;
  padding-left: 22px;
}

li {
  padding-left: 4px;
  color: var(--ds-color-justice-blue);
  font-size: 1rem;
  font-weight: 700;
}

li + li {
  margin-top: 14px;
}

li div {
  display: flex;
  align-items: baseline;
  gap: 7px;
}

li strong {
  color: var(--ds-color-primary);
  font-size: 1.1rem;
}

li div span {
  color: var(--ds-color-on-surface-variant);
  font-size: 0.95rem;
  font-weight: 500;
}

li p {
  margin: 5px 0 0;
  color: var(--ds-color-on-surface-variant);
  font-size: 1.05rem;
  font-weight: 400;
  line-height: 1.75;
}

.judgment-box {
  position: relative;
  padding: 16px 18px;
  border-left: 4px solid var(--ds-color-justice-blue);
  border-radius: var(--ds-radius-default);
  background: #edf4ff;
}

.judgment-box > span {
  position: absolute;
  top: 12px;
  right: 14px;
  color: #6680a1;
  font-size: 0.85rem;
  font-weight: 700;
}

h3 {
  margin: 0 0 5px;
  color: var(--ds-color-primary);
  font-size: 1.15rem;
}

.judgment-box p {
  margin: 0;
  padding-right: 40px;
  color: var(--ds-color-on-surface-variant);
  font-size: 1.05rem;
  line-height: 1.75;
}

@media (max-width: 620px) {
  .grounds-card {
    min-height: auto;
    padding: 22px 18px;
  }

  .judgment-box p {
    padding-right: 0;
  }

  .judgment-box > span {
    display: none;
  }
}
</style>
