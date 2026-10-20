import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import en from './locales/en.json';

export const LANGUAGES = [
  { code: 'en', label: 'English' },
  { code: 'rw', label: 'Ikinyarwanda' },
  { code: 'fr', label: 'Français' },
] as const;

const STORAGE_KEY = 'ptc-lang';
// English is bundled (it is also the fallback); the others load on demand
// and are precached by the service worker for offline use.
const loaders: Record<string, () => Promise<{ default: Record<string, unknown> }>> = {
  fr: () => import('./locales/fr.json'),
  rw: () => import('./locales/rw.json'),
};

function initialLanguage(): string {
  try {
    const saved = localStorage.getItem(STORAGE_KEY);
    if (saved && ['en', 'fr', 'rw'].includes(saved)) return saved;
  } catch {
    // storage unavailable
  }
  const nav = typeof navigator !== 'undefined' ? navigator.language.slice(0, 2) : 'en';
  return ['fr', 'rw'].includes(nav) ? nav : 'en';
}

void i18n.use(initReactI18next).init({
  resources: { en: { translation: en } },
  lng: 'en',
  fallbackLng: 'en',
  interpolation: { escapeValue: false },
  returnNull: false,
});

async function ensureLoaded(lng: string): Promise<void> {
  if (i18n.hasResourceBundle(lng, 'translation') || !loaders[lng]) return;
  const mod = await loaders[lng]();
  i18n.addResourceBundle(lng, 'translation', mod.default, true, true);
}

/** Language preference is a per-device convenience; the profile locale wins after sign-in. */
export function setLanguage(lng: string): void {
  void ensureLoaded(lng).then(() => i18n.changeLanguage(lng));
  try {
    localStorage.setItem(STORAGE_KEY, lng);
  } catch {
    // ignore
  }
  document.documentElement.lang = lng;
}

const first = initialLanguage();
if (first !== 'en') setLanguage(first);

export default i18n;
