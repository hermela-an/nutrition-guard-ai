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
  food_name: string;
  meal_type: string | null;
  portion_amount: number | null;
  portion_unit: string | null;
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
type SortMode = 'food_name' | 'calories' | 'protein';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, AppShellComponent],
  templateUrl: './food-search.component.html',
  styleUrls: ['./food-search.component.css']
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
  sortMode: SortMode = 'food_name';

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
      working.sort((a, b) => String(a.food_name).localeCompare(String(b.food_name)));
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

  formatAmount(value: unknown): string {
    const numberValue = this.toNumber(value);
    if (Math.abs(numberValue - Math.round(numberValue)) < 0.001) {
      return String(Math.round(numberValue));
    }
    return String(numberValue);
  }

  formatPortion(food: FoodItem): string {
    const amount = this.formatAmount(food.portion_amount);
    const unit = (food.portion_unit ?? '').trim();
    return unit ? `${amount} ${unit}` : amount;
  }

  formatHeadline(food: FoodItem): string {
    const calories = this.formatAmount(food.calories);
    return `${food.food_name} · ${this.formatPortion(food)} · ${calories} cal`;
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
