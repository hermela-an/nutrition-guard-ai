import { Injectable } from '@angular/core';

const THEME_KEY = 'ng_theme';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  constructor() {
    const saved = (localStorage.getItem(THEME_KEY) ?? '').toLowerCase();
    if (saved === 'dark') {
      document.documentElement.dataset['theme'] = 'dark';
    }
  }

  isDark(): boolean {
    return (document.documentElement.dataset['theme'] ?? '').toLowerCase() === 'dark';
  }

  toggle(): void {
    const next = this.isDark() ? 'light' : 'dark';
    if (next === 'dark') {
      document.documentElement.dataset['theme'] = 'dark';
    } else {
      delete document.documentElement.dataset['theme'];
    }
    localStorage.setItem(THEME_KEY, next);
  }
}

