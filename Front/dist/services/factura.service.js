/** The backend's single invoice API base URL for local development. */
export const FACTURAS_API_BASE_URL = "http://localhost:8080/api/facturas";
export class FacturaServiceError extends Error {
    status;
    diagnostic;
    constructor(message, status, diagnostic, options) {
        super(message, options);
        this.status = status;
        this.diagnostic = diagnostic;
        this.name = "FacturaServiceError";
    }
}
/** HTTP client for the existing Spring Boot invoice endpoints. */
export class FacturaService {
    baseUrl;
    traceListener;
    constructor(baseUrl = FACTURAS_API_BASE_URL) {
        this.baseUrl = baseUrl;
    }
    setTraceListener(listener) {
        this.traceListener = listener;
    }
    async obtenerFacturas(filtros) {
        return this.obtenerPaginaFacturas(this.baseUrl, filtros, true);
    }
    /** GET /api/facturas/pendientes: PENDIENTE and RECHAZADA invoices. */
    async obtenerFacturasPendientes(filtros) {
        return this.obtenerPaginaFacturas(`${this.baseUrl}/pendientes`, filtros, false);
    }
    async obtenerPuntosVenta() {
        return this.obtenerLista(`${this.apiRoot()}/puntos-venta`, "puntos de venta");
    }
    async obtenerClientes() {
        return this.obtenerLista(`${this.apiRoot()}/clientes`, "clientes");
    }
    async obtenerCondicionesIva() {
        return this.obtenerLista(`${this.apiRoot()}/condiciones-iva`, "condiciones de IVA");
    }
    async obtenerTiposMoneda() {
        return this.obtenerLista(`${this.apiRoot()}/tipos-moneda`, "tipos de moneda");
    }
    async obtenerListasPrecio() {
        return this.obtenerLista(`${this.apiRoot()}/listas-precio`, "listas de precio");
    }
    async obtenerArticulosDeLista(listaPrecioId) {
        if (!Number.isSafeInteger(listaPrecioId) || listaPrecioId <= 0) {
            throw new FacturaServiceError("Selecciona una lista de precio válida.");
        }
        return this.obtenerLista(`${this.apiRoot()}/listas-precio/${encodeURIComponent(String(listaPrecioId))}/articulos`, "artículos de la lista de precio");
    }
    async crearFactura(factura) {
        return (await this.request(this.baseUrl, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(factura),
        }));
    }
    /** PUT /api/facturas/{id}: full invoice update (PENDIENTE or RECHAZADA only). */
    async actualizarFactura(id, factura) {
        return (await this.request(`${this.baseUrl}/${encodeURIComponent(String(id))}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(factura),
        }));
    }
    /** DELETE /api/facturas/{id}: logical annulment (the server marks ANULADA_LOCAL). */
    async anularFactura(id) {
        await this.request(`${this.baseUrl}/${encodeURIComponent(String(id))}`, {
            method: "DELETE",
        });
    }
    /** POST /api/facturas/envios/arca: processes the selected invoices in ARCA. */
    async enviarFacturasAArca(ids) {
        if (ids.length === 0) {
            throw new FacturaServiceError("No hay facturas pendientes para enviar a ARCA.");
        }
        return (await this.request(`${this.baseUrl}/envios/arca`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ids }),
        }));
    }
    /** GET /api/facturas/{id}/pdf: authorized-invoice PDF. */
    async descargarPdfFactura(id) {
        return this.requestPdf(`${this.baseUrl}/${encodeURIComponent(String(id))}/pdf`, `factura-${id}.pdf`);
    }
    /** GET /api/facturas/pdf: complete invoice report (the backend accepts no filters). */
    async descargarPdfListado() {
        return this.requestPdf(`${this.baseUrl}/pdf`, "facturas.pdf");
    }
    async obtenerPaginaFacturas(urlBase, filtros, incluirCondicionIva) {
        const url = new URL(urlBase);
        this.agregarFiltro(url, "numeroFactura", filtros.numeroFactura);
        this.agregarFiltro(url, "clienteDenominacion", filtros.clienteDenominacion);
        if (incluirCondicionIva) {
            this.agregarFiltro(url, "condicionIva", filtros.condicionIva);
        }
        this.agregarFiltro(url, "fechaDesde", filtros.fechaDesde, true);
        this.agregarFiltro(url, "fechaHasta", filtros.fechaHasta, true);
        this.agregarFiltro(url, "page", filtros.page);
        const body = await this.request(url.toString(), { method: "GET" });
        if (!this.esPaginaFacturas(body)) {
            throw new FacturaServiceError("La API respondió correctamente, pero el listado paginado de facturas no tiene el formato esperado.", undefined, `Respuesta recibida: ${this.describeBody(body)}`);
        }
        return body;
    }
    async obtenerLista(url, descripcion) {
        const body = await this.request(url, { method: "GET" });
        if (!Array.isArray(body)) {
            throw new FacturaServiceError(`La API respondió correctamente, pero el catálogo de ${descripcion} no tiene el formato esperado.`, undefined, `Respuesta recibida: ${this.describeBody(body)}`);
        }
        return body;
    }
    esPaginaFacturas(body) {
        if (typeof body !== "object" || body === null)
            return false;
        const pagina = body;
        return Array.isArray(pagina.contenido)
            && pagina.contenido.length <= 10
            && Number.isInteger(pagina.pagina) && pagina.pagina >= 0
            && pagina.tamanoPagina === 10
            && Number.isInteger(pagina.totalElementos) && pagina.totalElementos >= 0
            && Number.isInteger(pagina.totalPaginas) && pagina.totalPaginas >= 0
            && pagina.contenido.every((factura) => typeof factura === "object"
                && factura !== null
                && typeof factura.id === "number"
                && typeof factura.numeroFactura === "number"
                && Array.isArray(factura.detalles));
    }
    apiRoot() {
        return new URL(this.baseUrl).origin + "/api";
    }
    agregarFiltro(url, nombre, valor, esFecha = false) {
        if (valor === undefined || (typeof valor === "string" && valor.trim() === "")) {
            return;
        }
        const valorFormateado = esFecha && typeof valor === "string"
            ? this.formatearFecha(valor)
            : String(valor);
        url.searchParams.set(nombre, valorFormateado);
    }
    formatearFecha(valor) {
        const fecha = valor.trim();
        const fechaIso = /^(\d{4}-\d{2}-\d{2})(?:$|T)/.exec(fecha)?.[1];
        if (fechaIso && this.esFechaCalendarioValida(fechaIso)) {
            return fechaIso;
        }
        throw new FacturaServiceError(`El filtro de fecha "${valor}" no es válido. Se espera yyyy-MM-dd o una fecha ISO con hora.`, undefined, `Valor recibido: ${valor}`);
    }
    esFechaCalendarioValida(fecha) {
        const [anio, mes, dia] = fecha.split("-").map(Number);
        const valor = new Date(Date.UTC(anio, mes - 1, dia));
        return valor.getUTCFullYear() === anio
            && valor.getUTCMonth() === mes - 1
            && valor.getUTCDate() === dia;
    }
    async request(url, init) {
        const method = init.method ?? "GET";
        const route = this.routeWithoutValues(url);
        let response;
        try {
            response = await fetch(url, init);
        }
        catch (cause) {
            const diagnostic = cause instanceof Error ? cause.message : String(cause);
            this.emitTrace({ method, route, status: null, result: "error", message: diagnostic });
            throw new FacturaServiceError("No fue posible conectar con el servicio de facturas. Comprueba que el backend esté disponible y revisa la configuración CORS si el navegador bloqueó la solicitud.", undefined, diagnostic, { cause });
        }
        let text;
        try {
            text = await response.text();
        }
        catch (cause) {
            const diagnostic = cause instanceof Error ? cause.message : String(cause);
            this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
            throw new FacturaServiceError("Se recibió una respuesta del servicio de facturas, pero no se pudo leer su contenido.", response.status, diagnostic, { cause });
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
            throw new FacturaServiceError(`La solicitud de facturas falló (HTTP ${response.status} ${response.statusText}). ${diagnostic}`, response.status, text || response.statusText || "Respuesta de error vacía");
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
            const parsed = JSON.parse(text);
            this.emitTrace({
                method,
                route,
                status: response.status,
                result: "success",
                message: "Respuesta JSON recibida.",
            });
            return parsed;
        }
        catch (cause) {
            this.emitTrace({
                method,
                route,
                status: response.status,
                result: "error",
                message: "Respuesta exitosa con JSON inválido.",
            });
            throw new FacturaServiceError("El backend devolvió una respuesta exitosa que no contiene JSON válido.", response.status, text, { cause });
        }
    }
    async requestPdf(url, fallbackFilename) {
        const method = "GET";
        const route = this.routeWithoutValues(url);
        let response;
        try {
            response = await fetch(url, { method });
        }
        catch (cause) {
            const diagnostic = cause instanceof Error ? cause.message : String(cause);
            this.emitTrace({ method, route, status: null, result: "error", message: diagnostic });
            throw new FacturaServiceError("No fue posible conectar con el servicio de facturas para generar el PDF.", undefined, diagnostic, { cause });
        }
        if (!response.ok) {
            const text = await response.text().catch(() => "");
            const diagnostic = text ? this.mensajeError(text) : response.statusText || "Sin cuerpo de error";
            this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
            throw new FacturaServiceError(`No se pudo generar el PDF (HTTP ${response.status} ${response.statusText}). ${diagnostic}`, response.status, text || response.statusText || "Respuesta de error vacía");
        }
        const contentType = response.headers.get("Content-Type") ?? "";
        if (!contentType.toLowerCase().includes("application/pdf")) {
            const diagnostic = `Content-Type recibido: ${contentType || "no informado"}`;
            this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
            throw new FacturaServiceError("El backend respondió correctamente, pero no devolvió un archivo PDF.", response.status, diagnostic);
        }
        let blob;
        try {
            blob = await response.blob();
        }
        catch (cause) {
            const diagnostic = cause instanceof Error ? cause.message : String(cause);
            this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
            throw new FacturaServiceError("El PDF se recibió, pero no pudo leerse como archivo.", response.status, diagnostic, { cause });
        }
        if (blob.size === 0) {
            const diagnostic = "El PDF recibido está vacío.";
            this.emitTrace({ method, route, status: response.status, result: "error", message: diagnostic });
            throw new FacturaServiceError("El backend devolvió un PDF vacío. No se descargó ningún archivo.", response.status, diagnostic);
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
    nombreArchivo(disposition) {
        if (!disposition)
            return null;
        const match = /filename\*?=(?:UTF-8''|")?([^";]+)"?/i.exec(disposition);
        const nombre = match?.[1]?.trim();
        if (!nombre)
            return null;
        try {
            return decodeURIComponent(nombre);
        }
        catch {
            return nombre;
        }
    }
    mensajeError(text) {
        try {
            const parsed = JSON.parse(text);
            if (typeof parsed === "object" && parsed !== null) {
                const error = parsed;
                for (const campo of ["detail", "message", "error", "title"]) {
                    const valor = error[campo];
                    if (typeof valor === "string" && valor.trim()) {
                        return valor;
                    }
                }
            }
        }
        catch {
            // Non-JSON error bodies (for example, plain text) remain useful diagnostics.
        }
        return text;
    }
    describeBody(body) {
        try {
            return JSON.stringify(body) ?? String(body);
        }
        catch {
            return String(body);
        }
    }
    routeWithoutValues(url) {
        const parsed = new URL(url);
        const queryNames = [...parsed.searchParams.keys()];
        return `${parsed.pathname}${queryNames.length ? `?${queryNames.join("&")}` : ""}`;
    }
    emitTrace(trace) {
        this.traceListener?.({ ...trace, timestamp: new Date().toISOString() });
    }
}
//# sourceMappingURL=factura.service.js.map