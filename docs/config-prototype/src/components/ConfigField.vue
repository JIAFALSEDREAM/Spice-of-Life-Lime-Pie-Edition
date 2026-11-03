<script setup lang="ts">
import { computed, nextTick, useTemplateRef } from 'vue';
import { defaultValue, fieldDescription, fieldLabel, isModified, type ConfigField, type ConfigValue, type Translator } from '../config';
import ConfigList from './ConfigList.vue';

const props = defineProps<{ field: ConfigField; index: number; t: Translator }>();
const value = defineModel<ConfigValue>({ required: true });
const box = useTemplateRef<HTMLDivElement>('box');
const label = computed(() => fieldLabel(props.field, props.t));
const description = computed(() => fieldDescription(props.field, props.t));
const changed = computed(() => isModified(props.field, value.value));
async function reset() {
  value.value = defaultValue(props.field);
  await nextTick();
  (box.value?.querySelector<HTMLInputElement>('.control input') ?? box.value?.querySelector<HTMLButtonElement>('.add'))?.focus({ preventScroll: true });
}
</script>

<template>
  <div ref="box" class="field" :class="{ changed }" :data-index="index" :data-type="field.type">
    <div class="field-top">
      <div class="field-heading">
        <component :is="field.type === 'list' ? 'span' : 'label'" :id="`label-${index}`" class="key" :for="field.type === 'list' ? undefined : `value-${index}`">{{ label }}</component>
        <code v-if="label !== field.key" class="raw-key">{{ field.key }}</code>
        <span class="edited" :class="{ 'is-visible': changed }" :aria-hidden="!changed">{{ t('site.field.modified') }}</span>
      </div>
      <button type="button" class="reset" :disabled="!changed" @click="reset">{{ t('site.actions.reset') }}</button>
    </div>
    <div class="control">
      <ConfigList v-if="field.type === 'list'" :field="field" :index="index" :label="label" :t="t" :model-value="Array.isArray(value) ? value : []" @update:model-value="value = $event" />
      <input v-else-if="field.type === 'boolean'" :id="`value-${index}`" type="checkbox" :checked="value === true" @change="value = ($event.target as HTMLInputElement).checked">
      <template v-else>
        <input :id="`value-${index}`" type="number" :value="typeof value === 'number' && Number.isFinite(value) ? value : ''" :step="field.type === 'integer' ? 1 : 'any'" :min="field.min" :max="field.max" required @input="value = ($event.target as HTMLInputElement).valueAsNumber">
        <div class="range">{{ t(field.type === 'integer' ? 'site.field.integer' : 'site.field.number') }} · {{ field.min }} ~ {{ field.max }}</div>
      </template>
    </div>
    <details class="help">
      <summary><span class="help-summary">{{ description.split('\n').find(line => line.trim()) || t('site.field.description') }}</span></summary>
      <p>{{ description }}</p>
    </details>
  </div>
</template>
