import type {
  EnvioArcaProcesoDTO,
  FacturaDetalleRequestDTO,
  FacturaFiltrosDTO,
  FacturaReporteDTO,
  FacturaRequestDTO,
  FacturaVentaPaginaDTO,
  CatalogoDenominacionDTO,
  PuntoVentaDTO,
  ListaPrecioArticuloDTO,
} from "../models/factura.dto.js";
import {
  FacturaService,
  FacturaServiceError,
  type FacturaHttpTrace,
} from "../services/factura.service.js";

type DetailDraft = {
  articuloId: string;
  listaPrecioArticuloId: string;
  descripcion: string;
  rubro: string;
  cantidad: string;
  precioUnitario: string;
  porcentajeBonificacion: string;
  porcentajeIva: string;
  listaPrecioId: string;
};

const service = new FacturaService();
const listView = requiredElement<HTMLElement>("list-view");
const formView = requiredElement<HTMLElement>("form-view");
const formTitle = requiredElement<HTMLHeadingElement>("form-title");
const filtersForm = requiredElement<HTMLFormElement>("filters-form");
const invoiceForm = requiredElement<HTMLFormElement>("invoice-form");
const rows = requiredElement<HTMLTableSectionElement>("invoice-rows");
const detailRows = requiredElement<HTMLTableSectionElement>("detail-rows");
const formMessage = requiredElement<HTMLDivElement>("form-message");
const traceList = requiredElement<HTMLDivElement>("trace-list");
const saveButton = requiredElement<HTMLButtonElement>("save-invoice");
const resultsCount = requiredElement<HTMLParagraphElement>("results-count");
const retryButton = requiredElement<HTMLButtonElement>("retry-list");
const previousPageButton = requiredElement<HTMLButtonElement>("previous-page");
const nextPageButton = requiredElement<HTMLButtonElement>("next-page");
const pageStatus = requiredElement<HTMLSpanElement>("page-status");
const catalogMessage = requiredElement<HTMLDivElement>("catalog-message");
const apiNotice = requiredElement<HTMLDivElement>("api-notice");
const actionNotice = requiredElement<HTMLDivElement>("action-notice");
const retryCatalogsFormButton = requiredElement<HTMLButtonElement>("retry-catalogs-form");
const articleExplorer = requiredElement<HTMLDialogElement>("article-explorer");
const articleRows = requiredElement<HTMLTableSectionElement>("article-rows");
const articleExplorerMessage = requiredElement<HTMLParagraphElement>("article-explorer-message");
const articleFilterName = requiredElement<HTMLInputElement>("article-filter-name");
const articleFilterRubro = requiredElement<HTMLSelectElement>("article-filter-rubro");
const confirmArticleSelection = requiredElement<HTMLButtonElement>("confirm-article-selection");
const retryArticlesButton = requiredElement<HTMLButtonElement>("retry-articles");
const clientExplorer = requiredElement<HTMLDialogElement>("client-explorer");
const clientRows = requiredElement<HTMLTableSectionElement>("client-rows");
const clientExplorerMessage = requiredElement<HTMLParagraphElement>("client-explorer-message");
const clientFilter = requiredElement<HTMLInputElement>("client-filter");
const chooseClientButton = requiredElement<HTMLButtonElement>("choose-client");
const confirmClientSelection = requiredElement<HTMLButtonElement>("confirm-client-selection");
const tracePanel = requiredElement<HTMLElement>("trace-panel");
const traceHeading = requiredElement<HTMLDivElement>("trace-heading");
const traceTitle = requiredElement<HTMLHeadingElement>("trace-title");
const arcaExplorer = requiredElement<HTMLDialogElement>("arca-explorer");
const arcaRows = requiredElement<HTMLTableSectionElement>("arca-rows");
const arcaMessage = requiredElement<HTMLParagraphElement>("arca-explorer-message");
const arcaResult = requiredElement<HTMLDivElement>("arca-result");
const sendArcaButton = requiredElement<HTMLButtonElement>("send-arca");
const retryArcaButton = requiredElement<HTMLButtonElement>("retry-arca");
const pdfExplorer = requiredElement<HTMLDialogElement>("pdf-explorer");
const pdfInvoiceSelect = requiredElement<HTMLSelectElement>("pdf-invoice");
const pdfMessage = requiredElement<HTMLParagraphElement>("pdf-explorer-message");
const printPdfButton = requiredElement<HTMLButtonElement>("print-pdf-confirm");

let invoices: FacturaReporteDTO[] = [];
let currentFilters: FacturaFiltrosDTO = {};
let facturaPagina: FacturaVentaPaginaDTO = {
  contenido: [], pagina: 0, tamanoPagina: 10, totalElementos: 0, totalPaginas: 0,
};
let details: DetailDraft[] = [];
let expandedInvoices = new Set<number>();
let puntosVenta: PuntoVentaDTO[] = [];
let clientes: CatalogoDenominacionDTO[] = [];
let condicionesIva: CatalogoDenominacionDTO[] = [];
let tiposMoneda: CatalogoDenominacionDTO[] = [];
let listasPrecio: CatalogoDenominacionDTO[] = [];
let rubros: CatalogoDenominacionDTO[] = [];
let articulosLista: ListaPrecioArticuloDTO[] = [];
let articulosSeleccionados = new Map<number, string>();
let articulosLoading = false;
let articleRequestVersion = 0;
let catalogosLoading = false;
let selectedListaPrecioId = "";
let loading = false;
let saving = false;
let formDirty = false;
let editingInvoiceId: number | null = null;
let editingImporteCobrado = 0;
let anulando = false;
let pendingInvoices: FacturaReporteDTO[] = [];
let arcaLoading = false;
let arcaSending = false;
let arcaLoadVersion = 0;
let pdfPrinting = false;
let latestTraceItems: FacturaHttpTrace[] = [];
let selectedClientId: number | null = null;

function requiredElement<T extends HTMLElement>(id: string): T {
  const element = document.getElementById(id);
  if (!element) throw new Error(`No se encontró el elemento requerido #${id}.`);
  return element as T;
}

function input(name: string): HTMLInputElement {
  const element = invoiceForm.elements.namedItem(name);
  if (!(element instanceof HTMLInputElement)) {
    throw new Error(`No se encontró el campo ${name}.`);
  }
  return element;
}

function select(name: string): HTMLSelectElement {
  const element = invoiceForm.elements.namedItem(name);
  if (!(element instanceof HTMLSelectElement)) throw new Error(`No se encontró el selector ${name}.`);
  return element;
}

function textElement(tag: keyof HTMLElementTagNameMap, text: string, className?: string): HTMLElement {
  const element = document.createElement(tag);
  element.textContent = text;
  if (className) element.className = className;
  return element;
}

function setFormMessage(message: string, kind: "error" | "success" | "warning"): void {
  formMessage.textContent = message;
  formMessage.className = `form-message ${kind}`;
  formMessage.hidden = false;
}

