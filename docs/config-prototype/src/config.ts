export type Scope = 'server' | 'client';
export type Entry = string | number;
export type ConfigValue = boolean | number | Entry[];
type BaseField = { scope: Scope; group: string; path: string[]; key: string; translation: string; comment: string };
export type ListField = BaseField & { type: 'list'; default: Entry[]; itemType: 'number' | 'string'; minItems: number };
export type ConfigField = ListField
  | (BaseField & { type: 'boolean'; default: boolean })
  | (BaseField & { type: 'number' | 'integer'; default: number; min: number; max: number });
export type Translator = (key: string, params?: Record<string, string | number>, fallback?: string) => string;

export const defaultValue = (field: ConfigField): ConfigValue => Array.isArray(field.default) ? [...field.default] : field.default;
export const isModified = (field: ConfigField, value: ConfigValue) => JSON.stringify(value) !== JSON.stringify(field.default);
export const fieldLabel = (field: ConfigField, t: Translator) => t(field.translation, {}, field.key);
export const fieldDescription = (field: ConfigField, t: Translator) => t(field.translation + '.description', {}, field.comment);

export function isValid(field: ConfigField, value: ConfigValue): boolean {
  if (field.type === 'list') return Array.isArray(value) && value.length >= field.minItems
    && value.every(item => field.itemType === 'number' ? typeof item === 'number' && Number.isFinite(item) : typeof item === 'string');
  if (field.type === 'boolean') return typeof value === 'boolean';
  return typeof value === 'number' && Number.isFinite(value)
    && (field.type !== 'integer' || Number.isInteger(value)) && value >= field.min && value <= field.max;
}

function tomlValue(value: Entry | boolean, type: string): string {
  if (type === 'number' && typeof value === 'number') {
    const text = String(value);
    return /[.eE]/.test(text) ? text : `${text}.0`;
  }
  return JSON.stringify(value).replace(/\u007f/g, '\\u007f');
}

export function exportToml(fields: ConfigField[], values: ConfigValue[], t: Translator): string {
  const output = ['# Spice of Life: Lime Pie Edition', '# ' + t('site.export.generated'), ''];
  let previous: string | undefined;
  fields.forEach((field, index) => {
    if (previous !== field.group) { output.push(`[${field.path.map(part => JSON.stringify(part)).join('.')}]`); previous = field.group; }
    for (const line of fieldDescription(field, t).split('\n')) output.push(`# ${line.trimEnd()}`);
    const value = values[index]!;
    const text = field.type === 'list' && Array.isArray(value)
      ? '[' + value.map(item => tomlValue(item, field.itemType)).join(', ') + ']'
      : tomlValue(value as number | boolean, field.type);
    output.push(`${JSON.stringify(field.key)} = ${text}`, '');
  });
  return output.join('\n');
}
