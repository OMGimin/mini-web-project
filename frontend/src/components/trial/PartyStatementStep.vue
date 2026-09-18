<script setup>
import { computed, nextTick, ref, useTemplateRef } from 'vue'
import { Bot, CheckCircle2, FileText, Send, Sparkles, UserRound } from '@lucide/vue'
import { createUuid } from '@/utils/createUuid.js'

const props = defineProps({
  side: { type: String, required: true, validator: (value) => ['A', 'B'].includes(value) },
  party: { type: Object, required: true },
  otherParty: { type: Object, default: null },
})

const emit = defineEmits(['update:party', 'back', 'prepare', 'confirm'])
const chatInput = ref('')
const chatLog = useTemplateRef('chatLog')
const statementFields = [
  { key: 'incidentTime', question: '사건이 언제 발생했는지 알려주세요.' },
  { key: 'situation', question: '당시 어떤 상황이었는지 구체적으로 설명해주세요.' },
  { key: 'counterpartAction', question: '그 상황에서 상대방은 어떻게 행동했나요?' },
  { key: 'ownAction', question: '그때 본인은 어떻게 행동했나요?' },
  { key: 'afterConversation', question: '사건 이후 두 분은 어떤 대화를 나눴나요?' },
  { key: 'desiredResolution', question: '이번 재판을 통해 원하는 해결 방향은 무엇인가요?' },
]

const userMessages = computed(() => props.party.messages.filter((message) => message.role === 'USER'))
const statementMessages = computed(() => userMessages.value.slice(0, statementFields.length))
const statementComplete = computed(() => statementMessages.value.length === statementFields.length)
const inputDisabled = computed(() =>
  props.party.pending ||
  props.party.draftGenerated ||
  statementComplete.value,
)
const actionDisabled = computed(() =>
  props.party.pending ||
  props.party.draftGenerated ||
  !statementComplete.value,
)
const actionLabel = computed(() => {
  if (props.party.pending) return '처리 중...'
  return props.party.draftGenerated ? '변론문 생성 완료' : '진술서 작성 완료'
})
const providerLabel = computed(() => {
  if (props.party.aiProvider === 'mock') return '모의 응답'
  if (props.party.aiProvider === 'langchain') return 'LangChain 연결 모드'
  return 'AI 변호사'
})

function updateParty(patch) {
  emit('update:party', { ...props.party, ...patch })
}

async function scrollChatToBottom() {
  await nextTick()
  if (chatLog.value) chatLog.value.scrollTop = chatLog.value.scrollHeight
}

function sendMessage() {
  const content = chatInput.value.trim()
  if (!content || inputDisabled.value) return

  const messages = [...props.party.messages, { id: createUuid(), role: 'USER', content }]
  let nextQuestion

  const answeredCount = messages.filter((message) => message.role === 'USER').length
  nextQuestion = statementFields[answeredCount]?.question

  messages.push({
    id: createUuid(),
    role: 'ASSISTANT',
    content:
      nextQuestion ??
      '진술 내용을 모두 확인했습니다. 아래 버튼을 눌러 변론문 초안을 작성해드릴게요.',
  })

  chatInput.value = ''
  updateParty({ messages, draftGenerated: false })
  scrollChatToBottom()
}

function advancePreparation() {
  if (actionDisabled.value) return

  const statement = Object.fromEntries(
    statementFields.map((field, index) => [field.key, statementMessages.value[index].content]),
  )
  emit('prepare', statement)
}
</script>

