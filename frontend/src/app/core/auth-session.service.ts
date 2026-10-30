/** Angular Imports */
import { Injectable } from '@angular/core';

import { environment } from '../../environments/environment';

const USER_KEY = 'trainingUser';
const BASIC_KEY = 'trainingBasicAuth';
const ACCESS_TOKEN_KEY = 'trainingAccessToken';
const REFRESH_TOKEN_KEY = 'trainingRefreshToken';

export interface OAuth2Token {
  access_token: string;
  refresh_token?: string;
  expires_in?: number;
  token_type?: string;
}

interface SessionUser {
  username?: string;
  displayName?: string;
}

/**
 * Holds post-login session — Basic or OAuth JWT (Fineract web-app dual mode).
 */
@Injectable({
  providedIn: 'root'
})
export class AuthSessionService {
  isOAuthEnabled(): boolean {
    return !!(environment.oauth && environment.oauth.enabled);
  }

  saveBasicLogin(user: unknown, username: string, password: string): void {
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    sessionStorage.setItem(BASIC_KEY, btoa(`${username}:${password}`));
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  saveOAuthLogin(user: unknown, token: OAuth2Token): void {
    sessionStorage.setItem(USER_KEY, JSON.stringify(user));
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token.access_token);
    if (token.refresh_token) {
      sessionStorage.setItem(REFRESH_TOKEN_KEY, token.refresh_token);
    } else {
      sessionStorage.removeItem(REFRESH_TOKEN_KEY);
    }
    sessionStorage.removeItem(BASIC_KEY);
  }

  clear(): void {
    sessionStorage.removeItem(USER_KEY);
    sessionStorage.removeItem(BASIC_KEY);
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
    sessionStorage.removeItem(REFRESH_TOKEN_KEY);
  }

  getUserRaw(): string | null {
    return sessionStorage.getItem(USER_KEY);
  }

  getUsername(): string {
    const raw = this.getUserRaw();
    if (!raw) {
      return 'User';
    }
    try {
      const user = JSON.parse(raw) as SessionUser;
      return user.username || user.displayName || 'User';
    } catch {
      return 'User';
    }
  }

  getBasicToken(): string | null {
    return sessionStorage.getItem(BASIC_KEY);
  }

  getAccessToken(): string | null {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  }

  getRefreshToken(): string | null {
    return sessionStorage.getItem(REFRESH_TOKEN_KEY);
  }

  isLoggedIn(): boolean {
    if (this.isOAuthEnabled()) {
      return !!this.getAccessToken();
    }
    return !!this.getBasicToken();
  }
}
