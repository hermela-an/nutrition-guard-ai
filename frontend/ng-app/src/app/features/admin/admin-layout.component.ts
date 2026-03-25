import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterOutlet } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { AuthService } from '../../core/services/auth.service';
import { ThemeService } from '../../shared/services/theme.service';

@Component({
  standalone: true,
  imports: [CommonModule, RouterOutlet, AppShellComponent],
  template: `
    <app-shell [items]="menuItems">
      <div class="admin-topbar">
        <div class="admin-topbar-left">
          <span class="pill safe">Admin</span>
          <span class="muted">NutritionGuard AI Console</span>
        </div>
        <div class="admin-topbar-right">
          <span class="muted">{{ email }}</span>
          <button type="button" class="btn ghost small" (click)="toggleTheme()">
            {{ theme.isDark() ? 'Light' : 'Dark' }} mode
          </button>
        </div>
      </div>

      <router-outlet></router-outlet>
    </app-shell>
  `
})
export class AdminLayoutComponent {
  readonly menuItems = [
    { label: 'Overview', path: '/admin/overview' },
    { label: 'User Management', path: '/admin/users' },
    { label: 'Food Management', path: '/admin/foods' },
    { label: 'Alerts', path: '/admin/alerts' }
  ];

  constructor(public readonly auth: AuthService, public readonly theme: ThemeService) {}

  get email(): string {
    return this.auth.userEmail() ?? 'admin';
  }

  toggleTheme(): void {
    this.theme.toggle();
  }
}

