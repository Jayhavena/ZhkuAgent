<script setup>
import RuleClarification from './RuleClarification.vue'
import RuleEvidence from './RuleEvidence.vue'
defineProps(['state', 'result', 'active', 'identityBusy', 'terminal', 'answerKind', 'answerStatus', 'answerTitle', 'hasProcessingFailure', 'issueLabels', 'conditionSummary', 'waitingForClarification', 'nextCondition', 'choice', 'relatedEvidence', 'leadClaim', 'leadClaimId', 'claimCitations', 'explanationLines', 'evidenceRows', 'selectedEvidence', 'roleLabels', 'evidenceFocusToken'])
defineEmits(['cancel', 'recover', 'refresh', 'newQuestion', 'update:choice', 'clarify', 'showOriginal', 'focusEvidence'])

// 说明性缺口与技术失败必须保持视觉区分：前者用暖色提示，后者用中性/红色收尾，
// 避免把“处理未完成”看成“资料不足”。
const gapKinds = new Set(['DATA_GAP', 'SCOPE_LIMIT', 'CONFLICT', 'CLARIFICATION'])
const issueTone = kind => gapKinds.has(kind) ? 'gap' : kind === 'PROCESSING_FAILURE' ? 'failure' : 'neutral'
</script>

<template>
  <section class="assistant-turn" aria-label="查证结果" aria-live="polite">
    <div class="assistant-mark" aria-hidden="true">Z</div>
    <div class="assistant-content">
      <p v-if="state.busy || active" class="progress-message" role="status">
        {{ state.cancelRequested ? '已收到停止请求，正在确认本轮状态…' : '正在查证相关规则…' }}
      </p>
      <div v-if="active && state.ticket && !state.pending" class="recovery-row">
        <button class="text-button" type="button" data-testid="cancel-task" :disabled="state.busy || state.cancelRequested || identityBusy" @click="$emit('cancel')">{{ state.cancelRequested ? '正在停止…' : '停止本次查证' }}</button>
      </div>
      <p v-if="state.error" class="error-message" role="alert">{{ state.error }}</p>
      <div v-if="state.pending" class="recovery-row">
        <button class="secondary-button" type="button" data-testid="recover-request" :disabled="state.busy || identityBusy" @click="$emit('recover')">查询原请求</button>
      </div>
      <!-- 只有“查询结果不明”才需要手动恢复：正常终态、等待澄清时给这个入口会让人以为任务还没结束。 -->
      <div v-else-if="state.ticket && !state.polling && !state.busy && (state.error || !state.snapshot)" class="recovery-row">
        <button class="secondary-button" type="button" data-testid="refresh-task" :disabled="identityBusy" @click="$emit('refresh')">继续查询</button>
      </div>
      <section v-if="state.evidenceUnavailable" class="answer-panel answer-panel--warning">
        <span class="status-label status-label--warning">依据已变化</span>
        <h2>上一轮结果已暂停展示</h2>
        <p>原文依据的资格或版本发生变化。为避免继续展示可能失效的结论，请重新查证。</p>
        <button class="secondary-button" type="button" :disabled="active || state.pending || state.busy" @click="$emit('newQuestion')">重新查证</button>
      </section>
      <template v-else-if="result">
        <header class="answer-heading">
          <p class="answer-status" :class="'answer-status--' + answerKind" data-testid="answer-status"><span class="status-dot" aria-hidden="true"></span>{{ answerStatus }}</p>
          <h2 data-testid="answer-title">{{ answerTitle }}</h2>
          <!-- 标题承载首条结论，它的引用就挂在标题下方：结论句与其依据必须连在一起看。 -->
          <p v-if="leadClaim" class="answer-citation-row">
            <button v-for="(evidence, index) in claimCitations(leadClaim.item, leadClaim.claim)" :key="evidence.id" class="inline-ref" type="button" data-testid="inline-ref" :aria-label="'依据 ' + (index + 1) + '：查看对应证据'" @click="$emit('focusEvidence', evidence.id)">依据 {{ index + 1 }}</button>
          </p>
          <p v-if="result.publishedVersion < state.snapshot.version" class="inline-status">以下为上一轮已确认内容，本轮补充仍在处理。</p>
          <p v-if="state.snapshot.execution.mode === 'ENGINEERING_REPLAY'" class="mode-notice mode-notice--inline">工程回放使用已封存的响应，不代表在线模型能力。</p>
        </header>
        <!-- 需要澄清时先给选择控件：这是该状态唯一可执行的操作，放在说明之前避免用户以为无事可做。 -->
        <RuleClarification v-if="waitingForClarification && nextCondition" :choice="choice" :next-condition="nextCondition" :busy="state.busy" :pending="state.pending" @update:choice="$emit('update:choice', $event)" @clarify="$emit('clarify', $event)" @new-question="$emit('newQuestion')" />
        <section v-if="result.items.some(item => item.claims.length)" class="claims-section" aria-label="能确认的内容">
          <h3 class="visually-hidden">能确认的内容</h3>
          <article v-for="item in result.items" :key="item.id">
            <!-- 标题已承载首条结论时不重复渲染同一句话。 -->
            <div v-for="claim in item.claims.filter(c => c.id !== leadClaimId)" :key="claim.id" class="claim">
              <p class="claim-text" data-testid="claim-text">{{ claim.text }}
                <!-- 行内引用只锚定 CLAIM 用途的已发布证据，编号与「本次使用的资料」顺序一致。 -->
                <button v-for="(evidence, index) in claimCitations(item, claim)" :key="evidence.id" class="inline-ref" type="button" data-testid="inline-ref" :aria-label="'依据 ' + (index + 1) + '：查看对应证据'" @click="$emit('focusEvidence', evidence.id)">依据 {{ index + 1 }}</button>
              </p>
              <p v-if="Object.keys(claim.when).length" class="claim-conditions">
                <span>适用条件</span>{{ conditionSummary(claim.when) }}
              </p>
            </div>
          </article>
        </section>
        <p v-else class="no-claim-message" data-testid="no-claim">目前没有足够依据给出确定结论。</p>
        <section v-if="result.items.some(item => item.issues.length)" class="issues-section" aria-label="限制与提醒">
          <h3 class="visually-hidden">{{ hasProcessingFailure ? '本次处理情况' : '需要注意' }}</h3>
          <article v-for="item in result.items" :key="item.id">
            <div v-for="issue in item.issues" :key="issue.id" class="issue" :class="'issue--' + issueTone(issue.kind)">
              <span class="issue-label">{{ issueLabels[issue.kind] }}</span>
              <p>{{ issue.message }}</p>
              <p class="issue-next-step">下一步：{{ issue.nextStep }}</p>
            </div>
          </article>
        </section>

        <!-- 三个默认收起的详情区：解释、资料、证据。它们只汇总已发布内容，不推断任何未返回的事实。 -->
        <div class="detail-stack">
          <details class="fold" data-testid="fold-explanation">
            <summary class="fold-summary">
              <span><span class="fold-title">查看具体解释</span><span class="fold-meta">{{ explanationLines.length ? '结论依据与适用边界' : '暂无额外解释' }}</span></span>
              <span class="chevron" aria-hidden="true">⌄</span>
            </summary>
            <div class="fold-body">
              <ul v-if="explanationLines.length" class="explanation-list">
                <li v-for="(line, index) in explanationLines" :key="index" :class="'explanation-' + line.kind"><span class="explanation-label">{{ line.label }}</span>{{ line.text }}</li>
              </ul>
              <!-- 后端没有独立 explanation 字段；没有可依据的内容时如实说明，不生成说明性文字。 -->
              <p v-else class="fold-empty" data-testid="explanation-empty">本轮没有可展示的额外解释：服务端未返回适用条件或限制说明。具体判断请以上方已发布内容与下方资料为准。</p>
            </div>
          </details>

          <details class="fold" data-testid="fold-sources">
            <summary class="fold-summary">
              <span><span class="fold-title">本次使用的资料</span><span class="fold-meta">{{ evidenceRows.length }} 份资料</span></span>
              <span class="chevron" aria-hidden="true">⌄</span>
            </summary>
            <div class="fold-body">
              <p v-if="!evidenceRows.length" class="fold-empty" data-testid="sources-empty">本轮没有已发布的可引用资料。</p>
              <template v-else>
                <button v-for="row in evidenceRows" :key="row.evidence.id" class="source-item" type="button" data-testid="source-item" @click="$emit('focusEvidence', row.evidence.id)">
                  <span class="file-icon" aria-hidden="true">原文</span>
                  <span class="source-item-copy">
                    <span class="source-name">依据 {{ row.index }} · 规则版本 {{ row.evidence.revision }}</span>
                    <!-- 书名与条款定位只在原文接口返回，快照阶段拿不到；这里不猜标题。 -->
                    <span class="source-meta">{{ row.roles.map(role => roleLabels[role] || role).join(' · ') || '已发布依据' }}</span>
                  </span>
                  <span class="source-go">查看 ↗</span>
                </button>
              </template>
            </div>
          </details>

          <RuleEvidence :selected="selectedEvidence" :rows="evidenceRows" :role-labels="roleLabels" :original-busy="state.originalBusy" :focus-token="evidenceFocusToken" @show-original="$emit('showOriginal', $event)" />
        </div>
      </template>
    </div>
  </section>
</template>
