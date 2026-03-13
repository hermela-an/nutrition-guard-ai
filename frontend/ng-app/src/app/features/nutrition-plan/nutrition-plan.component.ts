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
  name: string;
  calories: number;
  traffic_light?: string;
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
  template: `
  <app-shell [items]="menuItems">
    <div class="loading-overlay" *ngIf="loading">
      <div class="spinner"></div>
    </div>
    
    <div class="error-banner" *ngIf="error" [innerHTML]="error"></div>
    
    <div *ngIf="!loading && !error && targets" class="plan-container">
      <div class="plan-header">
        <div class="title-block">
          <span class="eyebrow">Personalized plan</span>
          <h2>Nutrition Plan</h2>
          <p class="muted">Built from your profile, goal, and health conditions.</p>
        </div>
        <div class="plan-actions">
          <a class="btn secondary" routerLink="/profile-setup">Update Profile</a>
          <a class="btn primary" routerLink="/daily-meal-plan">Daily Meal Plan</a>
        </div>
      </div>

      <!-- Summary Cards -->
      <div class="plan-summary">
        <div class="summary-card">
          <div class="summary-value">{{ targets.target_calories | number:'1.0-0' }}</div>
          <div class="summary-label">Target Calories</div>
          <div class="muted summary-sub">Daily goal</div>
        </div>
        <div class="summary-card">
          <div class="summary-value">{{ targets.bmi | number:'1.1-1' }}</div>
          <div class="summary-label">BMI</div>
          <div class="muted summary-sub" [style.color]="bmiCategory.color">{{ bmiCategory.text }}</div>
        </div>
        <div class="summary-card">
          <div class="summary-value">{{ targets.tdee | number:'1.0-0' }}</div>
          <div class="summary-label">Estimated TDEE</div>
          <div class="muted summary-sub">Maintenance</div>
        </div>
        <div class="summary-card">
          <div class="summary-value">{{ targets.bmr | number:'1.0-0' }}</div>
          <div class="summary-label">Basal Metabolic Rate</div>
          <div class="muted summary-sub">At rest</div>
        </div>
      </div>

      <div class="section-header">
        <h3>Macro Targets</h3>
        <span class="muted">Balanced to your goal</span>
      </div>
      <!-- Macro Breakdown -->
      <div class="macro-breakdown">
        <div class="macro-card" *ngFor="let macro of macros">
          <div class="macro-header">
            <span style="font-weight: 800;" [style.color]="macro.color">{{ macro.label }}</span>
            <span class="macro-value">{{ macro.value }}</span>
          </div>
          <div class="macro-bar">
            <div class="macro-fill" [style.width]="macro.percentage + '%'" [style.background]="macro.color"></div>
          </div>
          <div class="macro-details">
            <span>{{ macro.calories }} cal</span>
            <span title="Percent of target calories">{{ macro.percentage }}%</span>
          </div>
        </div>
      </div>

      <div class="section-header">
        <h3>Guidelines</h3>
        <span class="muted">Based on medical constraints and goal</span>
      </div>
      <!-- Constraints -->
      <div class="constraints-grid">
        <div class="constraint-card">
          <div class="constraint-title">Medical Constraints</div>
          <ul class="constraint-list">
            <li *ngFor="let rule of constraints.medical">{{ rule }}</li>
          </ul>
        </div>
        
        <div class="constraint-card">
          <div class="constraint-title">Goal-Specific Guidelines</div>
          <ul class="constraint-list">
            <li *ngFor="let guideline of constraints.goal">{{ guideline }}</li>
          </ul>
        </div>
      </div>

      <div class="section-header">
        <h3>Meal Suggestions</h3>
        <span class="muted">Quick ideas to get started</span>
      </div>
      <!-- Meal Suggestions -->
      <div class="meal-suggestions">
        <div class="meal-card" *ngFor="let meal of mealSuggestions">
          <div class="meal-title">
            <span class="meal-icon" aria-hidden="true">{{ meal.emoji }}</span>
            <span>{{ meal.meal }}</span>
          </div>
          <ul class="meal-list">
            <li *ngFor="let item of meal.items">{{ item }}</li>
          </ul>
        </div>
      </div>

      <!-- Progress Tracking -->
      <div class="progress-section">
        <div class="section-header">
          <h3>Daily Progress</h3>
          <span class="muted">Today vs targets</span>
        </div>
        <div class="progress-grid">
          <div class="progress-card">
            <div class="progress-header">
              <span style="font-weight: 800;">Calories</span>
              <span class="progress-value">{{ progress.calories.current | number:'1.0-0' }} / {{ progress.calories.target | number:'1.0-0' }}</span>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" [style.width]="getProgressPercentage(progress.calories) + '%'" [style.background]="getProgressColor(progress.calories)"></div>
            </div>
            <div class="progress-labels">
              <span>Today</span>
              <span>Target</span>
            </div>
          </div>
          
          <div class="progress-card">
            <div class="progress-header">
              <span style="font-weight: 800;">Water Intake</span>
              <span class="progress-value">{{ progress.water.current | number:'1.1-1' }}L / {{ progress.water.target | number:'1.1-1' }}L</span>
            </div>
            <div class="progress-bar">
              <div class="progress-fill" [style.width]="getProgressPercentage(progress.water) + '%'" [style.background]="getProgressColor(progress.water)"></div>
            </div>
            <div class="progress-labels">
              <span>Today</span>
              <span>Target</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Action Buttons -->
      <div class="action-buttons">
        <a class="btn primary" routerLink="/food-search">Search Foods</a>
        <a class="btn secondary" routerLink="/progress-tracking">Track Progress</a>
      </div>
    </div>

    <!-- No Profile Content -->
    <div *ngIf="!loading && !error && !targets" class="no-profile-content">
      <div class="card">
        <h3>Complete Your Profile</h3>
        <p class="muted">To generate your personalized nutrition plan, please complete your profile setup first.</p>
        <div class="action-buttons" style="margin-top: 16px;">
          <a class="btn primary" routerLink="/profile-setup">Setup Profile</a>
          <a class="btn ghost" routerLink="/dashboard">Back to Dashboard</a>
        </div>
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
    
    .title-block h2 {
      font-size: 28px;
      line-height: 1.1;
      margin: 6px 0 6px;
    }
    
    .eyebrow {
      text-transform: uppercase;
      font-size: 11px;
      letter-spacing: 1.4px;
      color: #7a8a7c;
      font-weight: 700;
    }
    
    .plan-container {
      animation: fadeIn 0.3s ease-in;
    }
    
    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(10px); }
      to { opacity: 1; transform: translateY(0); }
    }
    
    .plan-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 20px;
      margin-bottom: 16px;
    }
    
    .plan-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }
    
    .plan-summary {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }
    
    .summary-sub {
      font-size: 11px;
      margin-top: 4px;
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
    
    .section-header .muted {
      font-size: 12px;
    }
    
    .summary-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
      text-align: center;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }
    
    .summary-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 8px 20px rgba(26, 34, 22, 0.12);
    }
    
    .summary-value {
      font-size: 1.8rem;
      font-weight: 800;
      margin-bottom: 4px;
    }
    
    .summary-label {
      font-size: 12px;
      color: var(--muted);
      text-transform: uppercase;
      letter-spacing: 0.5px;
    }
    
    .macro-breakdown {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }
    
    .macro-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
      transition: transform 0.2s ease;
    }
    
    .macro-card:hover {
      transform: translateY(-2px);
    }
    
    .macro-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 8px;
    }
    
    .macro-value {
      font-size: 1.4rem;
      font-weight: 800;
    }
    
    .macro-bar {
      width: 100%;
      height: 8px;
      background: #e8f0e6;
      border-radius: 999px;
      overflow: hidden;
      margin-bottom: 8px;
    }
    
    .macro-fill {
      height: 100%;
      width: 0%;
      transition: width 0.3s ease;
    }
    
    .macro-details {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--muted);
    }
    
    .constraints-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }
    
    .constraint-card {
      background: #fff3dd;
      border: 1px solid #f2c0c0;
      border-radius: 12px;
      padding: 14px;
      transition: transform 0.2s ease;
    }
    
    .constraint-card:hover {
      transform: translateY(-1px);
    }
    
    .constraint-title {
      font-weight: 800;
      color: #8e2f2f;
      margin-bottom: 6px;
    }
    
    .constraint-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    
    .constraint-list li {
      padding: 4px 0;
      border-bottom: 1px dashed #f2c0c0;
      font-size: 13px;
    }
    
    .constraint-list li:last-child {
      border-bottom: none;
    }
    
    .meal-suggestions {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 16px;
    }
    
    .meal-card {
      background: #f0f4e8;
      border: 1px solid #d8dfcf;
      border-radius: 12px;
      padding: 14px;
      transition: transform 0.2s ease;
    }
    
    .meal-card:hover {
      transform: translateY(-1px);
    }
    
    .meal-title {
      font-weight: 800;
      margin-bottom: 8px;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    
    .meal-icon {
      display: inline-flex;
      width: 28px;
      height: 28px;
      align-items: center;
      justify-content: center;
      border-radius: 8px;
      background: #f4f7f3;
      font-size: 16px;
    }
    
    .meal-list {
      list-style: none;
      padding: 0;
      margin: 0;
    }
    
    .meal-list li {
      padding: 4px 0;
      font-size: 13px;
      color: var(--muted);
    }
    
    .progress-section {
      margin-top: 20px;
    }
    
    .progress-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 16px;
    }
    
    .progress-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
      transition: transform 0.2s ease;
    }
    
    .progress-card:hover {
      transform: translateY(-2px);
    }
    
    .progress-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 12px;
    }
    
    .progress-value {
      font-size: 1.6rem;
      font-weight: 800;
    }
    
    .progress-bar {
      width: 100%;
      height: 12px;
      background: #e8f0e6;
      border-radius: 999px;
      overflow: hidden;
      margin-bottom: 8px;
    }
    
    .progress-fill {
      height: 100%;
      width: 0%;
      transition: width 0.3s ease;
    }
    
    .progress-labels {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--muted);
    }
    
    .action-buttons {
      display: flex;
      gap: 12px;
      flex-wrap: wrap;
      margin-top: 16px;
    }
    
    .btn.secondary {
      background: #fff;
      border: 1px solid var(--line);
      color: var(--text);
    }
    
    .no-profile-content {
      animation: fadeIn 0.3s ease-in;
    }
    
    @media (max-width: 980px) {
      .plan-summary {
        grid-template-columns: repeat(2, 1fr);
      }
      
      .macro-breakdown,
      .constraints-grid,
      .meal-suggestions {
        grid-template-columns: 1fr;
      }
      
      .progress-grid {
        grid-template-columns: 1fr;
      }
      
      .plan-header {
        flex-direction: column;
        align-items: stretch;
      }
      
      .plan-actions {
        justify-content: flex-start;
      }
    }
    
    @media (max-width: 640px) {
      .plan-summary {
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
    const toItems = (items: MealPlanItem[] | undefined) =>
      (items ?? []).map((item) => `${item.name} · ${item.calories} cal`);

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
