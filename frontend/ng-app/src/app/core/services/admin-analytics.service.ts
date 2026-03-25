import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

export interface FoodSearchDay {
  day: string; // YYYY-MM-DD
  count: number;
  red_count: number;
}

export interface FoodSearchAnalytics {
  window_days: number;
  days: FoodSearchDay[];
}

@Injectable({ providedIn: 'root' })
export class AdminAnalyticsService {
  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  foodSearches(days = 14): Observable<FoodSearchAnalytics> {
    const safe = Math.max(3, Math.min(days, 60));
    return this.api.get<FoodSearchAnalytics>(`/api/admin/analytics/food-searches?days=${safe}`, this.auth.authHeaders());
  }
}

