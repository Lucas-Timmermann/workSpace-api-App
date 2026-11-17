
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


export interface FacturaDetalleRequestDTO {
  
  id?: number | null;
  articuloId: number;
  listaPrecioArticuloId: number;
  descripcion?: string | null;
  cantidad: number;
  precioUnitario: number;
  porcentajeBonificacion?: number | null;
  porcentajeIva: number;
}


export interface FacturaReporteDTO {
  id: number;
  numeroFactura: number;
  
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
  articuloId: number;
  listaPrecioId: number;
  listaPrecioArticuloId: number;
  rubro: string;
}


export interface FacturaFiltrosDTO {
  numeroFactura?: number;
  clienteDenominacion?: string;
  condicionIva?: string;
  /** yyyy-MM-dd */
  fechaDesde?: string;
  /** yyyy-MM-dd */
  fechaHasta?: string;

  page?: number;
}


export interface FacturaVentaPaginaDTO {
  contenido: FacturaReporteDTO[];
  pagina: number;
  tamanoPagina: number;
  totalElementos: number;
  totalPaginas: number;
}


export interface CatalogoDenominacionDTO {
  id: number;
  denominacion: string;
}


export interface PuntoVentaDTO {
  id: number;
  descripcion: string;
}


export interface ListaPrecioArticuloDTO {
  
  id: number;
  articuloId: number;
  denominacion: string;
  rubro: string;
  precioVenta: number;
}


export interface EnvioArcaRequestDTO {
  enviarTodas?: boolean;
  ids?: number[];
}


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


export interface EnvioArcaProcesoDTO {
  envioId: string;
  procesadas: number;
  autorizadas: number;
  rechazadas: number;
  resultadoDesconocido: number;
  noEnviadasPorValidacion: number;
  detalle: EnvioArcaItemResultadoDTO[];
}


export interface PdfDescargaDTO {
  blob: Blob;
  filename: string;
}
