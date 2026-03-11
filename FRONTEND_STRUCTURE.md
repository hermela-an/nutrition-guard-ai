# Frontend Structure Documentation

## 🎯 **Main Entry Point**

**Angular Application**: `frontend/ng-app/src/index.html`
- This is the **only** frontend entry point
- Professional single-page application architecture
- Proper Angular routing and component structure

## 🏗️ **Clean Structure**

```
frontend/
├── ng-app/                    # Main Angular application
│   ├── src/
│   │   ├── index.html         # ✅ MAIN ENTRY POINT
│   │   ├── main.ts            # Angular bootstrap
│   │   ├── app/
│   │   │   ├── app.ts         # Main component
│   │   │   ├── app.routes.ts  # Route configuration
│   │   │   └── features/      # Feature modules
│   │   │       ├── nutrition-plan/
│   │   │       ├── profile/
│   │   │       ├── auth/
│   │   │       └── dashboard/
│   │   └── shared/            # Shared components
│   └── angular.json           # Angular configuration
└── src/                       # Placeholder files only
    └── pages/
        └── placeholders/      # Demo placeholder pages
```

## 🚀 **How to Use**

### **Development**
```bash
cd frontend/ng-app
npm install
ng serve
```
- Application runs at: `http://localhost:4200`
- Navigate to routes: `/nutrition-plan`, `/profile-setup`, etc.

### **Production Build**
```bash
cd frontend/ng-app
ng build --prod
```
- Built files in: `dist/ng-app/`

## 📁 **Removed Files (Cleaned Up)**

The following standalone HTML files were removed to maintain a professional structure:

- ❌ `frontend/src/pages/nutrition-plan.html` - Replaced by Angular component
- ❌ `frontend/src/pages/nutrition-plan-standalone.html` - No longer needed
- ❌ `frontend/src/pages/test-nutrition-plan.html` - Test functionality integrated
- ❌ `frontend/src/index.html` - Replaced by Angular entry point
- ❌ `frontend/src/pages/landing.html` - Replaced by Angular landing component

## ✅ **Benefits of Clean Structure**

1. **Single Entry Point**: Only one main entry point (`ng-app/src/index.html`)
2. **Professional Architecture**: Proper Angular SPA structure
3. **Maintainable Code**: Organized feature modules and shared components
4. **Consistent Routing**: Angular router for navigation
5. **Development Tools**: Full Angular CLI support
6. **Production Ready**: Optimized builds and deployment

## 🎯 **Navigation Routes**

- `/` - Dashboard/Landing
- `/nutrition-plan` - Nutrition Plan
- `/profile-setup` - Profile Setup
- `/auth/login` - Login
- `/auth/signup` - Signup
- `/bmi-calculator` - BMI Calculator

## 🔧 **Component Structure**

### **Main Components**
- `app-shell.component.ts` - Layout wrapper with navigation
- `landing.component.ts` - Dashboard page
- `nutrition-plan.component.ts` - Nutrition plan page
- `profile-setup.component.ts` - Profile setup page

### **Shared Components**
- Navigation sidebar
- Authentication guards
- API services
- Utility functions

This structure provides a **professional, maintainable** frontend with a single entry point and proper Angular architecture.