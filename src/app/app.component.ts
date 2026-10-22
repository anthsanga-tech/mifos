/** Angular Imports */
import { Component } from '@angular/core';

/**
 * Root shell — only hosts {@code router-outlet}.
 * No global header: each feature page owns its own layout (login is full-screen).
 */
@Component({
  selector: 'mifosx-web-app',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent {}
