import type {
  EnvioArcaProcesoDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  FacturaVentaPaginaDTO,
  CatalogoDenominacionDTO,
  PdfDescargaDTO,
  PuntoVentaDTO,
  ListaPrecioArticuloDTO,
} from "../models/factura.dto.js";


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


export class FacturaService {
  private traceListener?: (trace: FacturaHttpTrace) => void;

  constructor(private readonly baseUrl = FACTURAS_API_BASE_URL) {}

  setTraceListener(listener: (trace: FacturaHttpTrace) => void): void {
    this.traceListener = listener;
  }

  async obtenerFacturas(filtros: FacturaFiltrosDTO): Promise<FacturaVentaPaginaDTO> {
    return this.obtenerPaginaFacturas(this.baseUrl, filtros, true);
  }

  /** GET /api/facturas/pendientes: PENDIENTE and RECHAZADA invoices. */
  async obtenerFacturasPendientes(filtros: FacturaFiltrosDTO): Promise<FacturaVentaPaginaDTO> {
    return this.obtenerPaginaFacturas(`${this.baseUrl}/pendientes`, filtros, false);
  }

  async obtenerPuntosVenta(): Promise<PuntoVentaDTO[]> {
    return this.obtenerLista<PuntoVentaDTO>(`${this.apiRoot()}/puntos-venta`, "puntos de venta");
  }

  async obtenerClientes(): Promise<CatalogoDenominacionDTO[]> {
    return this.obtenerLista<CatalogoDenominacionDTO>(`${this.apiRoot()}/clientes`, "clientes");
  }

  async obtenerCondicionesIva(): Promise<CatalogoDenominacionDTO[]> {
    return this.obtenerLista<CatalogoDenominacionDTO>(`${this.apiRoot()}/condiciones-iva`, "condiciones de IVA");
  }

  async obtenerTiposMoneda(): Promise<CatalogoDenominacionDTO[]> {
    return this.obtenerLista<CatalogoDenominacionDTO>(`${this.apiRoot()}/tipos-moneda`, "tipos de moneda");
  }

  async obtenerListasPrecio(): Promise<CatalogoDenominacionDTO[]> {
    return this.obtenerLista<CatalogoDenominacionDTO>(`${this.apiRoot()}/listas-precio`, "listas de precio");
  }

  async obtenerArticulosDeLista(listaPrecioId: number): Promise<ListaPrecioArticuloDTO[]> {
    if (!Number.isSafeInteger(listaPrecioId) || listaPrecioId <= 0) {
      throw new FacturaServiceError("Selecciona una lista de precio válida.");
    }
    return this.obtenerLista<ListaPrecioArticuloDTO>(
      `${this.apiRoot()}/listas-precio/${encodeURIComponent(String(listaPrecioId))}/articulos`,
      "artículos de la lista de precio",
    );
  }

