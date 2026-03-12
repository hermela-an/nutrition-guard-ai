import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { ProfileService } from '../../core/services/profile.service';
import { finalize, map, switchMap, timeout } from 'rxjs/operators';
import { forkJoin } from 'rxjs';

interface MealItem {
  name: string;
  portion: string;
  calories: number;
  traffic_light?: string;
}

interface MealBlock {
  title: string;
  time: string;
  targetCalories: number;
  items: MealItem[];
}

interface MealPlanResponse {
  goal?: string;
  breakfast: MealItem[];
  lunch: MealItem[];
  dinner: MealItem[];
  snacks?: MealItem[];
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

    <div class="page-header">
      <div>
        <span class="eyebrow">Daily planner</span>
        <h2>Daily Meal Plan</h2>
        <p class="muted">A structured plan with balanced portions and a clear calorie budget.</p>
      </div>
      <div class="header-actions">
        <a class="btn secondary" routerLink="/nutrition-plan">Back to Nutrition Plan</a>
        <a class="btn primary" routerLink="/profile-setup">Update Profile</a>
      </div>
    </div>

    <div class="summary-grid" *ngIf="!loading && !error">
      <div class="summary-card">
        <div class="summary-label">Target Calories</div>
        <div class="summary-value">{{ totals.targetCalories | number:'1.0-0' }}</div>
        <div class="summary-sub">Daily goal</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Planned Calories</div>
        <div class="summary-value">{{ totals.plannedCalories | number:'1.0-0' }}</div>
        <div class="summary-sub">Meals combined</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Protein Target</div>
        <div class="summary-value">{{ totals.protein }}g</div>
        <div class="summary-sub">Macro focus</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Hydration</div>
        <div class="summary-value">{{ totals.waterLiters }}L</div>
        <div class="summary-sub">Daily reminder</div>
      </div>
    </div>

    <div class="section-header" *ngIf="!loading && !error">
      <h3>Meal Schedule</h3>
      <span class="muted">Portions and targets by time</span>
    </div>

    <div class="meal-grid" *ngIf="!loading && !error">
      <article class="meal-card" *ngFor="let meal of meals">
        <div class="meal-header">
          <div>
            <div class="meal-title">{{ meal.title }}</div>
            <div class="meal-time">{{ meal.time }}</div>
          </div>
          <div class="meal-target">
            <span class="label">Target</span>
            <span class="value">{{ meal.targetCalories | number:'1.0-0' }} cal</span>
          </div>
        </div>
        <ul class="meal-items">
          <li *ngFor="let item of meal.items">
            <div class="item-name">{{ item.name }}</div>
            <div class="item-meta">
              <span>{{ item.portion }}</span>
              <span>{{ item.calories | number:'1.0-0' }} cal</span>
            </div>
          </li>
        </ul>
      </article>
    </div>

    <div class="empty-card" *ngIf="!loading && !error && meals.length === 0">
      <p class="muted">No meal suggestions yet. Update your profile or try again.</p>
    </div>

    <div class="section-header" *ngIf="!loading && !error">
      <h3>Daily Progress</h3>
      <span class="muted">Planned vs target calories</span>
    </div>

