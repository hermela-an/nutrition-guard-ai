import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ProfilePayload, ProfileService } from '../../core/services/profile.service';
import { ApiService } from '../../core/services/api.service';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { AuthService } from '../../core/services/auth.service';
import { EMPTY, TimeoutError, forkJoin } from 'rxjs';
import { finalize, map, switchMap, timeout } from 'rxjs/operators';

interface TargetResponse {
  bmi: number;
  bmr: number;
  tdee: number;
  target_calories: number;
}

interface MacroTarget {
  label: string;
  value: string;
  percentage: number;
  calories: number;
  color: string;
}

interface Constraint {
  medical: string[];
  goal: string[];
}

interface MealSuggestion {
  meal: string;
  items: string[];
  emoji: string;
}

interface MealPlanItem {
  id?: number;
  food_name: string;
  calories: number;
  portion_amount?: number | null;
  portion_unit?: string | null;
  traffic_light?: string;
  eaten?: boolean;
}

interface MealPlanResponse {
  goal?: string;
  breakfast: MealPlanItem[];
  lunch: MealPlanItem[];
  dinner: MealPlanItem[];
  snacks?: MealPlanItem[];
}

interface ProgressData {
  calories: { current: number; target: number };
  water: { current: number; target: number };
}

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, AppShellComponent],
  templateUrl: './nutrition-plan.component.html',
  styleUrls: ['./nutrition-plan.component.css']
})
export class NutritionPlanComponent implements OnInit {
  private readonly profileService = inject(ProfileService);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';
  targets: TargetResponse | null = null;
  macros: MacroTarget[] = [];
  constraints: Constraint = { medical: [], goal: [] };
  mealSuggestions: MealSuggestion[] = [];
  progress: ProgressData = {
    calories: { current: 0, target: 0 },
    water: { current: 0, target: 2.5 }
  };
  
