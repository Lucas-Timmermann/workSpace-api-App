/**
 * Request body accepted by POST /api/facturas and PUT /api/facturas/{id}.
 * java.util.Date fields are sent as ISO-8601 date-time strings by the API.
 */
export interface FacturaRequestDTO {
    numero: number;
    fechaEmision: string;
    clienteId: number;
    condicionIvaId: number;
    puntoVentaId: number;
    tipoMonedaId: number;
    importeCobrado: number;
    cae?: string | null;
    caeFechaVencimiento?: string | null;
    resultadoAfip: string;
    motivoRechazo?: string | null;
    estado: string;
    fechaAnulacion?: string | null;
    observaciones?: string | null;
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
/** Response returned by POST and PUT /api/facturas. */
export interface FacturaReporteDTO {
    numeroFactura: number;
    /** LocalDate serialized as yyyy-MM-dd. */
    fechaEmision: string | null;
    clienteDenominacion: string;
    condicionIva: string;
    puntoVentaDescripcion: string;
    importeTotal: number;
    cantidadItems: number;
}
/** Response item returned by GET /api/facturas. */
export interface FacturaVentaDTO {
    numeroFactura: number;
    /** LocalDate serialized as yyyy-MM-dd. */
    fechaEmision: string | null;
    clienteDenominacion: string;
    condicionIva: string;
    monedaDenominacion: string;
    puntoVentaDescripcion: string;
    importeNeto: number;
    importeIva: number;
    importeTotal: number;
    importeCobrado: number;
    importeSaldo: number;
    estado: string;
    detalles: FacturaVentaDetalleDTO[];
}
/** Read-only detail item nested in FacturaVentaDTO. */
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
}
//# sourceMappingURL=factura.dto.d.ts.map