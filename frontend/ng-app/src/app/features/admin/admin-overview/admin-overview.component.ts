import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { finalize, timeout } from 'rxjs/operators';
import { AdminFoodsService, AdminFoodItem } from '../../../core/services/admin-foods.service';
import { AdminStatsService, AdminStats } from '../../../core/services/admin-stats.service';
import { AdminAnalyticsService, FoodSearchDay } from '../../../core/services/admin-analytics.service';

@Component({
  standalone: true,
  imports: [CommonModule],
  templateUrl: './admin-overview.component.html'
})
export class AdminOverviewComponent {
  private readonly foodsApi = inject(AdminFoodsService);
  private readonly statsApi = inject(AdminStatsService);
  private readonly analyticsApi = inject(AdminAnalyticsService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';

  stats: AdminStats = { total_users: 0, total_foods: 0, avoid_foods: 0, caution_foods: 0 };
  riskyFoods: AdminFoodItem[] = [];
  searchDays: FoodSearchDay[] = [];
  searchLinePoints = '';
  searchAreaPath = '';
  searchMax = 0;
  searchTotal = 0;

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';

    let statsDone = false;
    let foodsDone = false;
    let analyticsDone = false;

    const finish = () => {
      if (statsDone && foodsDone && analyticsDone) {
        this.loading = false;
        this.cdr.detectChanges();
      }
    };

    this.statsApi
      .get()
      .pipe(
        timeout(8_000),
        finalize(() => {
          statsDone = true;
          finish();
        })
      )
      .subscribe({
        next: (s) => (this.stats = s),
        error: (err) => (this.error = this.error || this.errorFrom(err, 'Failed to load admin stats.'))
      });

    this.foodsApi
      .list({ limit: 120, offset: 0 })
      .pipe(
        timeout(8_000),
        finalize(() => {
          foodsDone = true;
          finish();
        })
      )
      .subscribe({
        next: (items) => {
          const list = (items ?? []).filter((f) => {
            const light = (f.traffic_light ?? '').toString().toUpperCase();
            return light === 'AVOID' || light === 'RED';
          });
          this.riskyFoods = list.slice(0, 6);
        },
        error: (err) => (this.error = this.error || this.errorFrom(err, 'Failed to load risky foods panel.'))
      });

    this.analyticsApi
      .foodSearches(14)
      .pipe(
        timeout(8_000),
        finalize(() => {
          analyticsDone = true;
          finish();
        })
      )
      .subscribe({
        next: (res) => {
          this.searchDays = res?.days ?? [];
          this.searchTotal = this.searchDays.reduce((sum, d) => sum + Number(d?.count ?? 0), 0);
          this.searchMax = this.searchDays.reduce((mx, d) => Math.max(mx, Number(d?.count ?? 0)), 0);
          const { line, area } = this.buildSpark(this.searchDays.map((d) => Number(d?.count ?? 0)));
          this.searchLinePoints = line;
          this.searchAreaPath = area;
        },
        error: (err) => (this.error = this.error || this.errorFrom(err, 'Failed to load analytics.'))
      });
  }

  lightClass(light: string | null | undefined): 'safe' | 'caution' | 'avoid' {
    const normalized = (light ?? '').toString().toUpperCase();
    if (normalized === 'AVOID' || normalized === 'RED') return 'avoid';
    if (normalized === 'CAUTION' || normalized === 'YELLOW') return 'caution';
    return 'safe';
  }

  private buildSpark(values: number[]): { line: string; area: string } {
    const w = 100;
    const h = 40;
    const pad = 3;
    const innerW = w - pad * 2;
    const innerH = h - pad * 2;
    const safe = (values ?? []).map((v) => (Number.isFinite(v) ? Math.max(0, v) : 0));
    const max = Math.max(1, ...safe);
    const n = Math.max(1, safe.length);
    const step = n <= 1 ? innerW : innerW / (n - 1);

    const pts = safe.map((v, i) => {
      const x = pad + i * step;
      const y = pad + innerH - (v / max) * innerH;
      return { x, y };
    });

    const line = pts.map((p) => `${p.x.toFixed(2)},${p.y.toFixed(2)}`).join(' ');
    const area =
      pts.length === 0
        ? ''
        : `M ${pts[0].x.toFixed(2)} ${h - pad} L ${pts.map((p) => `${p.x.toFixed(2)} ${p.y.toFixed(2)}`).join(' L ')} L ${
            pts[pts.length - 1].x.toFixed(2)
          } ${h - pad} Z`;

    return { line, area };
  }

  private errorFrom(err: any, fallback: string): string {
    const status = Number(err?.status ?? -1);
    if (status === 0) return 'Cannot reach server. Please ensure backend is running.';
    if (status === 401 || status === 403) return 'Not authorized. Please log in with an ADMIN account.';
    if (status === 404) return 'Endpoint not found. Restart backend with the latest code.';
    if (typeof err?.error === 'string' && err.error.trim()) return err.error;
    if (typeof err?.message === 'string' && err.message.trim()) return err.message;
    return fallback;
  }
}
