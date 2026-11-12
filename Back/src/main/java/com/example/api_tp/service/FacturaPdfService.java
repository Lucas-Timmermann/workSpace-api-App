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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.api_tp.config.ArcaProperties;
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
    private static final float INTERLINEADO = 12f;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withLocale(new Locale("es", "AR")).withZone(ZoneOffset.UTC);

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
                for (FacturaVenta factura : facturas) {
                    writer.factura(factura, arcaProperties.getCuit());
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
        return String.format(Locale.forLanguageTag("es-AR"), "$ %,.2f", importe);
    }

    private static String valor(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private static final class PdfWriter {
        private final PDDocument documento;
        private final PDType1Font normal = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDType1Font negrita = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private PDPageContentStream stream;
        private float y;

        private PdfWriter(PDDocument documento) throws IOException {
            this.documento = documento;
            nuevaPagina();
        }

        private void factura(FacturaVenta factura, String cuitEmisor) throws IOException {
            comprobarEspacio(76f);
            linea("Factura " + factura.getNumero() + "   |   Emisión: " + fecha(factura.getFechaEmision()),
                    true, 11f);
            linea("Emisor CUIT: " + valor(cuitEmisor)
                    + "   |   Tipo: " + tipoComprobante(factura.getTipoComprobanteArca())
                    + "   |   Punto de venta: " + puntoVenta(factura)
                    + "   |   Nro. fiscal: " + valor(factura.getNumeroFiscal() == null
                            ? null : String.valueOf(factura.getNumeroFiscal())), false, 9f);
            linea("Cliente: " + factura.getCliente().getDenominacion()
                    + "   |   Punto de venta: " + factura.getPuntoVenta().getDescripcion(), false, 9f);
            linea("Condición IVA: " + factura.getCondicionIva().getDenominacion()
                    + "   |   Moneda: " + factura.getTipoMoneda().getDenominacion()
                    + "   |   Estado: " + factura.getEstado(), false, 9f);
            linea("Neto: " + importe(neto(factura)) + "   |   IVA: " + importe(iva(factura))
                    + "   |   Total: " + importe(factura.getImporteTotal())
                    + "   |   Cobrado: " + importe(factura.getImporteCobrado())
                    + "   |   Saldo: " + importe(factura.getImporteSaldo()), true, 9f);
            linea("Resultado AFIP: " + valor(factura.getResultadoAfip())
                    + "   |   CAE: " + valor(factura.getCae())
                    + "   |   Vencimiento CAE: " + fecha(factura.getCaeFechaVencimiento()), false, 9f);
            if (factura.getMotivoRechazo() != null && !factura.getMotivoRechazo().isBlank()) {
                linea("Motivo de rechazo: " + factura.getMotivoRechazo(), false, 9f);
            }
            if (factura.getObservaciones() != null && !factura.getObservaciones().isBlank()) {
                linea("Observaciones: " + factura.getObservaciones(), false, 9f);
            }
            linea("Detalle:", true, 9f);
            for (FacturaVentaDetalle detalle : factura.getDetalles()) {
                String descripcion = detalle.getDescripcion() == null
                        ? "Artículo" : detalle.getDescripcion();
                String texto = String.format(Locale.ROOT,
                        "• %s | Cant.: %.2f | Unit.: %s | Bonif.: %.0f%% | IVA: %.0f%% | Subtotal: %s",
                        descripcion, detalle.getCantidad(), importe(detalle.getPrecioUnitario()),
                        detalle.getPorcentajeBonificacion(), detalle.getPorcentajeIva(),
                        importe(detalle.getImporteSubtotal()));
                linea(texto, false, 8.5f);
            }
            espacio(7f);
            linea("────────────────────────────────────────────────────────────────────────", false, 8f);
            espacio(5f);
        }

        private void linea(String texto, boolean destacado, float tamano) throws IOException {
            PDType1Font fuente = destacado ? negrita : normal;
            for (String parte : ajustar(texto, fuente, tamano, ANCHO_LINEA)) {
                comprobarEspacio(INTERLINEADO + 2f);
                stream.beginText();
                stream.setFont(fuente, tamano);
                stream.newLineAtOffset(MARGEN, y);
                stream.showText(parte);
                stream.endText();
                y -= INTERLINEADO;
            }
        }

        private void espacio(float cantidad) throws IOException {
            comprobarEspacio(cantidad);
            y -= cantidad;
        }

        private void comprobarEspacio(float requerido) throws IOException {
            if (y - requerido < MARGEN) nuevaPagina();
        }

        private void nuevaPagina() throws IOException {
            if (stream != null) stream.close();
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);
            stream = new PDPageContentStream(documento, pagina);
            y = pagina.getMediaBox().getHeight() - MARGEN;
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
                String candidato = linea.isEmpty() ? palabra : linea + " " + palabra;
                if (!linea.isEmpty() && fuente.getStringWidth(candidato) * tamano / 1000f > ancho) {
                    lineas.add(linea.toString());
                    linea.setLength(0);
                    linea.append(palabra);
                } else {
                    if (!linea.isEmpty()) linea.append(' ');
                    linea.append(palabra);
                }
            }
            if (!linea.isEmpty()) lineas.add(linea.toString());
            return lineas;
        }

        private static String normalizar(String texto) {
            StringBuilder resultado = new StringBuilder(texto.length());
            texto.codePoints().forEach(codigo -> {
                if (codigo == '\u2022') resultado.append("-");
                else if (codigo == '\u2500') resultado.append("-");
                else if (codigo >= 32 && codigo <= 255) resultado.appendCodePoint(codigo);
                else resultado.append('?');
            });
            return resultado.toString();
        }
    }

    private static double neto(FacturaVenta factura) {
        return factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteNeto).sum();
    }

    private static double iva(FacturaVenta factura) {
        return factura.getDetalles().stream().mapToDouble(FacturaVentaDetalle::getImporteIva).sum();
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

    private static String puntoVenta(FacturaVenta factura) {
        return factura.getPuntoVenta() == null ? "-" : String.valueOf(factura.getPuntoVenta().getNumero());
    }
}