<template>
  <div class="grid gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
    <section class="grid gap-4">
      <div v-if="otherParty" class="rounded-xl border border-border bg-card p-4">
        <h2 class="mb-2 flex items-center gap-2 font-heading font-semibold">
          <FileText class="size-4 text-primary" /> A측 주요 진술 요약
        </h2>
        <p class="rounded-lg bg-muted p-3 text-sm text-muted-foreground">{{ otherParty.argumentText }}</p>
      </div>

      <div class="flex min-h-[36rem] flex-col rounded-xl border border-border bg-card p-5 shadow-interactive">
        <div class="mb-4 flex items-center gap-3 border-b border-border pb-4">
          <span class="grid size-10 place-items-center rounded-full bg-[var(--ds-color-primary-fixed)] text-primary"><Bot class="size-5" /></span>
          <div>
            <h2 class="font-heading font-semibold">{{ side }}측 AI 변호사</h2>
            <p class="text-xs text-muted-foreground">대화를 통해 진술을 정리해드려요</p>
          </div>
        </div>

        <div ref="chatLog" class="flex max-h-[27rem] flex-1 flex-col gap-4 overflow-y-auto pr-1" aria-live="polite">
          <div
            v-for="message in party.messages"
            :key="message.id"
            class="flex items-end gap-2"
            :class="message.role === 'USER' ? 'justify-end' : 'justify-start'"
          >
            <span v-if="message.role === 'ASSISTANT'" class="grid size-8 shrink-0 place-items-center rounded-full bg-muted text-primary">
              <Bot class="size-4" />
            </span>
            <p
              class="max-w-[82%] rounded-xl px-4 py-3 text-sm leading-6"
              :class="message.role === 'USER' ? 'rounded-br-sm bg-primary text-primary-foreground' : 'rounded-bl-sm bg-muted'"
            >
              {{ message.content }}
            </p>
            <span v-if="message.role === 'USER'" class="grid size-8 shrink-0 place-items-center rounded-full bg-primary/10 text-primary">
              <UserRound class="size-4" />
            </span>
          </div>
        </div>

        <form class="mt-4 flex gap-2 border-t border-border pt-4" @submit.prevent="sendMessage">
          <input
            v-model="chatInput"
            class="min-w-0 flex-1 rounded-lg border border-input bg-muted px-4 py-2.5 outline-none focus:ring-2 focus:ring-ring disabled:cursor-not-allowed"
            :disabled="inputDisabled"
            :placeholder="inputDisabled ? '아래 버튼을 눌러 다음 단계로 진행해주세요.' : '상황을 설명해주세요...'"
          />
          <button
            class="rounded-lg bg-primary px-5 text-primary-foreground disabled:cursor-not-allowed disabled:opacity-40"
            :disabled="!chatInput.trim() || inputDisabled"
            type="submit"
            aria-label="메시지 전송"
          >
            <Send class="size-4" />
          </button>
        </form>

        <button
          class="mt-3 flex w-full items-center justify-center gap-2 rounded-lg border border-primary px-4 py-2.5 font-semibold text-primary disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="actionDisabled"
          type="button"
          @click="advancePreparation"
        >
          <Sparkles class="size-4" /> {{ actionLabel }}
        </button>

        <p v-if="party.error" class="mt-3 text-sm text-destructive" role="alert">
          {{ party.error }}
        </p>
      </div>
    </section>

    <section class="rounded-xl border border-border bg-card p-5 shadow-interactive">
      <h2 class="border-b border-border pb-3 font-heading text-xl font-semibold">변론문 초안 미리보기</h2>
      <div v-if="party.draftGenerated" class="mt-4 min-h-[31rem] rounded-lg bg-[var(--ds-color-primary-fixed)] p-5">
        <h3 class="mb-2 font-heading font-semibold">사건 개요 및 쟁점 파악</h3>
        <p class="mb-6 text-sm leading-6 text-muted-foreground">{{ party.caseOverview }}</p>

        <h3 class="mb-2 font-heading font-semibold">핵심 진술 요지</h3>
        <ul class="mb-6 list-disc space-y-2 pl-5 text-sm leading-6 text-muted-foreground">
          <li v-for="point in party.keyPoints" :key="point">{{ point }}</li>
        </ul>

        <h3 class="mb-2 font-heading font-semibold">{{ side }}측 변론문</h3>
        <textarea
          :value="party.argumentText"
          class="min-h-40 w-full resize-none rounded-lg border border-primary/20 bg-card/60 p-3 text-sm leading-6 outline-none focus:ring-2 focus:ring-ring"
          aria-label="변론문 수정"
          @input="updateParty({ argumentText: $event.target.value })"
        />
      </div>
      <div v-else class="mt-4 grid min-h-[31rem] place-items-center rounded-lg border border-dashed border-border bg-muted p-8 text-center text-sm text-muted-foreground">
        <div>
          <Sparkles class="mx-auto mb-3 size-7 text-primary" />
          AI 변호사와 대화를 마친 뒤<br />‘진술서 작성 완료’를 눌러주세요.
        </div>
      </div>
      <p class="mt-3 text-center text-xs text-muted-foreground">대화 완료 후 {{ providerLabel }}가 사건 개요와 핵심 진술을 요약합니다.</p>
    </section>

    <div class="flex justify-between border-t border-border pt-4 lg:col-span-2">
      <button class="rounded-lg border border-border bg-card px-5 py-2.5 disabled:cursor-not-allowed disabled:opacity-40" type="button" :disabled="party.pending" @click="$emit('back')">이전</button>
      <button
        class="flex items-center gap-2 rounded-lg bg-primary px-5 py-2.5 font-semibold text-primary-foreground disabled:cursor-not-allowed disabled:opacity-40"
        :disabled="party.pending || !party.draftGenerated || !party.caseOverview.trim() || !party.argumentText.trim()"
        type="button"
        @click="$emit('confirm')"
      >
        <CheckCircle2 class="size-4" /> {{ party.pending ? '저장 중...' : '진술 확정' }}
      </button>
    </div>
  </div>
</template>
