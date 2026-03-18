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
  templateUrl: './profile-setup.component.html',
  styleUrls: ['./profile-setup.component.css']
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
    allergy: false,
    heart_disease: false,
    kidney_disease: false
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
    allergy: [this.defaultFormValues.allergy],
    heart_disease: [this.defaultFormValues.heart_disease],
    kidney_disease: [this.defaultFormValues.kidney_disease]
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
          allergy: !!res.health_metadata?.['allergy'],
          heart_disease: !!res.health_metadata?.['heart_disease'],
          kidney_disease: !!res.health_metadata?.['kidney_disease']
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
        allergy: !!v.allergy,
        heart_disease: !!v.heart_disease,
        kidney_disease: !!v.kidney_disease
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
