import test from 'node:test'
import assert from 'node:assert/strict'
import { getNextGenerationState, shouldApplyEventToSnapshot } from '../src/utils/trialGenerationState.js'

test('스냅샷보다 오래된 순차 이벤트는 다시 적용하지 않는다', () => {
  const snapshot = { latestEventSequence: 8 }
  assert.equal(shouldApplyEventToSnapshot({ sequence: 8, type: 'A_DEBATE' }, snapshot), false)
  assert.equal(shouldApplyEventToSnapshot({ sequence: 9, type: 'GENERATION_STARTED' }, snapshot), true)
  assert.equal(shouldApplyEventToSnapshot({ sequence: 1, type: 'PRESENCE_UPDATED' }, snapshot), true)
})

test('실패한 생성은 재시도 시작 뒤 발언 저장 시 대기 상태를 해제한다', () => {
  const failed = getNextGenerationState({}, {
    type: 'GENERATION_FAILED',
    payload: { stage: 'DEBATE', turn: 2, retryable: true, error: '안전한 오류 문구' },
  })
  const retrying = getNextGenerationState(failed, {
    type: 'GENERATION_STARTED',
    payload: { stage: 'DEBATE', turn: 2, totalTurns: 4, nextSpeaker: 'B_LAWYER' },
  })
  const published = getNextGenerationState(retrying, {
    type: 'B_DEBATE',
    payload: { turn: 2, totalTurns: 4 },
  })

  assert.equal(failed.generationStatus, 'FAILED')
  assert.equal(failed.retryable, true)
  assert.equal(retrying.generationStatus, 'GENERATING')
  assert.equal(retrying.retryable, false)
  assert.equal(retrying.generationError, null)
  assert.equal(retrying.nextSpeaker, 'B_LAWYER')
  assert.equal(published.generationStatus, 'IDLE')
  assert.equal(published.retryable, false)
  assert.equal(published.generationError, null)
})
