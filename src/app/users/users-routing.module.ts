/** Angular Imports */
import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

/** Users Components */
import { UsersComponent } from './users.component';
import { CreateUserComponent } from './create-user/create-user.component';
import { EditUserComponent } from './edit-user/edit-user.component';

/** Custom Guards */
import { AuthGuard } from '../core/auth.guard';

const routes: Routes = [
  {
    path: 'users',
    component: UsersComponent,
    canActivate: [AuthGuard],
    data: { title: 'Users' }
  },
  {
    path: 'users/create',
    component: CreateUserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Create User' }
  },
  {
    path: 'users/:id/edit',
    component: EditUserComponent,
    canActivate: [AuthGuard],
    data: { title: 'Edit User' }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class UsersRoutingModule {}
