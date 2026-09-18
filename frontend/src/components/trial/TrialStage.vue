<script setup>
import { MessageCircle, Scale } from '@lucide/vue'

defineProps({
  participants: {
    type: Array,
    required: true,
  },
  activeSpeaker: { type: String, default: '' },
  speakingLabel: { type: String, default: '' },
})
</script>

<template>
  <section class="trial-stage" aria-label="재판 참여 AI">
    <article
      v-for="participant in participants"
      :key="participant.id"
      class="participant"
    :class="[
        `participant-${participant.position}`,
        `tone-${participant.tone}`,
        { 'participant--speaking': activeSpeaker === participant.speakerKey },
      ]"
    >
      <div v-if="activeSpeaker === participant.speakerKey" class="speaking-bubble" role="status">
        <MessageCircle :size="15" /> {{ speakingLabel || '발언 중' }}
      </div>
      <div
        class="portrait"
        :class="`portrait-${participant.position}`"
        role="img"
        :aria-label="participant.name"
      >
        <img
          v-if="participant.avatarUrl"
          class="portrait-image"
          :src="participant.avatarUrl"
          :alt="`${participant.name} 프로필`"
        />
        <span v-else>{{ participant.avatar }}</span>
      </div>
      <div class="participant-label">
        <Scale v-if="participant.position === 'center'" :size="16" />
        <span>{{ participant.name }}</span>
        <small v-if="participant.position !== 'center'">({{ participant.role }})</small>
      </div>
    </article>
  </section>
</template>

<style scoped>
.trial-stage {
  min-height: 286px;
  padding: 24px 44px 22px;
  display: grid;
  grid-template-columns: 1fr 1.15fr 1fr;
  align-items: end;
  gap: 28px;
  border: 1px solid var(--ds-color-outline-variant);
  border-radius: var(--ds-radius-md);
  background:
    radial-gradient(circle at 50% 12%, rgb(255 255 255 / 72%), transparent 25%),
    linear-gradient(135deg, #d7e6fb 0%, #eff5ff 54%, #eef4ff 100%);
  overflow: hidden;
}

.participant {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
}

.participant-center {
  align-self: start;
  gap: 16px;
}

.portrait {
  width: 120px;
  aspect-ratio: 1;
  border: 4px solid white;
  border-radius: 50%;
  background-color: #cbdcf3;
  display: grid;
  place-items: center;
  font-size: 4.2rem;
  line-height: 1;
  box-shadow: 0 12px 30px rgb(26 54 93 / 18%);
}

.portrait-image {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.portrait-left .portrait-image {
  object-position: left center;
}

.portrait-center .portrait-image {
  object-position: center;
}

.portrait-right .portrait-image {
  object-position: right center;
}

.portrait-left {
  background: linear-gradient(145deg, #fff1cc, #f6c86d);
}

.portrait-center {
  width: 174px;
  border-color: var(--ds-color-primary);
  background: linear-gradient(145deg, #e7edff, #aebce8);
}

.portrait-right {
  background: linear-gradient(145deg, #e2eaff, #9cb5ee);
}

.speaking-bubble {
  position: absolute;
  top: -8px;
  z-index: 2;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 8px 12px;
  border-radius: 999px;
  background: var(--ds-color-primary);
  color: white;
  box-shadow: 0 8px 18px rgb(26 54 93 / 20%);
  font-size: 0.9rem;
  font-weight: 800;
  animation: speaking-pulse 1.4s ease-in-out infinite;
}

.participant {
  position: relative;
}

@keyframes speaking-pulse {
  50% { transform: translateY(-3px); }
}

.participant-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 150px;
  min-height: 32px;
  padding: 0 16px;
  border-radius: var(--ds-radius-full);
  background: white;
  box-shadow: 0 3px 8px rgb(15 35 62 / 16%);
  color: var(--ds-color-primary);
  font-size: 1rem;
  font-weight: 700;
}

.participant-label small {
  font-size: inherit;
}

.tone-navy .participant-label {
  background: var(--ds-color-primary);
  color: white;
}

.tone-blue .participant-label {
  background: var(--ds-color-justice-blue);
  color: white;
}

@media (max-width: 680px) {
  .trial-stage {
    min-height: auto;
    padding: 28px 16px 22px;
    gap: 12px;
  }

  .portrait {
    width: min(25vw, 100px);
  }

  .portrait-center {
    width: min(31vw, 132px);
  }

  .participant-label {
    min-width: 0;
    width: 100%;
    padding: 7px 8px;
    text-align: center;
    font-size: 0.85rem;
  }

  .participant-label small {
    display: none;
  }
}
</style>
