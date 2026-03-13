import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, timeout } from 'rxjs/operators';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';

interface FoodItem {
  id: number;
  name: string;
  meal_type: string | null;
  calories: number;
  protein_g: number | null;
  carbs_g: number | null;
  fat_g: number | null;
  sugar_g: number | null;
  sodium_mg: number | null;
  cholesterol_mg: number | null;
  vitamins?: Record<string, number | string> | null;
  traffic_light?: 'safe' | 'caution' | 'avoid' | string;
}

type MealFilter = 'all' | 'breakfast' | 'lunch' | 'dinner' | 'snack';
type SortMode = 'name' | 'calories' | 'protein';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppShellComponent],
  template: `
  <app-shell [items]="menuItems">
    <div class="loading-overlay" *ngIf="loading">
      <div class="spinner"></div>
    </div>

    <div class="error-banner" *ngIf="error">{{ error }}</div>

    <div class="page-header">
      <div>
        <span class="eyebrow">Smart lookup</span>
        <h2>Food Search</h2>
        <p class="muted">Find foods, compare macros, and see health-friendly signals instantly.</p>
      </div>
      <div class="header-actions">
        <a class="btn secondary" routerLink="/nutrition-plan">Back to Nutrition Plan</a>
        <a class="btn primary" routerLink="/daily-meal-plan">Daily Meal Plan</a>
      </div>
    </div>

    <div class="search-card">
      <div class="search-bar">
        <input
          class="input"
          type="text"
          placeholder="Search foods, e.g. salmon, oats, lentils"
          [(ngModel)]="query"
          (keyup.enter)="runSearch()"
          aria-label="Search foods"
        />
        <button class="btn primary" type="button" (click)="runSearch()">Search</button>
      </div>
      <div class="search-meta">
        <div class="chip-row">
          <button class="chip" [class.active]="mealFilter === 'all'" (click)="setMealFilter('all')">All meals</button>
          <button class="chip" [class.active]="mealFilter === 'breakfast'" (click)="setMealFilter('breakfast')">Breakfast</button>
          <button class="chip" [class.active]="mealFilter === 'lunch'" (click)="setMealFilter('lunch')">Lunch</button>
          <button class="chip" [class.active]="mealFilter === 'dinner'" (click)="setMealFilter('dinner')">Dinner</button>
          <button class="chip" [class.active]="mealFilter === 'snack'" (click)="setMealFilter('snack')">Snack</button>
        </div>
        <div class="sort-row">
          <span class="muted">Sort by</span>
          <button class="chip" [class.active]="sortMode === 'name'" (click)="setSort('name')">Name</button>
          <button class="chip" [class.active]="sortMode === 'calories'" (click)="setSort('calories')">Calories</button>
          <button class="chip" [class.active]="sortMode === 'protein'" (click)="setSort('protein')">Protein</button>
        </div>
      </div>
    </div>

    <div class="summary-grid" *ngIf="!loading && !error">
      <div class="summary-card">
        <div class="summary-label">Results</div>
        <div class="summary-value">{{ filteredFoods.length }}</div>
        <div class="summary-sub">Foods found</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Avg Calories</div>
        <div class="summary-value">{{ averages.calories | number:'1.0-0' }}</div>
        <div class="summary-sub">Per item</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Safety Mix</div>
        <div class="summary-value">{{ safety.safe }} / {{ safety.caution }} / {{ safety.avoid }}</div>
        <div class="summary-sub">Safe / Caution / Avoid</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Avg Protein</div>
        <div class="summary-value">{{ averages.protein | number:'1.0-0' }}g</div>
        <div class="summary-sub">Per item</div>
      </div>
    </div>

    <div class="section-header" *ngIf="!loading && !error">
      <h3>Search Results</h3>
      <span class="muted">Tap a card to review nutrients</span>
    </div>

    <div class="results-grid" *ngIf="!loading && !error && filteredFoods.length > 0">
      <article class="food-card" *ngFor="let food of filteredFoods">
        <div class="food-title">
          <div>
            <div class="name">{{ food.name }}</div>
            <div class="meal-type">{{ mealLabel(food.meal_type) }}</div>
          </div>
          <span class="pill" [ngClass]="pillClass(food.traffic_light)">{{ pillLabel(food.traffic_light) }}</span>
        </div>
        <div class="macro-row">
          <div class="macro">
            <span class="label">Calories</span>
            <span class="value">{{ food.calories | number:'1.0-0' }}</span>
          </div>
          <div class="macro">
            <span class="label">Protein</span>
            <span class="value">{{ toNumber(food.protein_g) | number:'1.0-0' }}g</span>
          </div>
          <div class="macro">
            <span class="label">Carbs</span>
            <span class="value">{{ toNumber(food.carbs_g) | number:'1.0-0' }}g</span>
          </div>
          <div class="macro">
            <span class="label">Fat</span>
            <span class="value">{{ toNumber(food.fat_g) | number:'1.0-0' }}g</span>
          </div>
        </div>
        <div class="micro-row">
          <span>Sugar {{ toNumber(food.sugar_g) | number:'1.0-0' }}g</span>
          <span>Sodium {{ toNumber(food.sodium_mg) | number:'1.0-0' }}mg</span>
          <span>Cholesterol {{ toNumber(food.cholesterol_mg) | number:'1.0-0' }}mg</span>
        </div>

        <button
          type="button"
          class="btn ghost see-more"
          (click)="toggleDetails(food.id)"
          [attr.aria-expanded]="isExpanded(food.id)"
        >
          {{ isExpanded(food.id) ? 'Hide details' : 'See more' }}
        </button>

        <div class="details-panel" *ngIf="isExpanded(food.id)">
          <div class="detail-row">
            <span class="label">Meal Type</span>
            <span class="value">{{ mealLabel(food.meal_type) }}</span>
          </div>
          <div class="detail-row">
            <span class="label">Protein</span>
            <span class="value">{{ toNumber(food.protein_g) | number:'1.0-0' }} g</span>
          </div>
          <div class="detail-row">
            <span class="label">Carbs</span>
            <span class="value">{{ toNumber(food.carbs_g) | number:'1.0-0' }} g</span>
          </div>
          <div class="detail-row">
            <span class="label">Fat</span>
            <span class="value">{{ toNumber(food.fat_g) | number:'1.0-0' }} g</span>
          </div>
          <div class="detail-row">
            <span class="label">Sugar</span>
            <span class="value">{{ toNumber(food.sugar_g) | number:'1.0-0' }} g</span>
          </div>
          <div class="detail-row">
            <span class="label">Sodium</span>
            <span class="value">{{ toNumber(food.sodium_mg) | number:'1.0-0' }} mg</span>
          </div>
          <div class="detail-row">
            <span class="label">Cholesterol</span>
            <span class="value">{{ toNumber(food.cholesterol_mg) | number:'1.0-0' }} mg</span>
          </div>
          <div class="detail-row" *ngIf="food.vitamins && vitaminKeys(food).length > 0">
            <span class="label">Vitamins</span>
            <span class="value">
              {{ vitaminKeys(food).join(', ') }}
            </span>
          </div>
        </div>
      </article>
    </div>

    <div class="empty-card" *ngIf="!loading && !error && filteredFoods.length === 0">
      <p class="muted">No foods matched that search. Try a different keyword or remove filters.</p>
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

    .search-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
      margin-bottom: 16px;
    }

    .search-bar {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 12px;
      align-items: center;
    }

    .search-meta {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      margin-top: 12px;
      flex-wrap: wrap;
    }

    .chip-row,
    .sort-row {
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;
    }

    .chip {
      border: 1px solid var(--line);
      background: #fff;
      padding: 6px 12px;
      border-radius: 999px;
      font-weight: 700;
      font-size: 12px;
      cursor: pointer;
      color: var(--muted);
    }

    .chip.active {
      background: #e6f4ec;
      color: var(--brand-ink);
      border-color: #bfe8cf;
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

    .results-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .food-card {
      background: #fff;
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 12px;
      transition: transform 0.2s ease;
    }

    .food-card:hover {
      transform: translateY(-2px);
    }

    .food-title {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 10px;
    }

    .name {
      font-weight: 800;
      font-size: 16px;
    }

    .meal-type {
      font-size: 12px;
      color: var(--muted);
      margin-top: 4px;
      text-transform: capitalize;
    }

    .macro-row {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 10px;
    }

    .macro {
      background: #f4f7f3;
      border-radius: 10px;
      padding: 8px 10px;
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .macro .label {
      font-size: 11px;
      text-transform: uppercase;
      color: var(--muted);
      letter-spacing: 0.4px;
    }

    .macro .value {
      font-weight: 800;
    }

    .micro-row {
      display: flex;
      flex-wrap: wrap;
      gap: 10px;
      font-size: 12px;
      color: var(--muted);
    }

    .see-more {
      align-self: flex-start;
      padding: 8px 14px;
      font-size: 12px;
    }

    .details-panel {
      border-top: 1px dashed #d8dfcf;
      padding-top: 10px;
      display: grid;
      gap: 6px;
      font-size: 12px;
      color: var(--muted);
    }

    .detail-row {
      display: flex;
      justify-content: space-between;
      gap: 8px;
    }

    .detail-row .label {
      font-weight: 700;
      color: var(--text);
      text-transform: uppercase;
      letter-spacing: 0.4px;
      font-size: 10px;
    }

    .pill.safe { background: #e4f5eb; color: var(--safe); }
    .pill.caution { background: #fff4d8; color: var(--caution); }
    .pill.avoid { background: #fde8e8; color: var(--danger); }

    .empty-card {
      background: #fff;
      border: 1px dashed #d8dfcf;
      border-radius: 12px;
      padding: 16px;
      margin-bottom: 16px;
    }

    @media (max-width: 980px) {
      .summary-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .results-grid {
        grid-template-columns: 1fr;
      }

      .page-header {
        flex-direction: column;
        align-items: flex-start;
      }

      .search-bar {
        grid-template-columns: 1fr;
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

      .macro-row {
        grid-template-columns: 1fr;
      }
    }
  `]
})
export class FoodSearchComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';
  query = '';

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

  foods: FoodItem[] = [];
  filteredFoods: FoodItem[] = [];

  mealFilter: MealFilter = 'all';
  sortMode: SortMode = 'name';

  averages = {
    calories: 0,
    protein: 0
  };

  safety = {
    safe: 0,
    caution: 0,
    avoid: 0
  };

  expandedIds = new Set<number>();

  ngOnInit(): void {
    this.runSearch();
  }

  runSearch(): void {
    this.loading = true;
    this.error = '';

    const queryParam = encodeURIComponent(this.query.trim());
    const queryString = queryParam ? `?q=${queryParam}` : '';

    this.api.get<FoodItem[]>(`/api/foods/search${queryString}`, this.auth.authHeaders()).pipe(
      timeout(5_000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (items) => {
        this.foods = (items ?? []).map((item) => ({
          ...item,
          meal_type: item.meal_type ?? 'unknown'
        }));
        this.expandedIds.clear();
        this.applyFilters();
      },
      error: (err) => {
        this.error = typeof err?.message === 'string' ? err.message : 'Failed to load foods.';
      }
    });
  }

  setMealFilter(filter: MealFilter): void {
    this.mealFilter = filter;
    this.applyFilters();
  }

  setSort(mode: SortMode): void {
    this.sortMode = mode;
    this.applyFilters();
  }

  applyFilters(): void {
    let working = [...this.foods];

    if (this.mealFilter !== 'all') {
      working = working.filter((item) => (item.meal_type ?? '').toLowerCase() === this.mealFilter);
    }

    if (this.sortMode === 'calories') {
      working.sort((a, b) => this.toNumber(b.calories) - this.toNumber(a.calories));
    } else if (this.sortMode === 'protein') {
      working.sort((a, b) => this.toNumber(b.protein_g) - this.toNumber(a.protein_g));
    } else {
      working.sort((a, b) => String(a.name).localeCompare(String(b.name)));
    }

    this.filteredFoods = working;
    this.computeSummary();
  }

  toggleDetails(id: number): void {
    if (this.expandedIds.has(id)) {
      this.expandedIds.delete(id);
    } else {
      this.expandedIds.add(id);
    }
  }

  isExpanded(id: number): boolean {
    return this.expandedIds.has(id);
  }

  computeSummary(): void {
    const count = this.filteredFoods.length;
    if (count === 0) {
      this.averages = { calories: 0, protein: 0 };
      this.safety = { safe: 0, caution: 0, avoid: 0 };
      return;
    }

    const totalCalories = this.filteredFoods.reduce((sum, item) => sum + this.toNumber(item.calories), 0);
    const totalProtein = this.filteredFoods.reduce((sum, item) => sum + this.toNumber(item.protein_g), 0);

    this.averages = {
      calories: Math.round(totalCalories / count),
      protein: Math.round(totalProtein / count)
    };

    this.safety = this.filteredFoods.reduce(
      (acc, item) => {
        const tag = (item.traffic_light ?? '').toString().toLowerCase();
        if (tag === 'safe') acc.safe += 1;
        else if (tag === 'caution') acc.caution += 1;
        else if (tag === 'avoid') acc.avoid += 1;
        return acc;
      },
      { safe: 0, caution: 0, avoid: 0 }
    );
  }

  pillClass(tag: FoodItem['traffic_light']): string {
    const normalized = (tag ?? 'safe').toString().toLowerCase();
    if (normalized === 'avoid') return 'avoid';
    if (normalized === 'caution') return 'caution';
    return 'safe';
  }

  pillLabel(tag: FoodItem['traffic_light']): string {
    const normalized = (tag ?? 'safe').toString().toLowerCase();
    if (normalized === 'avoid') return 'Avoid';
    if (normalized === 'caution') return 'Caution';
    return 'Safe';
  }

  mealLabel(mealType: string | null | undefined): string {
    if (!mealType) return 'Unknown meal';
    return mealType.replace('_', ' ');
  }

  vitaminKeys(food: FoodItem): string[] {
    const vitamins = food.vitamins ?? {};
    return Object.keys(vitamins).map((key) => `${key}: ${vitamins[key]}`);
  }

  toNumber(value: unknown): number {
    if (typeof value === 'number' && !Number.isNaN(value)) return value;
    if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
      return Number(value);
    }
    return 0;
  }
}