function hideFormMessage(): void {
  formMessage.textContent = "";
  formMessage.hidden = true;
  formMessage.className = "form-message";
}

function setActionNotice(message: string, kind: "info" | "success" | "error" | "warning" = "info"): void {
  actionNotice.textContent = message;
  actionNotice.className = `notice notice-${kind}`;
}

function errorMessage(error: unknown): string {
  if (error instanceof FacturaServiceError) {
    return error.diagnostic && !error.message.includes(error.diagnostic)
      ? `${error.message} Diagnóstico: ${error.diagnostic}`
      : error.message;
  }
  return error instanceof Error ? error.message : String(error);
}

function renderTraces(): void {
  traceList.replaceChildren();
  if (latestTraceItems.length === 0) {
    traceList.append(textElement("p", "Las solicitudes realizadas aparecerán aquí.", "trace-empty"));
    return;
  }

  for (const trace of latestTraceItems) {
    const entry = document.createElement("div");
    entry.className = "trace-entry";
    entry.append(
      textElement("strong", trace.method),
      textElement("span", trace.route, "trace-route"),
      textElement("span", trace.status === null ? "—" : String(trace.status)),
      textElement(
        "span",
        trace.result === "success" ? "Correcto" : "Error",
        trace.result === "success" ? "trace-success" : "trace-failure",
      ),
      textElement("span", trace.message, "trace-message"),
    );
    traceList.append(entry);
  }
  traceList.scrollTop = traceList.scrollHeight;
}

service.setTraceListener((trace) => {
  latestTraceItems = [...latestTraceItems.slice(-39), trace];
  renderTraces();
});

function money(value: number): string {
  return new Intl.NumberFormat("es-AR", { style: "currency", currency: "ARS" }).format(value);
}

function renderListMessage(message: string, isError = false): void {
  rows.replaceChildren();
  const tr = document.createElement("tr");
  const td = document.createElement("td");
  td.textContent = message;
  td.className = `table-message${isError ? " table-error" : ""}`;
  td.colSpan = 9;
  tr.append(td);
  rows.append(tr);
  resultsCount.textContent = isError ? "No se pudo completar la búsqueda" : "—";
}

function appendCell(row: HTMLTableRowElement, content: string, className?: string): HTMLTableCellElement {
  const cell = document.createElement("td");
  cell.textContent = content;
  if (className) cell.className = className;
  row.append(cell);
  return cell;
}

function iconButton(action: "edit" | "delete", label: string): HTMLButtonElement {
  const button = document.createElement("button");
  button.type = "button";
  button.className = action === "delete"
    ? "button button-danger button-small icon-button"
    : "button button-secondary button-small icon-button";
  button.dataset.action = action;
  button.title = label;
  button.setAttribute("aria-label", label);
  const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
  svg.setAttribute("viewBox", "0 0 24 24");
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "currentColor");
  svg.setAttribute("stroke-width", "2");
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");
  svg.setAttribute("aria-hidden", "true");
  const paths = action === "edit"
    ? ["M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"]
    : ["M3 6h18", "M8 6V4h8v2", "M19 6l-1 14H6L5 6", "M10 11v6", "M14 11v6"];
  for (const data of paths) {
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("d", data);
    svg.append(path);
  }
  button.append(svg);
  return button;
}

function isEditable(invoice: FacturaReporteDTO): boolean {
  return invoice.estado === "PENDIENTE" || invoice.estado === "RECHAZADA";
}

function isAnulable(invoice: FacturaReporteDTO): boolean {
  return invoice.estado === "PENDIENTE" || invoice.estado === "RECHAZADA";
}

