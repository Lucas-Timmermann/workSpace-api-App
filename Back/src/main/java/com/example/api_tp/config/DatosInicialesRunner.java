package com.example.api_tp.config;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import com.example.api_tp.client.AfipWsfeClient;
import com.example.api_tp.client.arca.wsfe.PuntoVentaArca;
import com.example.api_tp.dto.FacturaDetalleRequestDTO;
import com.example.api_tp.dto.FacturaRequestDTO;
import com.example.api_tp.model.Articulo;
import com.example.api_tp.model.AuditoriaApp;
import com.example.api_tp.model.Cliente;
import com.example.api_tp.model.CondicionIva;
import com.example.api_tp.model.Contacto;
import com.example.api_tp.model.Domicilio;
import com.example.api_tp.model.ListaPrecio;
import com.example.api_tp.model.ListaPrecioArticulo;
import com.example.api_tp.model.Marca;
import com.example.api_tp.model.PuntoVenta;
import com.example.api_tp.model.Rubro;
import com.example.api_tp.model.TipoMoneda;
import com.example.api_tp.model.Usuario;
import com.example.api_tp.service.FacturaService;

import jakarta.persistence.EntityManager;


@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DatosInicialesRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosInicialesRunner.class);

    private static final String USUARIO_ADMIN = "admin";
    private static final String DOMICILIO_COMERCIAL = "Av. Corrientes 1234, Ciudad Autónoma de Buenos Aires";
    private static final int FACTURAS_EJEMPLO = 30;
    private static final String OBSERVACION_FACTURA_EJEMPLO = "Factura de ejemplo de la precarga inicial";
    private static final int[] BONIFICACIONES = {0, 5, 10};
    private static final long UN_DIA_MS = 24L * 60 * 60 * 1000;

    private static final List<CondicionIvaDef> CONDICIONES_IVA = List.of(
            new CondicionIvaDef(1, "IVA Responsable Inscripto"),
            new CondicionIvaDef(2, "IVA Responsable No Inscripto"),
            new CondicionIvaDef(3, "IVA No Responsable"),
            new CondicionIvaDef(4, "IVA Sujeto Exento"),
            new CondicionIvaDef(5, "Consumidor Final"),
            new CondicionIvaDef(6, "Responsable Monotributo"));

    private static final List<MonedaDef> MONEDAS = List.of(
            new MonedaDef("PES", "Peso Argentino", "$"),
            new MonedaDef("DOL", "Dólar Estadounidense", "US$"),
            new MonedaDef("060", "Euro", "€"),
            new MonedaDef("012", "Real Brasileño", "R$"));

    private static final List<ClienteDef> CLIENTES = List.of(
            new ClienteDef("30712345671", "Almacén Don Pedro S.A.",
                    "compras@almacendonpedro.com.ar", "011-4555-1001", "11-5555-1001",
                    "Av. Corrientes", "1234"),
            new ClienteDef("30709876542", "Supermercado La Estrella S.R.L.",
                    "compras@laestrella.com.ar", "011-4555-1002", "11-5555-1002",
                    "Av. Santa Fe", "2456"),
            new ClienteDef("30714567892", "Distribuidora del Sur S.A.",
                    "ventas@delsur.com.ar", "011-4555-1003", "11-5555-1003",
                    "Belgrano", "789"),
            new ClienteDef("20301234563", "Kiosco El Sol",
                    "elsol@kiosco.com.ar", "011-4555-1004", "11-5555-1004",
                    "Av. Rivadavia", "5678"),
            new ClienteDef("30715432109", "Autoservicio Los Andes S.R.L.",
                    "compras@losandes.com.ar", "0261-455-1005", "261-555-1005",
                    "Av. San Martín", "123"),
            new ClienteDef("30716789019", "Mercado Central Norte S.A.",
                    "compras@centralnorte.com.ar", "0387-455-1006", "387-555-1006",
                    "Ruta Nacional 9", "Km 12"),
            new ClienteDef("20285678901", "Almacén La Familia",
                    "familia@almacen.com.ar", "0341-455-1007", "341-555-1007",
                    "Mitre", "456"),
            new ClienteDef("30717890120", "Supermercado del Pueblo S.R.L.",
                    "compras@delpueblo.com.ar", "0351-455-1008", "351-555-1008",
                    "Av. Argentina", "789"),
            new ClienteDef("30718901231", "Distribuidora Patagonia S.A.",
                    "ventas@patagonia.com.ar", "0299-455-1009", "299-555-1009",
                    "Sarmiento", "321"),
            new ClienteDef("27324567890", "Minimercado San José",
                    "sanjose@minimercado.com.ar", "0223-455-1010", "223-555-1010",
                    "Av. Colón", "654"));

    private static final List<ArticuloDef> ARTICULOS = List.of(
            new ArticuloDef("ART-0001", "Coca-Cola Original 2,25 L", "Bebidas", 1, "Coca-Cola", 1, 2800, 3500),
            new ArticuloDef("ART-0002", "Pepsi Cola 2,25 L", "Bebidas", 1, "PepsiCo", 2, 2560, 3200),
            new ArticuloDef("ART-0003", "Agua Mineral Villavicencio 1,5 L", "Bebidas", 1, "Villavicencio", 3, 1440, 1800),
            new ArticuloDef("ART-0004", "Cerveza Quilmes Clásica 1 L", "Bebidas", 1, "Quilmes", 4, 2000, 2500),
            new ArticuloDef("ART-0005", "Jugo Cepita Naranja 1 L", "Bebidas", 1, "Cepita", 5, 1760, 2200),
            new ArticuloDef("ART-0006", "Yerba Mate Taragüí 1 kg", "Almacén", 2, "Taragüí", 6, 3840, 4800),
            new ArticuloDef("ART-0007", "Café La Virginia Molido 250 g", "Almacén", 2, "La Virginia", 7, 3120, 3900),
            new ArticuloDef("ART-0008", "Azúcar Ledesma 1 kg", "Almacén", 2, "Ledesma", 8, 1280, 1600),
            new ArticuloDef("ART-0009", "Aceite Natura Girasol 1,5 L", "Almacén", 2, "Natura", 9, 3360, 4200),
            new ArticuloDef("ART-0010", "Arroz Gallo Oro 1 kg", "Almacén", 2, "Gallo", 10, 1680, 2100),
            new ArticuloDef("ART-0011", "Fideos Matarazzo Spaghetti 500 g", "Almacén", 2, "Matarazzo", 11, 1200, 1500),
            new ArticuloDef("ART-0012", "Leche La Serenísima Entera 1 L", "Lácteos", 3, "La Serenísima", 12, 1360, 1700),
            new ArticuloDef("ART-0013", "Yogur Yogurísimo 1 kg", "Lácteos", 3, "La Serenísima", 12, 2320, 2900),
            new ArticuloDef("ART-0014", "Queso Cremoso La Paulina 500 g", "Lácteos", 3, "La Paulina", 13, 4480, 5600),
            new ArticuloDef("ART-0015", "Manteca Sancor 200 g", "Lácteos", 3, "Sancor", 14, 1920, 2400),
            new ArticuloDef("ART-0016", "Detergente Ala Limón 750 ml", "Limpieza", 4, "Ala", 15, 1840, 2300),
            new ArticuloDef("ART-0017", "Lavandina Ayudín 1 L", "Limpieza", 4, "Ayudín", 16, 1120, 1400),
            new ArticuloDef("ART-0018", "Papel Higiénico Higienol 4 u", "Limpieza", 4, "Higienol", 17, 2480, 3100),
            new ArticuloDef("ART-0019", "Shampoo Sedal Ceramidas 340 ml", "Perfumería", 5, "Sedal", 18, 3600, 4500),
            new ArticuloDef("ART-0020", "Jabón Dove Blanco 90 g", "Perfumería", 5, "Dove", 19, 1520, 1900));

    private final EntityManager entityManager;
    private final AfipWsfeClient afipWsfeClient;
    private final ArcaProperties arcaProperties;
    private final FacturaService facturaService;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate transaccionFacturas;

    public DatosInicialesRunner(EntityManager entityManager, AfipWsfeClient afipWsfeClient,
            ArcaProperties arcaProperties, PlatformTransactionManager transactionManager,
            FacturaService facturaService) {
        this.entityManager = entityManager;
        this.afipWsfeClient = afipWsfeClient;
        this.arcaProperties = arcaProperties;
        this.facturaService = facturaService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transaccionFacturas = new TransactionTemplate(transactionManager);
        this.transaccionFacturas.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public void run(String... args) {
        List<PuntoVentaArca> puntosVenta = obtenerPuntosVentaArca();
        transactionTemplate.executeWithoutResult(status -> sembrar(puntosVenta));
        try {
            transaccionFacturas.executeWithoutResult(status -> sembrarFacturas());
        } catch (RuntimeException ex) {
            log.warn("Precarga: no se pudieron generar las facturas de ejemplo ({})", ex.getMessage());
        }
    }

    private void sembrar(List<PuntoVentaArca> puntosVenta) {
        Usuario usuario = obtenerOCrearUsuario();
        Date ahora = new Date();

        ListaPrecio mayorista = obtenerOCrearListaPrecio("MAYORISTA", "Mayorista", usuario, ahora);
        ListaPrecio minorista = obtenerOCrearListaPrecio("MINORISTA", "Minorista", usuario, ahora);

        for (CondicionIvaDef def : CONDICIONES_IVA) {
            obtenerOCrearCondicionIva(def, usuario, ahora);
        }

        for (MonedaDef def : MONEDAS) {
            obtenerOCrearTipoMoneda(def, usuario, ahora);
        }

        for (ClienteDef def : CLIENTES) {
            obtenerOCrearCliente(def, usuario, ahora);
        }

        for (ArticuloDef def : ARTICULOS) {
            Rubro rubro = obtenerOCrearRubro(def.rubro(), def.rubroCodigo(), usuario, ahora);
            Marca marca = obtenerOCrearMarca(def.marca(), def.marcaCodigo(), usuario, ahora);
            Articulo articulo = obtenerOCrearArticulo(def, rubro, marca, usuario, ahora);
            obtenerOCrearListaPrecioArticulo(mayorista, articulo, def.precioMayorista(), usuario, ahora);
            obtenerOCrearListaPrecioArticulo(minorista, articulo, def.precioMinorista(), usuario, ahora);
        }

        for (PuntoVentaArca def : puntosVenta) {
            obtenerOCrearPuntoVenta(def, usuario, ahora);
        }

        entityManager.flush();
        log.info("Precarga de datos iniciales verificada");
    }

    private void sembrarFacturas() {
        long existentes = contarFacturasEjemplo();
        if (existentes > 0) {
            log.info("Precarga: ya existen {} facturas de ejemplo, no se generan nuevamente", existentes);
            return;
        }
        List<Cliente> clientes = listar("select c from Cliente c", Cliente.class);
        List<CondicionIva> condiciones = listar("select c from CondicionIva c", CondicionIva.class);
        List<TipoMoneda> monedas = listar("select m from TipoMoneda m", TipoMoneda.class);
        List<PuntoVenta> puntosVenta = listar("select p from PuntoVenta p", PuntoVenta.class);
        List<ListaPrecioArticulo> precios = listar(
                "select l from ListaPrecioArticulo l join fetch l.articulo", ListaPrecioArticulo.class);
        if (clientes.isEmpty() || condiciones.isEmpty() || monedas.isEmpty()
                || puntosVenta.isEmpty() || precios.isEmpty()) {
            log.warn("Precarga: faltan datos base para generar las facturas de ejemplo");
            return;
        }
        Random random = new Random();
        for (int indice = 0; indice < FACTURAS_EJEMPLO; indice++) {
            facturaService.crear(facturaAleatoria(clientes, condiciones, monedas, puntosVenta, precios, random, indice));
        }
        long total = contarFacturasEjemplo();
        long conDetalles = contarFacturasEjemploConDetalles();
        if (total == FACTURAS_EJEMPLO && conDetalles == total) {
            log.info("Precarga: {} facturas de ejemplo generadas con sus detalles", total);
        } else {
            log.warn("Precarga: se esperaban {} facturas de ejemplo con detalles y se generaron {} "
                    + "({} con detalles)", FACTURAS_EJEMPLO, total, conDetalles);
        }
    }

    private FacturaRequestDTO facturaAleatoria(List<Cliente> clientes, List<CondicionIva> condiciones,
            List<TipoMoneda> monedas, List<PuntoVenta> puntosVenta, List<ListaPrecioArticulo> precios,
            Random random, int indice) {
        FacturaRequestDTO request = new FacturaRequestDTO();
        request.setFechaEmision(new Date(System.currentTimeMillis() - random.nextInt(60) * UN_DIA_MS));
        request.setClienteId(aleatorio(clientes, random).getId());
        request.setCondicionIvaId(condicionAleatoria(condiciones, random, indice).getId());
        request.setPuntoVentaId(aleatorio(puntosVenta, random).getId());
        request.setTipoMonedaId(aleatorio(monedas, random).getId());
        request.setImporteCobrado(BigDecimal.ZERO);
        request.setObservaciones(OBSERVACION_FACTURA_EJEMPLO);
        request.setDetalles(detallesAleatorios(precios, random));
        return request;
    }


    private static CondicionIva condicionAleatoria(List<CondicionIva> condiciones, Random random, int indice) {
        if (indice % 5 == 0) {
            return condiciones.stream()
                    .filter(condicion -> condicion.getCodigoAfip() == 5)
                    .findFirst()
                    .orElseGet(() -> aleatorio(condiciones, random));
        }
        return aleatorio(condiciones, random);
    }

    private static List<FacturaDetalleRequestDTO> detallesAleatorios(List<ListaPrecioArticulo> precios,
            Random random) {
        List<ListaPrecioArticulo> disponibles = new ArrayList<>(precios);
        Collections.shuffle(disponibles, random);
        int cantidadDetalles = 1 + random.nextInt(Math.min(5, disponibles.size()));
        List<FacturaDetalleRequestDTO> detalles = new ArrayList<>(cantidadDetalles);
        for (int indice = 0; indice < cantidadDetalles; indice++) {
            ListaPrecioArticulo precio = disponibles.get(indice);
            detalles.add(new FacturaDetalleRequestDTO(
                    null,
                    precio.getArticulo().getId(),
                    precio.getId(),
                    null,
                    BigDecimal.valueOf(1 + random.nextInt(10)),
                    BigDecimal.valueOf(precio.getPrecioVenta()),
                    BigDecimal.valueOf(BONIFICACIONES[random.nextInt(BONIFICACIONES.length)]),
                    BigDecimal.valueOf(21)));
        }
        return detalles;
    }

    private long contarFacturasEjemplo() {
        Long total = entityManager.createQuery(
                "select count(f) from FacturaVenta f where f.observaciones = :valor", Long.class)
                .setParameter("valor", OBSERVACION_FACTURA_EJEMPLO)
                .getSingleResult();
        return total == null ? 0 : total;
    }

    private long contarFacturasEjemploConDetalles() {
        Long total = entityManager.createQuery(
                "select count(f) from FacturaVenta f where f.observaciones = :valor and size(f.detalles) > 0",
                Long.class)
                .setParameter("valor", OBSERVACION_FACTURA_EJEMPLO)
                .getSingleResult();
        return total == null ? 0 : total;
    }

    private <T> List<T> listar(String jpql, Class<T> tipo) {
        return entityManager.createQuery(jpql, tipo).getResultList();
    }

    private static <T> T aleatorio(List<T> valores, Random random) {
        return valores.get(random.nextInt(valores.size()));
    }

    private List<PuntoVentaArca> obtenerPuntosVentaArca() {
        if (!StringUtils.hasText(arcaProperties.getCuit())) {
            log.info("Precarga: ARCA_CUIT no configurado, se usan los puntos de venta 1 a 4 por defecto");
            return puntosVentaPorDefecto();
        }
        try {
            return completarHastaCuatro(afipWsfeClient.feParamGetPtosVenta());
        } catch (RuntimeException ex) {
            log.warn("Precarga: no se pudieron consultar los puntos de venta en ARCA ({}); "
                    + "se usan los puntos de venta 1 a 4 por defecto", ex.getMessage());
            return puntosVentaPorDefecto();
        }
    }

    private static List<PuntoVentaArca> completarHastaCuatro(List<PuntoVentaArca> puntos) {
        List<PuntoVentaArca> out = new ArrayList<>();
        if (puntos != null) {
            for (PuntoVentaArca punto : puntos) {
                if (out.size() == 4) {
                    break;
                }
                if (out.stream().noneMatch(actual -> actual.getNumero() == punto.getNumero())) {
                    out.add(punto);
                }
            }
        }
        for (int numero = 1; out.size() < 4 && numero <= 4; numero++) {
            int candidato = numero;
            if (out.stream().noneMatch(actual -> actual.getNumero() == candidato)) {
                out.add(new PuntoVentaArca(candidato, "CAE", false));
            }
        }
        return out;
    }

    private static List<PuntoVentaArca> puntosVentaPorDefecto() {
        List<PuntoVentaArca> out = new ArrayList<>();
        for (int numero = 1; numero <= 4; numero++) {
            out.add(new PuntoVentaArca(numero, "CAE", false));
        }
        return out;
    }

    private Usuario obtenerOCrearUsuario() {
        Usuario usuario = buscarUno("select u from Usuario u where u.usuario = :valor", Usuario.class, USUARIO_ADMIN);
        if (usuario != null) {
            return usuario;
        }
        usuario = new Usuario();
        usuario.setUsuario(USUARIO_ADMIN);
        usuario.setClave("admin123");
        usuario.setNombre("Administrador");
        usuario.setApellido("Sistema");
        entityManager.persist(usuario);
        return usuario;
    }

    private void obtenerOCrearCliente(ClienteDef def, Usuario usuario, Date ahora) {
        if (buscarUno("select c from Cliente c where c.cuitCuil = :valor", Cliente.class, def.cuit()) != null) {
            return;
        }
        Contacto contacto = new Contacto();
        contacto.setEmail(def.email());
        contacto.setTelefono(def.telefono());
        contacto.setCelular(def.celular());
        entityManager.persist(contacto);

        Domicilio domicilio = new Domicilio();
        domicilio.setNombreCalle(def.calle());
        domicilio.setNumeroCalle(def.numeroCalle());
        entityManager.persist(domicilio);

        Cliente cliente = new Cliente();
        cliente.setCuitCuil(def.cuit());
        cliente.setDenominacion(def.denominacion());
        cliente.setContacto(contacto);
        cliente.setDomicilio(domicilio);
        auditar(cliente, usuario, ahora);
        entityManager.persist(cliente);
    }

    private ListaPrecio obtenerOCrearListaPrecio(String codigo, String denominacion, Usuario usuario, Date ahora) {
        ListaPrecio lista = buscarUno("select l from ListaPrecio l where l.codigo = :valor", ListaPrecio.class, codigo);
        if (lista != null) {
            return lista;
        }
        lista = new ListaPrecio();
        lista.setCodigo(codigo);
        lista.setDenominacion(denominacion);
        auditar(lista, usuario, ahora);
        entityManager.persist(lista);
        return lista;
    }

    private CondicionIva obtenerOCrearCondicionIva(CondicionIvaDef def, Usuario usuario, Date ahora) {
        CondicionIva condicion = buscarUno("select c from CondicionIva c where c.codigoAfip = :valor",
                CondicionIva.class, def.codigoAfip());
        if (condicion != null) {
            return condicion;
        }
        condicion = new CondicionIva();
        condicion.setCodigoAfip(def.codigoAfip());
        condicion.setDenominacion(def.denominacion());
        auditar(condicion, usuario, ahora);
        entityManager.persist(condicion);
        return condicion;
    }

    private TipoMoneda obtenerOCrearTipoMoneda(MonedaDef def, Usuario usuario, Date ahora) {
        TipoMoneda moneda = buscarUno("select m from TipoMoneda m where m.codigoAfip = :valor",
                TipoMoneda.class, def.codigoAfip());
        if (moneda != null) {
            return moneda;
        }
        moneda = new TipoMoneda();
        moneda.setCodigoAfip(def.codigoAfip());
        moneda.setDenominacion(def.denominacion());
        moneda.setSimbolo(def.simbolo());
        auditar(moneda, usuario, ahora);
        entityManager.persist(moneda);
        return moneda;
    }

    private Rubro obtenerOCrearRubro(String denominacion, int codigo, Usuario usuario, Date ahora) {
        Rubro rubro = buscarUno("select r from Rubro r where r.codigo = :valor", Rubro.class, codigo);
        if (rubro != null) {
            return rubro;
        }
        rubro = new Rubro();
        rubro.setDenominacion(denominacion);
        rubro.setCodigo(codigo);
        auditar(rubro, usuario, ahora);
        entityManager.persist(rubro);
        return rubro;
    }

    private Marca obtenerOCrearMarca(String denominacion, int codigo, Usuario usuario, Date ahora) {
        Marca marca = buscarUno("select m from Marca m where m.codigo = :valor", Marca.class, codigo);
        if (marca != null) {
            return marca;
        }
        marca = new Marca();
        marca.setDenominacion(denominacion);
        marca.setCodigo(codigo);
        auditar(marca, usuario, ahora);
        entityManager.persist(marca);
        return marca;
    }

    private Articulo obtenerOCrearArticulo(ArticuloDef def, Rubro rubro, Marca marca, Usuario usuario, Date ahora) {
        Articulo articulo = buscarUno("select a from Articulo a where a.codigo = :valor", Articulo.class, def.codigo());
        if (articulo != null) {
            return articulo;
        }
        articulo = new Articulo();
        articulo.setRubro(rubro);
        articulo.setMarca(marca);
        articulo.setCodigo(def.codigo());
        articulo.setDenominacion(def.denominacion());
        auditar(articulo, usuario, ahora);
        entityManager.persist(articulo);
        return articulo;
    }

    private void obtenerOCrearListaPrecioArticulo(ListaPrecio lista, Articulo articulo, double precio,
            Usuario usuario, Date ahora) {
        List<ListaPrecioArticulo> existentes = entityManager.createQuery(
                "select l from ListaPrecioArticulo l where l.listaPrecio = :lista and l.articulo = :articulo",
                ListaPrecioArticulo.class)
                .setParameter("lista", lista)
                .setParameter("articulo", articulo)
                .setMaxResults(1)
                .getResultList();
        if (!existentes.isEmpty()) {
            return;
        }
        ListaPrecioArticulo precioArticulo = new ListaPrecioArticulo();
        precioArticulo.setListaPrecio(lista);
        precioArticulo.setArticulo(articulo);
        precioArticulo.setPrecioVenta(precio);
        auditar(precioArticulo, usuario, ahora);
        entityManager.persist(precioArticulo);
    }

    private void obtenerOCrearPuntoVenta(PuntoVentaArca def, Usuario usuario, Date ahora) {
        PuntoVenta puntoVenta = buscarUno("select p from PuntoVenta p where p.numero = :valor",
                PuntoVenta.class, def.getNumero());
        if (puntoVenta != null) {
            return;
        }
        puntoVenta = new PuntoVenta();
        puntoVenta.setNumero(def.getNumero());
        puntoVenta.setDescripcion("Punto de venta " + def.getNumero());
        puntoVenta.setTipoEmision(StringUtils.hasText(def.getEmisionTipo()) ? def.getEmisionTipo() : "CAE");
        puntoVenta.setDomicilioComercial(DOMICILIO_COMERCIAL);
        auditar(puntoVenta, usuario, ahora);
        entityManager.persist(puntoVenta);
    }

    private <T> T buscarUno(String jpql, Class<T> tipo, Object valor) {
        List<T> resultados = entityManager.createQuery(jpql, tipo)
                .setParameter("valor", valor)
                .setMaxResults(1)
                .getResultList();
        return resultados.isEmpty() ? null : resultados.get(0);
    }

    private static void auditar(AuditoriaApp entidad, Usuario usuario, Date ahora) {
        entidad.setFechaAlta(ahora);
        entidad.setFechaModificacion(ahora);
        entidad.setUsuarioCarga(usuario);
        entidad.setUsuarioModificacion(usuario);
    }

    private record CondicionIvaDef(int codigoAfip, String denominacion) {
    }

    private record MonedaDef(String codigoAfip, String denominacion, String simbolo) {
    }

    private record ClienteDef(String cuit, String denominacion, String email, String telefono,
            String celular, String calle, String numeroCalle) {
    }

    private record ArticuloDef(String codigo, String denominacion, String rubro, int rubroCodigo,
            String marca, int marcaCodigo, double precioMayorista, double precioMinorista) {
    }
}
