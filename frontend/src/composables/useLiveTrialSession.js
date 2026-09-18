import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { getTrial } from '@/apis/trialApi.js'
import { useLiveTrialRealtime } from '@/composables/useLiveTrialRealtime.js'
import { CONNECTION_STATUS } from '@/consts/liveTrialUiStatus.js'
import { recoverLiveTrial, recoverLiveTrialChat } from '@/lib/realtime.js'
import { getDemoUserId } from '@/composables/useDemoUser.js'
import {
  getLastContiguousMessageSequence,
  mergeMessages,
} from '@/utils/messageMerge.js'
import {
  applyEventsToSnapshot,
  getLastContiguousEventSequence,
  mergeTrialEvents,
} from '@/utils/trialEvent.js'

export function useLiveTrialSession(trialId, options = {}) {
  const detail = ref(null)
  const snapshot = ref(null)
  const events = ref([])
  const messages = ref([])
  const restoring = ref(true)
  const chatRestoring = ref(true)
  const chatSending = ref(false)
  const chatError = ref(null)
  const hasRecovered = ref(false)
  const demoUserId = getDemoUserId()
  let pendingChatContent = null
  let generationFallbackTimer = null

  function appendEvent(event) {
    events.value = mergeTrialEvents(events.value, [event])
    options.onEvent?.(event)
  }

  function appendMessage(message) {
    messages.value = mergeMessages(messages.value, [message])

    if (
      chatSending.value &&
      message?.sender?.demoUserId === demoUserId &&
      message?.content?.trim() === pendingChatContent
    ) {
      chatSending.value = false
      pendingChatContent = null
    }

    options.onMessage?.(message)
  }

  function handleRealtimeError(error) {
    chatSending.value = false
    pendingChatContent = null
    chatError.value = error
    options.onError?.(error)
  }

  async function recoverConnection({ trialId: currentTrialId }) {
    restoring.value = true
    chatRestoring.value = true
    chatSending.value = false
    pendingChatContent = null
    const afterEventSequence = getLastContiguousEventSequence(events.value)
    const afterMessageSequence = getLastContiguousMessageSequence(messages.value)
    const detailRequest = detail.value
      ? Promise.resolve(detail.value)
      : getTrial(currentTrialId)

    try {
      const [nextDetail] = await Promise.all([
        detailRequest,
        recoverLiveTrial({
          trialId: currentTrialId,
          afterEventSequence,
          onSnapshot: (nextSnapshot) => {
            snapshot.value = nextSnapshot
          },
          onEvent: appendEvent,
        }),
      ])

      detail.value = nextDetail
      const recoveredChat = await recoverLiveTrialChat({
        trialId: currentTrialId,
        afterMessageSequence,
      })
      messages.value = mergeMessages(messages.value, recoveredChat.messages)
      chatError.value = null
      hasRecovered.value = true
    } finally {
      chatRestoring.value = false
      restoring.value = false
    }
  }

  async function handleConnected(context) {
    await recoverConnection(context)
    await options.onConnected?.(context)
  }

  async function handleReconnect(context) {
    await recoverConnection(context)
    await options.onReconnect?.(context)
  }

  const realtime = useLiveTrialRealtime(trialId, {
    ...options,
    onConnected: handleConnected,
    onReconnect: handleReconnect,
    onEvent: appendEvent,
    onMessage: appendMessage,
    onError: handleRealtimeError,
  })

  function sendChat(rawContent) {
    const content = rawContent?.trim() || ''
    if (!content || content.length > 500 || chatSending.value) return false

    chatError.value = null
    const sent = realtime.sendChat(content)
    if (!sent) return false

    pendingChatContent = content
    chatSending.value = true
    return true
  }

  const currentSnapshot = computed(() => applyEventsToSnapshot(snapshot.value, events.value))
  const status = computed(() => currentSnapshot.value?.status ?? null)
  const connection = computed(() => {
    if (restoring.value && realtime.connection.value.status === CONNECTION_STATUS.CONNECTED) {
      return {
        status: hasRecovered.value
          ? CONNECTION_STATUS.RECONNECTING
          : CONNECTION_STATUS.CONNECTING,
        error: null,
      }
    }

    return realtime.connection.value
  })

  async function refreshGenerationFallback() {
    if (connection.value.status === CONNECTION_STATUS.CONNECTED) return
    if (currentSnapshot.value?.generationStatus !== 'GENERATING') return

    try {
      await recoverLiveTrial({
        trialId: unrefTrialId(),
        afterEventSequence: getLastContiguousEventSequence(events.value),
        onSnapshot: (nextSnapshot) => {
          snapshot.value = nextSnapshot
        },
        onEvent: appendEvent,
      })
    } catch (error) {
      options.onError?.(error)
    }
  }

  function unrefTrialId() {
    return typeof trialId === 'object' && 'value' in trialId
      ? trialId.value
      : trialId
  }

  function stopGenerationFallback() {
    if (generationFallbackTimer === null) return
    window.clearInterval(generationFallbackTimer)
    generationFallbackTimer = null
  }

  function syncGenerationFallback() {
    const shouldPoll =
      currentSnapshot.value?.generationStatus === 'GENERATING' &&
      connection.value.status !== CONNECTION_STATUS.CONNECTED
    if (!shouldPoll) {
      stopGenerationFallback()
      return
    }
    if (generationFallbackTimer !== null) return
    generationFallbackTimer = window.setInterval(refreshGenerationFallback, 5_000)
    refreshGenerationFallback()
  }

  watch(
    [() => currentSnapshot.value?.generationStatus, () => connection.value.status],
    syncGenerationFallback,
    { immediate: true },
  )
  onBeforeUnmount(stopGenerationFallback)

  return {
    demoUserId,
    detail,
    snapshot,
    events,
    messages,
    currentSnapshot,
    status,
    restoring,
    chatRestoring,
    chatSending,
    chatError,
    ...realtime,
    sendChat,
    connection,
  }
}
