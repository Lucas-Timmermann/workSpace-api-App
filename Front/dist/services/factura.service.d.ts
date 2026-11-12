import type { EnvioArcaProcesoDTO, FacturaFiltrosDTO, FacturaReporteDTO, FacturaRequestDTO, FacturaVentaPaginaDTO, CatalogoDenominacionDTO, PdfDescargaDTO, PuntoVentaDTO, ListaPrecioArticuloDTO } from "../models/factura.dto.js";
/** The backend's single invoice API base URL for local development. */
export declare const FACTURAS_API_BASE_URL = "http://localhost:8080/api/facturas";
export interface FacturaHttpTrace {
    method: string;
    route: string;
    status: number | null;
    result: "success" | "error";
    message: string;
    timestamp: string;
}
export declare class FacturaServiceError extends Error {
    readonly status?: number | undefined;
    readonly diagnostic?: string | undefined;
    constructor(message: string, status?: number | undefined, diagnostic?: string | undefined, options?: ErrorOptions);
}
/** HTTP client for the existing Spring Boot invoice endpoints. */
export declare class FacturaService {
    private readonly baseUrl;
    private traceListener?;
    constructor(baseUrl?: string);
    setTraceListener(listener: (trace: FacturaHttpTrace) => void): void;
    obtenerFacturas(filtros: FacturaFiltrosDTO): Promise<FacturaVentaPaginaDTO>;
    /** GET /api/facturas/pendientes: PENDIENTE and RECHAZADA invoices. */
    obtenerFacturasPendientes(filtros: FacturaFiltrosDTO): Promise<FacturaVentaPaginaDTO>;
    obtenerPuntosVenta(): Promise<PuntoVentaDTO[]>;
    obtenerClientes(): Promise<CatalogoDenominacionDTO[]>;
    obtenerCondicionesIva(): Promise<CatalogoDenominacionDTO[]>;
    obtenerTiposMoneda(): Promise<CatalogoDenominacionDTO[]>;
    obtenerListasPrecio(): Promise<CatalogoDenominacionDTO[]>;
    obtenerArticulosDeLista(listaPrecioId: number): Promise<ListaPrecioArticuloDTO[]>;
    crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO>;
    /** PUT /api/facturas/{id}: full invoice update (PENDIENTE or RECHAZADA only). */
    actualizarFactura(id: number, factura: FacturaRequestDTO): Promise<FacturaReporteDTO>;
    /** DELETE /api/facturas/{id}: logical annulment (the server marks ANULADA_LOCAL). */
    anularFactura(id: number): Promise<void>;
    /** POST /api/facturas/envios/arca: processes the selected invoices in ARCA. */
    enviarFacturasAArca(ids: number[]): Promise<EnvioArcaProcesoDTO>;
    /** GET /api/facturas/{id}/pdf: authorized-invoice PDF. */
    descargarPdfFactura(id: number): Promise<PdfDescargaDTO>;
    /** GET /api/facturas/pdf: complete invoice report (the backend accepts no filters). */
    descargarPdfListado(): Promise<PdfDescargaDTO>;
    private obtenerPaginaFacturas;
    private obtenerLista;
    private esPaginaFacturas;
    private apiRoot;
    private agregarFiltro;
    private formatearFecha;
    private esFechaCalendarioValida;
    private request;
    private requestPdf;
    private nombreArchivo;
    private mensajeError;
    private describeBody;
    private routeWithoutValues;
    private emitTrace;
}
//# sourceMappingURL=factura.service.d.ts.map