import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';

interface ProgressPoint {
  day: string;
  calories: number;
  water: number;
}

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, AppShellComponent],
  template: `
  <app-shell [items]="menuItems">
    <div class="page-header">
      <div>
        <span class="eyebrow">Progress insights</span>
        <h2>Progress Tracking</h2>
        <p class="muted">Monitor your weekly calories, hydration, and weight trends.</p>
      </div>
      <div class="header-actions">
        <a class="btn secondary" routerLink="/nutrition-plan">Back to Nutrition Plan</a>
        <a class="btn primary" routerLink="/profile-setup">Update Profile</a>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card">
        <div class="summary-label">Weekly Avg Calories</div>
        <div class="summary-value">{{ averages.calories | number:'1.0-0' }}</div>
        <div class="summary-sub">Target {{ targets.calories | number:'1.0-0' }}</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Weekly Avg Water</div>
        <div class="summary-value">{{ averages.water | number:'1.1-1' }}L</div>
        <div class="summary-sub">Target {{ targets.water | number:'1.1-1' }}L</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Weight Trend</div>
        <div class="summary-value">{{ averages.weightDelta }} kg</div>
        <div class="summary-sub">Last 7 days</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Consistency</div>
        <div class="summary-value">{{ averages.consistency }}%</div>
        <div class="summary-sub">Goal adherence</div>
      </div>
    </div>

    <div class="section-header">
      <h3>Weekly Overview</h3>
      <span class="muted">Calories and water by day</span>
    </div>

    <div class="progress-grid">
      <div class="progress-card" *ngFor="let point of weekly">
        <div class="progress-day">{{ point.day }}</div>
        <div class="progress-row">
          <span class="label">Calories</span>
          <span class="value">{{ point.calories | number:'1.0-0' }}</span>
        </div>
        <div class="bar">
          <div class="fill calories" [style.width]="percent(point.calories, targets.calories) + '%'"></div>
        </div>
        <div class="progress-row">
          <span class="label">Water</span>
          <span class="value">{{ point.water | number:'1.1-1' }}L</span>
        </div>
        <div class="bar">
          <div class="fill water" [style.width]="percent(point.water, targets.water) + '%'"></div>
        </div>
      </div>
    </div>

    <div class="section-header">
      <h3>Notes</h3>
      <span class="muted">Quick guidance</span>
    </div>

    <div class="insight-card">
      <p><strong>Nice work:</strong> Your hydration consistency is improving.</p>
      <p class="muted">If you want faster progress, keep daily calories within 5% of target.</p>
    </div>
  </app-shell>
  `,
  styles: [`
    .page-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 20px;
      margin-bottom: 18px;
    }

    .eyebrow {
      text-transform: uppercase;
      font-size: 11px;
      letter-spacing: 1.4px;
      color: #7a8a7c;
      font-weight: 700;
    }

    .page-header h2 {
      margin: 6px 0 6px;
      font-size: 28px;
      line-height: 1.1;
    }

    .header-actions {
      display: flex;
      gap: 10px;
      flex-wrap: wrap;
    }

    .summary-grid {
      display: grid;
      grid-template-columns: repeat(4, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .summary-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
    }

    .summary-label {
      font-size: 12px;
      text-transform: uppercase;
      letter-spacing: 0.6px;
      color: var(--muted);
    }

    .summary-value {
      font-size: 1.8rem;
      font-weight: 800;
      margin-top: 6px;
    }

    .summary-sub {
      font-size: 11px;
      margin-top: 4px;
      color: var(--muted);
    }

    .section-header {
      display: flex;
      align-items: baseline;
      justify-content: space-between;
      margin: 22px 0 10px;
    }

    .section-header h3 {
      margin: 0;
      font-size: 18px;
      font-weight: 800;
    }

    .progress-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .progress-card {
      background: #fff;
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .progress-day {
      font-weight: 800;
      font-size: 15px;
    }

    .progress-row {
      display: flex;
      justify-content: space-between;
      font-size: 12px;
      color: var(--muted);
    }

    .progress-row .value {
      font-weight: 700;
      color: var(--text);
    }

    .bar {
      height: 8px;
      background: #e8f0e6;
      border-radius: 999px;
      overflow: hidden;
    }

    .fill {
      height: 100%;
      width: 0%;
      transition: width 0.3s ease;
    }

    .fill.calories {
      background: linear-gradient(90deg, #2e9f61, #79c785);
    }

    .fill.water {
      background: linear-gradient(90deg, #3d64d8, #6d8cf0);
    }

    .insight-card {
      background: #f4f7f3;
      border: 1px solid #d8dfcf;
      border-radius: 14px;
      padding: 16px;
    }

    @media (max-width: 980px) {
      .summary-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .progress-grid {
        grid-template-columns: 1fr;
      }

      .page-header {
        flex-direction: column;
        align-items: flex-start;
      }
    }

    @media (max-width: 640px) {
      .summary-grid {
        grid-template-columns: 1fr;
      }

      .section-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 4px;
      }
    }
  `]
})
export class ProgressTrackingComponent {
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

  targets = {
    calories: 2100,
    water: 2.5
  };

  averages = {
    calories: 1985,
    water: 2.2,
    weightDelta: -0.4,
    consistency: 86
  };

  weekly: ProgressPoint[] = [
    { day: 'Mon', calories: 2050, water: 2.1 },
    { day: 'Tue', calories: 1900, water: 2.4 },
    { day: 'Wed', calories: 2140, water: 2.6 },
    { day: 'Thu', calories: 1800, water: 2.0 },
    { day: 'Fri', calories: 2200, water: 2.3 },
    { day: 'Sat', calories: 2000, water: 2.5 },
    { day: 'Sun', calories: 1950, water: 2.1 }
  ];

  percent(value: number, target: number): number {
    if (target <= 0) return 0;
    return Math.min(100, Math.round((value / target) * 100));
  }
}

