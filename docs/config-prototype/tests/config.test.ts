import assert from 'node:assert/strict';
import { test } from 'node:test';
import { execFileSync } from 'node:child_process';
import { defaultValue, exportToml, isValid, type ConfigField, type ListField, type Translator } from '../src/config.ts';
import { detectLanguage, translate } from '../src/language.ts';

const base = { scope: 'server' as const, group: 'Benefits', path: ['Benefits'], key: 'test', translation: 'config.test', comment: 'Original notes' };
const numeric: ConfigField = { ...base, type: 'integer', default: 2, min: 0, max: 10 };
const list: ListField = { ...base, type: 'list', default: [2, 4], itemType: 'number', minItems: 1 };
const fallback: Translator = (key, _params, original) => original ?? key;

test('editing defaults does not change the captured schema', () => {
  const value = defaultValue(list);
  assert(Array.isArray(value));
  value.push(6);
  assert.deepEqual(list.default, [2, 4]);
  assert.deepEqual(defaultValue(list), [2, 4]);
});

test('validation rejects incomplete numbers, wrong types and out-of-range values', () => {
  for (const value of [NaN, Infinity, -1, 11, 1.5, true, []]) assert.equal(isValid(numeric, value), false);
  for (const value of [0, 2, 10]) assert.equal(isValid(numeric, value), true);
  for (const value of [[], [NaN], [Infinity], ['2']]) assert.equal(isValid(list, value), false);
  assert.equal(isValid(list, [2.5]), true);
  assert.equal(isValid({ ...list, itemType: 'string', minItems: 0 }, []), true);
  assert.equal(isValid({ ...list, itemType: 'string' }, [2]), false);
  assert.equal(isValid({ ...base, type: 'boolean', default: false }, false), true);
});

test('TOML preserves group paths, float types, comments and escaped item IDs', () => {
  const fields: ConfigField[] = [
    { ...list, key: 'thresholds', comment: 'Line one\nLine two  ' },
    { ...base, key: 'multiplier', type: 'number', default: 1, min: 0, max: 10 },
    { ...list, group: 'Filtering', path: ['Filtering', 'Nested'], key: 'items', itemType: 'string', default: [] },
    { ...base, group: 'Filtering', path: ['Filtering', 'Nested'], key: 'enabled', type: 'boolean', default: true },
  ];
  assert.equal(exportToml(fields, [[2, 2.5], 1, ['mod:food"\\\n\b\f'], false], fallback), [
    '# Spice of Life: Lime Pie Edition', '# site.export.generated', '', '["Benefits"]',
    '# Line one', '# Line two', '"thresholds" = [2.0, 2.5]', '',
    '# Original notes', '"multiplier" = 1.0', '', '["Filtering"."Nested"]',
    '# Original notes', '"items" = ["mod:food\\"\\\\\\n\\b\\f"]', '',
    '# Original notes', '"enabled" = false', '',
  ].join('\n'));
});

test('TOML round-trips extreme floats, literal escape sequences and control characters', () => {
  const numbers = [0, 2, 1e21, -1e21, 1e-7, Number.MAX_VALUE];
  const strings = ['\\u0008', '\\u000c', '\\b', '\\f', '\b', '\f', '\u007f'];
  const fields: ConfigField[] = [{ ...list, key: 'thresholds' }, { ...list, key: 'items', itemType: 'string' }];
  const toml = exportToml(fields, [numbers, strings], fallback);
  const result = JSON.parse(execFileSync('python3', ['-c', [
    'import json, sys, tomllib',
    'data = tomllib.loads(sys.stdin.read())',
    'assert all(isinstance(value, float) for value in data["Benefits"]["thresholds"])',
    'print(json.dumps(data["Benefits"]))',
  ].join('\n')], { input: toml, encoding: 'utf-8' }));
  assert.deepEqual(result, { thresholds: numbers, items: strings });
});

test('language follows saved preference, browser order, then English', () => {
  assert.equal(detectLanguage('zh-CN', ['en-US']), 'zh-CN');
  assert.equal(detectLanguage('auto', ['fr-FR', 'zh-TW', 'en-US']), 'zh-CN');
  assert.equal(detectLanguage('auto', ['en-GB', 'zh-CN']), 'en');
  assert.equal(detectLanguage('auto', ['de-DE']), 'en');
  assert.equal(detectLanguage('invalid', []), 'en');
});

test('missing and blank translations fall back per key without losing placeholders', () => {
  const catalogs = { en: { name: 'Food {count}', blank: 'Default', empty: '' }, 'zh-CN': { name: '食物 {count}', blank: '  ' } };
  assert.equal(translate(catalogs, 'zh-CN', 'name', { count: 2 }), '食物 2');
  assert.equal(translate(catalogs, 'zh-CN', 'blank'), 'Default');
  assert.equal(translate(catalogs, 'zh-CN', 'empty', {}, 'Original'), 'Original');
  assert.equal(translate(catalogs, 'zh-CN', 'missing'), 'missing');
});
