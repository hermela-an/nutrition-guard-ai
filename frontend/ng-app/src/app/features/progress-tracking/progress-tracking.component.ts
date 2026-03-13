import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ProfileService } from '../../core/services/profile.service';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { forkJoin } from 'rxjs';
import { finalize, switchMap, timeout, map } from 'rxjs/operators';

interface ProgressPoint {
  day: string;
  calories: number;
  water: number;
}

interface TargetResponse {
  bmi: number;
  bmr: number;
  tdee: number;
  target_calories: number;
}

interface ProgressLog {
  log_date: string;
  weight_kg: number;
  consumed?: Record<string, unknown>;
}

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, AppShellComponent],
  template: `
  <app-shell [items]="menuItems">
    <div class="loading-overlay" *ngIf="loading">
      <div class="spinner"></div>
    </div>
    <div class="error-banner" *ngIf="error">{{ error }}</div>

    <div *ngIf="!loading && !error">
      <div class="page-header">
        <div>
          <span class="eyebrow">Progress insights</span>
          <h2>Progress Tracking</h2>
          <p class="muted">Monitor your weekly calories, hydration, and weight trends.</p>
        </div>
        <div class="header-actions">
          <a class="btn secondary" routerLink="/nutrition-plan">Back to Nutrition Plan</a>
          <a class="btn primary" routerLink="/profile-setup">Update Profile</a>
        </div>
      </div>

      <div class="summary-grid">
        <div class="summary-card">
          <div class="summary-label">Weekly Avg Calories</div>
          <div class="summary-value">{{ averages.calories | number:'1.0-0' }}</div>
          <div class="summary-sub">Target {{ targets.calories | number:'1.0-0' }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">Weekly Avg Water</div>
          <div class="summary-value">{{ averages.water | number:'1.1-1' }}L</div>
          <div class="summary-sub">Target {{ targets.water | number:'1.1-1' }}L</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">Weight Trend</div>
          <div class="summary-value" [ngClass]="{'positive': averages.weightDelta < 0, 'negative': averages.weightDelta > 0}">{{ averages.weightDelta > 0 ? '+' : '' }}{{ averages.weightDelta }} kg</div>
          <div class="summary-sub">Last 7 days</div>
        </div>
        <div class="summary-card">
          <div class="summary-label">Consistency</div>
          <div class="summary-value">{{ averages.consistency }}%</div>
          <div class="summary-sub">Goal adherence</div>
        </div>
      </div>

      <div class="section-header">
        <h3>Weekly Overview</h3>
        <span class="muted">Calories and water by day</span>
      </div>

      <div class="progress-grid">
        <div class="progress-card" *ngFor="let point of weekly">
          <div class="progress-day">{{ point.day }}</div>
          <div class="progress-row">
            <span class="label">Calories</span>
            <span class="value">{{ point.calories | number:'1.0-0' }}</span>
          </div>
          <div class="bar">
            <div class="fill calories" [style.width]="percent(point.calories, targets.calories) + '%'"></div>
          </div>
          <div class="progress-row">
            <span class="label">Water</span>
            <span class="value">{{ point.water | number:'1.1-1' }}L</span>
          </div>
          <div class="bar">
            <div class="fill water" [style.width]="percent(point.water, targets.water) + '%'"></div>
          </div>
        </div>
      </div>

      <div class="section-header">
        <h3>Notes</h3>
        <span class="muted">Quick guidance</span>
      </div>

      <div class="insight-card" *ngIf="averages.consistency >= 70">
        <p><strong>Nice work:</strong> Your hydration and calorie consistency is excellent this week!</p>
        <p class="muted">Keep up the great work to see steady progress towards your goals.</p>
      </div>
      <div class="insight-card caution-insight" *ngIf="averages.consistency < 70">
        <p><strong>Keep it up:</strong> Tracking your daily intake more consistently will provide better insights.</p>
        <p class="muted">Try to log your meals every day to build a stronger habit.</p>
      </div>
    </div>
  </app-shell>
  `,
  styles: [`
    .loading-overlay {
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(255, 255, 255, 0.9);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 1000;
    }

    .spinner {
      width: 40px;
      height: 40px;
      border: 4px solid #e8f0e6;
      border-top: 4px solid var(--brand);
      border-radius: 50%;
      animation: spin 1s linear infinite;
    }

    @keyframes spin {
      0% { transform: rotate(0deg); }
      100% { transform: rotate(360deg); }
    }

    .error-banner {
      background: #fde8e8;
      border: 1px solid #f2c0c0;
      color: #8e2f2f;
      padding: 12px;
      border-radius: 12px;
      margin-bottom: 16px;
    }

    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 20px;
      margin-bottom: 18px;
    }

    .eyebrow {
      text-transform: uppercase;
      font-size: 11px;
      letter-spacing: 1.4px;
      color: #7a8a7c;
      font-weight: 700;
    }

    .page-header h2 {
      margin: 6px 0 6px;
      font-size: 28px;
      line-height: 1.1;
    }

    .header-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }

    .summary-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .summary-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
    }

    .summary-label {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.6px;
      color: var(--muted);
    }

    .summary-value {
      font-size: 1.8rem;
      font-weight: 800;
      margin-top: 6px;
    }
    
    .summary-value.positive {
      color: #2e9f61; /* Green for weight loss */
    }

    .summary-value.negative {
      color: #cf3b3b; /* Red for weight gain */
    }

    .summary-sub {
      font-size: 11px;
      margin-top: 4px;
      color: var(--muted);
    }

    .section-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin: 22px 0 10px;
    }

    .section-header h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 800;
    }

    .progress-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .progress-card {
      background: #fff;
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .progress-day {
      font-weight: 800;
      font-size: 15px;
    }

    .progress-row {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--muted);
    }

    .progress-row .value {
      font-weight: 700;
      color: var(--text);
    }

    .bar {
      height: 8px;
      background: #e8f0e6;
      border-radius: 999px;
      overflow: hidden;
      width: 100%;
    }

    .fill {
      height: 100%;
      width: 0%;
      transition: width 0.3s ease;
    }

    .fill.calories {
      background: linear-gradient(90deg, #2e9f61, #79c785);
    }

    .fill.water {
      background: linear-gradient(90deg, #3d64d8, #6d8cf0);
    }

    .insight-card {
      background: #f4f7f3;
      border: 1px solid #d8dfcf;
      border-radius: 14px;
      padding: 16px;
    }

    .caution-insight {
      background: #fcf6eb;
      border-color: #f7dfb0;
    }

    @media (max-width: 980px) {
      .summary-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .progress-grid {
        grid-template-columns: 1fr;
      }

      .page-header {
        flex-direction: column;
        align-items: flex-start;
      }
    }

    @media (max-width: 640px) {
      .summary-grid {
        grid-template-columns: 1fr;
      }

      .section-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 4px;
      }
    }
  `]
})
export class ProgressTrackingComponent implements OnInit {
  private readonly profiles = inject(ProfileService);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';

