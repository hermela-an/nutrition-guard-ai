import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AppShellComponent } from '../../shared/components/app-shell.component';

@Component({
  standalone: true,
  imports: [CommonModule, AppShellComponent],
  template: `
  <app-shell [items]="menuItems">
    <div class="spaced">
      <div>
        <h2>User Dashboard</h2>
        <p class="muted">Welcome back. Here is your health and nutrition snapshot for today.</p>
      </div>
    </div>

    <div class="grid-3" style="margin-top:12px;">
      <div class="card"><small class="muted">Target Calories</small><div class="stat-value">2,020</div><span class="pill safe">On Track</span></div>
      <div class="card"><small class="muted">Sodium Consumed</small><div class="stat-value">1,120 mg</div><span class="pill caution">75% of Limit</span></div>
      <div class="card"><small class="muted">Weight Trend (30d)</small><div class="stat-value">-1.6 kg</div><span class="pill safe">Progressing</span></div>
    </div>
  </app-shell>
  `
})
export class DashboardComponent {
  menuItems = [
    { label: 'Dashboard', path: '/dashboard' },
    { label: 'Profile Setup', path: '/profile-setup' },
    { label: 'BMI Calculator', path: '/bmi-calculator' },
    { label: 'Nutrition Plan', path: '/nutrition-plan' },
    { label: 'Daily Meal Plan', path: '/daily-meal-plan' },
    { label: 'Progress Tracking', path: '/progress-tracking' },
    { label: 'AI Chat Assistant', path: '/ai-chat-assistant' },
    { label: 'Food Search', path: '/food-search' },
    { label: 'Notifications', path: '/notifications' }
  ];
}
