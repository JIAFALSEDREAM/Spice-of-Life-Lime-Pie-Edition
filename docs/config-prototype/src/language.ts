export type Language = 'en' | 'zh-CN';
export type Preference = Language | 'auto';
export type Catalogs = Record<Language, Record<string, string>>;

export function detectLanguage(preferred: string, languages: readonly string[]): Language {
  if (preferred === 'en' || preferred === 'zh-CN') return preferred;
  for (const tag of languages) {
    const base = tag.toLowerCase().split('-')[0];
    if (base === 'zh') return 'zh-CN';
    if (base === 'en') return 'en';
  }
  return 'en';
}

export function translate(catalogs: Catalogs, language: Language, key: string, params: Record<string, string | number> = {}, fallback = key): string {
  const selected = catalogs[language][key], english = catalogs.en[key];
  let text = fallback;
  if (selected?.trim()) text = selected;
  else if (english?.trim()) text = english;
  return text.replace(/\{(\w+)\}/g, (match, name: string) => params[name] === undefined ? match : String(params[name]));
}
