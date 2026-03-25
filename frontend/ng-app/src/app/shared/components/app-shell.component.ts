import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { FloatingChatComponent } from './floating-chat.component';
import { NotificationsService } from '../../core/services/notifications.service';
import { Subscription, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { ToastContainerComponent } from './toast-container.component';
import { ConfirmDialogHostComponent } from './confirm-dialog-host.component';

interface MenuItem {
  label: string;
  path: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive, FloatingChatComponent, ToastContainerComponent, ConfirmDialogHostComponent],
  template: `
    <div class="app-shell">
      <aside class="sidebar">
        <a class="brand" routerLink="/"><span class="brand-dot"></span> NutriGuard AI</a>
        <nav class="menu">
          <a *ngFor="let item of items" [routerLink]="item.path" routerLinkActive="active">
            <span>{{ item.label }}</span>
            <span *ngIf="item.path === '/notifications' && unreadCount > 0" class="notif-badge">{{ unreadCount }}</span>
          </a>
        </nav>
        <div class="sidebar-bottom">
          <button type="button" class="btn ghost logout-btn" (click)="logout()">Logout</button>
        </div>
      </aside>
      <main class="main">
        <ng-content></ng-content>
      </main>

      <!-- Global AI Chat Assistant -->
      <app-floating-chat></app-floating-chat>

      <!-- Global UI overlays -->
      <app-toast-container></app-toast-container>
      <app-confirm-dialog-host></app-confirm-dialog-host>
    </div>
  `,
  styles: [`
    .notif-badge {
      margin-left: 8px;
      min-width: 18px;
      height: 18px;
      padding: 0 6px;
      border-radius: 999px;
      background: #d64545;
      color: white;
      font-size: 11px;
      font-weight: 800;
      display: inline-flex;
      align-items: center;
      justify-content: center;
      line-height: 18px;
    }
  `]
})
export class AppShellComponent implements OnInit, OnDestroy {
  @Input({ required: true }) items: MenuItem[] = [];

  unreadCount = 0;
  private sub = new Subscription();

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly notifications: NotificationsService
  ) {}

  ngOnInit(): void {
    this.sub.add(
      timer(0, 20000).pipe(switchMap(() => this.notifications.refreshUnreadCount())).subscribe({
        next: (count) => (this.unreadCount = count),
        error: () => {}
      })
    );
  }

  ngOnDestroy(): void {
    this.sub.unsubscribe();
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}

