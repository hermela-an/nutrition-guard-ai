import { ChangeDetectorRef, Component, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, timeout } from 'rxjs/operators';
import { AdminFoodItem, AdminFoodsService } from '../../core/services/admin-foods.service';
import { ConfirmDialogService } from '../../shared/services/confirm-dialog.service';
import { ToastService } from '../../shared/services/toast.service';

type TrafficLight = 'SAFE' | 'CAUTION' | 'AVOID';
type FilterAll = 'ALL' | TrafficLight;

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.css']
})
export class AdminDashboardComponent {
  private readonly foodsApi = inject(AdminFoodsService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly confirm = inject(ConfirmDialogService);
  private readonly toast = inject(ToastService);

  @ViewChild('foodNameInput') private foodNameInput?: ElementRef<HTMLInputElement>;

  loading = true;
  error = '';

  query = '';
  foods: AdminFoodItem[] = [];
  filteredFoods: AdminFoodItem[] = [];
  pagedFoods: AdminFoodItem[] = [];
  imageName = '';

  mealFilter: 'ALL' | string = 'ALL';
  lightFilter: FilterAll = 'ALL';
  pageSize = 10;
  page = 1;
  pageCount = 1;

  editing: AdminFoodItem | null = null;
  form: Partial<AdminFoodItem> = this.emptyForm();
  healthEffect: Record<string, string> = {};
  vitaminRows: Array<{ label: string; value: string }> = [{ label: '', value: '' }];

  readonly trafficLights: TrafficLight[] = ['SAFE', 'CAUTION', 'AVOID'];
  readonly foodRoles = ['main', 'side'] as const;
  readonly mealTypes = ['breakfast', 'lunch', 'dinner', 'snack'];
  readonly categories = ['Fruits', 'Vegetables', 'Grains', 'Carbohydrates', 'Protein', 'Dairy', 'Nuts and Seeds'];
  readonly healthEffectKeys = [
    { key: 'high_bp', label: 'High BP' },
    { key: 'low_bp', label: 'Low BP' },
    { key: 'diabetes', label: 'Diabetes' },
    { key: 'high_cholesterol', label: 'High Cholesterol' },
    { key: 'anemia', label: 'Anemia' },
    { key: 'allergy', label: 'Allergy' },
    { key: 'heart_disease', label: 'Heart Disease' },
    { key: 'kidney_disease', label: 'Kidney Disease' },
    { key: 'weight_loss', label: 'Weight Loss' },
    { key: 'weight_gain', label: 'Weight Gain' }
  ] as const;
  readonly healthEffectOptions = [
    { value: '', label: 'â€”' },
    { value: 'good', label: 'Good' },
    { value: 'neutral', label: 'Neutral' },
    { value: 'bad', label: 'Bad' }
  ] as const;

  constructor() {
    // Avoid referencing `healthEffectKeys` before its field initializer runs.
    this.healthEffect = this.emptyHealthEffect();
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
          this.foods = Array.isArray(items) ? items : [];
          this.applyFilters(false);
        },
        error: (err) => {
          this.error = this.errorFrom(err, 'Failed to load foods.');
        }
      });
  }

  applyFilters(resetPage = false): void {
    if (resetPage) this.page = 1;
    const qMeal = (this.mealFilter ?? 'ALL').toString().toLowerCase();
    const qLight = (this.lightFilter ?? 'ALL').toString().toUpperCase();
    this.filteredFoods = (this.foods ?? []).filter((f) => {
      const mealOk = qMeal === 'all' || (f.meal_type ?? '').toString().toLowerCase() === qMeal;
      const light = (f.traffic_light ?? 'SAFE').toString().toUpperCase();
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
    this.healthEffect = this.emptyHealthEffect();
    this.vitaminRows = [{ label: '', value: '' }];
    this.imageName = '';
  }

  startEdit(item: AdminFoodItem): void {
    this.editing = item;
    this.healthEffect = this.hydrateHealthEffect(item.health_effect);
    this.form = {
      id: item.id,
      food_name: item.food_name,
      category: item.category ?? 'Vegetables',
      meal_type: item.meal_type,
      food_role: (item as any).food_role ?? ((item.meal_type ?? '').toLowerCase() === 'side' ? 'side' : 'main'),
      portion_amount: item.portion_amount,
      portion_unit: item.portion_unit,
      calories: item.calories,
      protein_g: item.protein_g,
      carbs_g: item.carbs_g,
      fat_g: item.fat_g,
      fiber_g: (item as any).fiber_g ?? 0,
      sugar_g: item.sugar_g,
      sodium_mg: item.sodium_mg,
      cholesterol_mg: item.cholesterol_mg,
      glycemic_index: (item as any).glycemic_index ?? 0,
      ingredients: (item as any).ingredients ?? '',
      image_url: item.image_url ?? '',
      definition: item.definition ?? '',
      traffic_light: (item.traffic_light ?? 'SAFE') as string
    };
    this.vitaminRows = this.vitaminRowsFrom((item as any).vitamins);
    this.imageName = item.image_url ? 'Current image' : '';
    this.focusFirstEditField();
  }

  cancelEdit(): void {
    this.editing = null;
    this.form = this.emptyForm();
    this.healthEffect = this.emptyHealthEffect();
    this.vitaminRows = [{ label: '', value: '' }];
    this.imageName = '';
  }

  onImageSelected(event: Event, input?: HTMLInputElement): void {
    const el = (input ?? (event.target as HTMLInputElement)) as HTMLInputElement;
    const file = el.files && el.files.length > 0 ? el.files[0] : null;
    if (!file) return;

    const maxBytes = 1_500_000; // ~1.5MB to keep payload reasonable
    if (file.size > maxBytes) {
      this.toast.error('Image too large. Please choose an image under ~1.5MB.');
      el.value = '';
      return;
    }

    if (!file.type.startsWith('image/')) {
      this.toast.error('Please choose a valid image file.');
      el.value = '';
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const result = reader.result;
      if (typeof result === 'string') {
        this.form.image_url = result;
        this.imageName = file.name;
        this.toast.success('Image attached');
        this.cdr.detectChanges();
      }
    };
    reader.onerror = () => {
      this.toast.error('Failed to read image file.');
    };
    reader.readAsDataURL(file);
  }

  clearImage(input: HTMLInputElement): void {
    input.value = '';
    this.form.image_url = '';
    this.imageName = '';
  }

  save(): void {
    const vitamins = this.vitaminsObjectFromRows(this.vitaminRows);
    const payload = this.cleanPayload({ ...this.form, vitamins, health_effect: this.cleanHealthEffect(this.healthEffect) });
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
          const msg = this.errorFrom(err, 'Save failed.');
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
          const msg = this.errorFrom(err, 'Delete failed.');
          this.error = msg;
          this.toast.error(msg);
        }
      });
  }

  syncDatabase(): void {
    this.loading = true;
    this.error = '';
    this.foodsApi.sync().pipe(
      timeout(15_000),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: (res) => {
        this.toast.success(`Synced successfully: ${res.synced_count} items updated.`);
      },
      error: (err) => {
        const msg = this.errorFrom(err, 'Sync failed.');
        this.error = msg;
        this.toast.error(msg);
      }
    });
  }

  importJson(event: Event, input: HTMLInputElement): void {
    const file = input.files && input.files.length > 0 ? input.files[0] : null;
    if (!file) return;

    if (!file.name.toLowerCase().endsWith('.json')) {
      this.toast.error('Invalid file format. Please select a .json file.');
      input.value = '';
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      try {
        const content = reader.result as string;
        const data = JSON.parse(content);
        
        // Ensure it's an array for the batch import
        const payload = Array.isArray(data) ? data : [data];

        this.loading = true;
        this.error = '';
        this.foodsApi.importFoods(payload).pipe(
          timeout(20_000),
          finalize(() => {
            this.loading = false;
            input.value = '';
            this.cdr.detectChanges();
          })
        ).subscribe({
          next: (res) => {
            this.toast.success(res.message || `Successfully imported ${res.count} items.`);
            this.refresh(true);
          },
          error: (err) => {
            const msg = this.errorFrom(err, 'Import failed.');
            this.error = msg;
            this.toast.error(msg);
          }
        });
      } catch (e) {
        this.toast.error('Invalid JSON format.');
        input.value = '';
      }
    };
    reader.onerror = () => {
      this.toast.error('Failed to read file.');
      input.value = '';
    };
    reader.readAsText(file);
  }

  lightClass(light: string | null | undefined): 'safe' | 'caution' | 'avoid' {
    const normalized = (light ?? '').toString().toUpperCase();
    if (normalized === 'AVOID' || normalized === 'RED') return 'avoid';
    if (normalized === 'CAUTION' || normalized === 'YELLOW') return 'caution';
    return 'safe';
  }

  trackById(_: number, item: AdminFoodItem): number {
    return item.id;
  }

  foodRoleLabel(item: AdminFoodItem | null | undefined): 'main' | 'side' {
    if (!item) return 'main';
    const explicit = ((item as any).food_role ?? '').toString().trim().toLowerCase();
    if (explicit === 'side') return 'side';
    if (explicit === 'main') return 'main';

    const mt = (item.meal_type ?? '').toString().trim().toLowerCase();
    return mt === 'side' ? 'side' : 'main';
  }

  private emptyForm(): Partial<AdminFoodItem> {
    return {
      food_name: '',
      meal_type: 'breakfast',
      food_role: 'main',
      portion_amount: 1,
      portion_unit: 'serving',
      calories: 0,
      protein_g: 0,
      carbs_g: 0,
      fat_g: 0,
      fiber_g: 0,
      sugar_g: 0,
      sodium_mg: 0,
      cholesterol_mg: 0,
      glycemic_index: 0,
      ingredients: '',
      vitamins: {},
      image_url: '',
      category: 'Vegetables',
      definition: '',
      health_effect: null,
      traffic_light: 'SAFE'
    };
  }

  private emptyHealthEffect(): Record<string, string> {
    const obj: Record<string, string> = {};
    for (const k of this.healthEffectKeys) obj[k.key] = '';
    return obj;
  }

  private hydrateHealthEffect(raw: AdminFoodItem['health_effect']): Record<string, string> {
    const base = this.emptyHealthEffect();
    if (!raw) return base;
    for (const k of this.healthEffectKeys) {
      const v = (raw as any)[k.key];
      if (typeof v === 'string') base[k.key] = v;
    }
    return base;
  }

  private cleanHealthEffect(raw: Record<string, string> | null | undefined): Record<string, string> | null {
    if (!raw) return null;
    const out: Record<string, string> = {};
    for (const k of this.healthEffectKeys) {
      const v = (raw[k.key] ?? '').toString().trim().toLowerCase();
      if (v === 'good' || v === 'neutral' || v === 'bad') {
        out[k.key] = v;
      }
    }
    return Object.keys(out).length ? out : null;
  }

  private cleanPayload(form: Partial<AdminFoodItem>): Partial<AdminFoodItem> {
    const text = (v: unknown) => (v ?? '').toString().trim();
    const num = (v: unknown) => {
      const n = typeof v === 'number' ? v : Number((v ?? '').toString());
      return Number.isFinite(n) ? n : 0;
    };

    const portionAmount = Math.max(0, num(form.portion_amount));
    const calories = Math.max(0, Math.round(num(form.calories)));
    const glycemicIndex = Math.max(0, Math.round(num((form as any).glycemic_index)));
    const foodRole = text((form as any).food_role || 'main').toLowerCase() || 'main';
    const normalizedFoodRole = foodRole === 'side' ? 'side' : 'main';

    return {
      food_name: text(form.food_name),
      category: text(form.category) || null,
      meal_type: text(form.meal_type).toLowerCase(),
      food_role: normalizedFoodRole,
      portion_amount: portionAmount,
      // Back-compat with DB column naming in this repo.
      // Backend will prefer portion_value / calories_kcal when present.
      ...(portionAmount !== null ? ({ portion_value: portionAmount } as any) : {}),
      portion_unit: text(form.portion_unit),
      calories,
      ...(calories !== null ? ({ calories_kcal: calories } as any) : {}),
      protein_g: Math.max(0, num(form.protein_g)),
      carbs_g: Math.max(0, num(form.carbs_g)),
      fat_g: Math.max(0, num(form.fat_g)),
      fiber_g: Math.max(0, num((form as any).fiber_g)),
      sugar_g: Math.max(0, num(form.sugar_g)),
      sodium_mg: Math.max(0, num(form.sodium_mg)),
      cholesterol_mg: Math.max(0, num(form.cholesterol_mg)),
      glycemic_index: glycemicIndex,
      ingredients: text((form as any).ingredients) || null,
      vitamins: (form as any).vitamins ?? {},
      image_url: text(form.image_url) || null,
      definition: text(form.definition) || null,
      health_effect: (form as any).health_effect ?? null,
      traffic_light: text(form.traffic_light || 'SAFE').toUpperCase()
    };
  }

  addVitaminRow(): void {
    this.vitaminRows = [...(this.vitaminRows ?? []), { label: '', value: '' }];
  }

  removeVitaminRow(idx: number): void {
    const next = (this.vitaminRows ?? []).filter((_, i) => i !== idx);
    this.vitaminRows = next.length ? next : [{ label: '', value: '' }];
  }

  vitaminsPreview(raw: Record<string, unknown> | null | undefined): string {
    const obj = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : null;
    if (!obj) return 'â€”';
    const pairs = Object.entries(obj)
      .map(([k, v]) => [k, (v ?? '').toString().trim()] as const)
      .filter(([k, v]) => k.trim() && v);
    if (!pairs.length) return 'â€”';
    return pairs
      .slice(0, 3)
      .map(([k, v]) => `${k}: ${v}`)
      .join(', ');
  }

  ingredientsPreview(raw: string | null | undefined): string {
    const s = (raw ?? '').toString().trim();
    if (!s) return 'â€”';
    return s.length > 60 ? `${s.slice(0, 60)}â€¦` : s;
  }

  private vitaminRowsFrom(raw: Record<string, unknown> | null | undefined): Array<{ label: string; value: string }> {
    const obj = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : null;
    if (!obj) return [{ label: '', value: '' }];
    const rows = Object.entries(obj)
      .map(([k, v]) => ({ label: k, value: (v ?? '').toString() }))
      .filter((r) => r.label.trim() || r.value.trim());
    return rows.length ? rows : [{ label: '', value: '' }];
  }

  private vitaminsObjectFromRows(
    rows: Array<{ label: string; value: string }> | null | undefined
  ): Record<string, string> {
    const out: Record<string, string> = {};
    for (const r of rows ?? []) {
      const label = (r?.label ?? '').toString().trim();
      const value = (r?.value ?? '').toString().trim();
      if (!label || !value) continue;
      out[label] = value;
    }
    return out;
  }

  private errorFrom(err: any, fallback: string): string {
    const status = Number(err?.status ?? -1);
    if (status === 0) return 'Cannot reach server. Please ensure backend is running.';
    if (status === 401 || status === 403) return 'Not authorized. Please log in with an ADMIN account.';
    if (status === 404) return 'Endpoint not found. Restart backend with the latest code.';
    if (typeof err?.error === 'string' && err.error.trim()) return err.error;
    if (typeof err?.message === 'string' && err.message.trim()) return err.message;
    return fallback;
  }

  private focusFirstEditField(): void {
    // Let the click event finish (button would otherwise keep focus).
    setTimeout(() => {
      const el = this.foodNameInput?.nativeElement;
      if (!el) return;
      el.focus();
      el.select();
    }, 0);
  }
}

