<script setup>
import { computed } from 'vue'
import { ruleCheckConditionLabel } from '../../state/rule-check-labels.js'
const props = defineProps(['choice', 'nextCondition', 'busy', 'pending'])
const emit = defineEmits(['update:choice', 'clarify', 'newQuestion'])
const choiceModel = computed({ get: () => props.choice, set: value => emit('update:choice', value) })
</script>

<template>
  <form class="clarification-form" data-testid="clarification-form" @submit.prevent="$emit('clarify', choice)">
    <h3 data-testid="clarification-question">{{ nextCondition.question }}</h3>
    <p class="clarification-hint">请选择你能确认的情况；不确定时不要猜。</p>
    <div class="clarification-options">
      <label v-for="value in nextCondition.allowedValues" :key="value" class="clarification-option">
        <input v-model="choiceModel" type="radio" name="rule-condition" :value="value" data-testid="clarification-option" :disabled="busy || !!pending" />
        <span>{{ ruleCheckConditionLabel(nextCondition.conditionId, value) }}</span>
      </label>
    </div>
    <button class="primary-button" type="submit" data-testid="submit-clarification" :disabled="!choice || busy || !!pending">确认并继续</button>
    <button class="text-button change-question-button" type="button" :disabled="busy || !!pending" @click="$emit('newQuestion')">改问其他问题</button>
  </form>
</template>
