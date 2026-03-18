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
  food_name: string;
  portion?: string;
  portion_amount?: number | null;
  portion_unit?: string | null;
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
  templateUrl: './daily-meal-plan.component.html',
  styleUrls: ['./daily-meal-plan.component.css']
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
      food_name: item.food_name,
      portion: item.portion ?? this.formatPortion(item),
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

  private formatPortion(item: MealItem): string {
    const amount = this.formatAmount(item.portion_amount);
    const unit = (item.portion_unit ?? '').trim();
    if (amount && unit) return `${amount} ${unit}`;
    if (amount) return amount;
    return '1 serving';
  }

  private formatAmount(value: unknown): string {
    const numberValue = this.toNumber(value);
    if (numberValue <= 0) return '';
    if (Math.abs(numberValue - Math.round(numberValue)) < 0.001) {
      return String(Math.round(numberValue));
    }
    return String(numberValue);
  }

  private toNumber(value: unknown): number {
    if (typeof value === 'number' && !Number.isNaN(value)) return value;
    if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return 0;
  }
}
