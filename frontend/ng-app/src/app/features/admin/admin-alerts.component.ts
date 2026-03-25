import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { finalize, timeout } from 'rxjs/operators';
import { AdminFoodsService, AdminFoodItem } from '../../core/services/admin-foods.service';

type AlertFilter = 'AVOID' | 'CAUTION';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-alerts.component.html'
})
export class AdminAlertsComponent {
  private readonly foodsApi = inject(AdminFoodsService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';

  filter: AlertFilter = 'AVOID';
  query = '';

  foods: AdminFoodItem[] = [];

  constructor() {
    this.refresh();
  }

  refresh(): void {
    this.loading = true;
    this.error = '';
    this.foodsApi
      .list({ q: this.query, limit: 200, offset: 0 })
      .pipe(
        timeout(8_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: (items) => {
          const list = items ?? [];
          const wanted = this.filter === 'AVOID' ? 'RED' : 'YELLOW';
          this.foods = list.filter((f) => (f.traffic_light ?? '').toString().toUpperCase() === wanted);
        },
        error: (err) => (this.error = this.errorFrom(err, 'Failed to load alerts.'))
      });
  }

  setFilter(next: AlertFilter): void {
    this.filter = next;
    this.refresh();
  }

  lightClass(light: string | null | undefined): 'safe' | 'caution' | 'avoid' {
    const normalized = (light ?? '').toString().toUpperCase();
    if (normalized === 'RED') return 'avoid';
    if (normalized === 'YELLOW') return 'caution';
    return 'safe';
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
