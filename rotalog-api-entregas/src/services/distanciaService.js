const RAIO_TERRA_KM = 6371;
const VELOCIDADE_MEDIA_PADRAO_KMH = 30;

const paraRadianos = (graus) => (graus * Math.PI) / 180;

const coordenada = (valor) => {
    if (valor === null || valor === undefined || valor === '') return NaN;
    return Number(valor);
};

// Haversine. Retorna null quando falta alguma coordenada; 0 é coordenada válida.
const calcularDistanciaKm = (origem, destino) => {
    const valores = [origem.lat, origem.lng, destino.lat, destino.lng].map(coordenada);
    if (valores.some(Number.isNaN)) return null;

    const [lat1, lng1, lat2, lng2] = valores;
    const dLat = paraRadianos(lat2 - lat1);
    const dLng = paraRadianos(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2
        + Math.cos(paraRadianos(lat1)) * Math.cos(paraRadianos(lat2)) * Math.sin(dLng / 2) ** 2;
    const km = 2 * RAIO_TERRA_KM * Math.asin(Math.sqrt(a));

    return Math.round(km * 100) / 100;
};

const velocidadeMediaKmh = () => Number(process.env.VELOCIDADE_MEDIA_KMH) || VELOCIDADE_MEDIA_PADRAO_KMH;

const estimarTempoMinutos = (km) => Math.ceil((km / velocidadeMediaKmh()) * 60);

module.exports = { calcularDistanciaKm, estimarTempoMinutos };
