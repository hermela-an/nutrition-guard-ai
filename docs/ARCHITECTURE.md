# Architecture Mapping (Your Blueprint -> Implementation)

## 1. Entry Point
- Landing/Login/Register handled by frontend pages.
- Backend support: `AuthVerticle` + `/api/auth/register`, `/api/auth/login`.

## 2. User Personalization ("Brain")
- Profile setup health buckets stored in `profiles.health_metadata` (JSONB).
- BMI/BMR/calorie target logic in `CalculationVerticle` (`/api/calculate/targets`).

## 3. Nutrition & Planning ("Filtering Engine")
- Rule-based filtering in `FilterVerticle`.
- Daily meal API: `/api/meal-plan/daily`.
- Medical constraints applied via traffic-light thresholds and filtering (RED blocked).

## 4. Search & Tracking
- Food search API with traffic-light response:
  - `GREEN` safe
  - `YELLOW` caution
  - `RED` avoid
- Progress logs/history APIs for chart data.

## 5. Vitamin Chatbot Integration
- `SocketVerticle` handles chat events through Vert.x Event Bus.
- REST chat endpoint: `/api/chat/ask`.
- Realtime websocket endpoint: `/api/chat/ws` for persistent frontend chat bubble.

## 6. Modern Backend Pattern
- Micro-verticles:
  - `AuthVerticle`
  - `CalculationVerticle`
  - `FilterVerticle`
  - `SocketVerticle`
  - `AppVerticle` (HTTP/API entry)
- MVC layering:
  - Controller -> Service -> Repository -> PostgreSQL
- JSONB profile model + GIN index ready for flexible medical constraints.

## Future Extensions
- Add `pgvector` migration for semantic vitamin recommendation.
- Add refresh tokens and role-based auth.
- Add Flyway/Liquibase migration automation.
