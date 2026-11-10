<script setup>
defineProps(['state', 'originalParts', 'originalLink'])
defineEmits(['close'])
</script>

<template>
  <aside class="original-panel" aria-label="原文核对" aria-live="polite" data-testid="original-panel">
    <header class="original-header">
      <div><p class="panel-eyebrow">来源核对</p><h2>原文</h2></div>
      <button class="text-button" type="button" data-testid="close-original" @click="$emit('close')">关闭</button>
    </header>
    <p v-if="state.originalBusy" class="inline-status" role="status">正在核对当前原文…</p>
    <template v-else-if="state.original">
      <h3 class="original-title">{{ state.original.title || '来源标题未记录' }}</h3>
      <!-- 定位信息只用服务端返回的字段：条款定位、章节标题、记录页码。
           后端没有段落索引，所以这里不写“第 N 段”；页码未记录时明确说明，不补一个猜测值。 -->
      <dl class="original-meta-list">
        <dt>规则版本</dt><dd>{{ state.original.revision }}</dd>
        <dt>条款定位</dt><dd>{{ state.original.sourceLocator || '条款定位尚未记录' }}</dd>
        <dt v-if="state.original.sectionTitle">条款标题</dt>
        <dd v-if="state.original.sectionTitle">{{ state.original.sectionTitle }}</dd>
        <dt>记录页码</dt>
        <dd data-testid="original-page">{{ state.original.pageNumberKind === 'RECORDED_SOURCE_PAGE'
          ? '第 ' + state.original.recordedPageNumber + ' 页'
          : '原件页码未记录' }}</dd>
      </dl>
      <blockquote class="original-text" data-testid="original-text">{{ originalParts.before }}<mark data-testid="original-mark">{{ originalParts.quote }}</mark>{{ originalParts.after }}</blockquote>
      <ul v-if="state.original.unknownItems.length" class="original-unknown" data-testid="original-unknown"><li v-for="(unknown, index) in state.original.unknownItems" :key="index">{{ unknown }}</li></ul>
      <a v-if="originalLink" class="original-link" :href="originalLink" target="_blank" rel="noopener noreferrer" referrerpolicy="no-referrer" data-testid="original-link">打开原件</a>
    </template>
    <p v-else-if="state.evidenceUnavailable" class="original-unavailable">原文资格或版本已变化。请重新查证后再查看依据。</p>
    <p v-else class="original-placeholder">原文正在加载，或暂时无法读取。可以稍后重试查证。</p>
  </aside>
</template>
