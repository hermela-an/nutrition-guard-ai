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
  id: number;
  food_name: string;
  image_url?: string | null;
  portion?: string;
  portion_amount?: number | null;
  portion_unit?: string | null;
  calories: number;
  traffic_light?: string;
  eaten?: boolean;
  sides?: MealItem[];
}

interface MealBlock {
  title: string;
  time: string;
  targetCalories: number;
  items: Array<MealItem & { meal_type: string }>;
}

interface MealPlanResponse {
  goal?: string;
  breakfast: MealItem[];
  lunch: MealItem[];
  dinner: MealItem[];
  snacks?: MealItem[];
  progress?: { total: number; eaten: number };
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

  private readonly intakeStoragePrefix = 'ng:intake:daily:';
  private readonly defaultFoodImageUrl =
    'https://images.unsplash.com/photo-1490645935967-10de6ba17061?auto=format&fit=crop&w=400&q=80';

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
    waterLiters: 2.5,
    totalRecommended: 0,
    eatenRecommended: 0
  };

  get progressPercentage(): number {
    if (this.totals.targetCalories <= 0) return 0;
    return Math.min(100, Math.round((this.totals.plannedCalories / this.totals.targetCalories) * 100));
  }

  get eatenPercentage(): number {
    if (this.totals.totalRecommended <= 0) return 0;
    return Math.min(100, Math.round((this.totals.eatenRecommended / this.totals.totalRecommended) * 100));
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
          this.toMeal('Breakfast', '07:30', 'breakfast', plan.breakfast ?? []),
          this.toMeal('Lunch', '12:45', 'lunch', plan.lunch ?? []),
          this.toMeal('Dinner', '19:00', 'dinner', plan.dinner ?? [])
        ];

        this.applyLocalIntakeFallback(plan);
        this.recomputeEatenTotals();

        this.totals.plannedCalories = this.meals.reduce((sum, meal) => sum + meal.targetCalories, 0);
        this.totals.protein = 0;
      },
      error: (err) => {
        this.error = typeof err?.message === 'string' ? err.message : 'Failed to load daily meal plan.';
      }
    });
  }

  private toMeal(title: string, time: string, mealType: string, items: MealItem[]): MealBlock {
    const normalized = items.map((item) => ({
      id: item.id,
      food_name: item.food_name,
      image_url: item.image_url ?? null,
      portion: item.portion ?? this.formatPortion(item),
      calories: Number(item.calories ?? 0),
      traffic_light: item.traffic_light,
      eaten: !!item.eaten,
      meal_type: mealType,
      sides: item.sides ? item.sides.map(s => ({
        ...s,
        portion: s.portion ?? this.formatPortion(s),
        calories: Number(s.calories ?? 0)
      })) : undefined
    }));

    const targetCalories = normalized.reduce((sum, item) => sum + item.calories, 0);

    return {
      title,
      time,
      targetCalories,
      items: normalized
    };
  }

  imageFor(item: MealItem): string {
    const raw = (item?.image_url ?? '').toString().trim();
    return raw ? raw : this.defaultFoodImageUrl;
  }

  onFoodImageError(evt: Event): void {
    const img = evt.target as HTMLImageElement | null;
    if (!img) return;
    if (img.dataset['fallbackApplied'] === '1') return;
    img.dataset['fallbackApplied'] = '1';
    img.src = this.defaultFoodImageUrl;
  }

  toggleEaten(item: MealItem & { meal_type?: string }): void {
    const mealType = (item as unknown as { meal_type?: string }).meal_type ?? '';
    if (!mealType || !item?.id) return;

    const next = !item.eaten;
    item.eaten = next;
    this.totals.eatenRecommended = Math.max(
      0,
      Math.min(this.totals.totalRecommended, this.totals.eatenRecommended + (next ? 1 : -1))
    );
    this.persistLocalIntake(mealType, item.id, next);
    this.cdr.detectChanges();

    this.api.put<{ progress?: { total: number; eaten: number } }>('/api/meal-plan/intake', {
      date: new Date().toISOString().slice(0, 10),
      meal_type: mealType,
      food_item_id: item.id,
      eaten: next
    }, this.auth.authHeaders()).subscribe({
      next: () => {},
      error: () => {
        // rollback on error
        item.eaten = !next;
        this.totals.eatenRecommended = Math.max(
          0,
          Math.min(this.totals.totalRecommended, this.totals.eatenRecommended + (next ? -1 : 1))
        );
        this.persistLocalIntake(mealType, item.id, !next);
        this.cdr.detectChanges();
      }
    });
  }

  private recomputeEatenTotals(): void {
    const all = this.meals.flatMap((m) => m.items ?? []);
    this.totals.totalRecommended = all.length;
    this.totals.eatenRecommended = all.reduce((sum, item) => sum + (item.eaten ? 1 : 0), 0);
  }

  private applyLocalIntakeFallback(plan: MealPlanResponse): void {
    const today = new Date().toISOString().slice(0, 10);
    const storage = this.readLocalIntake(today);

    const rawItems = [...(plan.breakfast ?? []), ...(plan.lunch ?? []), ...(plan.dinner ?? [])];
    const serverProvidesEaten = rawItems.some((it) => Object.prototype.hasOwnProperty.call(it, 'eaten'));
    if (serverProvidesEaten) return;

    for (const meal of this.meals) {
      for (const item of meal.items) {
        const key = this.intakeKey(item.meal_type, item.id);
        if (storage[key] === true) item.eaten = true;
        else if (storage[key] === false) item.eaten = false;
      }
    }
  }

  private intakeKey(mealType: string, foodId: number): string {
    return `${mealType}:${foodId}`;
  }

  private readLocalIntake(date: string): Record<string, boolean> {
    const key = `${this.intakeStoragePrefix}${date}`;
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return {};
      const parsed = JSON.parse(raw) as unknown;
      if (parsed && typeof parsed === 'object') return parsed as Record<string, boolean>;
      return {};
    } catch {
      return {};
    }
  }

  private persistLocalIntake(mealType: string, foodId: number, eaten: boolean): void {
    const today = new Date().toISOString().slice(0, 10);
    const key = `${this.intakeStoragePrefix}${today}`;
    const storage = this.readLocalIntake(today);
    storage[this.intakeKey(mealType, foodId)] = eaten;
    try {
      localStorage.setItem(key, JSON.stringify(storage));
    } catch {
      // ignore storage failures
    }
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
