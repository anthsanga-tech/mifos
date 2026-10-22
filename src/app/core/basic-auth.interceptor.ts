/** Angular Imports */
import { Injectable } from '@angular/core';
import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandler,
  HttpInterceptor,
  HttpRequest
} from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/** Custom Services */
import { AuthSessionService } from './auth-session.service';

/**
 * Attaches Authorization — Bearer JWT (OAuth) or Basic (legacy), like web-app.
 * On 401, clear session and navigate to login.
 */
@Injectable()
export class BasicAuthInterceptor implements HttpInterceptor {
  constructor(
    private readonly authSession: AuthSessionService,
    private readonly router: Router
  ) {}

  intercept(req: HttpRequest<unknown>, next: HttpHandler): Observable<HttpEvent<unknown>> {
    const isAuthApi = req.url.includes('/v1/authentication');
    const isKeycloakToken =
      req.url.includes('/protocol/openid-connect/token') ||
      req.url.includes('/protocol/openid-connect/logout');

    let outgoing = req;
    if (!isAuthApi && !isKeycloakToken) {
      if (this.authSession.isOAuthEnabled()) {
        const access = this.authSession.getAccessToken();
        if (access) {
          outgoing = req.clone({
            setHeaders: { Authorization: `Bearer ${access}` }
          });
        }
      } else {
        const basic = this.authSession.getBasicToken();
        if (basic) {
          outgoing = req.clone({
            setHeaders: { Authorization: `Basic ${basic}` }
          });
        }
      }
    }

    return next.handle(outgoing).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401 && !isAuthApi && !isKeycloakToken) {
          this.authSession.clear();
          this.router.navigateByUrl('/login');
        }
        return throwError(err);
      })
    );
  }
}