    <div class="progress-card" *ngIf="!loading && !error">
      <div class="progress-header">
        <span class="label">Calories</span>
        <span class="value">{{ totals.plannedCalories | number:'1.0-0' }} / {{ totals.targetCalories | number:'1.0-0' }}</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" [style.width]="progressPercentage + '%'"></div>
      </div>
      <div class="progress-sub muted">Aim to stay within 5% of your target.</div>
    </div>
  </app-shell>
  `,
  styles: [`
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 20px;
      margin-bottom: 18px;
    }

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

    .meal-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .meal-card {
      background: #fff;
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .meal-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
    }

    .meal-title {
      font-weight: 800;
      font-size: 16px;
    }

    .meal-time {
      font-size: 12px;
      color: var(--muted);
      margin-top: 2px;
    }

    .meal-target {
      text-align: right;
      background: #f4f7f3;
      padding: 6px 10px;
      border-radius: 10px;
    }

    .meal-target .label {
      display: block;
      font-size: 10px;
      text-transform: uppercase;
      color: #7a8a7c;
      letter-spacing: 0.6px;
    }

    .meal-target .value {
      font-weight: 700;
      font-size: 12px;
    }

    .meal-items {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .meal-items li {
      border: 1px solid #e6ede3;
      border-radius: 10px;
      padding: 8px 10px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 10px;
    }

    .item-name {
      font-weight: 600;
    }

    .item-meta {
      display: flex;
      gap: 10px;
      font-size: 12px;
      color: var(--muted);
    }

    .empty-card {
      background: #fff;
      border: 1px dashed #d8dfcf;
      border-radius: 12px;
      padding: 16px;
      margin-bottom: 16px;
    }

    .progress-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
    }

    .progress-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
    }

    .progress-header .label {
      font-weight: 700;
    }

    .progress-header .value {
      font-weight: 800;
      font-size: 1.2rem;
    }

    .progress-bar {
      height: 12px;
      background: #e8f0e6;
      border-radius: 999px;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      width: 0%;
      background: linear-gradient(90deg, #2e9f61, #7ac37f);
      transition: width 0.3s ease;
    }

    .progress-sub {
      margin-top: 8px;
      font-size: 12px;
    }

    @media (max-width: 980px) {
      .summary-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .meal-grid {
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
export class DailyMealPlanComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly profiles = inject(ProfileService);
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
    { label: 'AI Chat Assistant', path: '/ai-chat-assistant' },
    { label: 'Food Search', path: '/food-search' },
    { label: 'Notifications', path: '/notifications' }
  ];

  meals: MealBlock[] = [];

  totals = {
    targetCalories: 0,
    plannedCalories: 0,
    protein: 0,
    waterLiters: 2.5
  };

  get progressPercentage(): number {
    if (this.totals.targetCalories <= 0) return 0;
    return Math.min(100, Math.round((this.totals.plannedCalories / this.totals.targetCalories) * 100));
  }

  constructor() {
    this.loadPlan();
  }

  private loadPlan(): void {
    this.loading = true;
    this.error = '';

    this.profiles.getProfile().pipe(
      timeout(5_000),
      switchMap((profile) => {
        if (!profile || !(profile as { user_id?: string }).user_id) {
          throw new Error('Profile not found. Please complete Profile Setup first.');
        }
        return forkJoin({
          targets: this.api.post<{ target_calories: number }>('/api/calculate/targets', {
            age: Number(profile.age),
            gender: profile.gender,
            height_cm: Number(profile.height_cm),
            weight_kg: Number(profile.weight_kg),
            activity_level: profile.activity_level,
            goal: profile.goal
          }).pipe(timeout(5_000)),
          plan: this.api.get<MealPlanResponse>('/api/meal-plan/daily', this.auth.authHeaders()).pipe(timeout(5_000))
        });
      }),
      map(({ targets, plan }) => ({ targets, plan })),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: ({ targets, plan }) => {
        this.totals.targetCalories = Math.round(targets.target_calories ?? 0);

        this.meals = [
          this.toMeal('Breakfast', '07:30', plan.breakfast ?? []),
          this.toMeal('Lunch', '12:45', plan.lunch ?? []),
          this.toMeal('Dinner', '19:00', plan.dinner ?? [])
        ];

        this.totals.plannedCalories = this.meals.reduce((sum, meal) => sum + meal.targetCalories, 0);
        this.totals.protein = 0;
      },
      error: (err) => {
        this.error = typeof err?.message === 'string' ? err.message : 'Failed to load daily meal plan.';
      }
    });
  }

  private toMeal(title: string, time: string, items: MealItem[]): MealBlock {
    const normalized = items.map((item) => ({
      name: item.name,
      portion: item.portion ?? '1 serving',
      calories: Number(item.calories ?? 0),
      traffic_light: item.traffic_light
    }));

    const targetCalories = normalized.reduce((sum, item) => sum + item.calories, 0);

    return {
      title,
      time,
      targetCalories,
      items: normalized
    };
  }
}
