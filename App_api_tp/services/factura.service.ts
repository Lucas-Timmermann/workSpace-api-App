import type {
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  FacturaVentaDTO,
} from "../models/factura.dto.js";

/** The backend's single invoice API base URL for local development. */
export const FACTURAS_API_BASE_URL = "http://localhost:8080/api/facturas";

export interface FacturaHttpTrace {
  method: string;
  route: string;
  status: number | null;
  result: "success" | "error";
  message: string;
  timestamp: string;
}

export class FacturaServiceError extends Error {
  constructor(
    message: string,
    readonly status?: number,
    readonly diagnostic?: string,
    options?: ErrorOptions,
  ) {
    super(message, options);
    this.name = "FacturaServiceError";
  }
}

/** HTTP client for the existing Spring Boot invoice endpoints. */
export class FacturaService {
  private traceListener?: (trace: FacturaHttpTrace) => void;

  constructor(private readonly baseUrl = FACTURAS_API_BASE_URL) {}

  setTraceListener(listener: (trace: FacturaHttpTrace) => void): void {
    this.traceListener = listener;
  }

  async obtenerFacturas(filtros: FacturaFiltrosDTO): Promise<FacturaVentaDTO[]> {
    const url = new URL(this.baseUrl);
    this.agregarFiltro(url, "numeroFactura", filtros.numeroFactura);
    this.agregarFiltro(url, "clienteDenominacion", filtros.clienteDenominacion);
    this.agregarFiltro(url, "condicionIva", filtros.condicionIva);
    this.agregarFiltro(url, "fechaDesde", filtros.fechaDesde, true);
    this.agregarFiltro(url, "fechaHasta", filtros.fechaHasta, true);

    const body: unknown = await this.request(url.toString(), { method: "GET" });
    if (!Array.isArray(body)) {
      throw new FacturaServiceError(
        "La API respondió correctamente, pero el listado de facturas no tiene el formato esperado.",
        undefined,
        `Respuesta recibida: ${this.describeBody(body)}`,
      );
    }

    return body as FacturaVentaDTO[];
  }

