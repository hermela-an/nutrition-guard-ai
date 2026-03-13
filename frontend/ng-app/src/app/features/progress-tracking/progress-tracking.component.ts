import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppShellComponent } from '../../shared/components/app-shell.component';
import { ProfileService } from '../../core/services/profile.service';
import { ApiService } from '../../core/services/api.service';
import { AuthService } from '../../core/services/auth.service';
import { forkJoin } from 'rxjs';
import { finalize, switchMap, timeout, map } from 'rxjs/operators';

interface ProgressPoint {
  day: string;
  calories: number;
  water: number;
}

interface TargetResponse {
  bmi: number;
  bmr: number;
  tdee: number;
  target_calories: number;
}

interface ProgressLog {
  log_date: string;
  weight_kg: number;
  consumed?: Record<string, unknown>;
}

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, AppShellComponent],
  templateUrl: './progress-tracking.component.html',
  styleUrls: ['./progress-tracking.component.css']
})
export class ProgressTrackingComponent implements OnInit {
  private readonly profiles = inject(ProfileService);
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);

  loading = true;
  error = '';

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

  targets = {
    calories: 2000,
    water: 2.5
  };

  averages = {
    calories: 0,
    water: 0,
    weightDelta: 0,
    consistency: 0
  };

  weekly: ProgressPoint[] = [];

  ngOnInit() {
    this.loadData();
  }

  private loadData() {
    this.loading = true;
    this.error = '';

    this.profiles.getProfile().pipe(
      timeout(5_000),
      switchMap((profile) => {
        if (!profile || !(profile as any).user_id) {
          throw new Error('Profile not found. Please complete Profile Setup first.');
        }

        return forkJoin({
          targets: this.api.post<TargetResponse>('/api/calculate/targets', {
            age: Number(profile.age),
            gender: profile.gender,
            height_cm: Number(profile.height_cm),
            weight_kg: Number(profile.weight_kg),
            activity_level: profile.activity_level,
            goal: profile.goal
          }).pipe(timeout(5_000)),
          history: this.api.get<ProgressLog[]>('/api/progress/history', this.auth.authHeaders()).pipe(timeout(5_000))
        });
      }),
      finalize(() => {
        this.loading = false;
        this.cdr.detectChanges();
      })
    ).subscribe({
      next: ({ targets, history }) => {
        if (targets?.target_calories) {
          this.targets.calories = Math.round(targets.target_calories);
        }
        this.processHistory(history ?? []);
      },
      error: (err) => {
        this.error = typeof err?.message === 'string' ? err.message : 'Failed to load progress data.';
      }
    });
  }

  private processHistory(history: ProgressLog[]) {
    const today = new Date();
    const days = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
    
    // Sort array by date string descending
    const sorted = [...history].sort((a, b) => new Date(b.log_date).getTime() - new Date(a.log_date).getTime());
    
    let points: ProgressPoint[] = [];
    let totCal = 0, totWater = 0, count = 0;
    
    // Loop for last 7 days including today
    for (let i = 6; i >= 0; i--) {
      const d = new Date(today);
      d.setDate(d.getDate() - i);
      const dayStr = days[d.getDay()];
      const yyyy = d.getFullYear();
      const mm = String(d.getMonth() + 1).padStart(2, '0');
      const dd = String(d.getDate()).padStart(2, '0');
      const dateStr = `${yyyy}-${mm}-${dd}`;
      
      const log = sorted.find(h => h.log_date === dateStr);
      let calories = 0;
      let water = 0;
      
      if (log && log.consumed) {
        calories = this.pickNumber(log.consumed, ['calories', 'kcal']) ?? 0;
        water = this.pickNumber(log.consumed, ['water', 'water_l']) ?? 0;
      }
      
      points.push({ day: dayStr, calories, water });
      
      if (calories > 0 || water > 0) {
        totCal += calories;
        totWater += water;
        count++;
      }
    }
    
    this.weekly = points;
    
    if (count > 0) {
      this.averages.calories = Math.round(totCal / count);
      this.averages.water = Math.round((totWater / count) * 10) / 10;
    } else {
      this.averages.calories = 0;
      this.averages.water = 0;
    }

    // Weight delta processing for the last 7 days
    const cutoff = new Date(today);
    cutoff.setDate(today.getDate() - 7);
    const recentLogs = sorted
      .filter(entry => new Date(entry.log_date) >= cutoff)
      .reverse(); // oldest to newest
    
    if (recentLogs.length >= 2) {
      const first = recentLogs[0].weight_kg ?? 0;
      const last = recentLogs[recentLogs.length - 1].weight_kg ?? 0;
      this.averages.weightDelta = Math.round((last - first) * 10) / 10;
    } else {
      this.averages.weightDelta = 0;
    }

    // Consistency %
    this.averages.consistency = Math.round((count / 7) * 100);
  }

  private pickNumber(obj: Record<string, unknown>, keys: string[]): number | null {
    for (const key of keys) {
      const value = obj[key];
      if (typeof value === 'number' && !Number.isNaN(value)) {
        return value;
      }
      if (typeof value === 'string' && value.trim() !== '' && !Number.isNaN(Number(value))) {
        return Number(value);
      }
    }
    return null;
  }

  percent(value: number, target: number): number {
    if (target <= 0) return 0;
    return Math.min(100, Math.round((value / target) * 100));
  }
}
