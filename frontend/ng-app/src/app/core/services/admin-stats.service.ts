import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

export interface AdminStats {
  total_users: number;
  total_foods: number;
  avoid_foods: number;
  caution_foods: number;
}

@Injectable({ providedIn: 'root' })
export class AdminStatsService {
  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  get(): Observable<AdminStats> {
    return this.api.get<AdminStats>(`/api/admin/stats`, this.auth.authHeaders());
  }
}

