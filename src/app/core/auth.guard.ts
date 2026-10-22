/** Angular Imports */
import { Injectable } from '@angular/core';
import {
  CanActivate,
  ActivatedRouteSnapshot,
  RouterStateSnapshot,
  Router,
  UrlTree
} from '@angular/router';

/** Custom Services */
import { AuthSessionService } from './auth-session.service';

/**
 * Allows the route only when a training session exists; otherwise sends the user to login.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthGuard implements CanActivate {
  constructor(
    private readonly authSession: AuthSessionService,
    private readonly router: Router
  ) {}

  canActivate(
    _route: ActivatedRouteSnapshot,
    _state: RouterStateSnapshot
  ): boolean | UrlTree {
    if (this.authSession.isLoggedIn()) {
      return true;
    }
    return this.router.parseUrl('/login');
  }
}
