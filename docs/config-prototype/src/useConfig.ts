import { computed, ref } from 'vue';
import { defaultValue, exportToml, fieldDescription, fieldLabel, isModified, isValid, type ConfigField, type ConfigValue, type Scope, type Translator } from './config';

export function useConfig(schema: ConfigField[], t: Translator) {
  const scope = ref<Scope>('server'), query = ref('');
  const values = ref<ConfigValue[]>(schema.map(defaultValue));
  const indices = computed(() => schema.flatMap((field, index) => field.scope === scope.value ? [index] : []));
  const groupLabel = (name: string) => t('site.groups.' + name, {}, name);
  const sections = (['server', 'client'] as const).flatMap(scope => [...new Set(schema.filter(field => field.scope === scope).map(field => field.group))]
    .map(name => ({ scope, name, indices: schema.flatMap((field, index) => field.scope === scope && field.group === name ? [index] : []) })));
  const visible = computed(() => new Set(indices.value.filter(index => {
    const field = schema[index]!;
    return `${field.key} ${field.translation} ${field.comment} ${fieldLabel(field, t)} ${fieldDescription(field, t)} ${groupLabel(field.group)}`.toLowerCase().includes(query.value.toLowerCase());
  })));
  const wide = computed(() => new Set(sections.flatMap(section => {
    const members = section.indices.filter(index => visible.value.has(index));
    return members.length % 2 ? [members.at(-1)!] : [];
  })));
  const valid = computed(() => indices.value.every(index => isValid(schema[index]!, values.value[index]!)));
  const changed = computed(() => indices.value.filter(index => isModified(schema[index]!, values.value[index]!)).length);
  const toml = computed(() => valid.value ? exportToml(indices.value.map(index => schema[index]!), indices.value.map(index => values.value[index]!), t) : t('site.invalid'));
  const filename = computed(() => `sollimepie-${scope.value}.toml`);
  const resetAll = () => { for (const index of indices.value) values.value[index] = defaultValue(schema[index]!); };
  return { scope, query, values, sections, visible, wide, valid, changed, toml, filename, groupLabel, resetAll };
}