  menuItems = [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Profile Setup', path: '/profile-setup' },
    { label: 'BMI Calculator', path: '/bmi-calculator' },
    { label: 'Nutrition Plan', path: '/nutrition-plan' },
    { label: 'Daily Meal Plan', path: '/daily-meal-plan' },
    { label: 'Progress Tracking', path: '/progress-tracking' },
    { label: 'Food Search', path: '/food-search' },
    { label: 'Notifications', path: '/notifications' }
  ];

  targets = {
    calories: 2000,
    water: 2.5
  };

  averages = {
    calories: 0,
    water: 0,
    weightDelta: 0,
    consistency: 0
  };

  weekly: ProgressPoint[] = [];

  ngOnInit() {
    this.loadData();
  }

  private loadData() {
    this.loading = true;
    this.error = '';

    this.profiles.getProfile().pipe(
      timeout(5_000),
      switchMap((profile) => {
        if (!profile || !(profile as any).user_id) {
          throw new Error('Profile not found. Please complete Profile Setup first.');
        }

        return forkJoin({
          targets: this.api.post<TargetResponse>('/api/calculate/targets', {
            age: Number(profile.age),
            gender: profile.gender,
            height_cm: Number(profile.height_cm),
            weight_kg: Number(profile.weight_kg),
            activity_level: profile.activity_level,
            goal: profile.goal
          }).pipe(timeout(5_000)),
          history: this.api.get<ProgressLog[]>('/api/progress/history', this.auth.authHeaders()).pipe(timeout(5_000))
        });
      }),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: ({ targets, history }) => {
        if (targets?.target_calories) {
          this.targets.calories = Math.round(targets.target_calories);
        }
        this.processHistory(history ?? []);
      },
      error: (err) => {
        this.error = typeof err?.message === 'string' ? err.message : 'Failed to load progress data.';
      }
    });
  }

  private processHistory(history: ProgressLog[]) {
    const today = new Date();
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    
    // Sort array by date string descending
    const sorted = [...history].sort((a, b) => new Date(b.log_date).getTime() - new Date(a.log_date).getTime());
    
    let points: ProgressPoint[] = [];
    let totCal = 0, totWater = 0, count = 0;
    
    // Loop for last 7 days including today
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const dayStr = days[d.getDay()];
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      const dateStr = `${yyyy}-${mm}-${dd}`;
      
      const log = sorted.find(h => h.log_date === dateStr);
      let calories = 0;
      let water = 0;
      
      if (log && log.consumed) {
        calories = this.pickNumber(log.consumed, ['calories', 'kcal']) ?? 0;
        water = this.pickNumber(log.consumed, ['water', 'water_l']) ?? 0;
      }
      
      points.push({ day: dayStr, calories, water });
      
      if (calories > 0 || water > 0) {
        totCal += calories;
        totWater += water;
        count++;
      }
    }
    
    this.weekly = points;
    
    if (count > 0) {
      this.averages.calories = Math.round(totCal / count);
      this.averages.water = Math.round((totWater / count) * 10) / 10;
    } else {
      this.averages.calories = 0;
      this.averages.water = 0;
    }

    // Weight delta processing for the last 7 days
    const cutoff = new Date(today);
    cutoff.setDate(today.getDate() - 7);
    const recentLogs = sorted
      .filter(entry => new Date(entry.log_date) >= cutoff)
      .reverse(); // oldest to newest
    
    if (recentLogs.length >= 2) {
      const first = recentLogs[0].weight_kg ?? 0;
      const last = recentLogs[recentLogs.length - 1].weight_kg ?? 0;
      this.averages.weightDelta = Math.round((last - first) * 10) / 10;
    } else {
      this.averages.weightDelta = 0;
    }

    // Consistency %
    this.averages.consistency = Math.round((count / 7) * 100);
  }

  private pickNumber(obj: Record<string, unknown>, keys: string[]): number | null {
    for (const key of keys) {
      const value = obj[key];
      if (typeof value === 'number' && !Number.isNaN(value)) {
        return value;
      }
      if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
        return Number(value);
      }
    }
    return null;
  }

  percent(value: number, target: number): number {
    if (target <= 0) return 0;
    return Math.min(100, Math.round((value / target) * 100));
  }
}