function renderInvoiceList(): void {
  rows.replaceChildren();
  retryButton.hidden = false;
  retryButton.disabled = loading;
  if (loading) {
    renderListMessage("Cargando facturas…");
    updatePaginationControls();
    return;
  }
  retryButton.hidden = true;
  if (invoices.length === 0) {
    renderListMessage("No hay facturas que coincidan con los filtros.");
    resultsCount.textContent = `${facturaPagina.totalElementos} ${facturaPagina.totalElementos === 1 ? "factura" : "facturas"}`;
    updatePaginationControls();
    return;
  }

  resultsCount.textContent = `${facturaPagina.totalElementos} ${facturaPagina.totalElementos === 1 ? "factura" : "facturas"}`;
  invoices.forEach((invoice, index) => {
    const row = document.createElement("tr");
    appendCell(row, String(invoice.numeroFactura));
    appendCell(row, invoice.fechaEmision ?? "—");
    appendCell(row, invoice.clienteDenominacion);
    appendCell(row, invoice.condicionIva);
    appendCell(row, invoice.puntoVentaDescripcion);
    appendCell(row, invoice.estado);
    appendCell(row, money(invoice.importeTotal), "numeric");
    appendCell(row, String(invoice.detalles.length), "numeric");

    const actions = document.createElement("td");
    const actionGroup = document.createElement("div");
    actionGroup.className = "row-actions";

    const viewDetails = document.createElement("button");
    viewDetails.type = "button";
    viewDetails.className = "button button-secondary button-small";
    viewDetails.dataset.action = "details";
    viewDetails.dataset.index = String(index);
    viewDetails.textContent = expandedInvoices.has(invoice.id) ? "Ocultar ítems" : "Ver ítems";
    actionGroup.append(viewDetails);

    if (isEditable(invoice)) {
      const editButton = iconButton("edit", `Editar factura ${invoice.numeroFactura}`);
      editButton.dataset.index = String(index);
      actionGroup.append(editButton);
    }
    if (isAnulable(invoice)) {
      const deleteButton = iconButton("delete", `Anular factura ${invoice.numeroFactura}`);
      deleteButton.dataset.index = String(index);
      actionGroup.append(deleteButton);
    }
    if (invoice.estado === "PENDIENTE") {
      const arcaButton = document.createElement("button");
      arcaButton.type = "button";
      arcaButton.className = "button button-secondary button-small icon-button";
      arcaButton.dataset.action = "arca";
      arcaButton.dataset.index = String(index);
      arcaButton.title = `Enviar factura ${invoice.numeroFactura} a ARCA`;
      arcaButton.setAttribute("aria-label", `Enviar factura ${invoice.numeroFactura} a ARCA`);
      arcaButton.textContent = "A";
      actionGroup.append(arcaButton);
    }
    actions.append(actionGroup);
    row.append(actions);
    rows.append(row);

    if (expandedInvoices.has(invoice.id)) {
      const detailRow = document.createElement("tr");
      detailRow.className = "expanded-detail-row";
      const detailCell = document.createElement("td");
      detailCell.colSpan = 9;
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
  updatePaginationControls();
}

function updatePaginationControls(): void {
  const pages = facturaPagina.totalPaginas;
  pageStatus.textContent = pages === 0
    ? "Página 0 de 0"
    : `Página ${facturaPagina.pagina + 1} de ${pages}`;
  previousPageButton.disabled = loading || facturaPagina.pagina <= 0;
  nextPageButton.disabled = loading || pages === 0 || facturaPagina.pagina + 1 >= pages;
}

async function loadInvoices(filters: FacturaFiltrosDTO = currentFilters, page = filters.page ?? 0): Promise<void> {
  currentFilters = { ...filters, page };
  loading = true;
  renderInvoiceList();
  try {
    facturaPagina = await service.obtenerFacturas(currentFilters);
    invoices = facturaPagina.contenido;
    expandedInvoices = new Set();
    loading = false;
    renderInvoiceList();
  } catch (error) {
    invoices = [];
    facturaPagina = { contenido: [], pagina: page, tamanoPagina: 10, totalElementos: 0, totalPaginas: 0 };
    loading = false;
    renderListMessage(`Error al cargar facturas: ${errorMessage(error)}`, true);
    retryButton.hidden = false;
    retryButton.disabled = false;
    updatePaginationControls();
  }
}

function readFilters(): FacturaFiltrosDTO | null {
  const formData = new FormData(filtersForm);
  const filters: FacturaFiltrosDTO = {};
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
  if (cliente) filters.clienteDenominacion = cliente;
  if (iva) filters.condicionIva = iva;
  if (desde) filters.fechaDesde = desde;
  if (hasta) filters.fechaHasta = hasta;
  if (desde && hasta && desde > hasta) {
    window.alert("La fecha desde no puede ser posterior a la fecha hasta.");
    return null;
  }
  return filters;
}

function renderSelectOptions<T extends CatalogoDenominacionDTO | PuntoVentaDTO>(
  name: string,
  options: T[],
  label: (option: T) => string,
): void {
  const control = select(name);
  control.replaceChildren(new Option("Selecciona una opción", ""));
  for (const option of options) control.add(new Option(label(option), String(option.id)));
  control.disabled = catalogosLoading || options.length === 0;
}

async function loadCatalogs(): Promise<void> {
  catalogosLoading = true;
  catalogMessage.textContent = "Cargando catálogos…";
  catalogMessage.className = "catalog-message muted";
  apiNotice.hidden = false;
  apiNotice.firstChild!.textContent = "Cargando datos de catálogos… ";
  requiredElement<HTMLButtonElement>("retry-catalogs").hidden = true;
  retryCatalogsFormButton.hidden = true;
  for (const name of ["puntoVentaId", "clienteId", "condicionIvaId", "tipoMonedaId", "listaPrecioId"]) {
    select(name).disabled = true;
  }
  const results = await Promise.allSettled([
    service.obtenerPuntosVenta(),
    service.obtenerClientes(),
    service.obtenerCondicionesIva(),
    service.obtenerTiposMoneda(),
    service.obtenerListasPrecio(),
  ]);
  if (results[0]?.status === "fulfilled") puntosVenta = results[0].value;
  if (results[1]?.status === "fulfilled") clientes = results[1].value;
  if (results[2]?.status === "fulfilled") condicionesIva = results[2].value;
  if (results[3]?.status === "fulfilled") tiposMoneda = results[3].value;
  if (results[4]?.status === "fulfilled") listasPrecio = results[4].value;

  catalogosLoading = false;
  renderSelectOptions("puntoVentaId", puntosVenta, (item) => item.descripcion);
  renderSelectOptions("clienteId", clientes, (item) => item.denominacion);
  renderSelectOptions("condicionIvaId", condicionesIva, (item) => item.denominacion);
  renderSelectOptions("tipoMonedaId", tiposMoneda, (item) => item.denominacion);
  renderSelectOptions("listaPrecioId", listasPrecio, (item) => item.denominacion);
  refreshClientButton();
  const failed = results.some((result) => result.status === "rejected");
  const noData = [puntosVenta, clientes, condicionesIva, tiposMoneda, listasPrecio].some((items) => items.length === 0);
  catalogMessage.textContent = failed
    ? "No se pudieron cargar algunos catálogos. Reintenta o verifica la conexión con el servicio."
    : noData ? "Uno o más catálogos no tienen opciones disponibles." : "Catálogos cargados.";
  catalogMessage.className = `catalog-message${failed ? " catalog-error" : " muted"}`;
  apiNotice.hidden = !failed;
  if (failed) {
    apiNotice.firstChild!.textContent = "No se pudieron cargar algunos catálogos. ";
    requiredElement<HTMLButtonElement>("retry-catalogs").hidden = false;
    retryCatalogsFormButton.hidden = false;
  }
}

function normalizeText(value: string): string {
  return value.normalize("NFD").replace(/[\u0300-\u036f]/g, "").toLowerCase().trim();
}

function filteredArticles(): ListaPrecioArticuloDTO[] {
  const name = normalizeText(articleFilterName.value);
  const rubro = articleFilterRubro.value;
  return articulosLista.filter((article) =>
    (!name || normalizeText(article.denominacion).includes(name))
    && (!rubro || article.rubro === rubro));
}

function populateRubroFilter(): void {
  const selected = articleFilterRubro.value;
  const nombres = new Set<string>();
  for (const rubro of rubros) nombres.add(rubro.denominacion);
  for (const article of articulosLista) if (article.rubro) nombres.add(article.rubro);
  articleFilterRubro.replaceChildren(new Option("Todos los rubros", ""));
  for (const nombre of [...nombres].sort((a, b) => a.localeCompare(b, "es"))) {
    articleFilterRubro.add(new Option(nombre, nombre));
  }
  articleFilterRubro.value = nombres.has(selected) ? selected : "";
}

async function loadRubros(): Promise<void> {
  try {
    rubros = await service.obtenerRubros();
  } catch {
    rubros = [];
  }
  populateRubroFilter();
}

function renderArticleRows(): void {
  articleRows.replaceChildren();
  retryArticlesButton.hidden = true;
  if (articulosLoading) {
    articleExplorerMessage.textContent = "Cargando artículos…";
    confirmArticleSelection.disabled = true;
    return;
  }
  if (articulosLista.length === 0) {
    articleExplorerMessage.textContent = "Esta lista de precio no tiene artículos disponibles.";
    confirmArticleSelection.disabled = true;
    return;
  }
  const visibles = filteredArticles();
  articleExplorerMessage.textContent = visibles.length === 0
    ? "No hay artículos que coincidan con los filtros."
    : "Selecciona uno o más artículos e indica sus cantidades.";
  for (const article of visibles) {
    const row = document.createElement("tr");
    const selectionCell = document.createElement("td");
    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = articulosSeleccionados.has(article.id);
    checkbox.dataset.articleId = String(article.id);
    checkbox.setAttribute("aria-label", `Seleccionar ${article.denominacion}`);
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) {
        articulosSeleccionados.set(article.id, "1");
        quantity.disabled = false;
      } else {
        articulosSeleccionados.delete(article.id);
        quantity.disabled = true;
      }
      confirmArticleSelection.disabled = articulosSeleccionados.size === 0;
    });
    selectionCell.append(checkbox);
    const quantityCell = document.createElement("td");
    const quantity = document.createElement("input");
    quantity.type = "number";
    quantity.min = "1";
    quantity.step = "0.01";
    quantity.value = articulosSeleccionados.get(article.id) ?? "1";
    quantity.className = "article-quantity detail-input";
    quantity.dataset.articleId = String(article.id);
    quantity.setAttribute("aria-label", `Cantidad de ${article.denominacion}`);
    quantity.disabled = !checkbox.checked;
    quantity.addEventListener("input", () => {
      if (checkbox.checked) articulosSeleccionados.set(article.id, quantity.value);
    });
    quantityCell.append(quantity);
    row.append(selectionCell);
    appendCell(row, article.denominacion);
    appendCell(row, article.rubro);
    appendCell(row, money(article.precioVenta), "numeric");
    row.append(quantityCell);
    articleRows.append(row);
  }
  confirmArticleSelection.disabled = articulosSeleccionados.size === 0;
}

