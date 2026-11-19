import type { EnvioArcaProcesoDTO, FacturaFiltrosDTO, FacturaReporteDTO, FacturaRequestDTO, FacturaVentaPaginaDTO, CatalogoDenominacionDTO, PdfDescargaDTO, PuntoVentaDTO, ListaPrecioArticuloDTO } from "../models/factura.dto.js";
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
    obtenerRubros(): Promise<CatalogoDenominacionDTO[]>;
    obtenerArticulosDeLista(listaPrecioId: number): Promise<ListaPrecioArticuloDTO[]>;
    crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO>;
    actualizarFactura(id: number, factura: FacturaRequestDTO): Promise<FacturaReporteDTO>;
    anularFactura(id: number): Promise<void>;
    enviarFacturasAArca(ids: number[]): Promise<EnvioArcaProcesoDTO>;
    descargarPdfFactura(id: number): Promise<PdfDescargaDTO>;
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
    listarFacturasAutorizadas(): Promise<FacturaReporteDTO[]>;
}
//# sourceMappingURL=factura.service.d.ts.map