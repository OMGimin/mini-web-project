const TURN_PUBLISHED_EVENT_TYPES = new Set([
  'A_DEBATE',
  'B_DEBATE',
  'VERDICT_ANNOUNCED',
  'VERDICT_PUBLISHED',
])

export function shouldApplyEventToSnapshot(event = {}, snapshot = {}) {
  if (event.type === 'PRESENCE_UPDATED') return true
  return (Number(event.sequence) || 0) > (Number(snapshot.latestEventSequence) || 0)
}

export function getNextGenerationState(current = {}, event = {}) {
  const payload = event.payload || {}
  const common = {
    generationStage: payload.generationStage ?? payload.stage ?? current.generationStage,
    generationTurn: payload.generationTurn ?? payload.turn ?? current.generationTurn,
    totalDebateTurns: payload.totalDebateTurns ?? payload.totalTurns ?? current.totalDebateTurns,
    nextSpeaker: payload.nextSpeaker ?? current.nextSpeaker,
    aiProvider: payload.aiProvider ?? current.aiProvider,
  }

  if (event.type === 'GENERATION_STARTED') {
    return { ...common, generationStatus: 'GENERATING', generationError: null, retryable: false }
  }

  if (event.type === 'GENERATION_FAILED') {
    return {
      ...common,
      generationStatus: 'FAILED',
      generationError: payload.generationError ?? payload.error ?? current.generationError,
      retryable: Boolean(payload.retryable),
    }
  }

  if (TURN_PUBLISHED_EVENT_TYPES.has(event.type)) {
    return { ...common, generationStatus: 'IDLE', generationError: null, retryable: false }
  }

  return {
    ...common,
    generationStatus: payload.generationStatus ?? current.generationStatus,
    generationError: payload.generationError ?? payload.error ?? current.generationError,
    retryable: payload.retryable ?? current.retryable,
  }
}
