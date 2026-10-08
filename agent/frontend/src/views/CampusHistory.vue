<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import CampusSource from '../components/CampusSource.vue'
import { controlledClient as api, errorMessage, ControlledError, isResourceId } from '../api/controlled.js'
import { controlledSession as session } from '../state/controlled-session.js'
import { historyFacts, stateLabel, visibilityMessage } from '../state/controlled-results.js'
const route = useRoute(), router = useRouter()
const items = ref([]), cursor = ref(null), detail = ref(null), listError = ref(''), detailError = ref(''), busy = ref(false)
const user = computed(() => session.state.actor?.role === 'USER')
const controller = new AbortController()
let alive = true, detailVersion = 0
onUnmounted(() => { alive = false; detailVersion++; controller.abort(); items.value = []; detail.value = null })
function time(value) { const date = new Date(value); return value && Number.isFinite(date.getTime()) ? date.toLocaleString('zh-CN') : '尚未记录' }
async function list(next = false) {
  if (busy.value || !user.value) return
  busy.value = true; listError.value = ''
  if (!next) { items.value = []; cursor.value = null }
  try {
    const reply = await api.history(next ? cursor.value : null, { signal: controller.signal })
    if (!alive) return
    if (!Array.isArray(reply.items) || reply.items.some(item => !isResourceId(item.runId))) throw new ControlledError('BAD_RESPONSE')
    items.value = next ? [...items.value, ...reply.items] : reply.items
    cursor.value = typeof reply.nextCursor === 'string' ? reply.nextCursor : null
  } catch (failure) { if (alive && failure.code !== 'STALE_RESPONSE') listError.value = errorMessage(failure) }
  finally { if (alive) busy.value = false }
}
async function read(id) {
  const expected = ++detailVersion
  detail.value = null; detailError.value = ''
  if (!isResourceId(id) || !user.value) return
  try {
    const reply = await api.historyDetail(id, { signal: controller.signal })
    if (alive && expected === detailVersion) detail.value = historyFacts(reply)
  } catch (failure) { if (alive && expected === detailVersion && failure.code !== 'STALE_RESPONSE') detailError.value = errorMessage(failure) }
}
watch(() => route.query.runId, id => read(id), { immediate: true })
onMounted(() => list())
</script>
<template>
  <header class="page-heading"><h1>我的历史</h1><p>查看以前的学分核对结果与参考依据。</p></header>
  <p class="form-feedback" role="status">{{ listError }}</p>
  <div class="history-layout"><section class="history-list-area"><div class="section-heading"><h2>任务记录</h2><button class="text-button" type="button" :disabled="busy || !user" @click="list(false)">刷新列表</button></div>
    <p v-if="!items.length" class="empty-state">{{ busy ? '正在查询…' : '暂无本人任务记录。' }}</p>
    <button v-for="item in items" :key="item.runId" type="button" class="history-item" :class="{ selected: route.query.runId === item.runId }"
      @click="router.replace({ path: '/campus/history', query: { runId: item.runId } })"><strong>学分核对</strong><span>{{ time(item.admittedAt) }}</span></button>
    <button v-if="cursor" type="button" class="button secondary" :disabled="busy" @click="list(true)">加载下一批</button>
  </section><section class="history-detail"><div class="section-heading"><h2>任务详情</h2><button v-if="detail" type="button" class="text-button" @click="read(route.query.runId)">刷新详情</button></div>
    <p class="form-feedback" role="status">{{ detailError }}</p><p v-if="!detail" class="empty-state">选择一条记录，查看结果与参考依据。</p>
    <template v-else><p class="status-heading">{{ stateLabel(detail) }}</p><p v-if="detail.answer" class="answer-text">{{ detail.answer }}</p><p v-else class="empty-state">{{ visibilityMessage(detail) }}</p>
      <CampusSource :source="detail.source" /><p v-if="detail.answer" class="verification-note">出处为资料引用，请核对当前适用范围；此结果不能确认个人办理资格。</p>
    </template>
  </section></div>
</template>
