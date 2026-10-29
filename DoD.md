## Definition of done: Alerta de manutenção preventiva 

### Critérios de aceite:
- rotalog-api-frotas: endpoint que verifica veiculo com km maior que limite ou data maior que N meses
- rotalog-api-frotas: chamada http para o rotalog-api-notificações quando encontrar veiculo elegivel
- rotalog-api-notificações: endpoint que recebe pedido de alerta e envia e-mail para o gestor
- rotalog-api-notificações: retorna status da notificação (enviada/falha)
- rotalog-api-frotas: registra alerta no banco com status da notificação
- painel admin: tela que lista alertas com filtro de status
- teste unitario em cada serviço mais e2e do fluxo completo 
- comunicação: http sincrono entre seviço
- falhas: se rotalog-api-notificacoes estiver fora, o alerta fica com status pendente
