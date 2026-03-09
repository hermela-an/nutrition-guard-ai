import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';

interface MenuItem {
  label: string;
  path: string;
}

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterLink, RouterLinkActive],
  template: `
    <div class="app-shell">
      <aside class="sidebar">
        <a class="brand" routerLink="/"><span class="brand-dot"></span> NutriGuard AI</a>
        <nav class="menu">
          <a *ngFor="let item of items" [routerLink]="item.path" routerLinkActive="active">{{ item.label }}</a>
        </nav>
        <div class="sidebar-bottom">
          <button type="button" class="btn ghost logout-btn" (click)="logout()">Logout</button>
        </div>
      </aside>
      <main class="main">
        <ng-content></ng-content>
      </main>
    </div>
  `
})
export class AppShellComponent {
  @Input({ required: true }) items: MenuItem[] = [];

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}

