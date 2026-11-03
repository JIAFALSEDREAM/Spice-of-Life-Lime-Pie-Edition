import { computed, onUnmounted, ref, watch, watchEffect } from 'vue';
import { detectLanguage, translate, type Catalogs, type Preference } from './language';
import type { Translator } from './config';

const preferenceKey = 'sollimepie.site.language';
function readPreference(): Preference {
  try { const saved = localStorage.getItem(preferenceKey); return saved === 'en' || saved === 'zh-CN' ? saved : 'auto'; }
  catch { return 'auto'; }
}

export function useLocale(catalogs: Catalogs) {
  const preference = ref(readPreference());
  const languages = ref(navigator.languages?.length ? [...navigator.languages] : [navigator.language]);
  const language = computed(() => detectLanguage(preference.value, languages.value));
  const t: Translator = (key, params, fallback) => translate(catalogs, language.value, key, params, fallback);
  watchEffect(() => { document.documentElement.lang = language.value; document.title = t('site.title'); });
  watch(preference, value => {
    try { if (value === 'auto') localStorage.removeItem(preferenceKey); else localStorage.setItem(preferenceKey, value); }
    catch { /* Language switching also works with storage disabled. */ }
  });
  const browserChanged = () => { languages.value = navigator.languages?.length ? [...navigator.languages] : [navigator.language]; };
  window.addEventListener('languagechange', browserChanged);
  onUnmounted(() => window.removeEventListener('languagechange', browserChanged));
  return { preference, t };
}
