import { TRIAL_EVENT_TYPE } from "@/consts/trialEventType.js";
import { TRIAL_STATUS } from "@/consts/trialStatus.js";
import { getNextGenerationState, shouldApplyEventToSnapshot } from './trialGenerationState.js';

function parsePayload(payload) {
  if (!payload) return {};
  if (typeof payload === "object") return payload;

  try {
    return JSON.parse(payload);
  } catch {
    return {};
  }
}

function inferSpeaker(type) {
  switch (type) {
    case TRIAL_EVENT_TYPE.JUDGE_INTRODUCTION:
    case TRIAL_EVENT_TYPE.JUDGE_PHASE_NOTICE:
    case TRIAL_EVENT_TYPE.VERDICT_ANNOUNCED:
    case TRIAL_EVENT_TYPE.VERDICT_PUBLISHED:
      return "JUDGE";
    case TRIAL_EVENT_TYPE.A_ARGUMENT:
    case TRIAL_EVENT_TYPE.A_DEBATE:
      return "A_LAWYER";
    case TRIAL_EVENT_TYPE.B_ARGUMENT:
    case TRIAL_EVENT_TYPE.B_DEBATE:
      return "B_LAWYER";
    default:
      return "SYSTEM";
  }
}

export function normalizeTrialEvent(event = {}) {
  const sequence = Number(event.sequence ?? event.sequenceNo);
  const type = event.type || event.eventType || "";
  const payload = parsePayload(event.payload);

  return {
    eventId: event.eventId ?? payload.eventId ?? null,
    trialId: event.trialId ?? payload.trialId ?? null,
    sequence: Number.isFinite(sequence) ? sequence : null,
    type,
    speaker: event.speaker || payload.speaker || inferSpeaker(type),
    content: event.content || payload.content || "",
    occurredAt: event.occurredAt || event.createdAt || null,
    payload,
  };
}

function eventKey(event) {
  if (event.sequence !== null) return `sequence:${event.sequence}`;
  if (event.type === TRIAL_EVENT_TYPE.PRESENCE_UPDATED) {
    return `presence:${event.trialId ?? event.payload?.trialId ?? "global"}`;
  }
  if (event.eventId !== null) return `id:${event.eventId}`;
  return null;
}

function mergeEvent(previous, next) {
  return {
    ...previous,
    ...next,
    eventId: next.eventId ?? previous.eventId,
    trialId: next.trialId ?? previous.trialId,
    speaker: next.speaker || previous.speaker,
    content: next.content || previous.content,
    occurredAt: next.occurredAt || previous.occurredAt,
    payload: { ...previous.payload, ...next.payload },
  };
}

export function mergeTrialEvents(currentEvents = [], incomingEvents = []) {
  const merged = new Map();

  for (const rawEvent of [...currentEvents, ...incomingEvents]) {
    const event = normalizeTrialEvent(rawEvent);
    const key = eventKey(event);
    if (!key) continue;

    const previous = merged.get(key);
    merged.set(key, previous ? mergeEvent(previous, event) : event);
  }

  return [...merged.values()].sort((left, right) => {
    if (left.sequence === null) return 1;
    if (right.sequence === null) return -1;
    return left.sequence - right.sequence;
  });
}

export function getLastContiguousEventSequence(events = []) {
  let cursor = 0;

  for (const event of [...events].sort(
    (left, right) => left.sequence - right.sequence,
  )) {
    const sequence = Number(event.sequence) || 0;
    if (sequence <= cursor) continue;
    if (sequence !== cursor + 1) break;
    cursor = sequence;
  }

  return cursor;
}

export function applyEventsToSnapshot(snapshot, events = []) {
  if (!snapshot) return null;

  const snapshotSequence = Number(snapshot.latestEventSequence) || 0;
  return events
    .filter((event) => {
      return shouldApplyEventToSnapshot(event, { latestEventSequence: snapshotSequence });
    })
    .reduce((current, event) => {
      const audienceCountFromEvent =
        event.payload?.audienceCount ?? event.payload?.viewerCount ?? null;
      if (
        audienceCountFromEvent !== null &&
        audienceCountFromEvent !== undefined
      ) {
        const audienceCount = Number(audienceCountFromEvent);
        current = {
          ...current,
          audienceCount: Number.isFinite(audienceCount)
            ? audienceCount
            : audienceCountFromEvent,
        };
      }
      const votingOpened =
        event.type === TRIAL_EVENT_TYPE.VOTING_OPENED ||
        event.type === TRIAL_EVENT_TYPE.VOTING_STARTED;
      const status = votingOpened
        ? TRIAL_STATUS.VOTING
        : event.payload.status || current.status;
      const generation = getNextGenerationState(current, event);
      const phaseEndsAt =
        event.payload.phaseEndsAt ??
        event.payload.voteEndsAt ??
        (status === TRIAL_STATUS.ENDED ? null : current.phaseEndsAt);

      return {
        ...current,
        status,
        phaseStartedAt:
          event.payload.phaseStartedAt ||
          event.payload.voteStartedAt ||
          current.phaseStartedAt,
        phaseEndsAt,
        scheduledEndAt: event.payload.scheduledEndAt || current.scheduledEndAt,
        latestEventSequence: Math.max(
          Number(current.latestEventSequence) || 0,
          Number(event.sequence) || 0,
        ),
        voteOpen: votingOpened || status === TRIAL_STATUS.VOTING,
        ended: status === TRIAL_STATUS.ENDED,
        ...generation,
      };
    }, snapshot);
}

