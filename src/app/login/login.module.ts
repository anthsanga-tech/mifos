/** Angular Imports */
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

/** Custom Modules */
import { LoginRoutingModule } from './login-routing.module';

/** Custom Components */
import { LoginComponent } from './login.component';

/**
 * Login Module — same shape as home.module.ts / web-app login feature.
 * Frontend-only for now; wire LoginService to backend later.
 */
@NgModule({
  imports: [CommonModule, FormsModule, LoginRoutingModule],
  declarations: [LoginComponent]
})
export class LoginModule {}