  async crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    return (await this.request(this.baseUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(factura),
    })) as FacturaReporteDTO;
  }

  /**
   * The current API has no status-only route. Its PUT requires a complete
   * FacturaRequestDTO, while GET does not provide the database ID or the
   * relation IDs needed to safely construct that request.
   */
  async actualizarEstado(
    _id: number,
    _estado: string,
    _observaciones?: string,
  ): Promise<FacturaReporteDTO> {
    throw new FacturaServiceError(
      "No se puede actualizar solo el estado: el backend no expone un endpoint específico para esa operación.",
      undefined,
      "El backend solo ofrece PUT /api/facturas/{id} con FacturaRequestDTO completo. El GET no incluye el ID de la factura ni los IDs de cliente, condición de IVA, punto de venta y moneda necesarios para construirlo sin perder datos. Hace falta un endpoint PATCH/PUT específico para estado o ampliar el DTO de lectura con esos datos.",
    );
  }

  async eliminarFactura(id: number): Promise<void> {
    await this.request(`${this.baseUrl}/${encodeURIComponent(String(id))}`, {
      method: "DELETE",
    });
  }

  private agregarFiltro(
    url: URL,
    nombre: string,
    valor: number | string | undefined,
    esFecha = false,
  ): void {
    if (valor === undefined || (typeof valor === "string" && valor.trim() === "")) {
      return;
    }

    const valorFormateado = esFecha && typeof valor === "string"
      ? this.formatearFecha(valor)
      : String(valor);
    url.searchParams.set(nombre, valorFormateado);
  }

  private formatearFecha(valor: string): string {
    const fecha = valor.trim();
    const fechaIso = /^(\d{4}-\d{2}-\d{2})(?:$|T)/.exec(fecha)?.[1];
    if (fechaIso && this.esFechaCalendarioValida(fechaIso)) {
      return fechaIso;
    }

    throw new FacturaServiceError(
      `El filtro de fecha "${valor}" no es válido. Se espera yyyy-MM-dd o una fecha ISO con hora.`,
      undefined,
      `Valor recibido: ${valor}`,
    );
  }

  private esFechaCalendarioValida(fecha: string): boolean {
    const [anio, mes, dia] = fecha.split("-").map(Number);
    const valor = new Date(Date.UTC(anio!, mes! - 1, dia!));
    return valor.getUTCFullYear() === anio
      && valor.getUTCMonth() === mes! - 1
      && valor.getUTCDate() === dia;
  }

  private async request(url: string, init: RequestInit): Promise<unknown> {
    const method = init.method ?? "GET";
    const route = this.routeWithoutValues(url);
    let response: Response;
    try {
      response = await fetch(url, init);
    } catch (cause) {
      const diagnostic = cause instanceof Error ? cause.message : String(cause);
      this.emitTrace({ method, route, status: null, result: "error", message: diagnostic });
      throw new FacturaServiceError(
        "No fue posible conectar con el servicio de facturas. Comprueba que el backend esté disponible y revisa la configuración CORS si el navegador bloqueó la solicitud.",
        undefined,
        diagnostic,
        { cause },
      );
    }

    let text: string;
    try {
      text = await response.text();
    } catch (cause) {
      const diagnostic = cause instanceof Error ? cause.message : String(cause);
      this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
      throw new FacturaServiceError(
        "Se recibió una respuesta del servicio de facturas, pero no se pudo leer su contenido.",
        response.status,
        diagnostic,
        { cause },
      );
    }
    if (!response.ok) {
      const diagnostic = text ? this.mensajeError(text) : response.statusText || "Sin cuerpo de error";
      this.emitTrace({
        method,
        route,
        status: response.status,
        result: "error",
        message: diagnostic,
      });
      throw new FacturaServiceError(
        `La solicitud de facturas falló (HTTP ${response.status} ${response.statusText}). ${diagnostic}`,
        response.status,
        text || response.statusText || "Respuesta de error vacía",
      );
    }

    if (response.status === 204 || text.trim() === "") {
      this.emitTrace({
        method,
        route,
        status: response.status,
        result: "success",
        message: "Solicitud completada sin cuerpo.",
      });
      return undefined;
    }

    try {
      const parsed: unknown = JSON.parse(text);
      this.emitTrace({
        method,
        route,
        status: response.status,
        result: "success",
        message: "Respuesta JSON recibida.",
      });
      return parsed;
    } catch (cause) {
      this.emitTrace({
        method,
        route,
        status: response.status,
        result: "error",
        message: "Respuesta exitosa con JSON inválido.",
      });
      throw new FacturaServiceError(
        "El backend devolvió una respuesta exitosa que no contiene JSON válido.",
        response.status,
        text,
        { cause },
      );
    }
  }

  private mensajeError(text: string): string {
    try {
      const parsed: unknown = JSON.parse(text);
      if (typeof parsed === "object" && parsed !== null) {
        const error = parsed as Record<string, unknown>;
        for (const campo of ["detail", "message", "error", "title"]) {
          const valor = error[campo];
          if (typeof valor === "string" && valor.trim()) {
            return valor;
          }
        }
      }
    } catch {
      // Non-JSON error bodies (for example, plain text) remain useful diagnostics.
    }

    return text;
  }

  private describeBody(body: unknown): string {
    try {
      return JSON.stringify(body) ?? String(body);
    } catch {
      return String(body);
    }
  }

  private routeWithoutValues(url: string): string {
    const parsed = new URL(url);
    const queryNames = [...parsed.searchParams.keys()];
    return `${parsed.pathname}${queryNames.length ? `?${queryNames.join("&")}` : ""}`;
  }

  private emitTrace(trace: Omit<FacturaHttpTrace, "timestamp">): void {
    this.traceListener?.({ ...trace, timestamp: new Date().toISOString() });
  }
}
