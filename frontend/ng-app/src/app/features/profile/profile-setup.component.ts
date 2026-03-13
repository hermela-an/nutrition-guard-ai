import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { timeout } from 'rxjs/operators';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ProfileService } from '../../core/services/profile.service';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, AppShellComponent],
  template: `
    <app-shell [items]="menuItems">
      <h2>Profile Setup (Health Data Form)</h2>
      <p class="lead">Fill your health profile for personalized planning.</p>
      <div class="card">
        <form [formGroup]="form">
          <div class="grid-2">
            <div class="field"><label>Age</label><input #ageInput class="input" type="number" formControlName="age" placeholder="Enter your age"></div>
            <div class="field"><label>Gender</label><select formControlName="gender"><option value="">Select Gender</option><option value="female">Female</option><option value="male">Male</option></select></div>
            <div class="field"><label>Height (cm)</label><input class="input" type="number" formControlName="height_cm" placeholder="Enter height in cm"></div>
            <div class="field"><label>Weight (kg)</label><input class="input" type="number" formControlName="weight_kg" placeholder="Enter weight in kg"></div>
            <div class="field"><label>Activity Level</label><select formControlName="activity_level"><option value="">Select Activity</option><option value="moderate">Moderate</option><option value="sedentary">Sedentary</option><option value="active">Active</option><option value="extra_active">Extra Active</option></select></div>
            <div class="field"><label>Goal</label><select formControlName="goal"><option value="">Select Goal</option><option value="weight_loss">Weight Loss</option><option value="maintain">Maintain weight</option><option value="weight_gain">Weight Gain</option><option value="improve_health_conditions">Improve health conditions</option></select></div>
          </div>
          <h3 style="margin-top:12px;">Health Conditions</h3>
          <div class="grid-3">
            <label><input type="checkbox" formControlName="high_bp"> High BP</label>
            <label><input type="checkbox" formControlName="diabetes"> Diabetes</label>
            <label><input type="checkbox" formControlName="high_cholesterol"> High Cholesterol</label>
            <label><input type="checkbox" formControlName="low_bp"> Low BP</label>
            <label><input type="checkbox" formControlName="anemia"> Anemia</label>
            <label><input type="checkbox" formControlName="allergy"> Allergy</label>
          </div>
        </form>
        <div class="spaced" style="justify-content:flex-start; margin-top:14px;">
          <button class="btn primary" type="button" (click)="save()" [disabled]="saving">{{ saving ? 'Saving...' : 'Save Profile' }}</button>
          <button class="btn alt" type="button" (click)="resetForm()" [disabled]="saving">Reset</button>
        </div>
        <div *ngIf="status" class="status" [class.ok]="statusType === 'ok'" [class.err]="statusType === 'err'" [class.info]="statusType === 'info'">{{ status }}</div>
      </div>
    </app-shell>
  `
})
export class ProfileSetupComponent {
  @ViewChild('ageInput') ageInput!: ElementRef<HTMLInputElement>;

  saving = false;
  statusType: 'ok' | 'err' | 'info' = 'info';
  status = '';

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
  private readonly defaultFormValues = {
    age: null as number | null,
    gender: '',
    height_cm: null as number | null,
    weight_kg: null as number | null,
    activity_level: '',
    goal: '',
    high_bp: false,
    diabetes: false,
    high_cholesterol: false,
    low_bp: false,
    anemia: false,
    allergy: false
  };

  form = this.fb.group({
    age: [this.defaultFormValues.age, Validators.required],
    gender: [this.defaultFormValues.gender, Validators.required],
    height_cm: [this.defaultFormValues.height_cm, Validators.required],
    weight_kg: [this.defaultFormValues.weight_kg, Validators.required],
    activity_level: [this.defaultFormValues.activity_level, Validators.required],
    goal: [this.defaultFormValues.goal, Validators.required],
    high_bp: [this.defaultFormValues.high_bp],
    diabetes: [this.defaultFormValues.diabetes],
    high_cholesterol: [this.defaultFormValues.high_cholesterol],
    low_bp: [this.defaultFormValues.low_bp],
    anemia: [this.defaultFormValues.anemia],
    allergy: [this.defaultFormValues.allergy]
  });

  constructor(private readonly profile: ProfileService, private readonly router: Router) {
    this.load();
  }

  load(): void {
    this.profile.getProfile().subscribe({
      next: (res) => {
        const values = {
          age: res.age ?? null,
          gender: res.gender ?? '',
          height_cm: res.height_cm ?? null,
          weight_kg: res.weight_kg ?? null,
          activity_level: res.activity_level ?? '',
          goal: res.goal ?? '',
          high_bp: !!res.health_metadata?.['high_bp'],
          diabetes: !!res.health_metadata?.['diabetes'],
          high_cholesterol: !!res.health_metadata?.['high_cholesterol'],
          low_bp: !!res.health_metadata?.['low_bp'],
          anemia: !!res.health_metadata?.['anemia'],
          allergy: !!res.health_metadata?.['allergy']
        };
        this.form.patchValue(values);
      },
      error: () => {
        this.form.patchValue(this.defaultFormValues);
        this.setStatus('info', 'No profile yet. Fill and save.', 3000);
      }
    });
  }

  save(): void {
    if (this.form.invalid) {
      this.setStatus('err', 'Please fill all required fields.');
      return;
    }

    this.saving = true;
    this.setStatus('info', 'Saving profile...');

    const v = this.form.getRawValue();
    const payload = {
      age: Number(v.age),
      gender: v.gender!,
      height_cm: Number(v.height_cm),
      weight_kg: Number(v.weight_kg),
      activity_level: v.activity_level!,
      goal: v.goal!,
      health_metadata: {
        high_bp: !!v.high_bp,
        diabetes: !!v.diabetes,
        high_cholesterol: !!v.high_cholesterol,
        low_bp: !!v.low_bp,
        anemia: !!v.anemia,
        allergy: !!v.allergy
      }
    };

    this.profile.saveProfile(payload).pipe(timeout(5000)).subscribe({
      next: () => {
        this.setStatus('ok', 'Profile saved successfully.');
        this.saving = false;
        setTimeout(() => this.router.navigateByUrl('/bmi-calculator'), 700);
      },
      error: (err) => {
        if (err?.name === 'TimeoutError') {
          this.setStatus('err', 'Server is taking too long. Please try again.');
        } else {
          this.setStatus('err', err?.error || 'Failed to save profile');
        }
        this.saving = false;
      }
    });
  }

  resetForm(): void {
    this.form.reset(this.defaultFormValues);
    this.setStatus('info', 'Form cleared. Please enter new profile information.', 3000);
    setTimeout(() => this.ageInput?.nativeElement?.focus(), 0);
  }

  private setStatus(type: 'ok' | 'err' | 'info', message: string, clearAfterMs?: number): void {
    this.statusType = type;
    this.status = message;
    if (clearAfterMs) {
      setTimeout(() => {
        if (this.status === message) {
          this.status = '';
        }
      }, clearAfterMs);
    }
  }
}
