/** Angular Imports */
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

/**
 * Home service — same idea as web-app/src/app/home/home.service.ts (sample).
 * Calls the mini-fineract provider via the Angular proxy.
 */
@Injectable({
  providedIn: 'root'
})
export class HomeService {
  private readonly apiBase = '/mini-fineract-provider/api/v1';

  constructor(private readonly http: HttpClient) {}

  getHealth(): Observable<{ status: string; app?: string }> {
    return this.http.get<{ status: string; app?: string }>(`${this.apiBase}/health`);
  }
}
