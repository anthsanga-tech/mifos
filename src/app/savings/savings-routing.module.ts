import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/auth.guard';
import { SavingsComponent } from './savings-accounts/savings-accounts.component';
import { SavingsProductsComponent } from './savings-products/savings-products.component';
import { CreateSavingsProductComponent } from './savings-products/create-savings-product/create-savings-product.component';
import { EditSavingsProductComponent } from './savings-products/edit-savings-product/edit-savings-product.component';
import { CreateSavingsAccountComponent } from './savings-accounts/create-savings-account/create-savings-account.component';
import { SavingsAccountDetailComponent } from './savings-transaction/savings-account-detail.component';
import { MakerCheckerInboxComponent } from './maker-checker/maker-checker-inbox.component';

const routes: Routes = [
  {
    path: 'maker-checker',
    component: MakerCheckerInboxComponent,
    canActivate: [AuthGuard],
    data: { title: 'Maker-checker Inbox' }
  },
  { path: 'savings', redirectTo: 'savings-accounts', pathMatch: 'full' },
  {
    path: 'savings-products/create',
    component: CreateSavingsProductComponent,
    canActivate: [AuthGuard],
    data: { title: 'Create Savings Product' }
  },
  {
    path: 'savings-products/:id/edit',
    component: EditSavingsProductComponent,
    canActivate: [AuthGuard],
    data: { title: 'Edit Savings Product' }
  },
  {
    path: 'savings-accounts/create',
    component: CreateSavingsAccountComponent,
    canActivate: [AuthGuard],
    data: { title: 'Create Savings Account' }
  },
  {
    path: 'savings-accounts/:id',
    component: SavingsAccountDetailComponent,
    canActivate: [AuthGuard],
    data: { title: 'Savings Account Activity' }
  },
  {
    path: 'savings-products',
    pathMatch: 'full',
    component: SavingsProductsComponent,
    canActivate: [AuthGuard],
    data: { title: 'Savings Products' }
  },
  {
    path: 'savings-accounts',
    pathMatch: 'full',
    component: SavingsComponent,
    canActivate: [AuthGuard],
    data: { title: 'Savings Accounts' }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class SavingsRoutingModule {}