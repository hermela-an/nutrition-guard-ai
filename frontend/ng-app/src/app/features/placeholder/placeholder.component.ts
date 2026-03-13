import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';

@Component({
  standalone: true,
  imports: [CommonModule, AppShellComponent],
  template: `
  <app-shell [items]="menuItems">
    <h2>{{ title }}</h2>
    <div class="card" style="margin-top:12px;">
      <p><strong>Placeholder:</strong> This page is prepared in Angular structure and ready for implementation.</p>
      <p class="muted">Next iteration will add full UI and API integration for {{ title.toLowerCase() }}.</p>
    </div>
  </app-shell>
  `
})
export class PlaceholderComponent {
  private readonly route = inject(ActivatedRoute);
  title = this.route.snapshot.data['title'] ?? 'Page';

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
}
