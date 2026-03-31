# Frontend Structure Documentation

## Main Entry Point

Angular application entry: `frontend/ng-app/src/index.html`

## Clean Structure

```text
frontend/
  ng-app/                    # Main Angular application
    src/
      index.html             # Main entry point
      main.ts                # Angular bootstrap
      app/
        core/                # Config/guards/services
        shared/              # Shared components/services
        features/            # Feature pages
```

## How to Use

### Development

```bash
cd frontend/ng-app
npm install
ng serve
```

App runs at `http://localhost:4200`.

### Production Build

```bash
cd frontend/ng-app
ng build
```

Built files go to `frontend/ng-app/dist/`.
