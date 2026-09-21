import { http } from '@/lib/http.js'
import { DEFAULT_MESSAGE_PAGE_SIZE } from '@/consts/api.js'
import { unwrapApiResponse } from '@/utils/apiResponse.js'

export async function getTrials(params = {}) {
  const response = await http.get('/trials', { params })
  return unwrapApiResponse(response)
}

export async function getTrial(trialId) {
  const response = await http.get(`/trials/${trialId}`)
  return unwrapApiResponse(response)
}

export async function saveStatement(trialId, side, payload) {
  const response = await http.put(`/trials/${trialId}/parties/${side}/statement`, payload)
  return unwrapApiResponse(response)
}

export async function createGuideQuestions(trialId, side) {
  const response = await http.post(`/trials/${trialId}/parties/${side}/guide-questions`, undefined, { timeout: 90_000 })
  return unwrapApiResponse(response)
}

export async function saveGuideAnswers(trialId, side, payload) {
  const response = await http.put(`/trials/${trialId}/parties/${side}/guide-answers`, payload)
  return unwrapApiResponse(response)
}

export async function createArgumentDraft(trialId, side) {
  const response = await http.post(`/trials/${trialId}/parties/${side}/argument-draft`, undefined, {
    timeout: 90_000,
  })
  return unwrapApiResponse(response)
}

export async function updateArgumentDraft(trialId, side, payload) {
  const response = await http.put(`/trials/${trialId}/parties/${side}/argument-draft`, payload)
  return unwrapApiResponse(response)
}

export async function confirmArgument(trialId, side) {
  const response = await http.post(`/trials/${trialId}/parties/${side}/confirm`)
  return unwrapApiResponse(response)
}

export async function startTrial(trialId) {
  const response = await http.post(`/trials/${trialId}/start`)
  return unwrapApiResponse(response)
}

export async function getSnapshot(trialId) {
  const response = await http.get(`/trials/${trialId}/snapshot`)
  return unwrapApiResponse(response)
}

export async function getEvents(trialId, afterSequence = 0) {
  const response = await http.get(`/trials/${trialId}/events`, { params: { afterSequence } })
  return unwrapApiResponse(response)
}

export async function getMessages(trialId, afterSequence = 0, size = DEFAULT_MESSAGE_PAGE_SIZE) {
  const response = await http.get(`/trials/${trialId}/messages`, { params: { afterSequence, size } })
  return unwrapApiResponse(response)
}

export async function submitVote(trialId, selectedSide) {
  const response = await http.post(`/trials/${trialId}/votes`, { selectedSide })
  return unwrapApiResponse(response)
}

export async function getResults(trialId) {
  const response = await http.get(`/trials/${trialId}/results`)
  return unwrapApiResponse(response)
}

export async function retryAiGeneration(trialId) {
  const response = await http.post(`/trials/${trialId}/ai/retry`)
  return unwrapApiResponse(response)
}
