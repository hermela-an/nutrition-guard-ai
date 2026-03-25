import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

export interface AdminUser {
  id: string;
  email: string;
  role: 'USER' | 'ADMIN' | string;
  is_active: boolean;
  created_at: string;
}

export interface AdminUsersResponse {
  items: AdminUser[];
  total: number;
}

@Injectable({ providedIn: 'root' })
export class AdminUsersService {
  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  list(opts?: { q?: string; status?: 'ACTIVE' | 'INACTIVE' | 'ALL'; limit?: number; offset?: number }): Observable<AdminUsersResponse> {
    const q = encodeURIComponent((opts?.q ?? '').trim());
    const status = encodeURIComponent((opts?.status ?? 'ALL').toString());
    const limit = String(opts?.limit ?? 20);
    const offset = String(opts?.offset ?? 0);
    const params = `?q=${q}&status=${status}&limit=${limit}&offset=${offset}`;
    return this.api.get<AdminUsersResponse>(`/api/admin/users${params}`, this.auth.authHeaders());
  }

  update(id: string, payload: Partial<Pick<AdminUser, 'role' | 'is_active'>>): Observable<AdminUser> {
    return this.api.put<AdminUser>(`/api/admin/users/${encodeURIComponent(id)}`, payload, this.auth.authHeaders());
  }

  delete(id: string): Observable<{ deleted: number }> {
    return this.api.delete<{ deleted: number }>(`/api/admin/users/${encodeURIComponent(id)}`, this.auth.authHeaders());
  }
}

