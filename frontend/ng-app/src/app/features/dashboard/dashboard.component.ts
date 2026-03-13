import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ProfileService } from '../../core/services/profile.service';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { forkJoin } from 'rxjs';
import { finalize, map, switchMap, timeout } from 'rxjs/operators';

interface TargetResponse {
  bmi: number;
  bmr: number;
  tdee: number;
  target_calories: number;
}

interface MealPlanItem {
  name: string;
  calories: number;
}

interface MealPlanResponse {
  goal?: string;
  breakfast: MealPlanItem[];
  lunch: MealPlanItem[];
  dinner: MealPlanItem[];
}

interface ProgressLog {
  log_date: string;
  weight_kg: number;
  consumed?: Record<string, unknown>;
}

@Component({
  standalone: true,
  imports: [CommonModule, AppShellComponent],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.css']
})
export class DashboardComponent implements OnInit {
  private readonly profiles = inject(ProfileService);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';
  targets: TargetResponse | null = null;
  plan: MealPlanResponse | null = null;
  goalLabel = 'Maintain';
  bmiLabel = 'Normal';
  constraintsCount = 0;
  sodiumDisplay = '—';
  sodiumNote = 'No logs';
  weightTrendDisplay = '—';
  weightTrendNote = 'No logs';

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

  ngOnInit(): void {
    this.loadDashboard();
  }

  private loadDashboard(): void {
    this.loading = true;
    this.error = '';
    this.profiles.getProfile().pipe(
      timeout(5_000),
      switchMap((profile) => {
        if (!profile || !(profile as { user_id?: string }).user_id) {
          throw new Error('Profile not found. Please complete Profile Setup first.');
        }
        this.goalLabel = this.normalizeGoal(profile.goal);
        this.constraintsCount = this.countConstraints(profile.health_metadata ?? {});

        return forkJoin({
          targets: this.api.post<TargetResponse>('/api/calculate/targets', {
            age: Number(profile.age),
            gender: profile.gender,
            height_cm: Number(profile.height_cm),
            weight_kg: Number(profile.weight_kg),
            activity_level: profile.activity_level,
            goal: profile.goal
          }).pipe(timeout(5_000)),
          plan: this.api.get<MealPlanResponse>('/api/meal-plan/daily', this.auth.authHeaders()).pipe(timeout(5_000)),
          history: this.api.get<ProgressLog[]>('/api/progress/history', this.auth.authHeaders()).pipe(timeout(5_000))
        }).pipe(map((res) => ({ ...res })));
      }),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: ({ targets, plan, history }) => {
        this.targets = targets;
        this.plan = {
          breakfast: plan.breakfast?.slice(0, 3) ?? [],
          lunch: plan.lunch?.slice(0, 3) ?? [],
          dinner: plan.dinner?.slice(0, 3) ?? []
        };
        this.bmiLabel = this.bmiCategory(targets.bmi);
        this.applyHistoryMetrics(history ?? []);
      },
      error: (err) => {
        this.error = typeof err?.message === 'string' ? err.message : 'Failed to load dashboard.';
      }
    });
  }

  private normalizeGoal(goal: string | null | undefined): string {
    const normalized = (goal ?? '').toLowerCase();
    if (normalized === 'weight_loss') return 'Weight Loss';
    if (normalized === 'weight_gain') return 'Weight Gain';
    if (normalized === 'improve_health') return 'Improve Health';
    return 'Maintain';
  }

  private bmiCategory(bmi: number): string {
    if (bmi < 18.5) return 'Underweight';
    if (bmi < 25) return 'Normal';
    if (bmi < 30) return 'Overweight';
    return 'Obese';
  }

  private countConstraints(health: Record<string, boolean>): number {
    return Object.values(health).filter(Boolean).length;
  }

  private applyHistoryMetrics(history: ProgressLog[]): void {
    if (!history || history.length === 0) {
      this.sodiumDisplay = '—';
      this.sodiumNote = 'No logs';
      this.weightTrendDisplay = '—';
      this.weightTrendNote = 'No logs';
      return;
    }

    const today = new Date();
    const cutoff = new Date(today);
    cutoff.setDate(today.getDate() - 30);

    const recent = history
      .map((entry) => ({
        ...entry,
        date: new Date(entry.log_date + 'T00:00:00')
      }))
      .filter((entry) => !Number.isNaN(entry.date.getTime()) && entry.date >= cutoff);

    if (recent.length >= 2) {
      const first = recent[0].weight_kg ?? 0;
      const last = recent[recent.length - 1].weight_kg ?? 0;
      const delta = Math.round((last - first) * 10) / 10;
      this.weightTrendDisplay = `${delta >= 0 ? '+' : ''}${delta} kg`;
      this.weightTrendNote = delta < 0 ? 'Progressing' : delta > 0 ? 'Gaining' : 'Stable';
    } else {
      this.weightTrendDisplay = '—';
      this.weightTrendNote = 'Need more logs';
    }

    const latest = history[history.length - 1];
    const consumed = latest?.consumed ?? {};
    const sodium = this.pickNumber(consumed, ['sodium_mg', 'sodium', 'sodiumMg']);
    if (sodium !== null) {
      this.sodiumDisplay = `${Math.round(sodium)} mg`;
      this.sodiumNote = sodium > 1500 ? 'Above target' : 'Within target';
    } else {
      this.sodiumDisplay = '—';
      this.sodiumNote = 'No sodium data';
    }
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
}