async function loadArticlesForSelectedList(): Promise<void> {
  const listaPrecioId = validNumber(select("listaPrecioId").value, 1, true);
  const requestVersion = ++articleRequestVersion;
  articulosSeleccionados.clear();
  articulosLista = [];
  if (listaPrecioId === null) {
    articulosLoading = false;
    articleExplorerMessage.textContent = "Selecciona una Lista Precio en Datos de la Factura para consultar artículos.";
    renderArticleRows();
    return;
  }
  articulosLoading = true;
  retryArticlesButton.hidden = true;
  renderArticleRows();
  try {
    const result = await service.obtenerArticulosDeLista(listaPrecioId);
    if (requestVersion !== articleRequestVersion
        || String(listaPrecioId) !== select("listaPrecioId").value) return;
    articulosLista = result;
    articulosLoading = false;
    populateRubroFilter();
    renderArticleRows();
  } catch (error) {
    if (requestVersion !== articleRequestVersion
        || String(listaPrecioId) !== select("listaPrecioId").value) return;
    articulosLoading = false;
    articleRows.replaceChildren();
    articleExplorerMessage.textContent = `No se pudieron cargar los artículos: ${errorMessage(error)}`;
    retryArticlesButton.hidden = false;
    confirmArticleSelection.disabled = true;
  }
}

function openArticleExplorer(): void {
  if (!select("listaPrecioId").value) {
    setFormMessage("Selecciona una Lista Precio antes de agregar artículos.", "error");
    select("listaPrecioId").focus();
    return;
  }
  articulosSeleccionados.clear();
  articleFilterName.value = "";
  articleFilterRubro.value = "";
  articleExplorer.showModal();
  requiredElement<HTMLButtonElement>("close-article-explorer").focus();
  void loadArticlesForSelectedList();
}

function confirmArticles(): void {
  const nuevos: DetailDraft[] = [];
  for (const article of articulosLista) {
    const quantityText = articulosSeleccionados.get(article.id);
    if (quantityText === undefined) continue;
    const quantity = validNumber(quantityText, 1);
    if (quantity === null) {
      articleExplorerMessage.textContent = `Indica una cantidad positiva para ${article.denominacion}.`;
      return;
    }
    nuevos.push({
      articuloId: String(article.articuloId),
      listaPrecioArticuloId: String(article.id),
      descripcion: article.denominacion,
      rubro: article.rubro,
      cantidad: String(quantity),
      precioUnitario: String(article.precioVenta),
      porcentajeBonificacion: "0",
      porcentajeIva: "0",
      listaPrecioId: select("listaPrecioId").value,
    });
  }
  if (nuevos.length === 0) return;
  details.push(...nuevos);
  formDirty = true;
  renderDetailRows();
  articleExplorer.close();
  hideFormMessage();
}

function detailInput(field: keyof DetailDraft, value: string, label: string, type = "text"): HTMLInputElement {
  const element = document.createElement("input");
  element.className = "detail-input";
  element.type = type;
  element.value = value;
  element.dataset.field = field;
  element.setAttribute("aria-label", label);
  if (type === "number") {
    element.step = "0.01";
    element.min = field === "cantidad" ? "0.01" : "0";
  }
  return element;
}

function validNumber(text: string, minimum: number, integer = false): number | null {
  const trimmed = text.trim();
  if (!trimmed) return null;
  const value = Number(trimmed);
  if (!Number.isFinite(value) || value < minimum || (integer && !Number.isSafeInteger(value))) return null;
  return value;
}

function rounded(value: number): number {
  return Math.round((value + Number.EPSILON) * 100) / 100;
}

