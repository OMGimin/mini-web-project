<script setup>
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { createTrial, createPost } from "@/apis/postApi.js";
import {
  confirmArgument,
  createArgumentDraft,
  getTrial,
  saveStatement,
  startTrial as startTrialRequest,
  updateArgumentDraft,
} from "@/apis/trialApi.js";
import PartyStatementStep from "@/components/trial/PartyStatementStep.vue";
import TrialBasicInformation from "@/components/trial/TrialBasicInformation.vue";
import TrialFinalConfirmation from "@/components/trial/TrialFinalConfirmation.vue";
import TrialStepIndicator from "@/components/trial/TrialStepIndicator.vue";

const currentStep = ref(1);
const TRIAL_DRAFT_STORAGE_KEY = "love-war:trial-draft";
const route = useRoute();
const router = useRouter();
const startPending = ref(false);
const startError = ref("");
const bothConfirmed = ref(false);
const preparationPending = ref(false);
const preparationError = ref("");

function positiveInteger(value) {
  const normalizedValue = Array.isArray(value) ? value[0] : value;
  const parsedValue = Number(normalizedValue);

  return Number.isInteger(parsedValue) && parsedValue > 0 ? parsedValue : null;
}

const trialId = computed(() => {
  return positiveInteger(route.params.trialId ?? route.query.trialId);
});
const postId = computed(() => positiveInteger(route.query.postId));

const trial = reactive({
  title: "",
  aDisplayName: "박건우",
  bDisplayName: "김지민",
  summary: "",
});

onMounted(async () => {
  const storedDraft = sessionStorage.getItem(TRIAL_DRAFT_STORAGE_KEY);

  if (storedDraft) {
    try {
      const draft = JSON.parse(storedDraft);

      trial.title = draft.title ?? "";
      trial.summary = draft.content ?? "";
    } catch {
      sessionStorage.removeItem(TRIAL_DRAFT_STORAGE_KEY);
    }
  }

  if (!trialId.value) return;

  preparationPending.value = true;
  try {
    const savedTrial = await getTrial(trialId.value);
    trial.title = savedTrial.title;
    trial.summary = savedTrial.content;
    trial.aDisplayName = savedTrial.aParty.displayName;
    trial.bDisplayName = savedTrial.bParty.displayName;
    parties.A.confirmed = Boolean(savedTrial.aParty.ready);
    parties.B.confirmed = Boolean(savedTrial.bParty.ready);
    bothConfirmed.value = parties.A.confirmed && parties.B.confirmed;
  } catch (error) {
    preparationError.value =
      error?.message || "재판 정보를 불러오지 못했습니다.";
  } finally {
    preparationPending.value = false;
  }
});

