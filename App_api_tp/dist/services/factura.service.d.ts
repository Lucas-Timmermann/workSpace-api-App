import type { FacturaFiltrosDTO, FacturaReporteDTO, FacturaRequestDTO, FacturaVentaDTO } from "../models/factura.dto.js";
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
    obtenerFacturas(filtros: FacturaFiltrosDTO): Promise<FacturaVentaDTO[]>;
    crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO>;
    /**
     * The current API has no status-only route. Its PUT requires a complete
     * FacturaRequestDTO, while GET does not provide the database ID or the
     * relation IDs needed to safely construct that request.
     */
    actualizarEstado(_id: number, _estado: string, _observaciones?: string): Promise<FacturaReporteDTO>;
    eliminarFactura(id: number): Promise<void>;
    private agregarFiltro;
    private formatearFecha;
    private esFechaCalendarioValida;
    private request;
    private mensajeError;
    private describeBody;
    private routeWithoutValues;
    private emitTrace;
}
//# sourceMappingURL=factura.service.d.ts.map