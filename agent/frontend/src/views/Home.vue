<template>
  <div class="home-container">
    <main class="hero">
      <h1 class="title">仲园 <span class="brand">规则查证</span></h1>
      <p class="subtitle">提出问题，确认条件，核对原文。</p>

      <!-- 校园入口复用可信身份与本人归属，不能指向旧通用匿名API。 -->
      <div
        class="entry-card"
        role="link"
        tabindex="0"
        @click="navigateTo('/rules')"
        @keydown.enter="navigateTo('/rules')"
      >
        <div class="card-text">
          <div class="card-title">查证校园规则</div>
          <div class="card-desc">登录后提问，查看能确认的规则和原文依据</div>
        </div>
        <div class="card-arrow" aria-hidden="true">→</div>
      </div>
      <p class="legacy-note">个人项目，非学校官方系统。首轮以2025版文本为依据，现行适用性仍需核实。</p>
      <nav class="legacy-note" aria-label="实验入口"><RouterLink to="/campus/run">学分实验</RouterLink> / <RouterLink to="/super-agent">通用助手实验</RouterLink></nav>
    </main>

    <AppFooter />
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import AppFooter from '../components/AppFooter.vue'

// 设置页面标题和元数据
useHead({
  title: '校园规则查证 - 仲园',
  meta: [
    {
      name: 'description',
      content: '校园规则查证：自然语言提问、必要条件确认和原文核对。'
    },
    {
      name: 'keywords',
      content: '仲园,ZhkuAgent,校园规则查证,原文核对,本机演示'
    }
  ]
})

const router = useRouter()

const navigateTo = (path) => {
  router.push(path)
}
</script>

<style scoped>
/* 首页外壳样式说明：
   - 全部取值来自 style.css 的设计令牌层（含暗色自动翻转），不写死色值；
   - 宽度一律 max-width + flex，不写死固定像素，为以后的移动端适配留活口；
   - 设计依据见 docs/design-references/home-redesign-plan.md。 */

.home-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background-color: var(--color-page-bg);
  color: var(--color-text-primary);
}
.legacy-note { max-width:420px; margin-top:20px; color:var(--color-text-secondary); font-size:12px; text-align:center; }

.hero {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 20px;
}

.title {
  font-size: 2.5rem;
  font-weight: 500;
  letter-spacing: 1px;
  margin-bottom: 12px;
}

/* 品牌绿的唯一固定落点：克制使用，避免满屏绿色失去"安静"的基调 */
.brand {
  color: var(--color-brand);
}

.subtitle {
  font-size: 1rem;
  color: var(--color-text-secondary);
  letter-spacing: 2px;
  margin-bottom: 48px;
}

.entry-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  width: 100%;
  max-width: 480px;
  padding: 24px 28px;
  background-color: var(--color-surface);
  border: 1px solid var(--color-border-subtle);
  border-radius: var(--radius-bubble);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition:
    transform var(--duration-hover) ease,
    box-shadow var(--duration-hover) ease,
    border-color var(--duration-hover) ease;
}

/* 唯一的微交互：hover 上浮 + 品牌绿描边。
   不做持续动画——首屏注意力应落在标题与入口上，动效只做反馈不做表演 */
.entry-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--shadow-card-hover);
  border-color: var(--color-brand);
}

.entry-card:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.card-title {
  font-size: 1.25rem;
  font-weight: 500;
  margin-bottom: 6px;
}

.card-desc {
  font-size: 0.9rem;
  color: var(--color-text-secondary);
}

.card-arrow {
  font-size: 1.5rem;
  color: var(--color-brand);
  flex-shrink: 0;
  transition: transform var(--duration-hover) ease;
}

.entry-card:hover .card-arrow {
  transform: translateX(4px);
}
</style>
