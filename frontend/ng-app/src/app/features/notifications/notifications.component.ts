import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { NotificationsService, NotificationItem } from '../../core/services/notifications.service';

@Component({
  standalone: true,
  imports: [CommonModule, AppShellComponent],
  templateUrl: './notifications.component.html',
  styleUrls: ['./notifications.component.css']
})
export class NotificationsComponent implements OnInit, OnDestroy {
  private sub = new Subscription();

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

  constructor(public readonly notifications: NotificationsService) {}

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

  refresh(): void {
    this.loading = true;
    this.errorMessage = null;
    this.offset = 0;
    this.hasMore = false;
    this.expandedId = null;
    this.notifications.list({ unreadOnly: this.unreadOnly, limit: this.limit, offset: 0 }).subscribe({
      next: (items) => {
        const nextItems = items ?? [];
        this.items = nextItems;
        this.offset = nextItems.length;
        this.hasMore = nextItems.length === this.limit;
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'Failed to load notifications. Please try again.';
        this.loading = false;
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
    this.notifications.markAllRead().subscribe({
      next: () => this.refresh(),
      error: () => {
        this.errorMessage = 'Failed to mark all as read. Please try again.';
        this.loading = false;
      }
    });
  }

  markRead(item: NotificationItem): void {
    if (!item?.id || item.is_read) return;
    this.notifications.markRead([item.id]).subscribe({
      next: (updated) => {
        if (updated > 0) {
          if (this.unreadOnly) {
            this.refresh();
            return;
          }
          item.is_read = true;
        }
      },
      error: () => {
        this.errorMessage = 'Failed to mark notification as read.';
      }
    });
  }

  toggleExpanded(item: NotificationItem): void {
    const id = item?.id ?? null;
    if (!id) return;
    this.expandedId = this.expandedId === id ? null : id;
  }

  loadMore(): void {
    if (this.loadingMore || this.loading || !this.hasMore) return;
    this.loadingMore = true;
    this.errorMessage = null;
    this.notifications.list({ unreadOnly: this.unreadOnly, limit: this.limit, offset: this.offset }).subscribe({
      next: (items) => {
        const nextItems = items ?? [];
        this.items = [...this.items, ...nextItems];
        this.offset += nextItems.length;
        this.hasMore = nextItems.length === this.limit;
        this.loadingMore = false;
      },
      error: () => {
        this.errorMessage = 'Failed to load more notifications.';
        this.loadingMore = false;
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
