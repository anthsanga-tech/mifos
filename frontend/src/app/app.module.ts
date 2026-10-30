/** Angular Imports */
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { HttpClientModule, HTTP_INTERCEPTORS } from '@angular/common/http';

/** App Imports */
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { HomeModule } from './home/home.module';
import { LoginModule } from './login/login.module';
import { UsersModule } from './users/users.module';
import { BasicAuthInterceptor } from './core/basic-auth.interceptor';
import { ClientsModule } from './clients/clients.module';
import { SavingsModule } from './savings/savings.module';

/**
 * Main app module — feature modules MUST be imported before AppRoutingModule
 * so routes like {@code home} / {@code login} / {@code users} / {@code clients}
 * register before the {@code **} wildcard.
 */
@NgModule({
  declarations: [AppComponent],
  imports: [
    BrowserModule,
    HttpClientModule,
    HomeModule,
    LoginModule,
    UsersModule,
    ClientsModule,
    SavingsModule,
    AppRoutingModule
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: BasicAuthInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule {}