function lineAmounts(detail: DetailDraft): { subtotal: number; error: boolean } {
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

function renderDetailRows(): void {
  detailRows.replaceChildren();
  details.forEach((detail, index) => {
    const row = document.createElement("tr");
    appendCell(row, detail.descripcion || "—");
    appendCell(row, detail.rubro || "—");
    const quantity = detailInput("cantidad", detail.cantidad, "Cantidad", "number");
    const price = detailInput("precioUnitario", detail.precioUnitario, "Precio unitario", "number");
    const discount = detailInput("porcentajeBonificacion", detail.porcentajeBonificacion, "Bonificación porcentaje", "number");
    const tax = detailInput("porcentajeIva", detail.porcentajeIva, "IVA porcentaje", "number");
    for (const control of [quantity, price, discount, tax]) {
      const cell = document.createElement("td");
      cell.append(control);
      row.append(cell);
    }
    const subtotal = textElement("span", "—", "detail-subtotal");
    subtotal.dataset.subtotal = "true";
    const amounts = lineAmounts(detail);
    if (!amounts.error) subtotal.textContent = money(amounts.subtotal);

    const remove = document.createElement("button");
    remove.type = "button";
    remove.className = "button button-danger remove-item";
    remove.dataset.removeIndex = String(index);
    remove.setAttribute("aria-label", `Eliminar ítem ${index + 1}`);
    remove.textContent = "×";

    const subtotalCell = document.createElement("td");
    subtotalCell.className = "numeric";
    subtotalCell.append(subtotal);
    row.append(subtotalCell);
    const actionCell = document.createElement("td");
    actionCell.append(remove);
    row.append(actionCell);
    detailRows.append(row);
  });
  updateEstimatedTotal();
}

function updateEstimatedTotal(): void {
  const totalNode = requiredElement<HTMLTableCellElement>("estimated-total");
  const amounts = details.map(lineAmounts);
  if (amounts.some((amount) => amount.error)) {
    totalNode.textContent = "Completar ítems";
    return;
  }
  totalNode.textContent = money(rounded(amounts.reduce((sum, amount) => sum + amount.subtotal, 0)));
}

function todayIso(): string {
  const localToday = new Date();
  return [localToday.getFullYear(), String(localToday.getMonth() + 1).padStart(2, "0"), String(localToday.getDate()).padStart(2, "0")].join("-");
}

function setSelectValue(name: string, value: number | null): void {
  select(name).value = value === null ? "" : String(value);
}

function showForm(invoice?: FacturaReporteDTO): void {
  invoiceForm.reset();
  select("clienteId").classList.remove("invalid-field");
  chooseClientButton.classList.remove("invalid-field");
  selectedListaPrecioId = "";
  editingInvoiceId = invoice?.id ?? null;
  editingImporteCobrado = invoice?.importeCobrado ?? 0;
  formTitle.textContent = invoice
    ? `Editar Factura de Venta N° ${invoice.numeroFactura}`
    : "Nueva Factura de Venta";
  input("fechaEmision").value = invoice?.fechaEmision ?? todayIso();
  details = [];
  formDirty = false;
  hideFormMessage();
  if (invoice) {
    setSelectValue("puntoVentaId", invoice.puntoVentaId);
    setSelectValue("clienteId", invoice.clienteId);
    setSelectValue("condicionIvaId", invoice.condicionIvaId);
    setSelectValue("tipoMonedaId", invoice.tipoMonedaId);

    const primerDetalle = invoice.detalles?.[0];
    if (primerDetalle?.listaPrecioId) {
      selectedListaPrecioId = String(primerDetalle.listaPrecioId);
      setSelectValue("listaPrecioId", primerDetalle.listaPrecioId); 
    }

        
    details = (invoice.detalles ?? []).map((det) => ({
      listaPrecioId: det.listaPrecioId != null ? String(det.listaPrecioId) : selectedListaPrecioId,
      listaPrecioArticuloId: det.listaPrecioArticuloId != null ? String(det.listaPrecioArticuloId) : "",
      articuloId: det.articuloId != null ? String(det.articuloId) : "",
      descripcion: det.descripcion ?? "",
      rubro: det.rubro ?? "",
      cantidad: String(det.cantidad ?? 1),
      precioUnitario: String(det.precioUnitario ?? 0),
      porcentajeBonificacion: String(det.porcentajeBonificacion ?? 0),
      porcentajeIva: String(det.porcentajeIva ?? 0),
    }));


  }
  refreshClientButton();
  renderDetailRows();
  listView.hidden = true;
  formView.hidden = false;
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function discardForm(): void {
  if (formDirty && !window.confirm("Hay datos sin guardar. ¿Deseas descartarlos y volver al listado?")) return;
  editingInvoiceId = null;
  editingImporteCobrado = 0;
  formTitle.textContent = "Nueva Factura de Venta";
  formView.hidden = true;
  listView.hidden = false;
  hideFormMessage();
}

function collectRequest(): FacturaRequestDTO | null {
  invoiceForm.querySelectorAll(".invalid-field").forEach((element) => element.classList.remove("invalid-field"));
  const requiredNames = ["fechaEmision"];
  let invalid = false;
  for (const name of requiredNames) {
    const field = input(name);
    if (!field.value.trim()) {
      field.classList.add("invalid-field");
      invalid = true;
    }
  }
  const clienteId = validNumber(select("clienteId").value, 1, true);
  const condicionIvaId = validNumber(select("condicionIvaId").value, 1, true);
  const puntoVentaId = validNumber(select("puntoVentaId").value, 1, true);
  const tipoMonedaId = validNumber(select("tipoMonedaId").value, 1, true);
  const listaPrecioId = validNumber(select("listaPrecioId").value, 1, true);
  for (const [name, value] of [
    ["clienteId", clienteId],
    ["condicionIvaId", condicionIvaId],
    ["puntoVentaId", puntoVentaId],
    ["tipoMonedaId", tipoMonedaId],
    ["listaPrecioId", listaPrecioId],
  ] as const) {
    if (value === null) select(name).classList.add("invalid-field");
  }
  chooseClientButton.classList.toggle("invalid-field", clienteId === null);
  if (clienteId === null || condicionIvaId === null || puntoVentaId === null
      || tipoMonedaId === null || listaPrecioId === null) {
    invalid = true;
  }
  if (details.length === 0) {
    setFormMessage("Agrega al menos un detalle a la factura.", "error");
    return null;
  }

  const requestDetails: FacturaDetalleRequestDTO[] = [];
  for (let index = 0; index < details.length; index += 1) {
    const detail = details[index]!;
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
    const item: FacturaDetalleRequestDTO = {
      articuloId,
      listaPrecioArticuloId,
      cantidad,
      precioUnitario,
      porcentajeBonificacion,
      porcentajeIva,
    };
    if (detail.descripcion.trim()) item.descripcion = detail.descripcion.trim();
    requestDetails.push(item);
  }

  if (invalid || clienteId === null || condicionIvaId === null || puntoVentaId === null
      || tipoMonedaId === null || listaPrecioId === null) {
    setFormMessage("Completa fecha, Punto de Venta, Cliente, Condición IVA, Moneda y Lista Precio; verifica importes y cantidades.", "error");
    return null;
  }

  const fecha = input("fechaEmision").value;
  const fechaEmision = new Date(`${fecha}T00:00:00.000Z`);
  if (Number.isNaN(fechaEmision.getTime())) {
    setFormMessage("La fecha de emisión no es válida.", "error");
    return null;
  }

  return {
    fechaEmision: fechaEmision.toISOString(),
    clienteId,
    condicionIvaId,
    puntoVentaId,
    tipoMonedaId,
    importeCobrado: editingInvoiceId === null ? 0 : editingImporteCobrado,
    detalles: requestDetails,
  };
}

async function saveInvoice(event: SubmitEvent): Promise<void> {
  event.preventDefault();
  if (saving) return;
  hideFormMessage();
  const request = collectRequest();
  if (!request) return;

  const editingId = editingInvoiceId;
  const page = editingId === null ? 0 : facturaPagina.pagina;
  saving = true;
  saveButton.disabled = true;
  saveButton.textContent = "Guardando…";
  try {
    const response = editingId === null
      ? await service.crearFactura(request)
      : await service.actualizarFactura(editingId, request);
    formDirty = false;
    editingInvoiceId = null;
    editingImporteCobrado = 0;
    formTitle.textContent = "Nueva Factura de Venta";
    formView.hidden = true;
    listView.hidden = false;
    window.alert(`Factura ${response.numeroFactura} ${editingId === null ? "creada" : "actualizada"} correctamente.`);
    await loadInvoices(currentFilters, page);
    window.scrollTo({ top: 0, behavior: "smooth" });
  } catch (error) {
    setFormMessage(errorMessage(error), "error");
  } finally {
    saving = false;
    saveButton.disabled = false;
    saveButton.textContent = "Guardar";
  }
}

function startEdit(invoice: FacturaReporteDTO): void {
  if (!isEditable(invoice)) {
    setActionNotice("El estado actual de la factura no permite editarla.", "warning");
    return;
  }
  if (catalogosLoading) {
    setActionNotice("Esperá a que terminen de cargar los catálogos para editar la factura.", "warning");
    return;
  }
  if ([puntosVenta, clientes, condicionesIva, tiposMoneda, listasPrecio].some((items) => items.length === 0)) {
    setActionNotice("No se pudieron cargar los catálogos necesarios para editar la factura. Reintentá la carga y volvé a intentarlo.", "error");
    return;
  }
  showForm(invoice);
}

async function annulInvoice(invoice: FacturaReporteDTO, button: HTMLButtonElement): Promise<void> {
  if (anulando) return;
  if (!isAnulable(invoice)) {
    setActionNotice("El estado actual de la factura no permite anularla.", "warning");
    return;
  }
  const confirmation = window.confirm(
    `¿Anular la factura N° ${invoice.numeroFactura} de ${invoice.clienteDenominacion} por ${money(invoice.importeTotal)}? `
    + "La anulación es lógica y no puede revertirse desde esta interfaz.",
  );
  if (!confirmation) return;

  anulando = true;
  button.disabled = true;
  try {
    await service.anularFactura(invoice.id);
    setActionNotice(`Factura N° ${invoice.numeroFactura} anulada correctamente.`, "success");
    await loadInvoices(currentFilters, facturaPagina.pagina);
  } catch (error) {
    setActionNotice(`No se pudo anular la factura N° ${invoice.numeroFactura}: ${errorMessage(error)}`, "error");
    button.disabled = false;
  } finally {
    anulando = false;
  }
}

async function sendInvoiceToArca(invoice: FacturaReporteDTO, button: HTMLButtonElement): Promise<void> {
  if (arcaSending) return;
  if (invoice.estado !== "PENDIENTE") {
    setActionNotice("Solo las facturas pendientes se pueden enviar a ARCA.", "warning");
    return;
  }
  if (!window.confirm(`¿Enviar la factura N° ${invoice.numeroFactura} a ARCA?`)) return;

  arcaSending = true;
  button.disabled = true;
  button.textContent = "…";
  setActionNotice(`Enviando la factura N° ${invoice.numeroFactura} a ARCA…`, "info");
  try {
    const result = await service.enviarFacturasAArca([invoice.id]);
    const item = result.detalle[0];
    if (!item) {
      setActionNotice(`Envío de la factura N° ${invoice.numeroFactura} procesado sin detalle.`, "warning");
    } else {
      const cae = item.cae ? ` CAE: ${item.cae}.` : "";
      const mensaje = item.mensaje ? ` ${item.mensaje}` : "";
      const kind = item.resultado === "APROBADA" ? "success"
        : item.resultado === "RECHAZADA" ? "error"
          : item.resultado === "INCIERTO" ? "warning" : "info";
      setActionNotice(`Factura N° ${invoice.numeroFactura}: ${item.resultado}.${cae}${mensaje}`, kind);
    }
    await loadInvoices(currentFilters, facturaPagina.pagina);
  } catch (error) {
    setActionNotice(`No se pudo enviar la factura N° ${invoice.numeroFactura} a ARCA: ${errorMessage(error)}`, "error");
    button.disabled = false;
    button.textContent = "A";
  } finally {
    arcaSending = false;
  }
}

function selectedClientName(): string {
  const id = select("clienteId").value;
  return clientes.find((cliente) => String(cliente.id) === id)?.denominacion ?? "";
}

function refreshClientButton(): void {
  const name = selectedClientName();
  chooseClientButton.textContent = name || "Seleccionar cliente";
  chooseClientButton.classList.toggle("selected", Boolean(name));
  chooseClientButton.disabled = catalogosLoading || clientes.length === 0;
  chooseClientButton.classList.toggle("invalid-field", select("clienteId").classList.contains("invalid-field"));
}

function renderClientRows(): void {
  clientRows.replaceChildren();
  const filter = normalizeText(clientFilter.value);
  const visibles = clientes.filter((cliente) => !filter || normalizeText(cliente.denominacion).includes(filter));
  if (visibles.length === 0) {
    const row = document.createElement("tr");
    const cell = document.createElement("td");
    cell.colSpan = 2;
    cell.className = "table-message";
    cell.textContent = "No hay clientes que coincidan con el filtro.";
    row.append(cell);
    clientRows.append(row);
  }
  for (const cliente of visibles) {
    const row = document.createElement("tr");
    const selectionCell = document.createElement("td");
    const radio = document.createElement("input");
    radio.type = "radio";
    radio.name = "client-choice";
    radio.value = String(cliente.id);
    radio.checked = selectedClientId === cliente.id;
    radio.setAttribute("aria-label", `Seleccionar ${cliente.denominacion}`);
    radio.addEventListener("change", () => {
      selectedClientId = cliente.id;
      confirmClientSelection.disabled = false;
    });
    selectionCell.append(radio);
    row.append(selectionCell);
    appendCell(row, cliente.denominacion);
    clientRows.append(row);
  }
  confirmClientSelection.disabled = selectedClientId === null;
}

function openClientExplorer(): void {
  if (clientes.length === 0) {
    setActionNotice("No se pudieron cargar los clientes. Reintentá la carga de catálogos.", "warning");
    return;
  }
  const current = Number(select("clienteId").value);
  selectedClientId = Number.isSafeInteger(current) && current > 0 ? current : null;
  clientFilter.value = "";
  clientExplorerMessage.textContent = "Elige un cliente de la lista.";
  renderClientRows();
  clientExplorer.showModal();
  clientFilter.focus();
}

function applyClientSelection(): void {
  if (selectedClientId === null) return;
  select("clienteId").value = String(selectedClientId);
  select("clienteId").classList.remove("invalid-field");
  refreshClientButton();
  formDirty = true;
  hideFormMessage();
  clientExplorer.close();
}

function setTraceCollapsed(collapsed: boolean): void {
  tracePanel.classList.toggle("collapsed", collapsed);
  traceTitle.setAttribute("aria-expanded", collapsed ? "false" : "true");
}

function toggleTrace(): void {
  setTraceCollapsed(!tracePanel.classList.contains("collapsed"));
}

function renderPendingRows(): void {
  arcaRows.replaceChildren();
  for (const invoice of pendingInvoices) {
    const row = document.createElement("tr");
    appendCell(row, String(invoice.numeroFactura));
    appendCell(row, invoice.fechaEmision ?? "—");
    appendCell(row, invoice.clienteDenominacion);
    appendCell(row, money(invoice.importeTotal), "numeric");
    const stateCell = document.createElement("td");
    stateCell.append(textElement("span", invoice.estado, "status-pill"));
    row.append(stateCell);
    arcaRows.append(row);
  }
  sendArcaButton.disabled = arcaSending || arcaLoading || pendingInvoices.length === 0;
}

async function loadPendingInvoices(): Promise<void> {
  const version = ++arcaLoadVersion;
  arcaLoading = true;
  pendingInvoices = [];
  arcaRows.replaceChildren();
  retryArcaButton.hidden = true;
  sendArcaButton.disabled = true;
  arcaMessage.textContent = "Cargando facturas pendientes…";
  try {
    const todas: FacturaReporteDTO[] = [];
    let page = 0;
    let totalPaginas = 1;
    do {
      const result = await service.obtenerFacturasPendientes({ page });
      if (version !== arcaLoadVersion) return;
      todas.push(...result.contenido);
      totalPaginas = result.totalPaginas;
      page += 1;
    } while (page < totalPaginas);
    if (version !== arcaLoadVersion) return;
    pendingInvoices = todas;
    arcaLoading = false;
    arcaMessage.textContent = pendingInvoices.length === 0
      ? "No hay facturas pendientes de envío a ARCA."
      : `${pendingInvoices.length} factura(s) pendiente(s) de envío a ARCA.`;
    renderPendingRows();
  } catch (error) {
    if (version !== arcaLoadVersion) return;
    arcaLoading = false;
    pendingInvoices = [];
    arcaRows.replaceChildren();
    arcaMessage.textContent = `No se pudieron cargar las facturas pendientes: ${errorMessage(error)}`;
    retryArcaButton.hidden = false;
    sendArcaButton.disabled = true;
  }
}

function openArcaExplorer(): void {
  arcaResult.hidden = true;
  arcaResult.replaceChildren();
  arcaExplorer.showModal();
  requiredElement<HTMLButtonElement>("close-arca-explorer").focus();
  void loadPendingInvoices();
}

function arcaResultClass(resultado: string): string {
  switch (resultado) {
    case "APROBADA": return "send-result-success";
    case "RECHAZADA": return "send-result-error";
    case "INCIERTO": return "send-result-warning";
    default: return "send-result-muted";
  }
}

function renderArcaResult(result: EnvioArcaProcesoDTO): void {
  arcaResult.replaceChildren();
  const summary = textElement(
    "p",
    `Proceso ${result.envioId}: ${result.procesadas} procesada(s), ${result.autorizadas} autorizada(s), `
    + `${result.rechazadas} rechazada(s), ${result.resultadoDesconocido} con resultado desconocido, `
    + `${result.noEnviadasPorValidacion} no enviada(s) por validación.`,
    "send-summary",
  );
  arcaResult.append(summary);

  if (result.detalle.length === 0) {
    arcaResult.append(textElement("p", "El backend no devolvió detalle por factura.", "send-result-muted"));
  }
  for (const item of result.detalle) {
    const entry = document.createElement("div");
    entry.className = `send-result-item ${arcaResultClass(item.resultado)}`;
    const numero = item.numeroInterno ?? item.facturaId ?? "—";
    entry.append(textElement("strong", `Factura ${numero}: ${item.resultado}`));
    if (item.estado) entry.append(textElement("span", `Estado actual: ${item.estado}`));
    if (item.cae) entry.append(textElement("span", `CAE: ${item.cae}`));
    if (item.mensaje) entry.append(textElement("span", item.mensaje));
    arcaResult.append(entry);
  }

  if (result.resultadoDesconocido > 0) {
    arcaResult.append(textElement(
      "p",
      "Las facturas con resultado desconocido no se reenvían automáticamente. El backend debe resolver su estado mediante conciliación.",
      "send-result-warning",
    ));
  }
  arcaResult.hidden = false;
}

async function sendPendingToArca(): Promise<void> {
  if (arcaSending) return;
  if (pendingInvoices.length === 0) {
    arcaMessage.textContent = "No hay facturas pendientes para enviar a ARCA.";
    return;
  }
  if (!window.confirm(`Se enviarán ${pendingInvoices.length} factura(s) a ARCA. ¿Continuar?`)) return;

  arcaSending = true;
  sendArcaButton.disabled = true;
  sendArcaButton.textContent = "Enviando…";
  arcaMessage.textContent = "Procesando el envío a ARCA…";
  try {
    const result = await service.enviarFacturasAArca(pendingInvoices.map((invoice) => invoice.id));
    renderArcaResult(result);
    arcaMessage.textContent = "Envío finalizado. Revisá el resultado por factura.";
    await loadPendingInvoices();
    await loadInvoices(currentFilters, facturaPagina.pagina);
  } catch (error) {
    arcaMessage.textContent = `No se pudo completar el envío a ARCA: ${errorMessage(error)}`;
  } finally {
    arcaSending = false;
    sendArcaButton.textContent = "Envio a ARCA";
    sendArcaButton.disabled = arcaLoading || pendingInvoices.length === 0;
  }
}

function pdfTarget(): string {
  return document.querySelector<HTMLInputElement>('input[name="pdf-target"]:checked')?.value ?? "individual";
}

function updatePdfControls(): void {
  const individual = pdfTarget() === "individual";
  pdfInvoiceSelect.disabled = !individual;
  printPdfButton.disabled = pdfPrinting || (individual && !pdfInvoiceSelect.value);
}


async function openPdfExplorer(){
  pdfMessage.textContent = "Cargando facturas disponibles…";
  pdfInvoiceSelect.replaceChildren();
  pdfInvoiceSelect.disabled = true;
  printPdfButton.disabled = true;

  pdfExplorer.showModal();
  requiredElement<HTMLButtonElement>("close-pdf-explorer").focus();

  try{
    const pdfInvoices = await service.listarFacturasAutorizadas();
    pdfMessage.textContent = "";

  for (const invoice of pdfInvoices) {
    pdfInvoiceSelect.add(new Option(
      `${invoice.numeroFactura} — ${invoice.clienteDenominacion} — ${invoice.estado}`,
      String(invoice.id),
    ));
  }
  if (pdfInvoices.length === 0) {
    pdfMessage.textContent = "No hay facturas en la página actual. Podés imprimir el listado completo.";
  }
  const individualRadio = document.querySelector<HTMLInputElement>('input[name="pdf-target"][value="individual"]');
  const listadoRadio = document.querySelector<HTMLInputElement>('input[name="pdf-target"][value="listado"]');
  if (pdfInvoices.length === 0 && listadoRadio) {
    listadoRadio.checked = true;
  } else if (individualRadio) {
    individualRadio.checked = true;
  }
  }catch (error){
    pdfMessage.textContent = `Error al cargar las facturas: ${errorMessage(error)}`;
    } finally {
        updatePdfControls();
    }
  
}

function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function printPdf(): Promise<void> {
  if (pdfPrinting) return;
  const target = pdfTarget();
  const invoiceId = Number(pdfInvoiceSelect.value);
  if (target === "individual" && (!Number.isSafeInteger(invoiceId) || invoiceId <= 0)) {
    pdfMessage.textContent = "Selecciona una factura válida para imprimir.";
    return;
  }

  pdfPrinting = true;
  printPdfButton.disabled = true;
  printPdfButton.textContent = "Generando…";
  pdfMessage.textContent = "Solicitando el PDF al backend…";
  try {
    const pdf = target === "individual"
      ? await service.descargarPdfFactura(invoiceId)
      : await service.descargarPdfListado();
    downloadBlob(pdf.blob, pdf.filename);
    pdfMessage.textContent = "PDF generado y descargado.";
    setActionNotice(`PDF generado: ${pdf.filename}`, "success");
    pdfExplorer.close();
  } catch (error) {
    pdfMessage.textContent = `No se pudo generar el PDF: ${errorMessage(error)}`;
  } finally {
    pdfPrinting = false;
    printPdfButton.textContent = "Imprimir PDF";
    updatePdfControls();
  }
}

filtersForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const filters = readFilters();
  if (filters) void loadInvoices(filters);
});

