import { Injectable } from '@angular/core';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

export interface ProfilePayload {
  age: number;
  gender: string;
  height_cm: number;
  weight_kg: number;
  activity_level: string;
  goal: string;
  health_metadata: Record<string, boolean>;
}

@Injectable({ providedIn: 'root' })
export class ProfileService {
  constructor(private readonly api: ApiService, private readonly auth: AuthService) {}

  getProfile() {
    return this.api.get<ProfilePayload & { user_id: string }>('/api/profile', this.auth.authHeaders());
  }

  saveProfile(payload: ProfilePayload) {
    return this.api.put('/api/profile', payload, this.auth.authHeaders());
  }
}
