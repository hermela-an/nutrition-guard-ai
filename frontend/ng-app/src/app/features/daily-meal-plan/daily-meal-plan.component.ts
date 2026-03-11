import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';

interface MealItem {
  name: string;
  portion: string;
  calories: number;
}

interface MealBlock {
  title: string;
  time: string;
  targetCalories: number;
  items: MealItem[];
}

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, AppShellComponent],
  template: `
  <app-shell [items]="menuItems">
    <div class="page-header">
      <div>
        <span class="eyebrow">Daily planner</span>
        <h2>Daily Meal Plan</h2>
        <p class="muted">A structured plan with balanced portions and a clear calorie budget.</p>
      </div>
      <div class="header-actions">
        <a class="btn secondary" routerLink="/nutrition-plan">Back to Nutrition Plan</a>
        <a class="btn primary" routerLink="/profile-setup">Update Profile</a>
      </div>
    </div>

    <div class="summary-grid">
      <div class="summary-card">
        <div class="summary-label">Target Calories</div>
        <div class="summary-value">{{ totals.targetCalories | number:'1.0-0' }}</div>
        <div class="summary-sub">Daily goal</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Planned Calories</div>
        <div class="summary-value">{{ totals.plannedCalories | number:'1.0-0' }}</div>
        <div class="summary-sub">Meals combined</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Protein Target</div>
        <div class="summary-value">{{ totals.protein }}g</div>
        <div class="summary-sub">Macro focus</div>
      </div>
      <div class="summary-card">
        <div class="summary-label">Hydration</div>
        <div class="summary-value">{{ totals.waterLiters }}L</div>
        <div class="summary-sub">Daily reminder</div>
      </div>
    </div>

    <div class="section-header">
      <h3>Meal Schedule</h3>
      <span class="muted">Portions and targets by time</span>
    </div>

    <div class="meal-grid">
      <article class="meal-card" *ngFor="let meal of meals">
        <div class="meal-header">
          <div>
            <div class="meal-title">{{ meal.title }}</div>
            <div class="meal-time">{{ meal.time }}</div>
          </div>
          <div class="meal-target">
            <span class="label">Target</span>
            <span class="value">{{ meal.targetCalories | number:'1.0-0' }} cal</span>
          </div>
        </div>
        <ul class="meal-items">
          <li *ngFor="let item of meal.items">
            <div class="item-name">{{ item.name }}</div>
            <div class="item-meta">
              <span>{{ item.portion }}</span>
              <span>{{ item.calories | number:'1.0-0' }} cal</span>
            </div>
          </li>
        </ul>
      </article>
    </div>

    <div class="section-header">
      <h3>Daily Progress</h3>
      <span class="muted">Planned vs target calories</span>
    </div>

    <div class="progress-card">
      <div class="progress-header">
        <span class="label">Calories</span>
        <span class="value">{{ totals.plannedCalories | number:'1.0-0' }} / {{ totals.targetCalories | number:'1.0-0' }}</span>
      </div>
      <div class="progress-bar">
        <div class="progress-fill" [style.width]="progressPercentage + '%'"></div>
      </div>
      <div class="progress-sub muted">Aim to stay within 5% of your target.</div>
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

    .meal-grid {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 12px;
      margin-bottom: 18px;
    }

    .meal-card {
      background: #fff;
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      gap: 10px;
    }

    .meal-header {
      display: flex;
      justify-content: space-between;
      align-items: flex-start;
      gap: 12px;
    }

    .meal-title {
      font-weight: 800;
      font-size: 16px;
    }

    .meal-time {
      font-size: 12px;
      color: var(--muted);
      margin-top: 2px;
    }

    .meal-target {
      text-align: right;
      background: #f4f7f3;
      padding: 6px 10px;
      border-radius: 10px;
    }

    .meal-target .label {
      display: block;
      font-size: 10px;
      text-transform: uppercase;
      color: #7a8a7c;
      letter-spacing: 0.6px;
    }

    .meal-target .value {
      font-weight: 700;
      font-size: 12px;
    }

    .meal-items {
      list-style: none;
      padding: 0;
      margin: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }

    .meal-items li {
      border: 1px solid #e6ede3;
      border-radius: 10px;
      padding: 8px 10px;
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 10px;
    }

    .item-name {
      font-weight: 600;
    }

    .item-meta {
      display: flex;
      gap: 10px;
      font-size: 12px;
      color: var(--muted);
    }

    .progress-card {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 14px;
      padding: 16px;
    }

    .progress-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 10px;
    }

    .progress-header .label {
      font-weight: 700;
    }

    .progress-header .value {
      font-weight: 800;
      font-size: 1.2rem;
    }

    .progress-bar {
      height: 12px;
      background: #e8f0e6;
      border-radius: 999px;
      overflow: hidden;
    }

    .progress-fill {
      height: 100%;
      width: 0%;
      background: linear-gradient(90deg, #2e9f61, #7ac37f);
      transition: width 0.3s ease;
    }

    .progress-sub {
      margin-top: 8px;
      font-size: 12px;
    }

    @media (max-width: 980px) {
      .summary-grid {
        grid-template-columns: repeat(2, 1fr);
      }

      .meal-grid {
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
export class DailyMealPlanComponent {
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

  meals: MealBlock[] = [
    {
      title: 'Breakfast',
      time: '07:30',
      targetCalories: 520,
      items: [
        { name: 'Greek yogurt bowl', portion: '1 bowl', calories: 220 },
        { name: 'Whole-grain toast', portion: '2 slices', calories: 180 },
        { name: 'Mixed berries', portion: '1 cup', calories: 120 }
      ]
    },
    {
      title: 'Lunch',
      time: '12:45',
      targetCalories: 680,
      items: [
        { name: 'Grilled chicken salad', portion: '1 plate', calories: 340 },
        { name: 'Quinoa side', portion: '3/4 cup', calories: 220 },
        { name: 'Olive oil dressing', portion: '1 tbsp', calories: 120 }
      ]
    },
    {
      title: 'Dinner',
      time: '19:00',
      targetCalories: 700,
      items: [
        { name: 'Baked salmon', portion: '150 g', calories: 320 },
        { name: 'Roasted vegetables', portion: '2 cups', calories: 180 },
        { name: 'Brown rice', portion: '1 cup', calories: 200 }
      ]
    }
  ];

  totals = {
    targetCalories: 2100,
    plannedCalories: 1900,
    protein: 135,
    waterLiters: 2.5
  };

  get progressPercentage(): number {
    return Math.min(100, Math.round((this.totals.plannedCalories / this.totals.targetCalories) * 100));
  }
}

