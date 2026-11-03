<script setup lang="ts">
import { computed, ref } from 'vue';
import data from './generated/config.json';
import type { ConfigField } from './config';
import { useLocale } from './useLocale';
import { useConfig } from './useConfig';
import ConfigFieldControl from './components/ConfigField.vue';

const schema = data.schema as ConfigField[];
const { preference, t } = useLocale(data.locales);
const { scope, query, values, sections, visible, wide, valid, changed, toml, filename, groupLabel, resetAll } = useConfig(schema, t);
const noticeKey = ref(''), notice = computed(() => noticeKey.value ? t(noticeKey.value) : '');
async function copy() {
  if (!valid.value) return;
  try { await navigator.clipboard.writeText(toml.value); noticeKey.value = 'site.notice.copied'; }
  catch { noticeKey.value = 'site.notice.copyBlocked'; }
}
function download() {
  if (!valid.value) return;
  const url = URL.createObjectURL(new Blob([toml.value], { type: 'application/toml;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = filename.value; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000); noticeKey.value = 'site.notice.generated';
}
</script>

<template>
  <header><h1 class="brand">{{ t('site.brand') }}</h1></header>
  <div class="toolbar">
    <div class="tabs"><button v-for="name in (['server', 'client'] as const)" :id="name" :key="name" type="button" :class="{ active: scope === name }" :aria-pressed="scope === name" @click="scope = name">{{ t('site.scope.' + name) }}</button></div>
    <input id="search" v-model="query" type="search" :aria-label="t('site.search.label')" :placeholder="t('site.search.placeholder')">
    <select id="language" v-model="preference" :aria-label="t('site.language.label')"><option value="auto">{{ t('site.language.auto') }}</option><option value="en" lang="en">English</option><option value="zh-CN" lang="zh-CN">简体中文</option></select>
    <button id="reset-all" type="button" @click="resetAll">{{ t('site.actions.resetAll') }}</button>
    <button id="download-top" type="button" class="primary" data-download :disabled="!valid" @click="download">{{ t('site.actions.download') }}<span class="button-arrow" aria-hidden="true">→</span></button>
  </div>
  <div class="workspace">
    <main id="fields" class="editor">
      <!-- Keep controls mounted so filtering, scope and language changes preserve their DOM state. -->
      <section v-for="section in sections" :key="section.scope + ':' + section.name" v-show="section.indices.some(index => visible.has(index))" class="section">
        <h2>{{ groupLabel(section.name) }}<span class="badge">{{ t('site.count.items', { count: section.indices.filter(index => visible.has(index)).length }) }}</span></h2>
        <ConfigFieldControl v-for="index in section.indices" v-show="visible.has(index)" :key="index" v-model="values[index]!" :class="{ 'field-wide': wide.has(index) }" :field="schema[index]!" :index="index" :t="t" />
      </section>
      <p v-if="!visible.size" class="empty">{{ t('site.empty') }}</p>
    </main>
    <aside class="output">
      <div class="output-head"><strong id="filename">{{ filename }}</strong><span id="summary" class="badge">{{ t('site.count.changes', { count: changed }) }}</span></div>
      <pre id="toml" tabindex="0" :aria-label="t('site.output.preview')">{{ toml }}</pre>
      <div class="actions"><button id="download" type="button" class="primary" data-download :disabled="!valid" @click="download">{{ t('site.actions.download') }}<span class="button-arrow" aria-hidden="true">→</span></button><button id="copy" type="button" :disabled="!valid" @click="copy">{{ t('site.actions.copy') }}</button></div>
    </aside>
  </div>
  <div id="notice" role="status" aria-live="polite">{{ notice }}</div>
</template>
