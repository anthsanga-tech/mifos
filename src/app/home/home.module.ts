/** Angular Imports */
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';

/** Custom Modules */
import { HomeRoutingModule } from './home-routing.module';

/** Custom Components */
import { HomeComponent } from './home.component';

/**
 * Home Module — same shape as web-app/src/app/home/home.module.ts (training sample).
 */
@NgModule({
  imports: [CommonModule, ReactiveFormsModule, HomeRoutingModule],
  declarations: [HomeComponent]
})
export class HomeModule {}
