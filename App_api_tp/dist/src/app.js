import { FacturaService, FacturaServiceError, } from "../services/factura.service.js";
const service = new FacturaService();
const listView = requiredElement("list-view");
const formView = requiredElement("form-view");
const filtersForm = requiredElement("filters-form");
const invoiceForm = requiredElement("invoice-form");
const rows = requiredElement("invoice-rows");
const detailRows = requiredElement("detail-rows");
const formMessage = requiredElement("form-message");
const traceList = requiredElement("trace-list");
const saveButton = requiredElement("save-invoice");
const resultsCount = requiredElement("results-count");
const retryButton = requiredElement("retry-list");
let invoices = [];
let currentFilters = {};
let details = [];
let expandedInvoices = new Set();
let loading = false;
let saving = false;
let formDirty = false;
let latestTraceItems = [];
function requiredElement(id) {
    const element = document.getElementById(id);
    if (!element)
        throw new Error(`No se encontró el elemento requerido #${id}.`);
    return element;
}
function input(name) {
    const element = invoiceForm.elements.namedItem(name);
    if (!(element instanceof HTMLInputElement)) {
        throw new Error(`No se encontró el campo ${name}.`);
    }
    return element;
}
function textElement(tag, text, className) {
    const element = document.createElement(tag);
    element.textContent = text;
    if (className)
        element.className = className;
    return element;
}
function setFormMessage(message, kind) {
    formMessage.textContent = message;
    formMessage.className = `form-message ${kind}`;
    formMessage.hidden = false;
}
function hideFormMessage() {
    formMessage.textContent = "";
    formMessage.hidden = true;
    formMessage.className = "form-message";
}
function errorMessage(error) {
    if (error instanceof FacturaServiceError) {
        return error.diagnostic && !error.message.includes(error.diagnostic)
            ? `${error.message} Diagnóstico: ${error.diagnostic}`
            : error.message;
    }
    return error instanceof Error ? error.message : String(error);
}
function renderTraces() {
    traceList.replaceChildren();
    if (latestTraceItems.length === 0) {
        traceList.append(textElement("p", "Las solicitudes realizadas aparecerán aquí.", "trace-empty"));
        return;
    }
    for (const trace of latestTraceItems) {
        const entry = document.createElement("div");
        entry.className = "trace-entry";
        entry.append(textElement("strong", trace.method), textElement("span", trace.route, "trace-route"), textElement("span", trace.status === null ? "—" : String(trace.status)), textElement("span", trace.result === "success" ? "Correcto" : "Error", trace.result === "success" ? "trace-success" : "trace-failure"), textElement("span", trace.message, "trace-message"));
        traceList.append(entry);
    }
    traceList.scrollTop = traceList.scrollHeight;
}
service.setTraceListener((trace) => {
    latestTraceItems = [...latestTraceItems.slice(-39), trace];
    renderTraces();
});
function money(value) {
    return new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);
}
function renderListMessage(message, isError = false) {
    rows.replaceChildren();
    const tr = document.createElement("tr");
    const td = document.createElement("td");
    td.textContent = message;
    td.className = `table-message${isError ? " table-error" : ""}`;
    td.colSpan = 8;
    tr.append(td);
    rows.append(tr);
    resultsCount.textContent = isError ? "No se pudo completar la búsqueda" : "—";
}
function appendCell(row, content, className) {
    const cell = document.createElement("td");
    cell.textContent = content;
    if (className)
        cell.className = className;
    row.append(cell);
    return cell;
}
function renderInvoiceList() {
    rows.replaceChildren();
    retryButton.hidden = false;
    retryButton.disabled = loading;
    if (loading) {
        renderListMessage("Cargando facturas…");
        return;
    }
    retryButton.hidden = true;
    if (invoices.length === 0) {
        renderListMessage("No hay facturas que coincidan con los filtros.");
        resultsCount.textContent = "0 facturas";
        return;
    }
    resultsCount.textContent = `${invoices.length} ${invoices.length === 1 ? "factura" : "facturas"}`;
    invoices.forEach((invoice, index) => {
        const row = document.createElement("tr");
        appendCell(row, String(invoice.numeroFactura));
        appendCell(row, invoice.fechaEmision ?? "—");
        appendCell(row, invoice.clienteDenominacion);
        appendCell(row, invoice.condicionIva);
        appendCell(row, invoice.puntoVentaDescripcion);
        appendCell(row, money(invoice.importeTotal), "numeric");
        appendCell(row, String(invoice.detalles.length), "numeric");
        const actions = document.createElement("td");
        const viewDetails = document.createElement("button");
        viewDetails.type = "button";
        viewDetails.className = "button button-secondary button-small";
        viewDetails.dataset.action = "details";
        viewDetails.dataset.index = String(index);
        viewDetails.textContent = expandedInvoices.has(invoice.numeroFactura) ? "Ocultar ítems" : "Ver ítems";
        actions.append(viewDetails);
        const deleteButton = document.createElement("button");
        deleteButton.type = "button";
        deleteButton.className = "button button-danger button-small";
        deleteButton.textContent = "Anular";
        deleteButton.disabled = true;
        deleteButton.title = "El GET no expone el ID interno requerido por DELETE (anulación lógica).";
        actions.append(deleteButton);
        row.append(actions);
        rows.append(row);
        if (expandedInvoices.has(invoice.numeroFactura)) {
            const detailRow = document.createElement("tr");
            detailRow.className = "expanded-detail-row";
            const detailCell = document.createElement("td");
            detailCell.colSpan = 8;
            const detailsTable = document.createElement("table");
            detailsTable.className = "expanded-details";
            const heading = document.createElement("tr");
            for (const title of ["Descripción", "Cantidad", "Precio unitario", "Neto", "IVA", "Subtotal"]) {
                heading.append(textElement("th", title));
            }
            const head = document.createElement("thead");
            head.append(heading);
            const body = document.createElement("tbody");
            for (const detail of invoice.detalles) {
                const detailLine = document.createElement("tr");
                appendCell(detailLine, detail.descripcion ?? "—");
                appendCell(detailLine, String(detail.cantidad));
                appendCell(detailLine, money(detail.precioUnitario));
                appendCell(detailLine, money(detail.importeNeto));
                appendCell(detailLine, money(detail.importeIva));
                appendCell(detailLine, money(detail.importeSubtotal));
                body.append(detailLine);
            }
            detailsTable.append(head, body);
            detailCell.append(detailsTable);
            detailRow.append(detailCell);
            rows.append(detailRow);
        }
    });
}
async function loadInvoices(filters = currentFilters) {
    currentFilters = filters;
    loading = true;
    renderInvoiceList();
    try {
        invoices = await service.obtenerFacturas(filters);
        expandedInvoices = new Set();
        loading = false;
        renderInvoiceList();
    }
    catch (error) {
        invoices = [];
        loading = false;
        renderListMessage(`Error al cargar facturas: ${errorMessage(error)}`, true);
        retryButton.hidden = false;
        retryButton.disabled = false;
    }
}
function readFilters() {
    const formData = new FormData(filtersForm);
    const filters = {};
    const numero = String(formData.get("numeroFactura") ?? "").trim();
    if (numero) {
        const value = Number(numero);
        if (!Number.isSafeInteger(value) || value < 1) {
            window.alert("El número de factura debe ser un entero positivo válido.");
            return null;
        }
        filters.numeroFactura = value;
    }
    const cliente = String(formData.get("clienteDenominacion") ?? "").trim();
    const iva = String(formData.get("condicionIva") ?? "").trim();
    const desde = String(formData.get("fechaDesde") ?? "").trim();
    const hasta = String(formData.get("fechaHasta") ?? "").trim();
    if (cliente)
        filters.clienteDenominacion = cliente;
    if (iva)
        filters.condicionIva = iva;
    if (desde)
        filters.fechaDesde = desde;
    if (hasta)
        filters.fechaHasta = hasta;
    if (desde && hasta && desde > hasta) {
        window.alert("La fecha desde no puede ser posterior a la fecha hasta.");
        return null;
    }
    return filters;
}
function createDetailDraft() {
    return {
        articuloId: "",
        listaPrecioArticuloId: "",
        descripcion: "",
        cantidad: "1",
        precioUnitario: "",
        porcentajeBonificacion: "0",
        porcentajeIva: "0",
    };
}
function detailInput(field, value, label, type = "text") {
    const element = document.createElement("input");
    element.className = "detail-input";
    element.type = type;
    element.value = value;
    element.dataset.field = field;
    element.setAttribute("aria-label", label);
    if (type === "number") {
        element.step = field === "articuloId" || field === "listaPrecioArticuloId" ? "1" : "0.01";
        element.min = field === "articuloId" || field === "listaPrecioArticuloId" || field === "cantidad" ? "1" : "0";
    }
    return element;
}
function validNumber(text, minimum, integer = false) {
    const trimmed = text.trim();
    if (!trimmed)
        return null;
    const value = Number(trimmed);
    if (!Number.isFinite(value) || value < minimum || (integer && !Number.isSafeInteger(value)))
        return null;
    return value;
}
function rounded(value) {
    return Math.round((value + Number.EPSILON) * 100) / 100;
}
function lineAmounts(detail) {
    const quantity = validNumber(detail.cantidad, 1);
    const price = validNumber(detail.precioUnitario, 0);
    const discount = validNumber(detail.porcentajeBonificacion, 0);
    const vat = validNumber(detail.porcentajeIva, 0);
    if (quantity === null || price === null || discount === null || discount > 100 || vat === null || vat > 100) {
        return { subtotal: 0, error: true };
    }
    const net = rounded(quantity * price * (1 - discount / 100));
    const tax = rounded(net * vat / 100);
    return { subtotal: rounded(net + tax), error: false };
}
function renderDetailRows() {
    detailRows.replaceChildren();
    details.forEach((detail, index) => {
        const row = document.createElement("tr");
        const controlsCell = document.createElement("td");
        controlsCell.colSpan = 7;
        const controls = document.createElement("div");
        controls.className = "detail-controls";
        const product = document.createElement("div");
        product.className = "product-fields";
        product.append(detailInput("articuloId", detail.articuloId, "ID Artículo", "number"), detailInput("listaPrecioArticuloId", detail.listaPrecioArticuloId, "ID Lista precio artículo", "number"), detailInput("descripcion", detail.descripcion, "Descripción (opcional)", "text"));
        const quantity = detailInput("cantidad", detail.cantidad, "Cantidad", "number");
        const price = detailInput("precioUnitario", detail.precioUnitario, "Precio unitario", "number");
        const discount = detailInput("porcentajeBonificacion", detail.porcentajeBonificacion, "Bonificación porcentaje", "number");
        const tax = detailInput("porcentajeIva", detail.porcentajeIva, "IVA porcentaje", "number");
        const subtotal = textElement("span", "—", "detail-subtotal");
        subtotal.dataset.subtotal = "true";
        const amounts = lineAmounts(detail);
        if (!amounts.error)
            subtotal.textContent = money(amounts.subtotal);
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "button button-danger remove-item";
        remove.dataset.removeIndex = String(index);
        remove.setAttribute("aria-label", `Eliminar ítem ${index + 1}`);
        remove.textContent = "×";
        for (const control of [quantity, price, discount, tax])
            controls.append(control);
        controls.append(subtotal, remove);
        controls.prepend(product);
        controlsCell.append(controls);
        row.append(controlsCell);
        detailRows.append(row);
    });
    updateEstimatedTotal();
}
function updateEstimatedTotal() {
    const totalNode = requiredElement("estimated-total");
    const amounts = details.map(lineAmounts);
    if (amounts.some((amount) => amount.error)) {
        totalNode.textContent = "Completar ítems";
        return;
    }
    totalNode.textContent = money(rounded(amounts.reduce((sum, amount) => sum + amount.subtotal, 0)));
}
function showForm() {
    invoiceForm.reset();
    const localToday = new Date();
    input("fechaEmision").value = [localToday.getFullYear(), String(localToday.getMonth() + 1).padStart(2, "0"), String(localToday.getDate()).padStart(2, "0")].join("-");
    input("importeCobrado").value = "0";
    input("numero").value = "";
    details = [createDetailDraft()];
    formDirty = false;
    hideFormMessage();
    renderDetailRows();
    listView.hidden = true;
    formView.hidden = false;
    window.scrollTo({ top: 0, behavior: "smooth" });
}
function discardForm() {
    if (formDirty && !window.confirm("Hay datos sin guardar. ¿Deseas descartarlos y volver al listado?"))
        return;
    formView.hidden = true;
    listView.hidden = false;
    hideFormMessage();
}
function collectRequest() {
    invoiceForm.querySelectorAll(".invalid-field").forEach((element) => element.classList.remove("invalid-field"));
    const requiredNames = ["numero", "fechaEmision", "clienteId", "condicionIvaId", "puntoVentaId", "tipoMonedaId"];
    let invalid = false;
    for (const name of requiredNames) {
        const field = input(name);
        if (!field.value.trim()) {
            field.classList.add("invalid-field");
            invalid = true;
        }
    }
    const numero = validNumber(input("numero").value, 1, true);
    const clienteId = validNumber(input("clienteId").value, 1, true);
    const condicionIvaId = validNumber(input("condicionIvaId").value, 1, true);
    const puntoVentaId = validNumber(input("puntoVentaId").value, 1, true);
    const tipoMonedaId = validNumber(input("tipoMonedaId").value, 1, true);
    const cobrado = validNumber(input("importeCobrado").value, 0);
    if (numero === null || clienteId === null || condicionIvaId === null || puntoVentaId === null || tipoMonedaId === null || cobrado === null) {
        invalid = true;
    }
    if (details.length === 0) {
        setFormMessage("Agrega al menos un detalle a la factura.", "error");
        return null;
    }
    const requestDetails = [];
    for (let index = 0; index < details.length; index += 1) {
        const detail = details[index];
        const articuloId = validNumber(detail.articuloId, 1, true);
        const listaPrecioArticuloId = validNumber(detail.listaPrecioArticuloId, 1, true);
        const cantidad = validNumber(detail.cantidad, 1);
        const precioUnitario = validNumber(detail.precioUnitario, 0);
        const porcentajeBonificacion = validNumber(detail.porcentajeBonificacion, 0);
        const porcentajeIva = validNumber(detail.porcentajeIva, 0);
        if (articuloId === null || listaPrecioArticuloId === null || cantidad === null || precioUnitario === null || porcentajeBonificacion === null || porcentajeBonificacion > 100 || porcentajeIva === null || porcentajeIva > 100) {
            invalid = true;
            const row = detailRows.rows.item(index);
            row?.classList.add("invalid-field");
            continue;
        }
        const item = {
            articuloId,
            listaPrecioArticuloId,
            cantidad,
            precioUnitario,
            porcentajeBonificacion,
            porcentajeIva,
        };
        if (detail.descripcion.trim())
            item.descripcion = detail.descripcion.trim();
        requestDetails.push(item);
    }
    if (invalid || numero === null || clienteId === null || condicionIvaId === null || puntoVentaId === null || tipoMonedaId === null || cobrado === null) {
        setFormMessage("Revisa los campos obligatorios: IDs enteros positivos, cantidades positivas, importes no negativos y porcentajes entre 0 y 100.", "error");
        return null;
    }
    const totalEstimado = rounded(details.reduce((sum, detail) => sum + lineAmounts(detail).subtotal, 0));
    if (cobrado > totalEstimado) {
        setFormMessage("El importe cobrado no puede superar el total calculado de la factura.", "error");
        return null;
    }
    const fecha = input("fechaEmision").value;
    const fechaEmision = new Date(`${fecha}T00:00:00.000Z`);
    if (Number.isNaN(fechaEmision.getTime())) {
        setFormMessage("La fecha de emisión no es válida.", "error");
        return null;
    }
    return {
        numero,
        fechaEmision: fechaEmision.toISOString(),
        clienteId,
        condicionIvaId,
        puntoVentaId,
        tipoMonedaId,
        importeCobrado: cobrado,
        resultadoAfip: "PENDIENTE",
        estado: "PENDIENTE",
        detalles: requestDetails,
    };
}
async function saveInvoice(event) {
    event.preventDefault();
    if (saving)
        return;
    hideFormMessage();
    const request = collectRequest();
    if (!request)
        return;
    saving = true;
    saveButton.disabled = true;
    saveButton.textContent = "Guardando…";
    try {
        const response = await service.crearFactura(request);
        formDirty = false;
        formView.hidden = true;
        listView.hidden = false;
        window.alert(`Factura ${response.numeroFactura} creada correctamente.`);
        await loadInvoices(currentFilters);
        window.scrollTo({ top: 0, behavior: "smooth" });
    }
    catch (error) {
        setFormMessage(errorMessage(error), "error");
    }
    finally {
        saving = false;
        saveButton.disabled = false;
        saveButton.textContent = "Guardar";
    }
}
filtersForm.addEventListener("submit", (event) => {
    event.preventDefault();
    const filters = readFilters();
    if (filters)
        void loadInvoices(filters);
});
requiredElement("clear-filters").addEventListener("click", () => {
    filtersForm.reset();
    void loadInvoices({});
});
requiredElement("new-invoice").addEventListener("click", showForm);
requiredElement("back-to-list").addEventListener("click", discardForm);
requiredElement("cancel-form").addEventListener("click", discardForm);
requiredElement("add-item").addEventListener("click", () => {
    details.push(createDetailDraft());
    formDirty = true;
    renderDetailRows();
});
requiredElement("clear-traces").addEventListener("click", () => {
    latestTraceItems = [];
    renderTraces();
});
retryButton.addEventListener("click", () => void loadInvoices(currentFilters));
invoiceForm.addEventListener("submit", (event) => void saveInvoice(event));
invoiceForm.addEventListener("input", (event) => {
    formDirty = true;
    const target = event.target;
    if (target instanceof HTMLInputElement && target.dataset.field) {
        const detailRow = target.closest("tr");
        const detailIndex = detailRow ? Array.from(detailRows.rows).indexOf(detailRow) : -1;
        const field = target.dataset.field;
        if (detailIndex >= 0 && details[detailIndex]) {
            details[detailIndex][field] = target.value;
            const subtotal = detailRow?.querySelector('[data-subtotal="true"]');
            const amounts = lineAmounts(details[detailIndex]);
            if (subtotal)
                subtotal.textContent = amounts.error ? "—" : money(amounts.subtotal);
            updateEstimatedTotal();
        }
    }
    hideFormMessage();
});
detailRows.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement) || target.dataset.removeIndex === undefined)
        return;
    const index = Number(target.dataset.removeIndex);
    if (!Number.isInteger(index) || index < 0 || index >= details.length)
        return;
    details.splice(index, 1);
    formDirty = true;
    renderDetailRows();
});
rows.addEventListener("click", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLButtonElement) || target.dataset.action !== "details")
        return;
    const index = Number(target.dataset.index);
    const invoice = invoices[index];
    if (!invoice)
        return;
    if (expandedInvoices.has(invoice.numeroFactura))
        expandedInvoices.delete(invoice.numeroFactura);
    else
        expandedInvoices.add(invoice.numeroFactura);
    renderInvoiceList();
});
renderTraces();
void loadInvoices({});
//# sourceMappingURL=app.js.map