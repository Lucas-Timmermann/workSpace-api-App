/**
 * Request body accepted by POST /api/facturas and PUT /api/facturas/{id}.
 * java.util.Date fields are sent as ISO-8601 date-time strings by the API.
 * Fields such as estado, resultadoAfip, cae and fechaAnulacion are read-only
 * on the server and are intentionally not part of this contract.
 */
export interface FacturaRequestDTO {
  fechaEmision: string;
  clienteId: number;
  condicionIvaId: number;
  puntoVentaId: number;
  tipoMonedaId: number;
  importeCobrado: number;
  observaciones?: string | null;
  tipoComprobanteArca?: number | null;
  conceptoArca?: number | null;
  docTipoReceptorArca?: number | null;
  docNroReceptorArca?: number | null;
  condicionIvaReceptorArca?: number | null;
  monedaArca?: string | null;
  cotizacionArca?: number | null;
  detalles: FacturaDetalleRequestDTO[];
}

/** Request body item matching Java FacturaDetalleRequestDTO. */
export interface FacturaDetalleRequestDTO {
  /** Omit for a new item; provide to update an existing item. */
  id?: number | null;
  articuloId: number;
  listaPrecioArticuloId: number;
  descripcion?: string | null;
  cantidad: number;
  precioUnitario: number;
  porcentajeBonificacion?: number | null;
  porcentajeIva: number;
}

/** Response returned by POST and PUT /api/facturas and GET /api/facturas/{id}. */
export interface FacturaReporteDTO {
  id: number;
  numeroFactura: number;
  /** LocalDate serialized as yyyy-MM-dd. */
  fechaEmision: string | null;
  clienteId: number | null;
  clienteDenominacion: string;
  condicionIvaId: number | null;
  condicionIva: string;
  puntoVentaId: number | null;
  puntoVentaDescripcion: string;
  tipoMonedaId: number | null;
  monedaDenominacion: string;
  importeNeto: number;
  importeIva: number;
  importeTotal: number;
  importeCobrado: number;
  importeSaldo: number;
  estado: string;
  observaciones: string | null;
  detalles: FacturaVentaDetalleDTO[];
  cantidadItems: number;
}

/** Read-only detail item nested in FacturaReporteDTO. */
export interface FacturaVentaDetalleDTO {
  id: number;
  descripcion: string | null;
  cantidad: number;
  precioUnitario: number;
  porcentajeBonificacion: number;
  porcentajeIva: number;
  importeNeto: number;
  importeIva: number;
  importeSubtotal: number;
}

/** Optional query parameters accepted by GET /api/facturas. */
export interface FacturaFiltrosDTO {
  numeroFactura?: number;
  clienteDenominacion?: string;
  condicionIva?: string;
  /** yyyy-MM-dd */
  fechaDesde?: string;
  /** yyyy-MM-dd */
  fechaHasta?: string;
  /** Índice base cero aceptado por GET /api/facturas. */
  page?: number;
}

/** Paged response returned by GET /api/facturas and GET /api/facturas/pendientes. */
export interface FacturaVentaPaginaDTO {
  contenido: FacturaReporteDTO[];
  pagina: number;
  tamanoPagina: number;
  totalElementos: number;
  totalPaginas: number;
}

/** Catalog options returned by endpoints with an id and denominacion. */
export interface CatalogoDenominacionDTO {
  id: number;
  denominacion: string;
}

/** Point-of-sale option returned by GET /api/puntos-venta. */
export interface PuntoVentaDTO {
  id: number;
  descripcion: string;
}

/** Item offered by GET /api/listas-precio/{id}/articulos. */
export interface ListaPrecioArticuloDTO {
  /** ID of the ListaPrecioArticulo association required by invoice detail requests. */
  id: number;
  articuloId: number;
  denominacion: string;
  rubro: string;
  precioVenta: number;
}

/** Request body accepted by POST /api/facturas/envios/arca. */
export interface EnvioArcaRequestDTO {
  enviarTodas?: boolean;
  ids?: number[];
}

/** Per-invoice result returned by the ARCA submission endpoint. */
export interface EnvioArcaItemResultadoDTO {
  facturaId: number | null;
  numeroInterno: number | null;
  estado: string | null;
  /** APROBADA, RECHAZADA, INCIERTO or NO_ENVIADA. */
  resultado: string;
  mensaje: string | null;
  cae: string | null;
  caeVencimiento: string | null;
  numeroFiscal: number | null;
}

/** Summary returned by POST /api/facturas/envios/arca. */
export interface EnvioArcaProcesoDTO {
  envioId: string;
  procesadas: number;
  autorizadas: number;
  rechazadas: number;
  resultadoDesconocido: number;
  noEnviadasPorValidacion: number;
  detalle: EnvioArcaItemResultadoDTO[];
}

/** Binary PDF response and the filename advertised by the backend, if visible. */
export interface PdfDescargaDTO {
  blob: Blob;
  filename: string;
}
