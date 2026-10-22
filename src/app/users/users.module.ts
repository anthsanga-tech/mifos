/** Angular Imports */
import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';

/** Users Modules */
import { UsersRoutingModule } from './users-routing.module';

/** Users Components */
import { UsersComponent } from './users.component';
import { CreateUserComponent } from './create-user/create-user.component';
import { EditUserComponent } from './edit-user/edit-user.component';

/**
 * Users Module — list (table) + create + edit screens.
 */
@NgModule({
  imports: [CommonModule, ReactiveFormsModule, UsersRoutingModule],
  declarations: [UsersComponent, CreateUserComponent, EditUserComponent]
})
export class UsersModule {}