  async crearFactura(factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    return (await this.request(this.baseUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(factura),
    })) as FacturaReporteDTO;
  }


  async actualizarFactura(id: number, factura: FacturaRequestDTO): Promise<FacturaReporteDTO> {
    return (await this.request(`${this.baseUrl}/${encodeURIComponent(String(id))}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(factura),
    })) as FacturaReporteDTO;
  }

  
  async anularFactura(id: number): Promise<void> {
    await this.request(`${this.baseUrl}/${encodeURIComponent(String(id))}`, {
      method: "DELETE",
    });
  }

 
  async enviarFacturasAArca(ids: number[]): Promise<EnvioArcaProcesoDTO> {
    if (ids.length === 0) {
      throw new FacturaServiceError("No hay facturas pendientes para enviar a ARCA.");
    }
    return (await this.request(`${this.baseUrl}/envios/arca`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ids }),
    })) as EnvioArcaProcesoDTO;
  }

  
  async descargarPdfFactura(id: number): Promise<PdfDescargaDTO> {
    return this.requestPdf(
      `${this.baseUrl}/${encodeURIComponent(String(id))}/pdf`,
      `factura-${id}.pdf`,
    );
  }

  
  async descargarPdfListado(): Promise<PdfDescargaDTO> {
    return this.requestPdf(`${this.baseUrl}/pdf`, "facturas.pdf");
  }

  private async obtenerPaginaFacturas(
    urlBase: string,
    filtros: FacturaFiltrosDTO,
    incluirCondicionIva: boolean,
  ): Promise<FacturaVentaPaginaDTO> {
    const url = new URL(urlBase);
    this.agregarFiltro(url, "numeroFactura", filtros.numeroFactura);
    this.agregarFiltro(url, "clienteDenominacion", filtros.clienteDenominacion);
    if (incluirCondicionIva) {
      this.agregarFiltro(url, "condicionIva", filtros.condicionIva);
    }
    this.agregarFiltro(url, "fechaDesde", filtros.fechaDesde, true);
    this.agregarFiltro(url, "fechaHasta", filtros.fechaHasta, true);
    this.agregarFiltro(url, "page", filtros.page);

    const body: unknown = await this.request(url.toString(), { method: "GET" });
    if (!this.esPaginaFacturas(body)) {
      throw new FacturaServiceError(
        "La API respondió correctamente, pero el listado paginado de facturas no tiene el formato esperado.",
        undefined,
        `Respuesta recibida: ${this.describeBody(body)}`,
      );
    }

    return body;
  }

  private async obtenerLista<T>(url: string, descripcion: string): Promise<T[]> {
    const body: unknown = await this.request(url, { method: "GET" });
    if (!Array.isArray(body)) {
      throw new FacturaServiceError(
        `La API respondió correctamente, pero el catálogo de ${descripcion} no tiene el formato esperado.`,
        undefined,
        `Respuesta recibida: ${this.describeBody(body)}`,
      );
    }
    return body as T[];
  }

  private esPaginaFacturas(body: unknown): body is FacturaVentaPaginaDTO {
    if (typeof body !== "object" || body === null) return false;
    const pagina = body as Partial<FacturaVentaPaginaDTO>;
    return Array.isArray(pagina.contenido)
      && pagina.contenido.length <= 10
      && Number.isInteger(pagina.pagina) && pagina.pagina! >= 0
      && pagina.tamanoPagina === 10
      && Number.isInteger(pagina.totalElementos) && pagina.totalElementos! >= 0
      && Number.isInteger(pagina.totalPaginas) && pagina.totalPaginas! >= 0
      && pagina.contenido.every((factura) => typeof factura === "object"
        && factura !== null
        && typeof factura.id === "number"
        && typeof factura.numeroFactura === "number"
        && Array.isArray(factura.detalles));
  }

  private apiRoot(): string {
    return new URL(this.baseUrl).origin + "/api";
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

  private async requestPdf(url: string, fallbackFilename: string): Promise<PdfDescargaDTO> {
    const method = "GET";
    const route = this.routeWithoutValues(url);
    let response: Response;
    try {
      response = await fetch(url, { method });
    } catch (cause) {
      const diagnostic = cause instanceof Error ? cause.message : String(cause);
      this.emitTrace({ method, route, status: null, result: "error", message: diagnostic });
      throw new FacturaServiceError(
        "No fue posible conectar con el servicio de facturas para generar el PDF.",
        undefined,
        diagnostic,
        { cause },
      );
    }

    if (!response.ok) {
      const text = await response.text().catch(() => "");
      const diagnostic = text ? this.mensajeError(text) : response.statusText || "Sin cuerpo de error";
      this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
      throw new FacturaServiceError(
        `No se pudo generar el PDF (HTTP ${response.status} ${response.statusText}). ${diagnostic}`,
        response.status,
        text || response.statusText || "Respuesta de error vacía",
      );
    }

    const contentType = response.headers.get("Content-Type") ?? "";
    if (!contentType.toLowerCase().includes("application/pdf")) {
      const diagnostic = `Content-Type recibido: ${contentType || "no informado"}`;
      this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
      throw new FacturaServiceError(
        "El backend respondió correctamente, pero no devolvió un archivo PDF.",
        response.status,
        diagnostic,
      );
    }

    let blob: Blob;
    try {
      blob = await response.blob();
    } catch (cause) {
      const diagnostic = cause instanceof Error ? cause.message : String(cause);
      this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
      throw new FacturaServiceError(
        "El PDF se recibió, pero no pudo leerse como archivo.",
        response.status,
        diagnostic,
        { cause },
      );
    }
    if (blob.size === 0) {
      const diagnostic = "El PDF recibido está vacío.";
      this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
      throw new FacturaServiceError(
        "El backend devolvió un PDF vacío. No se descargó ningún archivo.",
        response.status,
        diagnostic,
      );
    }

    const filename = this.nombreArchivo(response.headers.get("Content-Disposition")) ?? fallbackFilename;
    this.emitTrace({
      method,
      route,
      status: response.status,
      result: "success",
      message: `PDF recibido (${blob.size} bytes).`,
    });
    return { blob, filename };
  }

  private nombreArchivo(disposition: string | null): string | null {
    if (!disposition) return null;
    const match = /filename\*?=(?:UTF-8''|")?([^";]+)"?/i.exec(disposition);
    const nombre = match?.[1]?.trim();
    if (!nombre) return null;
    try {
      return decodeURIComponent(nombre);
    } catch {
      return nombre;
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

  async listarFacturasAutorizadas(): Promise<FacturaReporteDTO[]> {
    const body: unknown = await this.request(`${this.baseUrl}/autorizadas`, {
        method: "GET",
    });

    if (!Array.isArray(body)) {
        throw new FacturaServiceError(
            "La API respondió correctamente, pero el listado de facturas autorizadas no tiene el formato esperado.",
            undefined,
            `Respuesta recibida: ${this.describeBody(body)}`,
        );
    }

    return body as FacturaReporteDTO[];
  }




}
