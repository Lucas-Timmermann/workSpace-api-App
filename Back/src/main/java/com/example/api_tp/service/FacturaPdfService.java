package com.example.api_tp.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.api_tp.config.ArcaProperties;
import com.example.api_tp.model.Contacto;
import com.example.api_tp.model.Domicilio;
import com.example.api_tp.model.EstadoFactura;
import com.example.api_tp.model.FacturaVenta;
import com.example.api_tp.model.FacturaVentaDetalle;
import com.example.api_tp.repository.FacturaVentaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FacturaPdfService {

    private static final float MARGEN = 42f;
    private static final float ANCHO_LINEA = PDRectangle.LETTER.getWidth() - (MARGEN * 2);
    private static final float ALTO_PIE = 18f;
    private static final float ALTO_ENCABEZADO_TABLA = 16f;
    private static final float ALTO_LINEA_TABLA = 12f;
    private static final float PADDING_CELDA = 3f;
    private static final float ANCHO_BLOQUE = 250f;
    private static final float ANCHO_TOTALES = 240f;
    private static final float[] COLUMNAS = {22f, 198f, 46f, 82f, 46f, 40f, 94f};
    private static final String[] TITULOS_TABLA = {
            "#", "Descripción", "Cant.", "P. Unit.", "Bonif.", "IVA", "Subtotal"};
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withLocale(new Locale("es", "AR")).withZone(ZoneOffset.UTC);
    private static final Locale LOCALE_AR = Locale.forLanguageTag("es-AR");

    private static final PDColor NEGRO = new PDColor(new float[]{0f, 0f, 0f}, PDDeviceRGB.INSTANCE);
    private static final PDColor GRIS_ENCABEZADO = new PDColor(
            new float[]{0.85f, 0.85f, 0.85f}, PDDeviceRGB.INSTANCE);
    private static final PDColor GRIS_FILA = new PDColor(
            new float[]{0.96f, 0.96f, 0.96f}, PDDeviceRGB.INSTANCE);
    private static final PDColor GRIS_BORDE = new PDColor(
            new float[]{0.75f, 0.75f, 0.75f}, PDDeviceRGB.INSTANCE);
    private static final PDColor ROJO_RECHAZO = new PDColor(
            new float[]{0.55f, 0f, 0f}, PDDeviceRGB.INSTANCE);

    private final FacturaVentaRepository facturaRepository;
    private final FacturaEstadoMachine estadoMachine;
    private final ArcaProperties arcaProperties;

    @Transactional(readOnly = true)
    public byte[] generarFacturas() {
        List<FacturaVenta> facturas = facturaRepository.buscarTodasConDetallesParaPdf();
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(documento);
            writer.linea("Registro de facturas", true, 16f);
            writer.linea("Facturas registradas: " + facturas.size(), false, 10f);
            writer.espacio(8f);

            if (facturas.isEmpty()) {
                writer.linea("No hay facturas registradas.", false, 10f);
            } else {
                for (int i = 0; i < facturas.size(); i++) {
                    if (i > 0) {
                        writer.nuevaPagina();
                    }
                    writer.factura(facturas.get(i), arcaProperties.getCuit());
                }
            }
            writer.cerrarPagina();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el PDF de facturas.", exception);
        }
    }

    @Transactional(readOnly = true)
    public byte[] generarFacturaAutorizada(Long facturaId) {
        FacturaVenta factura = facturaRepository.buscarPorIdConDetalles(facturaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Factura con ID " + facturaId + " no existe"));
        EstadoFactura estado = estadoMachine.parsear(factura.getEstado());
        if (estado != EstadoFactura.AUTORIZADA) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Solo se puede generar PDF de facturas AUTORIZADA");
        }
        if (factura.getCae() == null || factura.getCae().isBlank() || factura.getCaeFechaVencimiento() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La factura autorizada no tiene CAE o vencimiento registrado");
        }
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(documento);
            writer.linea("Comprobante autorizado", true, 16f);
            writer.espacio(4f);
            writer.factura(factura, arcaProperties.getCuit());
            writer.cerrarPagina();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo generar el PDF de la factura autorizada.", exception);
        }
    }

    private static String fecha(Date fecha) {
        return fecha == null ? "-" : FECHA.format(fecha.toInstant());
    }

    private static String importe(double importe) {
        return String.format(LOCALE_AR, "$ %,.2f", importe);
    }

    private static String valor(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private static String cuit(String valor) {
        String digitos = valor == null ? "" : valor.replaceAll("\\D", "");
        if (digitos.length() == 11) {
            return digitos.substring(0, 2) + "-" + digitos.substring(2, 10) + "-" + digitos.substring(10);
        }
        return valor(valor);
    }

    private static String domicilio(Domicilio domicilio) {
        if (domicilio == null) {
            return "-";
        }
        String calle = domicilio.getNombreCalle() == null ? "" : domicilio.getNombreCalle().trim();
        String numero = domicilio.getNumeroCalle() == null ? "" : domicilio.getNumeroCalle().trim();
        String texto = (calle + " " + numero).trim();
        return texto.isEmpty() ? "-" : texto;
    }

    private static String contacto(Contacto contacto) {
        if (contacto == null) {
            return "-";
        }
        List<String> partes = new ArrayList<>();
        if (contacto.getEmail() != null && !contacto.getEmail().isBlank()) {
            partes.add(contacto.getEmail());
        }
        if (contacto.getTelefono() != null && !contacto.getTelefono().isBlank()) {
            partes.add(contacto.getTelefono());
        }
        return partes.isEmpty() ? "-" : String.join(" / ", partes);
    }

    private static final class PdfWriter {
        private final PDDocument documento;
        private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;
        private int pagina;

        private PdfWriter(PDDocument documento) throws IOException {
            this.documento = documento;
            nuevaPagina();
        }

        private void factura(FacturaVenta factura, String cuitEmisor) throws IOException {
            encabezadoFactura(factura);
            bloquesPartes(factura, cuitEmisor);
            tablaDetalle(factura);
            bloqueTotales(factura);
            bloqueFiscal(factura);
        }

        private void encabezadoFactura(FacturaVenta factura) throws IOException {
            comprobarEspacio(56f);
            texto("Factura " + factura.getNumero(), MARGEN, y, negrita, 16f);
            float derecha = MARGEN + ANCHO_LINEA;
            textoDerecha("Fecha: " + fecha(factura.getFechaEmision()), derecha, y, normal, 9f);
            textoDerecha("Nro. fiscal: " + valor(factura.getNumeroFiscal() == null
                            ? null : String.valueOf(factura.getNumeroFiscal())),
                    derecha, y - 12f, normal, 9f);
            textoDerecha("Tipo: " + tipoComprobante(factura.getTipoComprobanteArca()),
                    derecha, y - 24f, normal, 9f);
            textoDerecha("Estado: " + valor(factura.getEstado()), derecha, y - 36f, normal, 9f);
            y -= 48f;
            lineaHorizontal(MARGEN, derecha, y, GRIS_BORDE, 0.8f);
            y -= 10f;
        }

        private void bloquesPartes(FacturaVenta factura, String cuitEmisor) throws IOException {
            float anchoDerecha = ANCHO_LINEA - ANCHO_BLOQUE - 12f;
            List<String> emisor = contenidoBloque("EMISOR", camposEmisor(factura, cuitEmisor), ANCHO_BLOQUE);
            List<String> receptor = contenidoBloque("RECEPTOR", camposReceptor(factura), anchoDerecha);
            float alto = Math.max(altoBloque(emisor), altoBloque(receptor));
            comprobarEspacio(alto + 10f);
            dibujarBloque(MARGEN, ANCHO_BLOQUE, emisor);
            dibujarBloque(MARGEN + ANCHO_BLOQUE + 12f, anchoDerecha, receptor);
            y -= alto + 10f;
        }

        private List<String> camposEmisor(FacturaVenta factura, String cuitEmisor) {
            List<String> campos = new ArrayList<>();
            campos.add("CUIT: " + cuit(cuitEmisor));
            if (factura.getPuntoVenta() != null) {
                campos.add("Punto de venta: " + factura.getPuntoVenta().getNumero()
                        + " - " + valor(factura.getPuntoVenta().getDescripcion()));
                campos.add("Domicilio: " + valor(factura.getPuntoVenta().getDomicilioComercial()));
            }
            return campos;
        }

        private List<String> camposReceptor(FacturaVenta factura) {
            List<String> campos = new ArrayList<>();
            if (factura.getCliente() != null) {
                campos.add(valor(factura.getCliente().getDenominacion()));
                campos.add("CUIT/CUIL: " + cuit(factura.getCliente().getCuitCuil()));
                if (factura.getCliente().getDomicilio() != null) {
                    campos.add("Domicilio: " + domicilio(factura.getCliente().getDomicilio()));
                }
                if (factura.getCliente().getContacto() != null) {
                    campos.add("Contacto: " + contacto(factura.getCliente().getContacto()));
                }
            }
            campos.add("Condición IVA: " + valor(factura.getCondicionIva() == null
                    ? null : factura.getCondicionIva().getDenominacion()));
            campos.add("Moneda: " + valor(factura.getTipoMoneda() == null
                    ? null : factura.getTipoMoneda().getDenominacion()));
            return campos;
        }

        private List<String> contenidoBloque(String titulo, List<String> campos, float ancho) throws IOException {
            List<String> lineas = new ArrayList<>();
            lineas.add(titulo);
            for (String campo : campos) {
                lineas.addAll(ajustar(campo, normal, 9f, ancho - 12f));
            }
            return lineas;
        }

        private float altoBloque(List<String> lineas) {
            return 14f + lineas.size() * 12f + 8f;
        }

        private void dibujarBloque(float x, float ancho, List<String> lineas) throws IOException {
            float alto = altoBloque(lineas);
            rectangulo(x, y - alto, ancho, alto, null, GRIS_BORDE);
            float cursor = y - 14f;
            for (int i = 0; i < lineas.size(); i++) {
                texto(lineas.get(i), x + 6f, cursor, i == 0 ? negrita : normal, 9f);
                cursor -= 12f;
            }
        }

        private void tablaDetalle(FacturaVenta factura) throws IOException {
            comprobarEspacio(ALTO_ENCABEZADO_TABLA + 22f);
            encabezadoTabla();
            List<FacturaVentaDetalle> detalles = factura.getDetalles() == null
                    ? List.<FacturaVentaDetalle>of() : factura.getDetalles();
            if (detalles.isEmpty()) {
                filaSinDetalle();
            } else {
                for (int i = 0; i < detalles.size(); i++) {
                    filaDetalle(i + 1, detalles.get(i));
                }
            }
            y -= 6f;
        }

        private void encabezadoTabla() throws IOException {
            float x = MARGEN;
            rectangulo(x, y - ALTO_ENCABEZADO_TABLA, ANCHO_LINEA, ALTO_ENCABEZADO_TABLA,
                    GRIS_ENCABEZADO, GRIS_BORDE);
            float xLinea = x;
            for (int i = 0; i < COLUMNAS.length - 1; i++) {
                xLinea += COLUMNAS[i];
                lineaVertical(xLinea, y, y - ALTO_ENCABEZADO_TABLA);
            }
            for (int i = 0; i < TITULOS_TABLA.length; i++) {
                float ancho = COLUMNAS[i];
                if (i == 0) {
                    textoCentrado(TITULOS_TABLA[i], x + ancho / 2f, y - 11f, negrita, 8.5f);
                } else if (i == 1) {
                    texto(TITULOS_TABLA[i], x + PADDING_CELDA, y - 11f, negrita, 8.5f);
                } else {
                    textoDerecha(TITULOS_TABLA[i], x + ancho - PADDING_CELDA, y - 11f, negrita, 8.5f);
                }
                x += ancho;
            }
            y -= ALTO_ENCABEZADO_TABLA;
        }

        private void filaDetalle(int indice, FacturaVentaDetalle detalle) throws IOException {
            String descripcion = detalle.getDescripcion() == null ? "Artículo" : detalle.getDescripcion();
            List<String> lineas = ajustar(descripcion, normal, 8.5f, COLUMNAS[1] - 2 * PADDING_CELDA);
            if (lineas.isEmpty()) {
                lineas = List.of("");
            }
            float alto = 14f + ALTO_LINEA_TABLA * (lineas.size() - 1);
            if (y - alto < MARGEN + ALTO_PIE) {
                nuevaPagina();
                encabezadoTabla();
            }
            if (indice % 2 == 0) {
                rectangulo(MARGEN, y - alto, ANCHO_LINEA, alto, GRIS_FILA, null);
            }
            rectangulo(MARGEN, y - alto, ANCHO_LINEA, alto, null, GRIS_BORDE);
            float x = MARGEN;
            for (int i = 0; i < COLUMNAS.length - 1; i++) {
                x += COLUMNAS[i];
                lineaVertical(x, y, y - alto);
            }

            float cursor = y - 10f;
            for (String linea : lineas) {
                texto(linea, MARGEN + COLUMNAS[0] + PADDING_CELDA, cursor, normal, 8.5f);
                cursor -= ALTO_LINEA_TABLA;
            }

            float base = y - 10f;
            textoCentrado(String.valueOf(indice), MARGEN + COLUMNAS[0] / 2f, base, normal, 8.5f);
            float x2 = MARGEN + COLUMNAS[0] + COLUMNAS[1];
            textoDerecha(cantidad(detalle.getCantidad()), x2 + COLUMNAS[2] - PADDING_CELDA,
                    base, normal, 8.5f);
            float x3 = x2 + COLUMNAS[2];
            textoDerecha(importe(detalle.getPrecioUnitario()), x3 + COLUMNAS[3] - PADDING_CELDA,
                    base, normal, 8.5f);
            float x4 = x3 + COLUMNAS[3];
            textoDerecha(porcentaje(detalle.getPorcentajeBonificacion()), x4 + COLUMNAS[4] - PADDING_CELDA,
                    base, normal, 8.5f);
            float x5 = x4 + COLUMNAS[4];
            textoDerecha(porcentaje(detalle.getPorcentajeIva()), x5 + COLUMNAS[5] - PADDING_CELDA,
                    base, normal, 8.5f);
            float x6 = x5 + COLUMNAS[5];
            textoDerecha(importe(detalle.getImporteSubtotal()), x6 + COLUMNAS[6] - PADDING_CELDA,
                    base, normal, 8.5f);
            y -= alto;
        }

        private void filaSinDetalle() throws IOException {
            float alto = 14f;
            if (y - alto < MARGEN + ALTO_PIE) {
                nuevaPagina();
                encabezadoTabla();
            }
            rectangulo(MARGEN, y - alto, ANCHO_LINEA, alto, null, GRIS_BORDE);
            texto("Sin detalle", MARGEN + PADDING_CELDA, y - 10f, normal, 8.5f);
            y -= alto;
        }

        private void bloqueTotales(FacturaVenta factura) throws IOException {
            float alto = 82f;
            comprobarEspacio(alto + 8f);
            float x = MARGEN + ANCHO_LINEA - ANCHO_TOTALES;
            rectangulo(x, y - alto, ANCHO_TOTALES, alto, null, GRIS_BORDE);
            float cursor = y - 14f;
            filaTotal(x, cursor, "Neto", importe(neto(factura)), false);
            cursor -= 13f;
            filaTotal(x, cursor, "IVA", importe(iva(factura)), false);
            cursor -= 13f;
            filaTotal(x, cursor, "Total", importe(factura.getImporteTotal()), true);
            cursor -= 13f;
            filaTotal(x, cursor, "Cobrado", importe(factura.getImporteCobrado()), false);
            cursor -= 13f;
            filaTotal(x, cursor, "Saldo", importe(factura.getImporteSaldo()), false);
            y -= alto + 8f;
        }

        private void filaTotal(float x, float y, String etiqueta, String valor, boolean destacado)
                throws IOException {
            PDType1Font fuente = destacado ? negrita : normal;
            float tamano = destacado ? 10f : 9f;
            texto(etiqueta, x + 10f, y, fuente, tamano);
            textoDerecha(valor, x + ANCHO_TOTALES - 10f, y, fuente, tamano);
        }

        private void bloqueFiscal(FacturaVenta factura) throws IOException {
            List<String> textos = new ArrayList<>();
            List<PDColor> colores = new ArrayList<>();
            agregarLinea(textos, colores, "Resultado AFIP: " + valor(factura.getResultadoAfip())
                    + "    CAE: " + valor(factura.getCae())
                    + "    Vencimiento CAE: " + fecha(factura.getCaeFechaVencimiento()), NEGRO);
            if (factura.getMotivoRechazo() != null && !factura.getMotivoRechazo().isBlank()) {
                agregarLinea(textos, colores, "Motivo de rechazo: " + factura.getMotivoRechazo(),
                        ROJO_RECHAZO);
            }
            if (factura.getObservaciones() != null && !factura.getObservaciones().isBlank()) {
                agregarLinea(textos, colores, "Observaciones: " + factura.getObservaciones(), NEGRO);
            }
            float alto = 12f + textos.size() * 12f + 8f;
            comprobarEspacio(alto + 8f);
            rectangulo(MARGEN, y - alto, ANCHO_LINEA, alto, null, GRIS_BORDE);
            float cursor = y - 14f;
            for (int i = 0; i < textos.size(); i++) {
                texto(textos.get(i), MARGEN + 6f, cursor, normal, 9f, colores.get(i));
                cursor -= 12f;
            }
            y -= alto + 8f;
        }

        private void agregarLinea(List<String> textos, List<PDColor> colores, String texto, PDColor color)
                throws IOException {
            for (String parte : ajustar(texto, normal, 9f, ANCHO_LINEA - 12f)) {
                textos.add(parte);
                colores.add(color);
            }
        }

        private void linea(String texto, boolean destacado, float tamano) throws IOException {
            PDType1Font fuente = destacado ? negrita : normal;
            for (String parte : ajustar(texto, fuente, tamano, ANCHO_LINEA)) {
                comprobarEspacio(tamano + 4f);
                texto(parte, MARGEN, y, fuente, tamano);
                y -= tamano + 3f;
            }
        }

        private void texto(String texto, float x, float y, PDType1Font fuente, float tamano)
                throws IOException {
            String limpio = normalizar(texto);
            if (limpio.isEmpty()) {
                return;
            }
            stream.beginText();
            stream.setFont(fuente, tamano);
            stream.newLineAtOffset(x, y);
            stream.showText(limpio);
            stream.endText();
        }

        private void texto(String texto, float x, float y, PDType1Font fuente, float tamano, PDColor color)
                throws IOException {
            stream.setNonStrokingColor(color);
            texto(texto, x, y, fuente, tamano);
            stream.setNonStrokingColor(NEGRO);
        }

        private void textoDerecha(String texto, float xDerecha, float y, PDType1Font fuente, float tamano)
                throws IOException {
            String limpio = normalizar(texto);
            float ancho = fuente.getStringWidth(limpio) * tamano / 1000f;
            texto(limpio, xDerecha - ancho, y, fuente, tamano);
        }

        private void textoCentrado(String texto, float xCentro, float y, PDType1Font fuente, float tamano)
                throws IOException {
            String limpio = normalizar(texto);
            float ancho = fuente.getStringWidth(limpio) * tamano / 1000f;
            texto(limpio, xCentro - ancho / 2f, y, fuente, tamano);
        }

        private void rectangulo(float x, float y, float ancho, float alto, PDColor relleno, PDColor borde)
                throws IOException {
            if (relleno != null) {
                stream.setNonStrokingColor(relleno);
                stream.addRect(x, y, ancho, alto);
                stream.fill();
            }
            if (borde != null) {
                stream.setStrokingColor(borde);
                stream.setLineWidth(0.5f);
                stream.addRect(x, y, ancho, alto);
                stream.stroke();
            }
            stream.setNonStrokingColor(NEGRO);
            stream.setStrokingColor(NEGRO);
        }

        private void lineaHorizontal(float x1, float x2, float y, PDColor color, float grosor)
                throws IOException {
            stream.setStrokingColor(color);
            stream.setLineWidth(grosor);
            stream.moveTo(x1, y);
            stream.lineTo(x2, y);
            stream.stroke();
            stream.setStrokingColor(NEGRO);
        }

        private void lineaVertical(float x, float y1, float y2) throws IOException {
            stream.setStrokingColor(GRIS_BORDE);
            stream.setLineWidth(0.5f);
            stream.moveTo(x, y1);
            stream.lineTo(x, y2);
            stream.stroke();
            stream.setStrokingColor(NEGRO);
        }

        private void espacio(float cantidad) throws IOException {
            comprobarEspacio(cantidad);
            y -= cantidad;
        }

        private void comprobarEspacio(float requerido) throws IOException {
            if (y - requerido < MARGEN + ALTO_PIE) {
                nuevaPagina();
            }
        }

        private void nuevaPagina() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage paginaPdf = new PDPage(PDRectangle.LETTER);
            documento.addPage(paginaPdf);
            stream = new PDPageContentStream(documento, paginaPdf);
            pagina++;
            y = paginaPdf.getMediaBox().getHeight() - MARGEN;
            piePagina();
        }

        private void piePagina() throws IOException {
            float yPie = MARGEN - 4f;
            lineaHorizontal(MARGEN, MARGEN + ANCHO_LINEA, yPie, GRIS_BORDE, 0.5f);
            textoCentrado("Página " + pagina, MARGEN + ANCHO_LINEA / 2f, yPie - 10f, normal, 8f);
        }

        private void cerrarPagina() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        private static List<String> ajustar(String texto, PDType1Font fuente, float tamano, float ancho)
                throws IOException {
            List<String> lineas = new ArrayList<>();
            StringBuilder linea = new StringBuilder();
            for (String palabra : normalizar(texto).split("\\s+")) {
                if (palabra.isEmpty()) {
                    continue;
                }
                if (ancho(fuente, tamano, palabra) > ancho) {
                    if (!linea.isEmpty()) {
                        lineas.add(linea.toString());
                        linea.setLength(0);
                    }
                    List<String> trozos = partirPalabra(palabra, fuente, tamano, ancho);
                    for (int i = 0; i < trozos.size(); i++) {
                        if (i == trozos.size() - 1) {
                            linea.append(trozos.get(i));
                        } else {
                            lineas.add(trozos.get(i));
                        }
                    }
                    continue;
                }
                String candidato = linea.isEmpty() ? palabra : linea + " " + palabra;
                if (!linea.isEmpty() && ancho(fuente, tamano, candidato) > ancho) {
                    lineas.add(linea.toString());
                    linea.setLength(0);
                    linea.append(palabra);
                } else {
                    if (!linea.isEmpty()) {
                        linea.append(' ');
                    }
                    linea.append(palabra);
                }
            }
            if (!linea.isEmpty()) {
                lineas.add(linea.toString());
            }
            return lineas;
        }

        private static List<String> partirPalabra(String palabra, PDType1Font fuente, float tamano,
                float ancho) throws IOException {
            List<String> partes = new ArrayList<>();
            StringBuilder actual = new StringBuilder();
            for (int i = 0; i < palabra.length(); i++) {
                char caracter = palabra.charAt(i);
                String candidato = actual.toString() + caracter;
                if (!actual.isEmpty() && ancho(fuente, tamano, candidato) > ancho) {
                    partes.add(actual.toString());
                    actual.setLength(0);
                }
                actual.append(caracter);
            }
            if (!actual.isEmpty()) {
                partes.add(actual.toString());
            }
            return partes;
        }

        private static float ancho(PDType1Font fuente, float tamano, String texto) throws IOException {
            return fuente.getStringWidth(texto) * tamano / 1000f;
        }

        private static String normalizar(String texto) {
            if (texto == null) {
                return "";
            }
            StringBuilder resultado = new StringBuilder(texto.length());
            texto.codePoints().forEach(codigo -> {
                if (codigo == '\u2022') {
                    resultado.append('-');
                } else if (codigo == '\u2500') {
                    resultado.append('-');
                } else if (codigo >= 32 && codigo <= 255) {
                    resultado.appendCodePoint(codigo);
                } else {
                    resultado.append('?');
                }
            });
            return resultado.toString();
        }

        private static String cantidad(double valor) {
            return String.format(LOCALE_AR, "%,.2f", valor);
        }

        private static String porcentaje(double valor) {
            return String.format(LOCALE_AR, "%.0f%%", valor);
        }
    }

    private static double neto(FacturaVenta factura) {
        return factura.getDetalles() == null ? 0d
                : factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteNeto).sum();
    }

    private static double iva(FacturaVenta factura) {
        return factura.getDetalles() == null ? 0d
                : factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteIva).sum();
    }

    private static String tipoComprobante(Integer codigo) {
        if (codigo == null) {
            return "-";
        }
        return switch (codigo) {
            case 1 -> "Factura A";
            case 6 -> "Factura B";
            case 11 -> "Factura C";
            default -> "Comprobante " + codigo;
        };
    }
}
