import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule } from '@angular/forms';

import { ClientsRoutingModule } from './clients-routing.module';
import { ClientsComponent } from './clients.component';
import { CreateClientComponent } from './create-client/create-client.component';
import { EditClientComponent } from './edit-client/edit-client.component';

@NgModule({
  declarations: [ClientsComponent, CreateClientComponent, EditClientComponent],
  imports: [CommonModule, ReactiveFormsModule, ClientsRoutingModule]
})
export class ClientsModule {}
