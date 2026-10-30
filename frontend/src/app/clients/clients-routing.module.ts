import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { ClientsComponent } from './clients.component';
import { CreateClientComponent } from './create-client/create-client.component';
import { EditClientComponent } from './edit-client/edit-client.component';
import { AuthGuard } from '../core/auth.guard';

const routes: Routes = [
  {
    path: 'clients',
    component: ClientsComponent,
    canActivate: [AuthGuard],
    data: { title: 'Clients' }
  },
  {
    path: 'clients/create',
    component: CreateClientComponent,
    canActivate: [AuthGuard],
    data: { title: 'Create Client' }
  },
  {
    path: 'clients/:id/edit',
    component: EditClientComponent,
    canActivate: [AuthGuard],
    data: { title: 'Edit Client' }
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class ClientsRoutingModule {}
