/** Angular Imports */
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

/** Custom Services */
import { AuthSessionService } from '../core/auth-session.service';
import { LoginService } from './login.service';
import { environment } from '../../environments/environment';

/**
 * Login page — Basic API or Keycloak OAuth password grant.
 */
@Component({
  selector: 'mifosx-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent {
  username = 'mifos';
  password = '';
  error = '';
  busy = false;
  oauthEnabled = !!(environment.oauth && environment.oauth.enabled);

  constructor(
    private readonly loginService: LoginService,
    private readonly authSession: AuthSessionService,
    private readonly router: Router
  ) {
    if (this.authSession.isLoggedIn()) {
      this.router.navigateByUrl('/home');
    }
  }

  onSubmit(): void {
    this.error = '';
    this.busy = true;

    this.loginService.login(this.username, this.password).subscribe(
      (result) => {
        this.busy = false;
        if (result.mode === 'oauth' && result.token) {
          this.authSession.saveOAuthLogin(result.user, result.token);
        } else {
          this.authSession.saveBasicLogin(result.user, this.username, this.password);
        }
        this.router.navigateByUrl('/home');
      },
      (err: HttpErrorResponse) => {
        this.busy = false;
        this.error =
          (err && err.error && (err.error.error_description || err.error.message || err.error.error)) ||
          err.message ||
          'Login failed';
      }
    );
  }
}
