import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FrotasService } from '../../services/frotas.service';
import { AlertaManutencao } from '../../models';

@Component({
  selector: 'app-alertas-manutencao',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="alertas-page">
      <div class="page-header">
        <h1>Alertas de Manutenção</h1>
        <select class="filter-select" [value]="statusFiltro" (change)="filtrar($any($event.target).value)">
          <option value="">Todos os status</option>
          <option *ngFor="let status of statusOpcoes" [value]="status">{{ status }}</option>
        </select>
      </div>

      <div *ngIf="loading" class="loading">Carregando alertas...</div>

      <table class="data-table" *ngIf="!loading && alertas.length > 0">
        <thead>
          <tr>
            <th>ID</th>
            <th>Veículo ID</th>
            <th>Placa</th>
            <th>Motivo</th>
            <th>Quilometragem</th>
            <th>Última Manutenção</th>
            <th>Status</th>
            <th>Notificação ID</th>
            <th>Erro</th>
            <th>Criado em</th>
            <th>Atualizado em</th>
          </tr>
        </thead>
        <tbody>
          <tr *ngFor="let alerta of alertas">
            <td>{{ alerta.id }}</td>
            <td>{{ alerta.veiculoId }}</td>
            <td>{{ alerta.placa || '-' }}</td>
            <td>{{ alerta.motivo }}</td>
            <td>{{ alerta.quilometragem != null ? (alerta.quilometragem | number) + ' km' : '-' }}</td>
            <td>{{ alerta.dataUltimaManutencao ? (alerta.dataUltimaManutencao | date:'dd/MM/yyyy') : '-' }}</td>
            <td>
              <span class="status-badge" [ngClass]="'status-' + alerta.status">
                {{ alerta.status }}
              </span>
            </td>
            <td>{{ alerta.notificacaoId ?? '-' }}</td>
            <td>{{ alerta.erroMensagem || '-' }}</td>
            <td>{{ alerta.dataCriacao | date:'dd/MM/yyyy HH:mm' }}</td>
            <td>{{ alerta.dataAtualizacao | date:'dd/MM/yyyy HH:mm' }}</td>
          </tr>
        </tbody>
      </table>

      <div *ngIf="!loading && alertas.length === 0" class="empty-state">
        Nenhum alerta encontrado.
      </div>
    </div>
  `,
  styles: [`
    .alertas-page { padding: 20px; }
    .page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
    .page-header h1 { margin: 0; color: #333; font-size: 24px; }
    .filter-select { padding: 10px 14px; border: 1px solid #ddd; border-radius: 6px; background: white; font-size: 14px; }
    .loading { text-align: center; padding: 40px; color: #666; }
    .data-table { width: 100%; border-collapse: collapse; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
    .data-table th { text-align: left; padding: 14px; background: #f5f5f5; color: #666; font-size: 13px; text-transform: uppercase; }
    .data-table td { padding: 14px; border-bottom: 1px solid #f5f5f5; font-size: 14px; }
    .data-table tr:hover { background: #f9f9f9; }
    .status-badge { padding: 4px 10px; border-radius: 12px; font-size: 12px; font-weight: 500; }
    .status-PENDENTE { background: #fff3e0; color: #e65100; }
    .status-ENVIADO { background: #e8f5e9; color: #2e7d32; }
    .status-FALHA { background: #ffebee; color: #c62828; }
    .empty-state { text-align: center; padding: 40px; color: #999; background: white; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
  `]
})
export class AlertasManutencaoComponent implements OnInit {
  alertas: AlertaManutencao[] = [];
  statusOpcoes = ['PENDENTE', 'ENVIADO', 'FALHA'];
  statusFiltro = '';
  loading = true;

  constructor(private frotasService: FrotasService) {}

  ngOnInit(): void {
    this.carregarAlertas();
  }

  async filtrar(status: string): Promise<void> {
    this.statusFiltro = status;
    await this.carregarAlertas();
  }

  async carregarAlertas(): Promise<void> {
    this.loading = true;
    this.alertas = await this.frotasService.getAlertasManutencao(this.statusFiltro || undefined);
    this.loading = false;
  }
}