const SPEAKER_LABEL = Object.freeze({
  JUDGE: "AI 판사",
  A_LAWYER: "A측 AI 변호사",
  B_LAWYER: "B측 AI 변호사",
  SYSTEM: "재판 시스템",
});

const SPEAKER_KEY_BY_PREFIX = Object.freeze({
  JUDGE: "JUDGE",
  A_LAWYER: "A_LAWYER",
  B_LAWYER: "B_LAWYER",
  SYSTEM: "SYSTEM",
  A: "A_LAWYER",
  B: "B_LAWYER",
  J: "JUDGE",
});

export function normalizeSpeakerKey(rawSpeaker = "") {
  if (!rawSpeaker) return "";

  const token = String(rawSpeaker).trim().toUpperCase();
  if (!token) return "";
  if (Object.prototype.hasOwnProperty.call(SPEAKER_KEY_BY_PREFIX, token)) {
    return SPEAKER_KEY_BY_PREFIX[token];
  }

  if (token.includes("A_")) return "A_LAWYER";
  if (token.includes("B_")) return "B_LAWYER";
  if (token.includes("JUDGE")) return "JUDGE";
  if (token === "SYSTEM") return "SYSTEM";

  return token;
}

const EVENT_LABEL = Object.freeze({
  [TRIAL_EVENT_TYPE.JUDGE_INTRODUCTION]: "사건 소개",
  [TRIAL_EVENT_TYPE.JUDGE_PHASE_NOTICE]: "진행 안내",
  [TRIAL_EVENT_TYPE.A_ARGUMENT]: "A측 발언",
  [TRIAL_EVENT_TYPE.B_ARGUMENT]: "B측 발언",
  [TRIAL_EVENT_TYPE.A_DEBATE]: "A측 반론",
  [TRIAL_EVENT_TYPE.B_DEBATE]: "B측 반론",
  [TRIAL_EVENT_TYPE.VERDICT_ANNOUNCED]: "최종 판결",
  [TRIAL_EVENT_TYPE.VERDICT_PUBLISHED]: "최종 판결",
});

const SPEECH_EVENT_TYPES = new Set(Object.keys(EVENT_LABEL));

export function toLawyerDebateEvents(events = []) {
  return events
    .filter((event) =>
      [TRIAL_EVENT_TYPE.A_DEBATE, TRIAL_EVENT_TYPE.B_DEBATE].includes(
        event.type,
      ),
    )
    .map((event) => ({
      id: event.eventId ?? event.sequence,
      side: event.type === TRIAL_EVENT_TYPE.A_DEBATE ? "A" : "B",
      speaker:
        SPEAKER_LABEL[normalizeSpeakerKey(event.speaker)] ||
        event.speaker ||
        "AI 변호사",
      content: event.content || "변론 내용을 불러오는 중입니다.",
      occurredAt: event.occurredAt,
    }));
}

const TRIAL_CONVERSATION_EVENT_TYPES = new Set([
  TRIAL_EVENT_TYPE.JUDGE_INTRODUCTION,
  TRIAL_EVENT_TYPE.JUDGE_PHASE_NOTICE,
  TRIAL_EVENT_TYPE.A_ARGUMENT,
  TRIAL_EVENT_TYPE.B_ARGUMENT,
  TRIAL_EVENT_TYPE.A_DEBATE,
  TRIAL_EVENT_TYPE.B_DEBATE,
]);

export function toTrialConversationEvents(events = []) {
  return events
    .filter((event) => TRIAL_CONVERSATION_EVENT_TYPES.has(event.type))
    .map((event) => {
      const speakerKey = normalizeSpeakerKey(
        event.speaker || inferSpeaker(event.type),
      );
      const side =
        speakerKey === "A_LAWYER"
          ? "A"
          : speakerKey === "B_LAWYER"
            ? "B"
            : "JUDGE";

      return {
        id: event.eventId ?? event.sequence,
        sequence: event.sequence,
        side,
        speaker: SPEAKER_LABEL[speakerKey] || event.speaker || "AI 재판",
        label: EVENT_LABEL[event.type] || event.type,
        content: event.content || "발언 내용을 불러오는 중입니다.",
        occurredAt: event.occurredAt,
      };
    });
}

export function toTimelineEvents(events = []) {
  return events
    .filter((event) => SPEECH_EVENT_TYPES.has(event.type))
    .map((event) => ({
      id: event.eventId ?? event.sequence,
      sequence: event.sequence,
      speaker:
        SPEAKER_LABEL[normalizeSpeakerKey(event.speaker)] ||
        event.speaker ||
        "AI 재판",
      label: EVENT_LABEL[event.type] || event.type,
      content:
        event.content ||
        "이 발언의 상세 내용은 서버 응답에 포함되지 않았습니다.",
      occurredAt: event.occurredAt,
    }));
}
