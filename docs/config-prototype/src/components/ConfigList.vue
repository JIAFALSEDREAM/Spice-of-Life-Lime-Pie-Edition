<script setup lang="ts">
import { nextTick, useTemplateRef } from 'vue';
import type { Entry, ListField, Translator } from '../config';

const props = defineProps<{ field: ListField; index: number; label: string; t: Translator }>();
const value = defineModel<Entry[]>({ required: true });
const rows = useTemplateRef<HTMLDivElement>('rows'), addButton = useTemplateRef<HTMLButtonElement>('addButton');

async function focusRow(index: number) {
  await nextTick();
  const input = rows.value?.querySelectorAll<HTMLInputElement>('input')[index];
  (input ?? addButton.value)?.focus({ preventScroll: true });
  if (!input || !rows.value) return;
  const viewport = rows.value.getBoundingClientRect(), rect = input.getBoundingClientRect();
  if (rect.bottom > viewport.bottom - 8) rows.value.scrollTop += rect.bottom - viewport.bottom + 8;
  else if (rect.top < viewport.top + 8) rows.value.scrollTop -= viewport.top - rect.top + 8;
}
function add() {
  const next = [...value.value, props.field.itemType === 'number' ? 0 : ''];
  value.value = next;
  void focusRow(next.length - 1);
}
function remove(index: number) {
  if (value.value.length <= props.field.minItems) return;
  const next = value.value.filter((_, item) => item !== index);
  value.value = next;
  void focusRow(Math.min(index, next.length - 1));
}
function update(index: number, event: Event) {
  const input = event.target as HTMLInputElement;
  value.value = value.value.map((item, n) => n === index ? (props.field.itemType === 'number' ? input.valueAsNumber : input.value) : item);
}
</script>

<template>
  <div class="list-meta"><span>{{ t('site.count.items', { count: value.length }) }}</span><span class="list-hint">{{ t('site.list.resize') }}</span></div>
  <div :id="`value-${index}`" ref="rows" class="rows" tabindex="0" role="group" :aria-labelledby="`label-${index}`" :title="t('site.list.hint')" :style="{ '--row-count': value.length }">
    <p v-if="!value.length" class="list-empty">{{ t('site.list.empty') }}</p>
    <div v-for="(item, n) in value" :key="n" class="row">
      <input :type="field.itemType === 'number' ? 'number' : 'text'" :value="field.itemType === 'number' && !Number.isFinite(item) ? '' : item" :step="field.itemType === 'number' ? 'any' : undefined" :required="field.itemType === 'number'" :aria-label="t('site.field.item', { name: label, number: n + 1 })" @input="update(n, $event)">
      <button type="button" :disabled="value.length <= field.minItems" :aria-label="t('site.field.remove', { name: label, number: n + 1 })" @click="remove(n)">{{ t('site.actions.remove') }}</button>
    </div>
  </div>
  <button ref="addButton" type="button" class="add" @click="add">{{ t('site.actions.add') }}</button>
</template>
