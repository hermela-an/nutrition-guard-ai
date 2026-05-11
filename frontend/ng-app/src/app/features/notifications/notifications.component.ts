import { Component, OnDestroy, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer } from 'rxjs';
import { switchMap, finalize } from 'rxjs/operators';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { NotificationsService, NotificationItem } from '../../core/services/notifications.service';

export interface GroupedNotification extends NotificationItem {
  count: number;
  groupedIds: number[];
  displayType: string;
  priorityClass: string;
}

@Component({
  standalone: true,
  imports: [CommonModule, AppShellComponent],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css']
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private sub = new Subscription();
  private cdr = inject(ChangeDetectorRef);

  unreadOnly = true;
  loading = false;
  loadingMore = false;
  errorMessage: string | null = null;
  items: NotificationItem[] = [];
  unreadCount = 0;

  private readonly limit = 25;
  private offset = 0;
  hasMore = false;
  expandedId: number | null = null;

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

  mealPlanReminders: GroupedNotification[] = [];
  healthRiskAlerts: GroupedNotification[] = [];
  healthProfileNotifs: GroupedNotification[] = [];
  aiNutritionAlerts: GroupedNotification[] = [];

  constructor(public readonly notifications: NotificationsService) { }

  ngOnInit(): void {
    this.refresh();
    this.sub.add(this.notifications.unreadCount$.subscribe((count) => (this.unreadCount = count)));
    this.sub.add(
      timer(0, 15000)
        .pipe(switchMap(() => this.notifications.refreshUnreadCount()))
        .subscribe()
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  private groupNotifications(items: NotificationItem[]): GroupedNotification[] {
    const map = new Map<string, GroupedNotification>();
    for (const item of items) {
      const key = `${item.title}|${item.message}|${item.type}`;
      if (map.has(key)) {
        const existing = map.get(key)!;
        existing.count++;
        if (item.id != null) {
          existing.groupedIds.push(item.id);
        }
        if (!item.is_read) existing.is_read = false;
        if (new Date(item.created_at) > new Date(existing.created_at)) {
          existing.created_at = item.created_at;
          existing.id = item.id;
        }
      } else {
        map.set(key, {
          ...item,
          count: 1,
          groupedIds: item.id != null ? [item.id] : [],
          displayType: this.getDisplayType(item.title),
          priorityClass: this.getPriorityClass(item.title)
        });
      }
    }
    return Array.from(map.values()).sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
  }

  private getDisplayType(title?: string): string {
    const t = (title || '').toLowerCase();
    if (t.includes('food safety') || t.includes('chat food') || t.includes('risk')) return 'Safety Alert';
    if (t.includes('meal plan')) return 'Reminder';
    if (t.includes('warning')) return 'Warning';
    return 'Info';
  }

  private getPriorityClass(title?: string): string {
    const t = (title || '').toLowerCase();
    if (t.includes('food safety') || t.includes('chat food') || t.includes('risk')) return 'high';
    if (t.includes('warning')) return 'medium';
    return 'low';
  }

  private categorizeItems(items: NotificationItem[]) {
    const grouped = this.groupNotifications(items);
    this.mealPlanReminders = grouped.filter(i => (i.title?.toLowerCase() || '').includes('meal plan'));
    this.healthRiskAlerts = grouped.filter(i => {
      const t = i.title?.toLowerCase() || '';
      return t.includes('food safety') || t.includes('risk');
    });
    this.aiNutritionAlerts = grouped.filter(i => {
      const t = i.title?.toLowerCase() || '';
      return t.includes('chat food') || t.includes('ai advice');
    });

    this.healthProfileNotifs = grouped.filter(i =>
      !this.mealPlanReminders.includes(i) &&
      !this.healthRiskAlerts.includes(i) &&
      !this.aiNutritionAlerts.includes(i)
    );
  }

  refresh(): void {
    this.loading = true;
    this.errorMessage = null;
    this.offset = 0;
    this.hasMore = false;
    this.expandedId = null;
    this.notifications.list({ unreadOnly: this.unreadOnly, limit: this.limit, offset: 0 })
      .pipe(finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      }))
      .subscribe({
      next: (items) => {
        const nextItems = items ?? [];
        this.items = nextItems;
        this.categorizeItems(this.items);
        this.offset = nextItems.length;
        this.hasMore = nextItems.length === this.limit;
      },
      error: () => {
        this.errorMessage = 'Failed to load notifications. Please try again.';
      }
    });
  }

  setUnreadOnly(unreadOnly: boolean): void {
    if (this.unreadOnly === unreadOnly) return;
    this.unreadOnly = unreadOnly;
    this.refresh();
  }

  markAllRead(): void {
    this.loading = true;
    this.errorMessage = null;
    this.notifications.markAllRead()
      .pipe(finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      }))
      .subscribe({
      next: () => this.refresh(),
      error: () => {
        this.errorMessage = 'Failed to mark all as read. Please try again.';
      }
    });
  }

  markRead(item: GroupedNotification): void {
    if (!item?.groupedIds || item.groupedIds.length === 0 || item.is_read) return;
    this.notifications.markRead(item.groupedIds).subscribe({
      next: (updated) => {
        // Even if updated is 0, we can optimistically mark as read locally
        if (this.unreadOnly) {
          this.refresh();
          return;
        }
        item.is_read = true;
        for (const raw of this.items) {
          if (raw.id != null && item.groupedIds.includes(raw.id)) {
            raw.is_read = true;
          }
        }
      },
      error: () => {
        this.errorMessage = 'Failed to mark notification as read.';
      }
    });
  }

  toggleExpanded(item: GroupedNotification): void {
    const id = item?.id ?? null;
    if (!id) return;
    this.expandedId = this.expandedId === id ? null : id;
  }

  loadMore(): void {
    if (this.loadingMore || this.loading || !this.hasMore) return;
    this.loadingMore = true;
    this.errorMessage = null;
    this.notifications.list({ unreadOnly: this.unreadOnly, limit: this.limit, offset: this.offset })
      .pipe(finalize(() => {
        this.loadingMore = false;
        this.cdr.detectChanges();
      }))
      .subscribe({
      next: (items) => {
        const nextItems = items ?? [];
        this.items = [...this.items, ...nextItems];
        this.categorizeItems(this.items);
        this.offset += nextItems.length;
        this.hasMore = nextItems.length === this.limit;
      },
      error: () => {
        this.errorMessage = 'Failed to load more notifications.';
      }
    });
  }

  formatDataValue(value: unknown): string {
    if (value === null || value === undefined) return '';
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value);
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
}
