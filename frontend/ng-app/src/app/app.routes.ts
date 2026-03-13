import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { LandingComponent } from './features/landing/landing.component';
import { LoginComponent } from './features/auth/login/login.component';
import { SignupComponent } from './features/auth/signup/signup.component';
import { ProfileSetupComponent } from './features/profile/profile-setup.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { BmiCalculatorComponent } from './features/bmi/bmi-calculator.component';
import { NutritionPlanComponent } from './features/nutrition-plan/nutrition-plan.component';
import { PlaceholderComponent } from './features/placeholder/placeholder.component';
import { DailyMealPlanComponent } from './features/daily-meal-plan/daily-meal-plan.component';
import { ProgressTrackingComponent } from './features/progress-tracking/progress-tracking.component';
import { FoodSearchComponent } from './features/food-search/food-search.component';

export const routes: Routes = [
  { path: '', component: LandingComponent },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'profile-setup', component: ProfileSetupComponent, canActivate: [authGuard] },
  { path: 'bmi-calculator', component: BmiCalculatorComponent, canActivate: [authGuard] },
  { path: 'nutrition-plan', component: NutritionPlanComponent, canActivate: [authGuard] },
  { path: 'daily-meal-plan', component: DailyMealPlanComponent, canActivate: [authGuard] },
  { path: 'progress-tracking', component: ProgressTrackingComponent, canActivate: [authGuard] },
  { path: 'food-search', component: FoodSearchComponent, canActivate: [authGuard] },
  { path: 'notifications', component: PlaceholderComponent, canActivate: [authGuard], data: { title: 'Notifications' } },
  { path: '**', redirectTo: '' }
];
