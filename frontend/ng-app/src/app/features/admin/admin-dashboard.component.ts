import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, timeout } from 'rxjs/operators';
import { AdminFoodItem, AdminFoodsService } from '../../core/services/admin-foods.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { ToastService } from '../../shared/services/toast.service';

type TrafficLight = 'GREEN' | 'YELLOW' | 'RED';
type FilterAll = 'ALL' | TrafficLight;

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: []
})
export class AdminDashboardComponent {
  private readonly foodsApi = inject(AdminFoodsService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);

  loading = true;
  error = '';

  query = '';
  foods: AdminFoodItem[] = [];
  filteredFoods: AdminFoodItem[] = [];
  pagedFoods: AdminFoodItem[] = [];

  mealFilter: 'ALL' | string = 'ALL';
  lightFilter: FilterAll = 'ALL';
  pageSize = 10;
  page = 1;
  pageCount = 1;

  editing: AdminFoodItem | null = null;
  form: Partial<AdminFoodItem> = this.emptyForm();

  readonly trafficLights: TrafficLight[] = ['GREEN', 'YELLOW', 'RED'];
  readonly mealTypes = ['breakfast', 'lunch', 'dinner', 'snack'];

  constructor() {
    this.refresh();
  }

  refresh(resetPage = false): void {
    if (resetPage) this.page = 1;
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
          this.foods = items ?? [];
          this.applyFilters(false);
        },
        error: (err) => {
          this.error = typeof err?.error === 'string' && err.error.trim() ? err.error : 'Failed to load foods.';
        }
      });
  }

  applyFilters(resetPage = false): void {
    if (resetPage) this.page = 1;
    const qMeal = (this.mealFilter ?? 'ALL').toString().toLowerCase();
    const qLight = (this.lightFilter ?? 'ALL').toString().toUpperCase();
    this.filteredFoods = (this.foods ?? []).filter((f) => {
      const mealOk = qMeal === 'all' || (f.meal_type ?? '').toString().toLowerCase() === qMeal;
      const light = (f.traffic_light ?? 'GREEN').toString().toUpperCase();
      const lightOk = qLight === 'ALL' || light === qLight;
      return mealOk && lightOk;
    });

    this.pageCount = Math.max(1, Math.ceil(this.filteredFoods.length / this.pageSize));
    this.page = Math.min(Math.max(1, this.page), this.pageCount);
    this.pagedFoods = this.filteredFoods.slice((this.page - 1) * this.pageSize, this.page * this.pageSize);
  }

  setPage(p: number): void {
    this.page = p;
    this.applyFilters(false);
  }

  startCreate(): void {
    this.editing = null;
    this.form = this.emptyForm();
  }

  startEdit(item: AdminFoodItem): void {
    this.editing = item;
    this.form = {
      id: item.id,
      food_name: item.food_name,
      meal_type: item.meal_type,
      portion_amount: item.portion_amount,
      portion_unit: item.portion_unit,
      calories: item.calories,
      protein_g: item.protein_g,
      carbs_g: item.carbs_g,
      fat_g: item.fat_g,
      sugar_g: item.sugar_g,
      sodium_mg: item.sodium_mg,
      cholesterol_mg: item.cholesterol_mg,
      image_url: item.image_url ?? '',
      definition: item.definition ?? '',
      description: item.description ?? '',
      traffic_light: (item.traffic_light ?? 'GREEN') as string
    };
  }

  cancelEdit(): void {
    this.editing = null;
    this.form = this.emptyForm();
  }

  save(): void {
    const payload = this.cleanPayload(this.form);
    if (!payload.food_name || !payload.meal_type) {
      this.error = 'Food name and meal type are required.';
      return;
    }

    this.loading = true;
    this.error = '';
    const request$ = this.editing?.id
      ? this.foodsApi.update(this.editing.id, payload)
      : this.foodsApi.create(payload);

    request$
      .pipe(
        timeout(8_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.toast.success(this.editing?.id ? 'Food updated' : 'Food created');
          this.cancelEdit();
          this.refresh(true);
        },
        error: (err) => {
          const msg = typeof err?.error === 'string' && err.error.trim() ? err.error : 'Save failed.';
          this.error = msg;
          this.toast.error(msg);
        }
      });
  }

  async delete(item: AdminFoodItem): Promise<void> {
    if (!item?.id) return;
    const ok = await this.confirm.open({
      title: 'Delete food',
      message: `Delete "${item.food_name}"? This cannot be undone.`,
      confirmText: 'Delete',
      intent: 'danger'
    });
    if (!ok) return;

    this.loading = true;
    this.error = '';
    this.foodsApi
      .delete(item.id)
      .pipe(
        timeout(8_000),
        finalize(() => {
          this.loading = false;
          this.cdr.detectChanges();
        })
      )
      .subscribe({
        next: () => {
          this.toast.success('Food deleted');
          this.refresh(true);
        },
        error: (err) => {
          const msg = typeof err?.error === 'string' && err.error.trim() ? err.error : 'Delete failed.';
          this.error = msg;
          this.toast.error(msg);
        }
      });
  }

  lightClass(light: string | null | undefined): 'safe' | 'caution' | 'avoid' {
    const normalized = (light ?? '').toString().toUpperCase();
    if (normalized === 'RED') return 'avoid';
    if (normalized === 'YELLOW') return 'caution';
    return 'safe';
  }

  trackById(_: number, item: AdminFoodItem): number {
    return item.id;
  }

  private emptyForm(): Partial<AdminFoodItem> {
    return {
      food_name: '',
      meal_type: 'breakfast',
      portion_amount: 1,
      portion_unit: 'serving',
      calories: 0,
      protein_g: 0,
      carbs_g: 0,
      fat_g: 0,
      sugar_g: 0,
      sodium_mg: 0,
      cholesterol_mg: 0,
      image_url: '',
      definition: '',
      description: '',
      traffic_light: 'GREEN'
    };
  }

  private cleanPayload(form: Partial<AdminFoodItem>): Partial<AdminFoodItem> {
    const text = (v: unknown) => (v ?? '').toString().trim();
    const num = (v: unknown) => {
      const n = typeof v === 'number' ? v : Number((v ?? '').toString());
      return Number.isFinite(n) ? n : 0;
    };

    return {
      food_name: text(form.food_name),
      meal_type: text(form.meal_type).toLowerCase(),
      portion_amount: num(form.portion_amount),
      portion_unit: text(form.portion_unit),
      calories: Math.max(0, Math.round(num(form.calories))),
      protein_g: Math.max(0, num(form.protein_g)),
      carbs_g: Math.max(0, num(form.carbs_g)),
      fat_g: Math.max(0, num(form.fat_g)),
      sugar_g: Math.max(0, num(form.sugar_g)),
      sodium_mg: Math.max(0, num(form.sodium_mg)),
      cholesterol_mg: Math.max(0, num(form.cholesterol_mg)),
      image_url: text(form.image_url) || null,
      definition: text(form.definition) || null,
      description: text(form.description) || null,
      traffic_light: text(form.traffic_light || 'GREEN').toUpperCase()
    };
  }
}
