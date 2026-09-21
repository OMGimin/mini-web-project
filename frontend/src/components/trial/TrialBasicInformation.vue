<script setup>
import { Globe2, Info, LockKeyhole } from "@lucide/vue";

defineProps({
  modelValue: {
    type: Object,
    required: true,
  },
  pending: {
    type: Boolean,
    default: false,
  },
  postLocked: { type: Boolean, default: false },
  locked: {
    type: Boolean,
    default: false,
  },
});

defineEmits(["update:modelValue", "next"]);
</script>

<template>
  <form
    class="rounded-xl border border-border bg-card p-5 shadow-interactive sm:p-7"
    @submit.prevent="$emit('next')"
  >
    <div class="grid gap-5">
      <label class="grid gap-2 text-sm font-medium">
        재판 제목
        <input
          :value="modelValue.title"
          class="rounded-lg border border-input bg-muted px-4 py-3 outline-none focus:ring-2 focus:ring-ring"
          maxlength="150"
          placeholder="재판 제목을 입력해주세요"
          :readonly="locked || postLocked"
          @input="
            $emit('update:modelValue', {
              ...modelValue,
              title: $event.target.value,
            })
          "
        />
      </label>

      <label v-if="!locked && !postLocked" class="grid gap-2 text-sm font-medium">
        관계 유형
        <select :value="modelValue.relationshipType" required
          class="rounded-lg border border-input bg-muted px-4 py-3"
          @change="$emit('update:modelValue', { ...modelValue, relationshipType: $event.target.value })">
          <option value="" disabled>관계 유형을 선택하세요</option>
          <option value="COUPLE">연인</option>
          <option value="SOME">썸</option>
          <option value="SPOUSE">부부</option>
        </select>
      </label>

      <div class="grid gap-4 sm:grid-cols-2">
        <label class="grid gap-2 text-sm font-medium">
          A측 이름
          <input
            :value="modelValue.aDisplayName"
            class="rounded-lg border border-input bg-muted px-4 py-3 outline-none focus:ring-2 focus:ring-ring"
            maxlength="50"
            placeholder="A측 이름"
            :readonly="locked"
            @input="
              $emit('update:modelValue', {
                ...modelValue,
                aDisplayName: $event.target.value,
              })
            "
          />
        </label>
        <label class="grid gap-2 text-sm font-medium">
          B측 이름
          <input
            :value="modelValue.bDisplayName"
            class="rounded-lg border border-input bg-muted px-4 py-3 outline-none focus:ring-2 focus:ring-ring"
            maxlength="50"
            placeholder="B측 이름"
            :readonly="locked"
            @input="
              $emit('update:modelValue', {
                ...modelValue,
                bDisplayName: $event.target.value,
              })
            "
          />
        </label>
      </div>

      <label class="grid gap-2 text-sm font-medium">
        사건 요약
        <textarea
          :value="modelValue.summary"
          class="min-h-32 resize-none rounded-lg border border-input bg-muted px-4 py-3 outline-none focus:ring-2 focus:ring-ring"
          maxlength="1000"
          placeholder="두 사람 사이에 있었던 갈등을 설명해주세요"
          :readonly="locked || postLocked"
          @input="
            $emit('update:modelValue', {
              ...modelValue,
              summary: $event.target.value,
            })
          "
        />
      </label>

      <fieldset class="grid gap-2">
        <legend class="mb-2 text-sm font-medium">공개 여부</legend>
        <div class="grid gap-3 sm:grid-cols-2">
          <label
            class="flex cursor-pointer items-center gap-3 rounded-lg border-2 border-primary bg-primary/5 p-4 text-primary"
          >
            <input class="sr-only" type="radio" checked />
            <Globe2 class="size-5" />
            <span
              ><strong class="block">공개 재판</strong
              ><small>누구나 관전할 수 있어요.</small></span
            >
          </label>
          <div
            class="flex items-center gap-3 rounded-lg border border-border bg-muted p-4 text-muted-foreground"
            aria-disabled="true"
          >
            <LockKeyhole class="size-5" />
            <span
              ><strong class="block">비공개 재판</strong
              ><small>추후 지원 예정입니다.</small></span
            >
          </div>
        </div>
      </fieldset>

      <div
        class="flex gap-3 rounded-lg bg-[var(--ds-color-primary-fixed)] p-4 text-sm text-[var(--ds-color-on-primary-fixed-variant)]"
      >
        <Info class="mt-0.5 size-5 shrink-0 text-primary" />
        양측 진술을 확정한 뒤 마지막 확인 화면에서 재판을 시작할 수 있습니다.
      </div>
    </div>

    <button
      class="mt-8 w-full rounded-lg bg-[var(--ds-color-primary)] px-5 py-3 font-semibold text-white disabled:cursor-not-allowed disabled:opacity-40"
      :disabled="
        pending ||
        !modelValue.title.trim() ||
        !modelValue.aDisplayName.trim() ||
        !modelValue.bDisplayName.trim() ||
        !modelValue.summary.trim() ||
        (!locked && !postLocked && !modelValue.relationshipType)
      "
      type="submit"
    >
      {{ pending ? "재판을 생성하는 중..." : "다음: A측 진술 →" }}
    </button>
  </form>
</template>
