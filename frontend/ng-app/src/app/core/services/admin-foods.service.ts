import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

export interface AdminFoodItem {
  id: number;
  food_name: string;
  meal_type: string;
  portion_amount: number;
  portion_unit: string;
  calories: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  sugar_g: number;
  sodium_mg: number;
  cholesterol_mg: number;
  vitamins?: Record<string, unknown> | null;
  image_url?: string | null;
  definition?: string | null;
  description?: string | null;
  traffic_light?: 'GREEN' | 'YELLOW' | 'RED' | string;
}

@Injectable({ providedIn: 'root' })
export class AdminFoodsService {
  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  list(opts?: { q?: string; limit?: number; offset?: number }): Observable<AdminFoodItem[]> {
    const q = encodeURIComponent((opts?.q ?? '').trim());
    const limit = String(opts?.limit ?? 50);
    const offset = String(opts?.offset ?? 0);
    const query = q ? `?q=${q}&limit=${limit}&offset=${offset}` : `?limit=${limit}&offset=${offset}`;
    return this.api.get<AdminFoodItem[]>(`/api/admin/foods${query}`, this.auth.authHeaders());
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
