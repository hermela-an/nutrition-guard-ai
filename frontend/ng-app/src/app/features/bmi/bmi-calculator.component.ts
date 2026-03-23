import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ProfileService } from '../../core/services/profile.service';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, AppShellComponent],
  templateUrl: './bmi-calculator.component.html',
  styleUrls: ['./bmi-calculator.component.css']
})
export class BmiCalculatorComponent {
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
    this.marker = this.markerForBmi(this.bmi);
    this.showResult = true;
  }

  private markerForBmi(bmi: number): number {
    if (!Number.isFinite(bmi)) return 0;

    const clamped = Math.max(14, Math.min(40, bmi));

    // The UI shows 4 evenly spaced category labels, so map each category range
    // into a 25% segment of the track.
    if (clamped < 18.5) return this.scale(clamped, 14, 18.5, 0, 25);
    if (clamped < 25) return this.scale(clamped, 18.5, 25, 25, 50);
    if (clamped < 30) return this.scale(clamped, 25, 30, 50, 75);
    return this.scale(clamped, 30, 40, 75, 100);
  }

  private scale(value: number, inMin: number, inMax: number, outMin: number, outMax: number): number {
    if (inMax <= inMin) return outMin;
    const t = (value - inMin) / (inMax - inMin);
    const clampedT = Math.max(0, Math.min(1, t));
    return outMin + clampedT * (outMax - outMin);
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
