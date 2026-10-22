/** Angular Imports */
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { environment } from '../../environments/environment';
import { OAuth2Token } from '../core/auth-session.service';

/** Response from POST /v1/authentication (Basic mode) */
export interface AuthenticationData {
  userId?: number;
  username: string;
  displayName: string;
  email?: string | null;
  authenticated: boolean;
}

export interface LoginResult {
  user: AuthenticationData;
  token?: OAuth2Token;
  mode: 'oauth' | 'basic';
}

/**
 * Login service — Basic API login or Keycloak password grant (web-app style).
 */
@Injectable({
  providedIn: 'root'
})
export class LoginService {
  private readonly apiBase = '/mini-fineract-provider/api/v1';

  constructor(private readonly http: HttpClient) {}

  login(username: string, password: string): Observable<LoginResult> {
    if (environment.oauth && environment.oauth.enabled) {
      return this.loginWithOAuth(username, password);
    }
    return this.loginWithBasic(username, password);
  }

  private loginWithBasic(username: string, password: string): Observable<LoginResult> {
    return this.http
      .post<AuthenticationData>(`${this.apiBase}/authentication`, { username, password })
      .pipe(
        map((user) => ({
          user,
          mode: 'basic' as const
        }))
      );
  }

  private loginWithOAuth(username: string, password: string): Observable<LoginResult> {
    let httpParams = new HttpParams();
    httpParams = httpParams.set('username', username);
    httpParams = httpParams.set('password', password);
    httpParams = httpParams.set('client_id', environment.oauth.appId);
    httpParams = httpParams.set('grant_type', 'password');

    const headers = new HttpHeaders({
      'Content-Type': 'application/x-www-form-urlencoded'
    });

    const tokenUrl = `${environment.oauth.serverUrl}/token`;
    return this.http.post<OAuth2Token>(tokenUrl, httpParams.toString(), { headers }).pipe(
      map((token) => ({
        mode: 'oauth' as const,
        token,
        user: {
          username,
          displayName: username,
          email: null,
          authenticated: true
        }
      }))
    );
  }
}