  bmiCategory = { text: 'Normal', color: '#2e9f61' };

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
    this.loadPlan();
  }

  private loadPlan(): void {
    this.loading = true;
    this.error = '';
    this.targets = null;
    console.log('[NutritionPlan] loadPlan start');

    this.profileService.getProfile().pipe(
      timeout(5_000),
      switchMap((profile) => {
        if (!profile || !(profile as { user_id?: string }).user_id) {
          this.targets = null;
          this.error = '';
          this.loading = false;
          this.cdr.detectChanges();
          return EMPTY;
        }
        console.log('[NutritionPlan] profile ok', profile);
        const payload = this.toTargetsPayload(profile);
        return forkJoin({
          targets: this.api.post<TargetResponse>('/api/calculate/targets', payload).pipe(timeout(5_000)),
          mealPlan: this.api.get<MealPlanResponse>('/api/meal-plan/daily', this.auth.authHeaders()).pipe(timeout(5_000))
        }).pipe(map(({ targets, mealPlan }) => ({ profile, targets, mealPlan })));
      }),
      finalize(() => {
        this.loading = false;
        console.log('[NutritionPlan] finalize loading=false');
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: ({ profile, targets, mealPlan }) => {
        // Redundant with `finalize`, but keeps UI responsive even if downstream code throws.
        this.loading = false;
        console.log('[NutritionPlan] targets ok', targets);
        try {
          this.targets = targets;
          this.applyBmiCategory(targets.bmi);
          this.macros = this.buildMacros(targets.target_calories, profile.goal);
          this.constraints = this.buildConstraints(profile.health_metadata ?? {}, profile.goal);
          this.mealSuggestions = this.buildMealSuggestionsFromPlan(mealPlan);
          this.progress.calories.target = targets.target_calories;
          this.progress.calories.current = this.calculateConsumedCalories(mealPlan);
          this.cdr.detectChanges();
        } catch (e) {
          this.targets = null;
          this.error = `Failed to render nutrition plan: ${(e as Error)?.message ?? String(e)}`;
          this.cdr.detectChanges();
        }
      },
      error: (err) => {
        this.loading = false;
        console.log('[NutritionPlan] error', err);
        const anyErr = err as { status?: number } | null;
        if (anyErr?.status === 404) {
          // No profile yet: show the call-to-action card (no error banner).
          this.error = '';
          this.cdr.detectChanges();
          return;
        }

        this.error = this.humanizeLoadError(err);
        this.cdr.detectChanges();
        console.error('Nutrition plan load error:', err);
      }
    });
  }

  private toTargetsPayload(profile: ProfilePayload) {
    return {
      age: Number(profile.age),
      gender: profile.gender,
      height_cm: Number(profile.height_cm),
      weight_kg: Number(profile.weight_kg),
      activity_level: profile.activity_level,
      goal: profile.goal
    };
  }

  private humanizeLoadError(err: unknown): string {
    if (err instanceof TimeoutError) {
      return 'Request timed out. Check that the backend is running on http://localhost:8080 and try again.';
    }

    const anyErr = err as { status?: number; error?: unknown; message?: string } | null;
    const status = anyErr?.status;
    if (status === 401) {
      return 'Session expired. Please logout and login again.';
    }
    if (status === 404) {
      return 'Profile not found. Please complete Profile Setup first.';
    }

    // Angular HttpErrorResponse often puts the backend text in `error`.
    const backendText = typeof anyErr?.error === 'string' ? anyErr.error : null;
    if (backendText && backendText.trim()) {
      return backendText;
    }

    return 'Failed to load nutrition plan. Please try again.';
  }

  private buildMealSuggestionsFromPlan(plan: MealPlanResponse): MealSuggestion[] {
    const formatPortion = (item: MealPlanItem): string => {
      const amount = this.formatAmount(item.portion_amount);
      const unit = (item.portion_unit ?? '').trim();
      if (amount && unit) return `${amount} ${unit}`;
      if (amount) return amount;
      return '1 serving';
    };

    const toItems = (items: MealPlanItem[] | undefined) =>
      (items ?? []).map((item) => `${item.food_name} · ${formatPortion(item)} · ${this.formatAmount(item.calories)} cal`);

    return [
      { meal: 'Breakfast', emoji: '🍳', items: toItems(plan.breakfast) },
      { meal: 'Lunch', emoji: '🥗', items: toItems(plan.lunch) },
      { meal: 'Dinner', emoji: '🍽️', items: toItems(plan.dinner) }
    ];
  }

  private buildMacros(calories: number, goal: string): MacroTarget[] {
    const normalizedGoal = (goal ?? '').toLowerCase();
    const ratios = normalizedGoal === 'weight_loss'
      ? { protein: 0.35, carbs: 0.35, fats: 0.30 }
      : normalizedGoal === 'weight_gain'
        ? { protein: 0.25, carbs: 0.50, fats: 0.25 }
        : { protein: 0.30, carbs: 0.45, fats: 0.25 };

    const proteinGrams = Math.round((calories * ratios.protein) / 4);
    const carbsGrams = Math.round((calories * ratios.carbs) / 4);
    const fatsGrams = Math.round((calories * ratios.fats) / 9);

    return [
      {
        label: 'Protein',
        value: `${proteinGrams}g`,
        percentage: Math.round(ratios.protein * 100),
        calories: Math.round(calories * ratios.protein),
        color: '#2e9f61'
      },
      {
        label: 'Carbohydrates',
        value: `${carbsGrams}g`,
        percentage: Math.round(ratios.carbs * 100),
        calories: Math.round(calories * ratios.carbs),
        color: '#e6a700'
      },
      {
        label: 'Fats',
        value: `${fatsGrams}g`,
        percentage: Math.round(ratios.fats * 100),
        calories: Math.round(calories * ratios.fats),
        color: '#d64545'
      }
    ];
  }

  private buildConstraints(health: Record<string, boolean>, goal: string): Constraint {
    const medical: string[] = [];

    // Medical constraints
    if (health['high_bp']) medical.push('Keep sodium below 1,500 mg/day');
    if (health['low_bp']) medical.push('Prioritize hydration and moderate sodium');
    if (health['diabetes']) medical.push('Focus on low-GI carbs, spread consistently');
    if (health['high_cholesterol']) medical.push('Limit saturated fat, prioritize fiber');
    if (health['anemia']) medical.push('Prioritize iron-rich foods with Vitamin C');
    if (health['allergy']) medical.push('Avoid identified allergens, verify labels');

    if (medical.length === 0) {
      medical.push('No medical constraints selected');
    }

    // Goal-specific guidelines
    const normalizedGoal = (goal ?? '').toLowerCase();
    const goalGuidelines: string[] = [];

    if (normalizedGoal === 'weight_loss') {
      goalGuidelines.push('Create 20-25% calorie deficit from TDEE');
      goalGuidelines.push('Prioritize protein to preserve muscle mass');
      goalGuidelines.push('Include fiber-rich foods for satiety');
      goalGuidelines.push('Spread calories evenly across 3-4 meals');
    } else if (normalizedGoal === 'weight_gain') {
      goalGuidelines.push('Create 10-15% calorie surplus from TDEE');
      goalGuidelines.push('Focus on nutrient-dense calorie sources');
      goalGuidelines.push('Include strength training for muscle gain');
      goalGuidelines.push('Eat every 3-4 hours to meet targets');
    } else {
      goalGuidelines.push('Maintain consistent calorie intake');
      goalGuidelines.push('Focus on balanced macronutrient distribution');
      goalGuidelines.push('Include variety of whole foods');
      goalGuidelines.push('Stay hydrated and get adequate sleep');
    }

    return { medical, goal: goalGuidelines };
  }

  private buildMealSuggestions(goal: string): MealSuggestion[] {
    const normalizedGoal = (goal ?? '').toLowerCase();
    
    if (normalizedGoal === 'weight_loss') {
      return [
        {
          meal: 'Breakfast',
          emoji: '🍳',
          items: [
            'Greek yogurt with berries and nuts',
            'Oatmeal with protein powder and fruit',
            'Veggie omelet with whole grain toast'
          ]
        },
        {
          meal: 'Lunch',
          emoji: '🥗',
          items: [
            'Grilled chicken salad with quinoa',
            'Lentil soup with mixed greens',
            'Turkey wrap with avocado and veggies'
          ]
        },
        {
          meal: 'Dinner',
          emoji: '🍽️',
          items: [
            'Baked salmon with roasted vegetables',
            'Stir-fry with lean protein and brown rice',
            'Grilled shrimp with quinoa and asparagus'
          ]
        }
      ];
    } else if (normalizedGoal === 'weight_gain') {
      return [
        {
          meal: 'Breakfast',
          emoji: '🥞',
          items: [
            'Peanut butter banana smoothie with protein',
            'Avocado toast with eggs and cheese',
            'Oatmeal with nuts, seeds, and honey'
          ]
        },
        {
          meal: 'Lunch',
          emoji: '🍛',
          items: [
            'Chicken and rice bowl with avocado',
            'Pasta with lean meat and olive oil',
            'Bean burrito with cheese and guacamole'
          ]
        },
        {
          meal: 'Dinner',
          emoji: '🥩',
          items: [
            'Steak with sweet potatoes and vegetables',
            'Salmon with quinoa and olive oil dressing',
            'Chicken curry with rice and nuts'
          ]
        }
      ];
    } else {
      return [
        {
          meal: 'Breakfast',
          emoji: '🥣',
          items: [
            'Whole grain cereal with milk and fruit',
            'Eggs with whole grain toast and avocado',
            'Smoothie with protein and healthy fats'
          ]
        },
        {
          meal: 'Lunch',
          emoji: '🥪',
          items: [
            'Grilled chicken with mixed vegetables',
            'Fish tacos with slaw and beans',
            'Veggie burger with sweet potato fries'
          ]
        },
        {
          meal: 'Dinner',
          emoji: '🍲',
          items: [
            'Lean meat with quinoa and vegetables',
            'Fish with brown rice and salad',
            'Stir-fry with tofu and whole grains'
          ]
        }
      ];
    }
  }

  private applyBmiCategory(bmi: number): void {
    if (bmi < 18.5) {
      this.bmiCategory = { text: 'Underweight', color: '#e6a700' };
      return;
    }
    if (bmi < 25) {
      this.bmiCategory = { text: 'Normal', color: '#2e9f61' };
      return;
    }
    if (bmi < 30) {
      this.bmiCategory = { text: 'Overweight', color: '#e6a700' };
      return;
    }
    this.bmiCategory = { text: 'Obese', color: '#d64545' };
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

  private calculateConsumedCalories(plan: MealPlanResponse): number {
    const today = new Date().toISOString().slice(0, 10);
    const storage = this.readLocalIntake(today);
    
    let total = 0;
    
    const processMeal = (items: MealPlanItem[] | undefined, mealType: string) => {
      if (!items) return;
      for (const item of items) {
        if (!item.id) continue;
        const key = `${mealType}:${item.id}`;
        // Local storage takes precedence for immediate UI feedback, fallback to backend's "eaten" boolean
        let isEaten = item.eaten;
        if (storage[key] === true) isEaten = true;
        else if (storage[key] === false) isEaten = false;
        
        if (isEaten) {
          total += item.calories;
        }
      }
    };

    processMeal(plan.breakfast, 'breakfast');
    processMeal(plan.lunch, 'lunch');
    processMeal(plan.dinner, 'dinner');
    processMeal(plan.snacks, 'snack');

    return total;
  }

  private readLocalIntake(date: string): Record<string, boolean> {
    const key = `ng:intake:daily:${date}`;
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

  getProgressPercentage(data: { current: number; target: number }): number {
    if (data.target === 0) return 0;
    return Math.min(100, (data.current / data.target) * 100);
  }

  getProgressColor(data: { current: number; target: number }): string {
    const percentage = this.getProgressPercentage(data);
    if (percentage < 50) return '#d64545';
    if (percentage < 80) return '#e6a700';
    return '#2e9f61';
  }
}
