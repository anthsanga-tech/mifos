import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { SavingsRoutingModule } from './savings-routing.module';
import { SavingsComponent } from './savings-accounts/savings-accounts.component';
import { SavingsProductsComponent } from './savings-products/savings-products.component';
import { CreateSavingsProductComponent } from './savings-products/create-savings-product/create-savings-product.component';
import { EditSavingsProductComponent } from './savings-products/edit-savings-product/edit-savings-product.component';
import { CreateSavingsAccountComponent } from './savings-accounts/create-savings-account/create-savings-account.component';
import { SavingsAccountDetailComponent } from './savings-transaction/savings-account-detail.component';
import { MakerCheckerInboxComponent } from './maker-checker/maker-checker-inbox.component';

@NgModule({
  declarations: [
    SavingsComponent,
    SavingsProductsComponent,
    CreateSavingsProductComponent,
    EditSavingsProductComponent,
    CreateSavingsAccountComponent,
    SavingsAccountDetailComponent,
    MakerCheckerInboxComponent
  ],
  imports: [CommonModule, FormsModule, ReactiveFormsModule, SavingsRoutingModule]
})
export class SavingsModule {}