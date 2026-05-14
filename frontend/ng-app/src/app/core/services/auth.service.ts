import { Injectable } from '@angular/core';
import { HttpHeaders } from '@angular/common/http';
import { map, tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { TOKEN_KEY, USER_KEY } from '../config/app.constants';

export interface AuthResponse {
  user: { id: string; email: string; role?: 'USER' | 'ADMIN' | string };
  token: string;
}

export interface RegisterResponse {
  message: string;
  user?: { id: string; email: string; role?: 'USER' | 'ADMIN' | string; is_verified?: boolean };
}

export interface VerifyEmailResponse {
  message: string;
  email?: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  constructor(private readonly api: ApiService) {}

  register(email: string, password: string, fullName?: string) {
    return this.api.post<RegisterResponse>('/api/auth/register', { email, password, fullName });
  }

  login(email: string, password: string) {
    return this.api.post<AuthResponse>('/api/auth/login', { email, password }).pipe(
      tap((res) => this.persist(res))
    );
  }

  verifyEmail(token: string) {
    const safe = encodeURIComponent(token ?? '');
    return this.api.get<VerifyEmailResponse>(`/api/auth/verify-email?token=${safe}`);
  }

  resendVerification(email: string) {
    return this.api.post<{ message: string }>('/api/auth/resend-verification', { email });
  }

  forgotPassword(email: string) {
    return this.api.post<{ message: string }>('/api/auth/forgot-password', { email });
  }

  resetPassword(token: string, password: string) {
    return this.api.post<{ message: string }>('/api/auth/reset-password', { token, password });
  }

  token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  authHeaders(): HttpHeaders {
    const token = this.token();
    return new HttpHeaders(token ? { Authorization: `Bearer ${token}` } : {});
  }

  isAuthenticated(): boolean {
    return !!this.token();
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  userEmail(): string | null {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw).email ?? null;
    } catch {
      return null;
    }
  }

  userRole(): string {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return 'USER';
    try {
      return (JSON.parse(raw).role ?? 'USER').toString();
    } catch {
      return 'USER';
    }
  }

  isAdmin(): boolean {
    return this.userRole().trim().toUpperCase() === 'ADMIN';
  }

  private persist(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, JSON.stringify(res.user));
  }
}
