import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { adminGuard } from './core/guards/admin.guard';
import { LandingComponent } from './features/landing/landing.component';
import { LoginComponent } from './features/auth/login/login.component';
import { SignupComponent } from './features/auth/signup/signup.component';
import { ProfileSetupComponent } from './features/profile/profile-setup.component';
import { DashboardComponent } from './features/dashboard/dashboard.component';
import { BmiCalculatorComponent } from './features/bmi/bmi-calculator.component';
import { NutritionPlanComponent } from './features/nutrition-plan/nutrition-plan.component';
import { DailyMealPlanComponent } from './features/daily-meal-plan/daily-meal-plan.component';
import { ProgressTrackingComponent } from './features/progress-tracking/progress-tracking.component';
import { FoodSearchComponent } from './features/food-search/food-search.component';
import { NotificationsComponent } from './features/notifications/notifications.component';
import { AdminDashboardComponent } from './features/admin/admin-dashboard.component';
import { AdminLayoutComponent } from './features/admin/admin-layout.component';
import { AdminOverviewComponent } from './features/admin/admin-overview.component';
import { AdminUsersComponent } from './features/admin/admin-users.component';
import { AdminAlertsComponent } from './features/admin/admin-alerts.component';

export const routes: Routes = [
  { path: '', component: LandingComponent },
  { path: 'user', redirectTo: 'dashboard', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'signup', component: SignupComponent },
  {
    path: 'admin',
    component: AdminLayoutComponent,
    canActivate: [adminGuard],
    children: [
      { path: '', redirectTo: 'overview', pathMatch: 'full' },
      { path: 'overview', component: AdminOverviewComponent },
      { path: 'users', component: AdminUsersComponent },
      { path: 'foods', component: AdminDashboardComponent },
      { path: 'alerts', component: AdminAlertsComponent }
    ]
  },
  { path: 'dashboard', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'profile-setup', component: ProfileSetupComponent, canActivate: [authGuard] },
  { path: 'bmi-calculator', component: BmiCalculatorComponent, canActivate: [authGuard] },
  { path: 'nutrition-plan', component: NutritionPlanComponent, canActivate: [authGuard] },
  { path: 'daily-meal-plan', component: DailyMealPlanComponent, canActivate: [authGuard] },
  { path: 'progress-tracking', component: ProgressTrackingComponent, canActivate: [authGuard] },
  { path: 'food-search', component: FoodSearchComponent, canActivate: [authGuard] },
  { path: 'notifications', component: NotificationsComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' }
];
