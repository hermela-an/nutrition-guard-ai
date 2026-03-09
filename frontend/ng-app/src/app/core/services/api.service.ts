import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { API_BASE_URL } from '../config/app.constants';

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private readonly http: HttpClient) {}

  get<T>(path: string, headers?: HttpHeaders) {
    return this.http.get<T>(`${API_BASE_URL}${path}`, { headers });
  }

  post<T>(path: string, body: unknown, headers?: HttpHeaders) {
    return this.http.post<T>(`${API_BASE_URL}${path}`, body, { headers });
  }

  put<T>(path: string, body: unknown, headers?: HttpHeaders) {
    return this.http.put<T>(`${API_BASE_URL}${path}`, body, { headers });
  }
}
