import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ProfileService } from '../../core/services/profile.service';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AppShellComponent],
  template: `
  <app-shell [items]="menuItems">
    <h2>BMI Calculator</h2>
    <p class="muted">Enter your height and weight to calculate BMI.</p>

    <section class="card" style="margin-top:12px;">
      <form [formGroup]="form">
        <div class="field"><label>Height (cm)</label><input class="input" type="number" formControlName="height" placeholder="e.g. 160"></div>
        <div class="field"><label>Weight (kg)</label><input class="input" type="number" formControlName="weight" placeholder="e.g. 56"></div>
      </form>
      <div class="spaced" style="justify-content:flex-start; margin-top:8px;">
        <button class="btn primary" type="button" (click)="calculate()">Calculate BMI</button>
        <button class="btn alt" type="button" (click)="reset()">Reset</button>
      </div>
      <div *ngIf="error" class="status err">Please enter valid height and weight values.</div>
    </section>

    <section *ngIf="showResult" class="result-panel" style="margin-top:14px;">
      <h3>Your Result</h3>
      <p><strong>BMI:</strong> {{ bmi | number:'1.1-1' }}</p>
      <p><strong>Category:</strong><span [class]="categoryClass">{{ category }}</span></p>
      <p><strong>Healthy Weight Range:</strong> {{ minHealthy | number:'1.1-1' }} kg - {{ maxHealthy | number:'1.1-1' }} kg</p>

      <div class="bmi-scale">
        <div class="bmi-track"><span class="bmi-marker" [style.left.%]="marker"></span></div>
        <div class="bmi-labels"><span>Underweight</span><span>Normal</span><span>Overweight</span><span>Obese</span></div>
      </div>
    </section>

    <section class="card" style="margin-top:14px;">
      <h3>BMI Categories</h3>
      <ul class="list">
        <li><strong class="bmi-under">Underweight:</strong> less than 18.5</li>
        <li><strong class="bmi-normal">Normal weight:</strong> 18.5 to 24.9</li>
        <li><strong class="bmi-over">Overweight:</strong> 25 to 29.9</li>
        <li><strong class="bmi-obese">Obese:</strong> 30 and above</li>
      </ul>
    </section>

    <div class="note-box">Note: BMI is a screening tool and does not measure body fat directly.</div>
    <p style="margin-top:16px;"><a routerLink="/dashboard" style="color:#3d64d8; font-weight:700;">Back to Dashboard</a></p>
  </app-shell>
  `
})
export class BmiCalculatorComponent {
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

  private readonly fb = inject(FormBuilder);
  private readonly profile = inject(ProfileService);

  form = this.fb.group({ height: [null as number | null, Validators.required], weight: [null as number | null, Validators.required] });
  bmi = 21.9;
  minHealthy = 47.4;
  maxHealthy = 63.7;
  category = 'Normal weight';
  marker = 35;
  showResult = false;
  error = false;

  constructor() {
    this.prefillFromProfile();
  }

  private prefillFromProfile(): void {
    this.profile.getProfile().subscribe({
      next: (res) => {
        this.form.patchValue({
          height: res.height_cm ?? null,
          weight: res.weight_kg ?? null
        });
      },
      error: () => {
        // Keep empty placeholders if profile is not available.
      }
    });
  }

  calculate(): void {
    const height = Number(this.form.value.height);
    const weight = Number(this.form.value.weight);
    if (!height || !weight || height <= 0 || weight <= 0) {
      this.error = true;
      this.showResult = false;
      return;
    }
    this.error = false;
    const h = height / 100;
    this.bmi = weight / (h * h);
    this.minHealthy = 18.5 * h * h;
    this.maxHealthy = 24.9 * h * h;
    this.category = this.bmi < 18.5 ? 'Underweight' : this.bmi < 25 ? 'Normal weight' : this.bmi < 30 ? 'Overweight' : 'Obese';
    const normalized = Math.max(14, Math.min(40, this.bmi));
    this.marker = ((normalized - 14) / (40 - 14)) * 100;
    this.showResult = true;
  }

  reset(): void {
    this.form.reset({ height: null, weight: null });
    this.error = false;
    this.showResult = false;
  }

  get categoryClass(): string {
    if (this.category === 'Underweight') return 'bmi-under';
    if (this.category === 'Normal weight') return 'bmi-normal';
    if (this.category === 'Overweight') return 'bmi-over';
    return 'bmi-obese';
  }
}
