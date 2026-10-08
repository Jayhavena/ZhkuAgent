<script setup>
defineProps({ source: Object })
</script>
<template>
  <section v-if="source" class="source-section" aria-label="结果出处">
    <h3>政策出处</h3>
    <dl class="metadata"><dt>资料</dt><dd>{{ source.sourceTitle }}</dd><dt>版本</dt><dd>{{ source.sourceVersion }}</dd>
      <dt>位置</dt><dd>{{ source.sourceLocator }}</dd><dt>适用范围</dt><dd>{{ source.applicableScope }}</dd></dl>
  </section>
</template>
