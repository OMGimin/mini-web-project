<script setup>
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import { Bold, Image, Italic, Link, Scale } from "@lucide/vue";

import { createPost } from "@/apis/postApi.js";
import CommunityLayout from "@/components/community/CommunityLayout.vue";
import Button from "@/components/ui/Button.vue";
import Input from "@/components/ui/Input.vue";
import Textarea from "@/components/ui/Textarea.vue";
import { categoryTypes } from "@/mock/community/communityData.js";
import { useCommunityStore } from "@/stores/communityStore.js";
import { createUuid } from "@/utils/createUuid.js";

const TRIAL_DRAFT_STORAGE_KEY = "love-war:trial-draft";
const relationshipOptions = [
  { label: "연인", value: "COUPLE" },
  { label: "썸", value: "SOME" },
  { label: "부부", value: "SPOUSE" },
];
const router = useRouter();
const { addPost } = useCommunityStore();

const category = ref("");
const relationshipType = ref("");
const title = ref("");
const content = ref("");
const requestTrial = ref(false);
const submitPending = ref(false);
const submitError = ref("");
const canSubmit = computed(() =>
  Boolean(
    category.value &&
    relationshipType.value &&
    title.value.trim() &&
    content.value.trim(),
  ),
);

async function submitPost() {
  if (!canSubmit.value || submitPending.value) return;

  submitPending.value = true;
  submitError.value = "";

  try {
    if (requestTrial.value) {
      sessionStorage.setItem(TRIAL_DRAFT_STORAGE_KEY, JSON.stringify({
        title: title.value.trim(), content: content.value.trim(),
        relationshipType: relationshipType.value, category: category.value,
      }));
      await router.push({ name: "trial-preparation" });
      return;
    }
    const createdPost = await createPost({
      title: title.value.trim(),
      content: content.value.trim(),
      relationshipType: relationshipType.value,
      trialRequested: requestTrial.value,
    });

    if (!requestTrial.value) {
      const localPost = addPost({
        id: `created-${createdPost.postId}-${createUuid()}`,
        backendPostId: createdPost.postId,
        category: category.value,
        relationshipType: createdPost.relationshipType,
        title: createdPost.title,
        content: createdPost.content,
      });
      sessionStorage.removeItem(TRIAL_DRAFT_STORAGE_KEY);
      await router.push({
        name: "post-detail",
        params: { postId: localPost.id },
      });
      return;
    }

  } catch (error) {
    submitError.value =
      error?.message ||
      "게시글을 등록하지 못했습니다. 잠시 후 다시 시도해주세요.";
  } finally {
    submitPending.value = false;
  }
}

</script>

<template>
  <CommunityLayout>
    <section
      class="mx-auto max-w-3xl rounded-xl border border-border bg-card p-5 shadow-sm sm:p-8"
    >
      <h1 class="text-2xl font-bold tracking-tight">글쓰기</h1>
      <div class="mt-6 grid gap-4 sm:grid-cols-2">
        <label class="grid gap-2 text-sm font-semibold">
          <span>카테고리</span>
          <select
            class="h-10 rounded-lg border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
            v-model="category"
          >
            <option value="" disabled>카테고리를 선택하세요</option>
            <option v-for="type in categoryTypes.slice(1)" :key="type">
              {{ type }}
            </option>
          </select>
        </label>
        <label class="grid gap-2 text-sm font-semibold">
          <span>관계 유형</span>
          <select
            class="h-10 rounded-lg border border-input bg-background px-3 text-sm outline-none focus:ring-2 focus:ring-ring"
            v-model="relationshipType"
          >
            <option value="" disabled>관계 유형을 선택하세요</option>
            <option
              v-for="option in relationshipOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </option>
          </select>
        </label>
      </div>
      <Input
        v-model="title"
        class="mt-6 h-12 border-0 border-b border-border rounded-none px-0 text-lg shadow-none focus-visible:ring-0"
        placeholder="제목을 입력하세요."
      />
      <div
        class="mt-4 flex gap-1 rounded-lg border border-border bg-muted p-2"
        aria-label="글 편집 도구"
      >
        <button
          class="rounded-md p-2 transition hover:bg-card"
          type="button"
          aria-label="굵게"
        >
          <Bold :size="17" />
        </button>
        <button
          class="rounded-md p-2 transition hover:bg-card"
          type="button"
          aria-label="기울임"
        >
          <Italic :size="17" />
        </button>
        <button
          class="rounded-md p-2 transition hover:bg-card"
          type="button"
          aria-label="이미지"
        >
          <Image :size="17" />
        </button>
        <button
          class="rounded-md p-2 transition hover:bg-card"
          type="button"
          aria-label="링크"
        >
          <Link :size="17" />
        </button>
      </div>
      <Textarea
        v-model="content"
        class="mt-2 min-h-72 border-0 px-0 shadow-none focus-visible:ring-0"
        placeholder="당신의 이야기를 자세히 적어주세요. 판결에 도움이 될 구체적인 상황일수록 좋습니다."
      />
      <label
        class="mt-4 flex items-start gap-3 rounded-xl border border-border bg-muted p-4"
      >
        <Scale class="mt-0.5 text-primary" :size="25" />
        <span class="grid gap-1 text-sm"
          ><strong>재판 신청 (Request Trial)</strong
          ><small class="leading-5 text-muted-foreground"
            >재판 기본 정보를 확인한 뒤 게시글과 재판을 등록합니다. 이후 AI
            변호사와 함께 양측 진술을 준비합니다.</small
          ></span
        >
        <input
          class="ml-auto mt-1 size-4 accent-primary"
          v-model="requestTrial"
          type="checkbox"
        />
      </label>
      <p v-if="submitError" class="mt-3 text-sm text-destructive" role="alert">
        {{ submitError }}
      </p>
      <div
        class="mt-6 flex items-center justify-end gap-4 border-t border-border pt-5"
      >
        <RouterLink
          class="text-sm font-semibold text-muted-foreground hover:text-foreground"
          to="/community"
          >취소</RouterLink
        >
        <Button
          type="button"
          :disabled="!canSubmit || submitPending"
          @click="submitPost"
        >
          {{ submitPending ? "처리 중..." : requestTrial ? "재판 정보 입력" : "등록하기" }}</Button
        >
      </div>
    </section>
  </CommunityLayout>
</template>
