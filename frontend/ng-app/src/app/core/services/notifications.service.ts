import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { map, switchMap, tap } from 'rxjs/operators';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

export interface NotificationItem {
  id: number | null;
  type: 'ALERT' | 'ACTIVITY' | 'REMINDER' | string;
  title: string;
  message: string;
  data?: Record<string, unknown>;
  is_read: boolean;
  created_at: string;
}

@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly unreadCountSubject = new BehaviorSubject<number>(0);
  readonly unreadCount$ = this.unreadCountSubject.asObservable();

  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  list(opts?: { unreadOnly?: boolean; limit?: number; offset?: number }): Observable<NotificationItem[]> {
    const unreadOnly = opts?.unreadOnly ? 'true' : 'false';
    const limit = String(opts?.limit ?? 50);
    const offset = String(opts?.offset ?? 0);
    return this.api.get<NotificationItem[]>(
      `/api/notifications?unreadOnly=${unreadOnly}&limit=${limit}&offset=${offset}`,
      this.auth.authHeaders()
    );
  }

  refreshUnreadCount(): Observable<number> {
    return this.api
      .get<{ count: number }>(`/api/notifications/unread-count`, this.auth.authHeaders())
      .pipe(
        map((r) => Number(r?.count ?? 0)),
        tap((count) => this.unreadCountSubject.next(count))
      );
  }

  markAllRead(): Observable<number> {
    return this.api
      .post<{ updated: number }>(`/api/notifications/mark-read`, {}, this.auth.authHeaders())
      .pipe(
        map((r) => Number(r?.updated ?? 0)),
        switchMap((updated) => this.refreshUnreadCount().pipe(map(() => updated)))
      );
  }

  markRead(ids: number[]): Observable<number> {
    return this.api
      .post<{ updated: number }>(`/api/notifications/mark-read`, { ids }, this.auth.authHeaders())
      .pipe(
        map((r) => Number(r?.updated ?? 0)),
        switchMap((updated) => this.refreshUnreadCount().pipe(map(() => updated)))
      );
  }
}
