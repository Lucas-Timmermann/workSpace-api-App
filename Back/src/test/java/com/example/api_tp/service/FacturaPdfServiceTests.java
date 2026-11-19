package com.example.api_tp.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import com.example.api_tp.config.ArcaProperties;
import com.example.api_tp.model.Cliente;
import com.example.api_tp.model.CondicionIva;
import com.example.api_tp.model.Contacto;
import com.example.api_tp.model.Domicilio;
import com.example.api_tp.model.FacturaVenta;
import com.example.api_tp.model.FacturaVentaDetalle;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.repository.FacturaVentaRepository;

class FacturaPdfServiceTests {

    private static final String CAE = "86471089828489";

    @Test
    void paginaElDetalleLargoYConservaElUltimoArticulo() throws Exception {
        List<FacturaVentaDetalle> detalles = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            detalles.add(detalle("Articulo " + i));
        }

        byte[] pdf = servicio(List.of(factura(1L, detalles))).generarFacturas();

        assertTrue(paginas(pdf) >= 2);
        assertTrue(texto(pdf).contains("Articulo 30"));
    }

    @Test
    void ajustaDescripcionLargaSinPerderTexto() throws Exception {
        String descripcion = "Inicio " + "palabra ".repeat(40) + "Fin";

        byte[] pdf = servicio(List.of(factura(2L, List.of(detalle(descripcion))))).generarFacturas();

        String texto = texto(pdf);
        assertTrue(texto.contains("Inicio"));
        assertTrue(texto.contains("Fin"));
    }

    @Test
    void muestraSinDetalleCuandoLaFacturaNoTieneItems() throws Exception {
        byte[] pdf = servicio(List.of(factura(3L, List.of()))).generarFacturas();

        assertTrue(texto(pdf).contains("Sin detalle"));
    }

    @Test
    void muestraMotivoDeRechazo() throws Exception {
        FacturaVenta factura = factura(4L, List.of(detalle("Articulo")));
        factura.setEstado("RECHAZADA");
        factura.setMotivoRechazo("10016: fecha invalida");

        byte[] pdf = servicio(List.of(factura)).generarFacturas();

        assertTrue(texto(pdf).contains("10016: fecha invalida"));
    }

    @Test
    void generaPdfIndividualConCae() throws Exception {
        FacturaVenta factura = factura(5L, List.of(detalle("Articulo")));
        factura.setCae(CAE);
        factura.setCaeFechaVencimiento(new Date());

        FacturaVentaRepository repositorio = mock(FacturaVentaRepository.class);
        when(repositorio.buscarPorIdConDetalles(5L)).thenReturn(Optional.of(factura));
        FacturaPdfService servicio = new FacturaPdfService(repositorio, new FacturaEstadoMachine(), arca());

        byte[] pdf = servicio.generarFacturaAutorizada(5L);

        assertTrue(texto(pdf).contains(CAE));
    }

    @Test
    void reporteDeVariasFacturasEmpiezaCadaUnaEnUnaPagina() throws Exception {
        List<FacturaVenta> facturas = new ArrayList<>();
        for (long numero = 101; numero <= 112; numero++) {
            facturas.add(factura(numero, List.of(detalle("Articulo " + numero))));
        }

        byte[] pdf = servicio(facturas).generarFacturas();

        assertTrue(paginas(pdf) >= 12);
        String texto = texto(pdf);
        for (long numero = 101; numero <= 112; numero++) {
            assertTrue(texto.contains("Factura " + numero));
        }
    }

    @Test
    void imprimeAcentosYEnie() throws Exception {
        FacturaVenta factura = factura(6L, List.of(detalle("Artículo Ñandú")));
        factura.setCliente(new Cliente("20123456789", "Año Nuevo",
                new Contacto("correo@ejemplo.com", "555-1234", null),
                new Domicilio("Calle", "123")));

        byte[] pdf = servicio(List.of(factura)).generarFacturas();

        String texto = texto(pdf);
        assertTrue(texto.contains("Artículo Ñandú"));
        assertTrue(texto.contains("Año Nuevo"));
        assertTrue(texto.contains("Descripción"));
    }

    @Test
    void conservaElTituloYElNumeroDeFactura() throws Exception {
        byte[] pdf = servicio(List.of(factura(500001L, List.of(detalle("Artículo de prueba")))))
                .generarFacturas();

        String texto = texto(pdf);
        assertTrue(texto.contains("Registro de facturas"));
        assertTrue(texto.contains("Factura 500001"));
        assertTrue(texto.contains("Artículo de prueba"));
    }

    private FacturaPdfService servicio(List<FacturaVenta> facturas) {
        FacturaVentaRepository repositorio = mock(FacturaVentaRepository.class);
        when(repositorio.buscarTodasConDetallesParaPdf()).thenReturn(facturas);
        return new FacturaPdfService(repositorio, new FacturaEstadoMachine(), arca());
    }

    private ArcaProperties arca() {
        ArcaProperties propiedades = new ArcaProperties();
        propiedades.setCuit("20465471752");
        return propiedades;
    }

    private FacturaVenta factura(long numero, List<FacturaVentaDetalle> detalles) {
        FacturaVenta factura = FacturaVenta.builder()
                .numero(numero)
                .fechaEmision(new Date())
                .cliente(new Cliente("30718901231", "Cliente de prueba",
                        new Contacto("correo@ejemplo.com", "555-1234", null),
                        new Domicilio("Calle", "123")))
                .condicionIva(new CondicionIva(1, "IVA Responsable Inscripto"))
                .tipoMoneda(new TipoMoneda("PES", "Peso Argentino", "$"))
                .puntoVenta(new PuntoVenta(1, "Punto de prueba", "CAE", "Av. Siempre Viva 742"))
                .importeCobrado(0d)
                .importeSaldo(2873.75d)
                .importeTotal(2873.75d)
                .resultadoAfip("A")
                .estado("AUTORIZADA")
                .tipoComprobanteArca(1)
                .numeroFiscal(numero)
                .detalles(detalles)
                .build();
        detalles.forEach(detalle -> detalle.setFactura(factura));
        return factura;
    }

    private FacturaVentaDetalle detalle(String descripcion) {
        return new FacturaVentaDetalle(null, null, descripcion, 2d, 1250d, 5d, 2375d, 498.75d, 2873.75d, 21d);
    }

    private String texto(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private int paginas(byte[] pdf) throws IOException {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return documento.getNumberOfPages();
        }
    }
}
