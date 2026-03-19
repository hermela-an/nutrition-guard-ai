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
  items: NotificationItem[] = [];

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
    this.notifications.list({ unreadOnly: this.unreadOnly, limit: 100 }).subscribe({
      next: (items) => {
        this.items = items ?? [];
        this.loading = false;
      },
      error: () => {
        this.loading = false;
      }
    });
  }

  toggleUnreadOnly(): void {
    this.unreadOnly = !this.unreadOnly;
    this.refresh();
  }

  markAllRead(): void {
    this.notifications.markAllRead().subscribe({
      next: () => this.refresh(),
      error: () => this.refresh()
    });
  }

  markRead(item: NotificationItem): void {
    if (!item?.id || item.is_read) return;
    this.notifications.markRead([item.id]).subscribe({
      next: () => this.refresh(),
      error: () => this.refresh()
    });
  }
}

