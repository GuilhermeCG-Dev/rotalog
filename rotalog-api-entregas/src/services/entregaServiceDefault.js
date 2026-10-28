const { sequelize } = require('../config/database');
const entregaRepo = require('../repositories/entregaRepository');
const distancia = require('./distanciaService');
const { gerarNumeroPedido } = require('./numeroPedido');
const { criarEntregaService } = require('./entregaService');

module.exports = criarEntregaService({ entregaRepo, sequelize, distancia, gerarNumeroPedido });
