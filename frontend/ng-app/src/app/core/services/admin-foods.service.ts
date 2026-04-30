import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

export interface AdminFoodItem {
  id: number;
  food_name: string;
  meal_type: string;
  food_role?: 'main' | 'side' | string | null;
  item_type?: 'food' | 'meal' | string | null;
  components_json?: unknown[] | null;
  portion_amount: number;
  portion_unit: string;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  fiber_g: number;
  sugar_g: number;
  sodium_mg: number;
  cholesterol_mg: number;
  glycemic_index: number;
  vitamins?: Record<string, unknown> | null;
  ingredients?: string | null;
  image_url?: string | null;
  category?: string | null;
  definition?: string | null;
  health_effect?: Record<string, 'good' | 'neutral' | 'bad' | string> | null;
  traffic_light?: 'SAFE' | 'CAUTION' | 'AVOID' | string;
}

@Injectable({ providedIn: 'root' })
export class AdminFoodsService {
  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  list(opts?: { q?: string; limit?: number; offset?: number }): Observable<AdminFoodItem[]> {
    const q = encodeURIComponent((opts?.q ?? '').trim());
    const limit = String(opts?.limit ?? 50);
    const offset = String(opts?.offset ?? 0);
    const query = q ? `?q=${q}&limit=${limit}&offset=${offset}` : `?limit=${limit}&offset=${offset}`;
    return this.api.get<unknown>(`/api/admin/foods${query}`, this.auth.authHeaders()).pipe(
      map((res) => {
        if (Array.isArray(res)) return res as AdminFoodItem[];
        const maybeItems = (res as any)?.items;
        if (Array.isArray(maybeItems)) return maybeItems as AdminFoodItem[];
        return [];
      })
    );
  }

  create(payload: Partial<AdminFoodItem>): Observable<AdminFoodItem> {
    return this.api.post<AdminFoodItem>(`/api/admin/foods`, payload, this.auth.authHeaders());
  }

  update(id: number, payload: Partial<AdminFoodItem>): Observable<AdminFoodItem> {
    return this.api.put<AdminFoodItem>(`/api/admin/foods/${id}`, payload, this.auth.authHeaders());
  }

  delete(id: number): Observable<{ deleted: number }> {
    return this.api.delete<{ deleted: number }>(`/api/admin/foods/${id}`, this.auth.authHeaders());
  }
}
