/** Angular Imports */
import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

/** Custom Components */
import { HomeComponent } from './home.component';

/** Custom Guards */
import { AuthGuard } from '../core/auth.guard';

/** Home routes — mirrors web-app/src/app/home/home-routing.module.ts */
const routes: Routes = [
  {
    path: 'home',
    component: HomeComponent,
    canActivate: [AuthGuard],
    data: { title: 'Home' }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class HomeRoutingModule {}