const parties = reactive({
  A: {
    messages: [
      {
        id: "a-introduction",
        role: "ASSISTANT",
        content:
          "안녕하세요. A측의 입장을 담당한 AI 변호사입니다. 사건이 언제 발생했는지 알려주세요.",
      },
      { id: "a-incidentTime", role: "USER", content: "며칠 전" },
      {
        id: "a-situation",
        role: "USER",
        content: "친구가 남자친구에게 지속적으로 과한 관심을 보였습니다.",
      },
      {
        id: "a-counterpartAction",
        role: "USER",
        content: "남자친구는 별다른 제지 없이 웃어넘겼습니다.",
      },
      {
        id: "a-ownAction",
        role: "USER",
        content: "나는 불편함을 느껴 지적했습니다.",
      },
      {
        id: "a-afterConversation",
        role: "USER",
        content: "남자친구는 대수롭지 않게 넘겼습니다.",
      },
      {
        id: "a-desiredResolution",
        role: "USER",
        content: "남자친구가 명확한 선을 그어주길 바랍니다.",
      },
    ],
    draftGenerated: false,
    caseOverview: "",
    keyPoints: [],
    argumentText: "",
    aiProvider: null,
    confirmed: false,
    confirmedAt: null,
    statementSaved: false,
    guideQuestions: [],
    guideAnswers: [],
    pending: false,
    error: "",
  },
  B: {
    messages: [
      {
        id: "b-introduction",
        role: "ASSISTANT",
        content:
          "안녕하세요. B측의 입장을 담당한 AI 변호사입니다. 사건이 언제 발생했는지 알려주세요.",
      },
      { id: "b-incidentTime", role: "USER", content: "며칠 전" },
      {
        id: "b-situation",
        role: "USER",
        content: "친구는 호감 표현을 가볍게 표현했을 뿐이라는 입장입니다.",
      },
      {
        id: "b-counterpartAction",
        role: "USER",
        content: "친구는 장난스러운 말투로 행동했습니다.",
      },
      {
        id: "b-ownAction",
        role: "USER",
        content: "나는 그 행동이 불편하다고 느꼈습니다.",
      },
      {
        id: "b-afterConversation",
        role: "USER",
        content: "현장에서 큰 갈등은 없었습니다.",
      },
      {
        id: "b-desiredResolution",
        role: "USER",
        content: "오해를 풀고 선을 지켜주길 바랍니다.",
      },
    ],
    draftGenerated: false,
    caseOverview: "",
    keyPoints: [],
    argumentText: "",
    aiProvider: null,
    confirmed: false,
    confirmedAt: null,
    statementSaved: false,
    guideQuestions: [],
    guideAnswers: [],
    pending: false,
    error: "",
  },
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const currentSide = computed(() => (currentStep.value === 2 ? "A" : "B"));

function updateTrial(value) {
  Object.assign(trial, value);
}

function updateParty(value) {
  Object.assign(parties[currentSide.value], value);
}

async function createTrialAndContinue() {
  if (preparationPending.value) return;

  if (trialId.value) {
    goToStep(2);
    return;
  }

  if (!postId.value) {
    preparationError.value =
      "게시글 식별 정보가 없습니다. 게시글 등록부터 다시 진행해주세요.";
    return;
  }

  preparationPending.value = true;
  preparationError.value = "";

  try {
    const createdTrial = await createTrial(postId.value, {
      visibility: "PUBLIC",
      aDisplayName: trial.aDisplayName.trim(),
      bDisplayName: trial.bDisplayName.trim(),
    });

    await router.replace({
      name: "trial-preparation",
      query: { postId: postId.value, trialId: createdTrial.trialId },
    });
    goToStep(2);
  } catch (error) {
    preparationError.value = error?.message || "재판을 생성하지 못했습니다.";
  } finally {
    preparationPending.value = false;
  }
}

async function prepareParty(statement) {
  if (!trialId.value) return;

  const side = currentSide.value;
  const party = parties[side];
  party.pending = true;
  party.error = "";

  try {
    await saveStatement(trialId.value, side, statement);
    const draft = await createArgumentDraft(trialId.value, side);

    Object.assign(party, {
      statementSaved: true,
      draftGenerated: true,
      caseOverview: draft.factSummary,
      keyPoints: Object.values(statement),
      argumentText: draft.argumentText,
      aiProvider: draft.aiProvider ?? null,
    });
  } catch (error) {
    party.error = error?.code === "REQUEST_TIMEOUT"
      ? "AI 변론문을 준비하는 데 시간이 더 필요합니다. 잠시 후 다시 시도해 주세요."
      : error?.message || "진술을 저장하지 못했습니다.";
  } finally {
    party.pending = false;
  }
}

async function confirmParty() {
  if (!trialId.value) return;

  const side = currentSide.value;
  const party = parties[side];
  party.pending = true;
  party.error = "";

  try {
    const updatedDraft = await updateArgumentDraft(trialId.value, side, {
      factSummary: party.caseOverview.trim(),
      argumentText: party.argumentText.trim(),
    });
    const confirmation = await confirmArgument(trialId.value, side);

    party.caseOverview = updatedDraft.factSummary;
    party.argumentText = updatedDraft.argumentText;
    party.confirmed = true;
    party.confirmedAt = confirmation.confirmedAt;
    bothConfirmed.value = Boolean(confirmation.bothConfirmed);
    currentStep.value += 1;
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (error) {
    party.error = error?.message || "진술을 확정하지 못했습니다.";
  } finally {
    party.pending = false;
  }
}

function goToStep(step) {
  startError.value = "";
  currentStep.value = step;
  window.scrollTo({ top: 0, behavior: "smooth" });
}

async function startTrial() {
  if (startPending.value) return;

  if (!trialId.value) {
    startError.value =
      "재판 식별 정보가 없습니다. 게시글 등록부터 다시 진행해주세요.";
    return;
  }

  if (!bothConfirmed.value) {
    startError.value = "양측 진술이 모두 확정되어야 재판을 시작할 수 있습니다.";
    return;
  }

  startPending.value = true;
  startError.value = "";

  try {
    await startTrialRequest(trialId.value);
    sessionStorage.removeItem(TRIAL_DRAFT_STORAGE_KEY);
    await router.push({
      name: "live-trial",
      params: { trialId: trialId.value },
    });
  } catch (error) {
    startError.value =
      error?.message ||
      "재판을 시작하지 못했습니다. 잠시 후 다시 시도해주세요.";
  } finally {
    startPending.value = false;
  }
}

// Single-button demo starter: creates post+trial, saves statements, confirms, and starts the trial
async function startDemo() {
  if (startPending.value) return;
  startPending.value = true;
  startError.value = "";

  try {
    let createdPostId = postId.value;
    if (!createdPostId) {
      const created = await createPost({
        title: trial.title || "내 친구의 여우짓을 남자친구가 거절을 안해",
        content: trial.summary || "친구의 과도한 관심 표현으로 불편했습니다.",
        relationshipType: "COUPLE",
        trialRequested: true,
      });
      createdPostId = created.postId;
    }

    const createdTrial = await createTrial(createdPostId, {
      visibility: "PUBLIC",
      aDisplayName: trial.aDisplayName.trim() || "A측",
      bDisplayName: trial.bDisplayName.trim() || "B측",
    });

    // Ensure URL has trialId so subsequent APIs work
    await router.replace({
      name: "trial-preparation",
      query: { postId: createdPostId, trialId: createdTrial.trialId },
    });
    await sleep(200);

    const tid = createdTrial.trialId;

    // Prepare A
    currentStep.value = 2;
    const statementA = {
      incidentTime: "며칠 전",
      situation: "친구가 남자친구에게 지속적으로 과한 관심을 보였습니다.",
      counterpartAction: "남자친구는 별다른 제지 없이 웃어넘겼습니다.",
      ownAction: "나는 불편함을 느껴 지적했습니다.",
      afterConversation: "남자친구는 대수롭지 않게 넘겼습니다.",
      desiredResolution: "남자친구가 명확한 선을 그어주길 바랍니다.",
    };
    parties.A.pending = true;
    await saveStatement(tid, "A", statementA);
    const draftA = await createArgumentDraft(tid, "A");
    Object.assign(parties.A, {
      statementSaved: true,
      draftGenerated: true,
      caseOverview: draftA.factSummary,
      keyPoints: Object.values(statementA),
      argumentText: draftA.argumentText,
      aiProvider: draftA.aiProvider ?? null,
    });
    parties.A.pending = false;

    // Confirm A
    parties.A.pending = true;
    const updatedDraftA = await updateArgumentDraft(tid, "A", {
      factSummary: parties.A.caseOverview,
      argumentText: parties.A.argumentText,
    });
    const confirmationA = await confirmArgument(tid, "A");
    parties.A.caseOverview = updatedDraftA.factSummary;
    parties.A.argumentText = updatedDraftA.argumentText;
    parties.A.confirmed = true;
    parties.A.confirmedAt = confirmationA.confirmedAt;
    parties.A.pending = false;

    await sleep(150);

    // Prepare B
    currentStep.value = 3;
    const statementB = {
      incidentTime: "며칠 전",
      situation: "친구는 호감 표현을 가볍게 표현했을 뿐이라는 입장입니다.",
      counterpartAction: "친구는 장난스러운 말투로 행동했습니다.",
      ownAction: "나는 그 행동이 불편하다고 느꼈습니다.",
      afterConversation: "현장에서 큰 갈등은 없었습니다.",
      desiredResolution: "오해를 풀고 선을 지켜주길 바랍니다.",
    };
    parties.B.pending = true;
    await saveStatement(tid, "B", statementB);
    const draftB = await createArgumentDraft(tid, "B");
    Object.assign(parties.B, {
      statementSaved: true,
      draftGenerated: true,
      caseOverview: draftB.factSummary,
      keyPoints: Object.values(statementB),
      argumentText: draftB.argumentText,
      aiProvider: draftB.aiProvider ?? null,
    });
    parties.B.pending = false;

    // Confirm B
    parties.B.pending = true;
    const updatedDraftB = await updateArgumentDraft(tid, "B", {
      factSummary: parties.B.caseOverview,
      argumentText: parties.B.argumentText,
    });
    const confirmationB = await confirmArgument(tid, "B");
    parties.B.caseOverview = updatedDraftB.factSummary;
    parties.B.argumentText = updatedDraftB.argumentText;
    parties.B.confirmed = true;
    parties.B.confirmedAt = confirmationB.confirmedAt;
    parties.B.pending = false;

    bothConfirmed.value = true;

    await sleep(200);

    // Don't auto-start the live trial for the demo button — stop at final confirmation step
    sessionStorage.removeItem(TRIAL_DRAFT_STORAGE_KEY);
    currentStep.value = 4;
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (error) {
    startError.value = error?.message || "데모 시작에 실패했습니다.";
  } finally {
    startPending.value = false;
  }
}
</script>

<template>
  <div class="min-h-screen bg-background">
    <main
      class="mx-auto max-w-[var(--ds-container-max)] px-4 py-10 sm:px-6 sm:py-14"
    >
      <h1
        class="mb-8 text-center font-heading text-heading-1 text-[var(--ds-color-primary)]"
      >
        새로운 재판 열기
      </h1>

      <div v-if="currentStep === 1" class="mb-6 text-center">
        <button
          class="rounded-lg bg-primary px-5 py-2.5 font-semibold text-primary-foreground"
          :disabled="startPending"
          type="button"
          @click="startDemo"
        >
          빠른 데모 시작
        </button>
      </div>

      <div class="mx-auto mb-12 max-w-3xl">
        <TrialStepIndicator :current-step="currentStep" />
      </div>

      <div
        :class="
          currentStep === 2 || currentStep === 3 ? '' : 'mx-auto max-w-3xl'
        "
      >
        <p
          v-if="preparationError"
          class="mb-4 rounded-lg bg-[var(--ds-color-error-container)] px-4 py-3 text-sm text-[var(--ds-color-on-error-container)]"
          role="alert"
        >
          {{ preparationError }}
        </p>

        <TrialBasicInformation
          v-if="currentStep === 1"
          :model-value="trial"
          @update:model-value="updateTrial"
          :pending="preparationPending"
          :locked="Boolean(trialId)"
          @next="createTrialAndContinue"
        />

        <PartyStatementStep
          v-else-if="currentStep === 2 || currentStep === 3"
          :key="currentSide"
          :side="currentSide"
          :party="parties[currentSide]"
          :other-party="currentSide === 'B' ? parties.A : null"
          @update:party="updateParty"
          @back="goToStep(currentStep - 1)"
          @prepare="prepareParty"
          @confirm="confirmParty"
        />

        <TrialFinalConfirmation
          v-else
          :trial="trial"
          :parties="parties"
          :both-confirmed="bothConfirmed"
          :start-pending="startPending"
          :start-error="startError"
          @back="goToStep(3)"
          @edit="goToStep"
          @start="startTrial"
        />
      </div>
    </main>
  </div>
</template>