requiredElement<HTMLButtonElement>("clear-filters").addEventListener("click", () => {
  filtersForm.reset();
  void loadInvoices({}, 0);
});
requiredElement<HTMLButtonElement>("new-invoice").addEventListener("click", () => showForm());
requiredElement<HTMLButtonElement>("print-pdf").addEventListener("click", openPdfExplorer);
requiredElement<HTMLButtonElement>("preview-arca").addEventListener("click", openArcaExplorer);
requiredElement<HTMLButtonElement>("back-to-list").addEventListener("click", discardForm);
requiredElement<HTMLButtonElement>("cancel-form").addEventListener("click", discardForm);
requiredElement<HTMLButtonElement>("add-item").addEventListener("click", openArticleExplorer);
requiredElement<HTMLButtonElement>("close-article-explorer").addEventListener("click", () => articleExplorer.close());
requiredElement<HTMLButtonElement>("cancel-article-selection").addEventListener("click", () => articleExplorer.close());
confirmArticleSelection.addEventListener("click", confirmArticles);
retryArticlesButton.addEventListener("click", () => void loadArticlesForSelectedList());
requiredElement<HTMLButtonElement>("close-arca-explorer").addEventListener("click", () => arcaExplorer.close());
retryArcaButton.addEventListener("click", () => void loadPendingInvoices());
sendArcaButton.addEventListener("click", () => void sendPendingToArca());
requiredElement<HTMLButtonElement>("close-pdf-explorer").addEventListener("click", () => pdfExplorer.close());
printPdfButton.addEventListener("click", () => void printPdf());
document.querySelectorAll<HTMLInputElement>('input[name="pdf-target"]').forEach((radio) => {
  radio.addEventListener("change", updatePdfControls);
});
previousPageButton.addEventListener("click", () => {
  if (facturaPagina.pagina > 0) void loadInvoices(currentFilters, facturaPagina.pagina - 1);
});
nextPageButton.addEventListener("click", () => {
  if (facturaPagina.pagina + 1 < facturaPagina.totalPaginas) {
    void loadInvoices(currentFilters, facturaPagina.pagina + 1);
  }
});
select("listaPrecioId").addEventListener("change", () => {
  const nextId = select("listaPrecioId").value;
  if (details.length > 0 && nextId !== selectedListaPrecioId) {
    if (!window.confirm("Cambiar la Lista Precio eliminará los detalles agregados. ¿Continuar?")) {
      select("listaPrecioId").value = selectedListaPrecioId;
      return;
    }
    details = [];
    renderDetailRows();
  }
  selectedListaPrecioId = nextId;
  articleRequestVersion += 1;
  articulosLoading = false;
  articulosSeleccionados.clear();
  articulosLista = [];
  formDirty = true;
  hideFormMessage();
});
requiredElement<HTMLButtonElement>("retry-catalogs").addEventListener("click", () => void loadCatalogs());
retryCatalogsFormButton.addEventListener("click", () => void loadCatalogs());
requiredElement<HTMLButtonElement>("clear-traces").addEventListener("click", () => {
  latestTraceItems = [];
  renderTraces();
});
traceHeading.addEventListener("click", (event) => {
  const target = event.target;
  if (target instanceof Element && target.closest("#clear-traces")) return;
  toggleTrace();
});
traceTitle.addEventListener("keydown", (event) => {
  if (event.key !== "Enter" && event.key !== " ") return;
  event.preventDefault();
  toggleTrace();
});
articleFilterName.addEventListener("input", renderArticleRows);
articleFilterRubro.addEventListener("change", renderArticleRows);
chooseClientButton.addEventListener("click", openClientExplorer);
requiredElement<HTMLButtonElement>("close-client-explorer").addEventListener("click", () => clientExplorer.close());
requiredElement<HTMLButtonElement>("cancel-client-selection").addEventListener("click", () => clientExplorer.close());
confirmClientSelection.addEventListener("click", applyClientSelection);
clientFilter.addEventListener("input", renderClientRows);
retryButton.addEventListener("click", () => void loadInvoices(currentFilters));
invoiceForm.addEventListener("submit", (event) => void saveInvoice(event as SubmitEvent));
invoiceForm.addEventListener("input", (event) => {
  formDirty = true;
  const target = event.target;
  if (target instanceof HTMLInputElement && target.dataset.field) {
    const detailRow = target.closest("tr");
    const detailIndex = detailRow ? Array.from(detailRows.rows).indexOf(detailRow as HTMLTableRowElement) : -1;
    const field = target.dataset.field as keyof DetailDraft;
    if (detailIndex >= 0 && details[detailIndex]) {
      details[detailIndex]![field] = target.value;
      const subtotal = detailRow?.querySelector<HTMLElement>('[data-subtotal="true"]');
      const amounts = lineAmounts(details[detailIndex]!);
      if (subtotal) subtotal.textContent = amounts.error ? "—" : money(amounts.subtotal);
      updateEstimatedTotal();
    }
  }
  hideFormMessage();
});
invoiceForm.addEventListener("change", () => {
  formDirty = true;
  hideFormMessage();
});
detailRows.addEventListener("click", (event) => {
  const target = event.target;
  if (!(target instanceof HTMLButtonElement) || target.dataset.removeIndex === undefined) return;
  const index = Number(target.dataset.removeIndex);
  if (!Number.isInteger(index) || index < 0 || index >= details.length) return;
  details.splice(index, 1);
  formDirty = true;
  renderDetailRows();
});
rows.addEventListener("click", (event) => {
  const target = event.target;
  if (!(target instanceof Element)) return;
  const button = target.closest("button");
  if (!(button instanceof HTMLButtonElement)) return;
  const index = Number(button.dataset.index);
  const invoice = invoices[index];
  if (!invoice) return;
  if (button.dataset.action === "details") {
    if (expandedInvoices.has(invoice.id)) expandedInvoices.delete(invoice.id);
    else expandedInvoices.add(invoice.id);
    renderInvoiceList();
    return;
  }
  if (button.dataset.action === "edit") {
    startEdit(invoice);
    return;
  }
  if (button.dataset.action === "delete") {
    void annulInvoice(invoice, button);
    return;
  }
  if (button.dataset.action === "arca") {
    void sendInvoiceToArca(invoice, button);
  }
});

renderTraces();
apiNotice.hidden = true;
void loadCatalogs();
void loadRubros();
void loadInvoices({});
