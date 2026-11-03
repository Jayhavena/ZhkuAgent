<script setup>
import { nextTick, ref, watch } from 'vue'
const props = defineProps(['selected', 'rows', 'originalBusy', 'roleLabels', 'focusToken'])
defineEmits(['showOriginal'])
// 用户点行内引用或资料条目时，把「证据详情」展开并把该条依据滚进视野。
// 只改展示状态，不改变任何业务数据，也不触发原文请求；滚动用 nearest，
// 避免把用户已经调好的阅读位置整体弹走。
const panel = ref(null)
watch(() => props.focusToken, async () => {
  if (!panel.value) return
  panel.value.open = true
  await nextTick()
  panel.value.querySelector('.evidence-panel--selected')?.scrollIntoView({ block: 'nearest' })
})
</script>

<template>
  <!-- 证据详情只展示已发布字段：规则版本、字符跨度与原文摘录。
       核验状态、支持程度、适用范围与原件地址后端均未下发，页面不得自行生成。 -->
  <details ref="panel" class="fold" data-testid="fold-evidence">
    <summary class="fold-summary">
      <span>
        <span class="fold-title">证据详情</span>
        <span class="fold-meta">{{ rows.length ? (rows.length > 1 ? '共 ' + rows.length + ' 条，可逐条核对' : '1 条依据') : '本轮没有已发布依据' }}</span>
      </span>
      <span class="chevron" aria-hidden="true">⌄</span>
    </summary>
    <div class="fold-body">
      <p v-if="!rows.length" class="fold-empty" data-testid="evidence-empty">本轮没有已发布的可核对依据。</p>
      <template v-else>
        <div v-for="row in rows" :key="row.evidence.id" class="evidence-panel" :class="{ 'evidence-panel--selected': selected && selected.evidence.id === row.evidence.id }">
          <p class="evidence-heading">
            <span class="evidence-index">依据 {{ row.index }}</span>
            <span class="evidence-role">{{ row.roles.map(role => roleLabels[role] || role).join(' · ') || '已发布依据' }}</span>
          </p>
          <!-- 只展示服务端真实下发的字段，标签与取值一一对应，不做任何推断。 -->
          <dl class="evidence-meta">
            <dt>规则版本</dt><dd data-testid="evidence-revision">{{ row.evidence.revision }}</dd>
            <dt>原文位置</dt><dd>第 {{ row.evidence.startInclusive }}–{{ row.evidence.endExclusive }} 字符</dd>
            <dt>校验摘要</dt><dd class="evidence-checksum">{{ row.evidence.checksum.slice(0, 12) }}…</dd>
          </dl>
          <blockquote class="quote" data-testid="evidence-quote">{{ row.evidence.quote }}</blockquote>
          <div class="evidence-foot">
            <small>摘录由服务端按字符跨度校验后发布。</small>
            <button class="text-button" type="button" data-testid="open-original" :disabled="originalBusy" @click="$emit('showOriginal', row.evidence.originalToken)">在原文中定位 ↗</button>
          </div>
        </div>
      </template>
    </div>
  </details>
</template>
