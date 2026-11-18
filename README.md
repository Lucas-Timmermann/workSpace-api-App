# TP Integrador — Sistema de Facturación Electrónica

Aplicación de facturación de venta con backend **Spring Boot** y frontend **TypeScript** sin framework. Permite administrar clientes y artículos de catálogo, crear facturas con renglones, consultarlas con filtros y paginación, descargar PDF y ejecutar el circuito de envío a **ARCA** (WSAA + WSFEv1) en entorno de **homologación**.

> **Alcance.** Es un trabajo académico. La integración con ARCA es para probar el circuito real de homologación;


## Integrantes

| Integrante |
|---|
| Lucas Timmermann |
| Javier Alaniz |
| Aracelly Caballero |
| Nathaniel Herrera |
| Alejo Pavon |

## 1. Qué hace el sistema

- **Catálogos de solo lectura**: puntos de venta, clientes, condiciones de IVA, monedas y listas de precio con sus artículos.
- **Facturas**: alta, edición y anulación lógica mientras no hayan sido enviadas o autorizadas; listado general, listado de pendientes y listado de autorizadas.
- **Cálculo**: neto, IVA y subtotal por renglón; totales de cabecera y saldo.
- **Reportes PDF**: registro de facturas autorizadas y comprobante individual de una factura autorizada.
- **ARCA (opcional)**: obtención y reutilización del ticket WSAA, consulta del último comprobante autorizado, solicitud de CAE, consulta de comprobantes para conciliar y registro de cada intento.
- **Auditoría**: usuario de carga, modificación y baja; alta de intentos de envío ARCA.

## 2. Stack real

| Capa | Tecnología verificada | Referencia |
|---|---|---|
| Backend | Spring Boot **4.1.1**, Java **17**, Maven Wrapper | `Back/pom.xml` (7-8, 30) |
| Persistencia | Spring Data JPA / Hibernate, MySQL | `Back/pom.xml` (41, 70-73) |
| Validación | Jakarta Bean Validation | `Back/pom.xml` (49) |
| PDF | Apache PDFBox **3.0.6** | `Back/pom.xml` (53-54) |
| ARCA | BouncyCastle **1.78** (PKCS#7) y `java.net.http` | `Back/pom.xml` (97-103) |
| Utilidades | Lombok | `Back/pom.xml` (75, 128) |
| Frontend | TypeScript estricto, módulos ES, sin framework | `Front/tsconfig.json`, `Front/package.json` |
| Servidor frontend | Node HTTP propio en el puerto **5173** | `Front/server.cjs` (63-65) |


## 3. Puesta en marcha

### Requisitos

- Java 17.
- MySQL 8 con una base llamada `facturacionlombock` (o el nombre que se indique en `DB_URL`).
- Node.js 18.13 o posterior.
- No hace falta instalar Maven ni TypeScript globalmente: se usa el wrapper y las dependencias locales.

### Backend

```powershell
cd Back
$env:DB_URL="jdbc:mysql://localhost:3306/"Tu base de datos" "
$env:DB_USERNAME="root"
$env:DB_PASSWORD="<tu-password>"
.\mvnw.cmd spring-boot:run
```

Queda escuchando en `http://localhost:8080`. La configuración está en `Back/src/main/resources/application.properties` (1-25):

- `ddl-auto=update` y `open-in-view=false` (5-6).
- Siembra de datos activada por defecto: `app.seed.enabled=true` (8).
- Usuario de auditoría por defecto: `app.auditoria.usuario-id=1` (7).
- CORS para `localhost` y `127.0.0.1` en el puerto 5173 (9).

> El archivo incluye un valor por defecto para la contraseña de base de datos. Debe sobrescribirse siempre con `DB_PASSWORD`; no se reproduce aquí.

### Frontend

```powershell
cd Front
npm ci
npm run build
npm run dev
```

`npm run dev` compila y luego sirve la aplicación en `http://127.0.0.1:5173` (`Front/package.json` 6-10, `Front/server.cjs` 63-65). El HTML carga `./dist/src/app.js` (`Front/index.html` 9), por eso **hay que compilar antes de levantar el servidor**.

### Base de datos y datos iniciales

`DatosInicialesRunner` se ejecuta al arrancar si `app.seed.enabled=true`. Es idempotente: crea lo que falta y omite lo existente.

- 6 condiciones de IVA, 4 monedas, 10 clientes y 20 artículos (`DatosInicialesRunner.java` 44-110).
- 2 listas de precio (`DatosInicialesRunner.java` 125-164).
- Puntos de venta 1 a 4: primero intenta obtenerlos de ARCA y, si falla, usa valores locales (`DatosInicialesRunner.java` 166-207).
- Usuario `admin` (`DatosInicialesRunner.java` 209-221).

## 4. Estructura del repositorio

```text
Back/
  src/main/java/com/example/api_tp/
    model/         entidades JPA y enums
    dto/           contratos de entrada y salida
    repository/    repositorios Spring Data
    service/       lógica de facturas, estados y PDF
    service/arca/  armado e interpretación de comprobantes ARCA
    client/        clientes SOAP WSAA y WSFEv1
    config/        propiedades, CORS, excepciones y datos iniciales
    controller/    endpoints REST
  src/test/java/   pruebas de integración y de clientes ARCA
Front/
  index.html       estructura de la interfaz
  src/app.ts       estado, DOM y flujos de la pantalla
  services/        cliente HTTP y manejo de errores
  server.cjs       servidor estático del puerto 5173
```

---

## Etapa 0 — Puesta en marcha

**Objetivo.** Dejar backend y frontend ejecutables, con configuración comprensible y comandos reproducibles.

**Explicación sencilla.** Es la etapa de “que arranque”. Se elige el stack, se configura la base, se prepara el proyecto y se documenta cómo levantarlo.

**Implementación en el código.**
- Backend Maven con Java 17: `Back/pom.xml` (7-8, 30) y wrapper `Back/mvnw.cmd`.
- Configuración de aplicación: `Back/src/main/resources/application.properties` (1-25).
- CORS local: `Back/src/main/java/com/example/api_tp/config/CorsConfig.java` (18-25).
- Frontend y servidor local: `Front/package.json` (6-10), `Front/server.cjs` (63-65).
- Página de entrada: `Front/index.html` (1-20).
- Este README.

**Funcionamiento dentro del sistema.** Al arrancar el backend se crea/actualiza el esquema (`ddl-auto=update`), se verifica/crea la siembra de catálogos y se expone la API en el puerto 8080. El frontend compila TypeScript a `Front/dist` y el servidor Node entrega `index.html`, `styles.css` y el JavaScript compilado en el puerto 5173.

---

## Etapa 1 — Persistencia con JPA/Hibernate

**Objetivo.** Modelar el dominio persistente respetando relaciones, restricciones, cascadas y unicidad.

**Explicación sencilla.** Cada concepto del negocio (cliente, artículo, factura, renglón) es una tabla. Las facturas se guardan con sus renglones y queda registrado quién y cuándo hizo cada cambio.

**Implementación en el código.**
- Superclases:
  - `EntityId.java` (12-21): `id` autogenerado.
  - `AuditoriaApp.java` (16-38): fechas de alta, baja y modificación, y usuarios de carga, baja y modificación.
- Entidades del modelo en `Back/src/main/java/com/example/api_tp/model/`:
  `Usuario`, `Cliente`, `Contacto`, `Domicilio`, `CondicionIva`, `PuntoVenta`, `TipoMoneda`, `Rubro`, `Marca`, `Articulo`, `ListaPrecio`, `ListaPrecioArticulo`, `FacturaVenta`, `FacturaVentaDetalle`.
  Se agrega `FacturaEnvioArca` para la trazabilidad de envíos.
- Factura y renglones:
  - `FacturaVenta.java` (22-88): número único y obligatorio, cliente/condición IVA/moneda/punto de venta obligatorios, totales y campos ARCA. La colección de detalles usa `cascade = ALL` (77-79) y `addDetalle` mantiene ambos extremos (85-88).
  - `FacturaVentaDetalle.java` (15-41): factura y artículo de lista obligatorios, cantidad, precio, bonificación, IVA e importes.
- Restricciones de catálogo: `Cliente.java` (24-33) exige CUIT, denominación, contacto y domicilio. `ListaPrecioArticulo.java` (23-32) relaciona lista y artículo, pero **no declara una restricción única** para esa combinación.
- Estado: `EstadoFactura.java` (3-17) define los estados y helpers `esEditable`/`esEnviable`.
- Datos iniciales: `DatosInicialesRunner.java` (35-37 condición de siembra, 44-110 catálogos, 125-221 ejecución, puntos de venta y usuario).
- Pruebas de persistencia dentro de `FacturaIntegrationTests.java` y `DatosInicialesRunnerTests.java`.

**Funcionamiento dentro del sistema.** Toda operación pasa por repositorios Spring Data. La factura es la raíz del agregado: al guardarla se guardan sus renglones por cascada. La auditoría se completa desde el servicio antes de persistir (`FacturaService.java` 156-166 y 197-215).

---

## Etapa 2 — Lombok, DTO, consultas JPQL y reportes

**Objetivo.** Obtener reportes mediante DTO, sin exponer entidades, y generar archivos PDF, Excel y TXT.

**Explicación sencilla.** En lugar de devolver las tablas completas, se arman objetos livianos con solo los datos que necesita la pantalla o el archivo. Luego se imprimen en distintos formatos.

**Implementación en el código.**
- Lombok configurado en `Back/pom.xml` (75 y 128). Las entidades usan `@Getter`, `@Setter`, `@ToString(exclude = ...)` y `@EqualsAndHashCode(... exclude = ...)` para evitar recursión, por ejemplo `FacturaVenta.java` (24-30) y `FacturaVentaDetalle.java` (17-22).
- DTO de reporte: `FacturaReporteDTO.java` (12-37) con número, fecha, cliente, condición IVA, punto de venta, moneda, importes, estado, observaciones, detalles y cantidad de ítems.
- Armado del DTO: `FacturaService.java` (875-922). Se cargan las asociaciones dentro de la transacción y se mapea a mano.
- Consultas con filtros y paginación: `FacturaVentaRepository.java` (71-98); la carga con detalles para PDF está en (38-51).
- Generación de PDF: `FacturaPdfService.java`
  - Registro de facturas autorizadas: (45-67); la consulta filtra `estado = 'AUTORIZADA'` (`FacturaVentaRepository.java` 38-51).
  - Comprobante individual: (69-94); exige estado `AUTORIZADA` y CAE con vencimiento.
  - Escritura, saltos de página y formato: (108-228).
  - Conversión de caracteres no latinos: (219-228) reemplaza por `?`.

**Funcionamiento dentro del sistema.** `GET /api/facturas/pdf` arma un PDF con las facturas **autorizadas**. `GET /api/facturas/{id}/pdf` genera el comprobante de una factura autorizada. No se usan entidades como respuesta HTTP: el servicio devuelve `FacturaReporteDTO` o `FacturaVentaPaginaDTO`.

---

## Etapa 3 — API REST de lectura

**Objetivo.** Exponer consultas de facturas, catálogos y reportes con contratos HTTP claros y errores controlados.

**Explicación sencilla.** El frontend pide datos por HTTP: listados con filtros, detalle de una factura, catálogos para los selectores y archivos PDF.

**Implementación en el código.** (`FacturaController.java`, base `/api/facturas`)
- Listado paginado: (55-65). Parámetros: `numeroFactura`, `clienteDenominacion`, `condicionIva`, `fechaDesde`, `fechaHasta`, `page`.
- Detalle por ID: (105-108).
- Pendientes de envío: (86-95).
- Autorizadas: (97-102).
- PDF de facturas autorizadas: (45-53).
- PDF individual: (132-140).
- Catálogos: `CatalogoController.java` (25-54): `/api/puntos-venta`, `/api/clientes`, `/api/condiciones-iva`, `/api/tipos-moneda`, `/api/listas-precio`, `/api/listas-precio/{id}/articulos`.
- Filtros y paginación en repositorio: `FacturaVentaRepository.java` (71-98).
- CORS: `CorsConfig.java` (18-25), orígenes `http://localhost:5173` y `http://127.0.0.1:5173`.
- Errores centralizados: `GlobalExceptionHandler.java` (36-143) y cuerpo JSON con `timestamp`, `status`, `error`, `tipo`, `message` y `detalles` (153-165).

**Funcionamiento dentro del sistema.** El frontend consulta el listado con filtros y `page`; el backend responde `FacturaVentaPaginaDTO` con contenido, total de elementos y total de páginas. El detalle se usa para editar. Los catálogos alimentan los selectores del formulario.

---

## Etapa 4 — Escritura, estados y consistencia transaccional

**Objetivo.** Crear y modificar facturas, emitirlas o anularlas y proteger los datos ante entradas inválidas y operaciones simultáneas.

**Explicación sencilla.** Es la etapa en la que la factura deja de ser solo lectura: se crea, se corrige, se envía a ARCA y se anula. Los estados impiden operaciones incompatibles y las transacciones evitan datos a medias.

**Implementación en el código.**
- Endpoints de escritura (`FacturaController.java`):
  - `POST /api/facturas` (67-70).
  - `PUT /api/facturas/{id}` (72-76).
  - `DELETE /api/facturas/{id}` (78-82).
  - No hay `PATCH /{id}/estado`.
- Lógica:
  - Crear: `FacturaService.java` (146-177). Valida, aplica cabecera y datos fiscales, asigna número interno, estado `PENDIENTE`, sincroniza detalles y recalcula totales.
  - Editar: (180-217). Solo permite estados editables (`PENDIENTE` o `RECHAZADA`), reemplaza renglones y vuelve a `PENDIENTE`.
  - Anular: (219-245). Pasa a `ANULADA_LOCAL`, registra fecha y usuario de baja. No se permite anular una `AUTORIZADA`.
  - Eliminar: (248-251). Es una **anulación lógica**, no un borrado físico.
- Contratos de entrada:
  - `FacturaRequestDTO.java` (23-81): exige fecha, cliente, condición IVA, punto de venta, moneda, importe cobrado y al menos un detalle; rechaza propiedades desconocidas y campos de control (`@JsonAnySetter`, 69-81).
  - `FacturaDetalleRequestDTO.java` (19-49): exige `articuloId`, `listaPrecioArticuloId`, `precioUnitario` y `porcentajeIva`.
  - Validaciones de servicio: `FacturaService.java` (1041-1070).
- Cálculo: `recalcularTotales` (1022-1039), `mapearDetalle` (993-1020) y `sincronizarDetalles` (955-991).
- Estados: `FacturaEstadoMachine.java` (17-30 transiciones, 32-59 validación y parseo). Transiciones permitidas: `PENDIENTE → EN_PROCESO_ENVIO`, `PENDIENTE → ANULADA_LOCAL`, `EN_PROCESO_ENVIO → AUTORIZADA/RECHAZADA/RESULTADO_DESCONOCIDO`, `RECHAZADA → PENDIENTE`, `RESULTADO_DESCONOCIDO → AUTORIZADA/RECHAZADA`.
- Concurrencia y numeración: `generarNumeroInterno` (950-953) bloquea todos los puntos de venta (`PuntoVentaRepository.java` 13-15) y toma el máximo número (`FacturaVentaRepository.java` 20-24). El envío ARCA usa además un `ReentrantLock` (110, 446-491).
- Auditoría: `obtenerUsuarioAuditoria` (1072-1085) lee `app.auditoria.usuario-id` y busca el usuario; no se usa `X-Usuario`.

**Funcionamiento dentro del sistema.** El circuito es: `POST` crea la factura en `PENDIENTE` con número interno y sin CAE; `PUT` corrige mientras siga pendiente o rechazada; el envío a ARCA la pasa a `EN_PROCESO_ENVIO` y luego a `AUTORIZADA`, `RECHAZADA` o `RESULTADO_DESCONOCIDO`; `DELETE` la anula localmente. Una factura autorizada ya no se edita ni se anula desde este circuito.

---

## Etapa 5 — Frontend TypeScript e integración

**Objetivo.** Construir una interfaz web que permita operar el circuito completo y mantener resultados coherentes ante errores y respuestas asíncronas.

**Explicación sencilla.** Es la pantalla: filtros, tabla de facturas, formulario con renglones, botones para editar/anular, envío a ARCA y descarga de PDF.

**Implementación en el código.**
- Estructura de la página: `Front/index.html` (13-101 listado y filtros, 103-120 diálogo ARCA, 122-144 diálogo PDF, 147-233 formulario y selector de artículos, 237-245 panel de trazas).
- Estado y utilidades: `Front/src/app.ts` (30-95 referencias del DOM y estado, 97-241 helpers de DOM, formato y mensajes).
- Listado y filtros: (339-358 carga, 360-385 lectura de filtros, 242-330 render y paginación).
- Catálogos: (398-440).
- Artículos y detalles: (499-529 carga con control de versión de solicitud, 543-571 agregar renglones, 612-659 render y total estimado).
- Formulario: (670-724 mostrar/descartar; 725-808 armar el request; 810-842 guardar con bloqueo de doble envío; 844-858 edición; 860-884 anulación/eliminación).
- ARCA: (886-1016 pendientes, envío y resultado).
- PDF: (1029-1105 descarga y apertura).
- Acciones por fila: (1199-1220).
- Cliente HTTP: `Front/services/factura.service.ts` (14 URL base, 47-133 operaciones, 232-314 manejo de respuestas y errores, 316-387 PDF).
- Compilación y servidor: `Front/package.json` (6-10), `Front/server.cjs` (63-65).

**Funcionamiento dentro del sistema.** Al cargar, la pantalla pide catálogos y la primera página de facturas. Los filtros se aplican al listado; el PDF es un listado global de facturas autorizadas y no recibe esos filtros. El formulario permite elegir lista de precios y artículos, agregar renglones, ver el total estimado sin IVA y guardar. Según el estado de cada factura se habilitan editar, anular o ninguna acción. El diálogo ARCA lista las pendientes y envía el lote.

---

## Integración ARCA (WSAA + WSFEv1)

Esta sección documenta la extensión del grupo. **No forma parte del enunciado original**, que pedía un CAE simulado de 14 dígitos. Aquí se implementó el circuito real contra los web services de **homologación** de ARCA.

### Configuración

- Propiedades: `Back/src/main/java/com/example/api_tp/config/ArcaProperties.java`
  - Datos generales y entorno (11-29).
  - Enum de entorno (66-69).
  - Valores por defecto de WSAA y WSFEv1 para homologación (71-151).
  - Rutas de certificado y clave (153-174).
- Variables en `application.properties` (13-25): `ARCA_CUIT`, `ARCA_WSAA_URL`, `ARCA_WSFEV1_URL`, `ARCA_CERT_PATH`, `ARCA_KEY_PATH`, timeouts y margen de renovación.
- `ArcaConfig.java` (10-17) aporta el `Clock` usado para validar vencimientos.
- Los certificados de prueba están versionados en `Back/src/main/resources/certs/` y se cargan por `classpath:`.
- **Bloqueo de producción:** `AfipWsaaClient.asegurarHomologacion` (478-497) y `AfipWsfeClient` (560-576) rechazan URL de producción. El sistema solo funciona contra homologación.

### WSAA: autenticación

1. Se genera un **TRA** (Ticket de Requerimiento de Acceso) con servicio `wsfe` y ventana de validez: `AfipWsaaClient.java` (151-168).
2. El TRA se firma como **PKCS#7/CMS** con el certificado y la clave privada: (170-186). La clave se lee desde el archivo configurado (276-348).
3. Se envía el CMS por SOAP al `LoginCms`: (188-228).
4. Se parsea la respuesta y se obtiene `token` + `sign` con vencimientos: (230-274).
5. **Reutilización del ticket:** se guarda en un `ConcurrentHashMap` por servicio (81-82) y se devuelve mientras siga vigente, con margen de renovación (97-124). Si está vencido o por vencer, se pide uno nuevo. `invalidarCache` (132-134) permite descartarlo.

### WSFEv1: comprobantes

- Autenticación con token/sign: `AfipWsfeClient.java` (67-70).
- Último comprobante autorizado (`FECompUltimoAutorizado`): (72-91). Devuelve el próximo número fiscal.
- Solicitud de CAE (`FECAESolicitar`): (93-122).
- Consulta de un comprobante (`FECompConsultar`): (124-141). Se usa para conciliar un envío incierto.
- Parámetros de ARCA (tipos de comprobante, concepto, documento, IVA, monedas, puntos de venta y cotización): (143-230).
- Serialización del detalle y parseo de CAE, observaciones y errores: (267-368).
- Armado del sobre SOAP y manejo de fallas de comunicación: (370-420).

### Orquestación en el servicio

- `ArcaFacturaElectronicaService.java`
  - Ticket reutilizable (48-50), último autorizado (52-58), catálogos de IVA/puntos de venta (60-68), cotización (70-73).
  - Datos fiscales base del comprobante (76-125).
  - Armado de la solicitud `FECAESolicitar` (128-161) y envío (164-176).
  - Consulta para conciliación (179-186) e interpretación de CAE (188-220) y de la consulta (222-248).
  - Resolución del código de alícuota de IVA (285-312).
- `FacturaService.java`
  - Envío por lote o por IDs (371-414).
  - Preparación y validación de una factura (494-526).
  - Paso a `EN_PROCESO_ENVIO` y construcción de la solicitud (536-566).
  - Persistencia del resultado (598-660).
  - Datos fiscales derivados de la factura: tipo de comprobante (692-702), condición IVA, tipo y número de documento, moneda y cotización (704-757).

### Persistencia del CAE y trazabilidad

- `FacturaVenta.java` (54-75) guarda `cae`, `caeFechaVencimiento`, `resultadoAfip`, `motivoRechazo`, `numeroFiscal`, tipo de comprobante, concepto, datos del receptor, moneda, cotización y fechas de envío/autorización/resultado.
- `FacturaEnvioArca.java` (27-59) registra cada intento: fecha, número de intento, estado, punto de venta, tipo y número de comprobante, resultado, CAE, vencimiento, observaciones, errores, eventos, mensaje técnico y `procesoEnvioId`.
- El estado de la factura y el resultado ARCA se actualizan en la misma transacción que persiste el intento.

### Pendientes, conciliación y errores

- `GET /api/facturas/pendientes` lista lo enviable (`FacturaController.java` 86-95).
- `POST /api/facturas/envios/arca` envía un lote (`FacturaController.java` 116-120). Sin IDs envía todas las `PENDIENTE`.
- `GET /api/facturas/envios/{envioId}` devuelve el resumen del proceso (`FacturaController.java` 122-125).
- `GET /api/facturas/{id}/envios` devuelve el historial de intentos (`FacturaController.java` 127-130).
- `POST /api/facturas/{id}/conciliar` consulta ARCA por el comprobante y corrige el estado (`FacturaController.java` 110-114; `FacturaService.java` 569-595).
- Si la comunicación falla después de enviar, la factura queda en `RESULTADO_DESCONOCIDO` y se resuelve por conciliación. Si ARCA rechaza, queda `RECHAZADA` con el motivo. Los errores de validación previos no se envían y se informan por factura sin cortar el lote (`FacturaService.java` 385-392, 416-492).

### Simulación académica vs. homologación

- **Lo que pide el enunciado** es una simulación: CAE de 14 dígitos, vencimiento a 10 días y autorización `A`, sin llamadas externas.
- **Lo que hace este repositorio** es una integración real contra **homologación**: firma CMS, pide ticket WSAA, consulta numeración y solicita CAE a WSFEv1. Los datos que devuelve ARCA se persisten tal cual.
- **Lo que no es:** no es producción, no usa certificados productivos, no reemplaza la declaración fiscal real y no debería usarse para emitir comprobantes con validez fiscal.
- Para llevarlo a producción faltaría, como mínimo: certificado y clave productivos, URLs de producción, habilitación del punto de venta, manejo de contingencias y revisión fiscal. El código actual **rechaza** las URLs de producción a propósito.

> **Seguridad.** El repositorio incluye un certificado y una clave privada de prueba en `Back/src/main/resources/certs/`. No deben usarse fuera de homologación. Antes de publicar o reutilizar el proyecto conviene rotar esos archivos y sacarlos del control de versiones.

---

## Estados de una factura

| Estado | Significado | Operaciones posibles |
|---|---|---|
| `PENDIENTE` | Creada, sin envío exitoso a ARCA | Editar, anular, enviar |
| `EN_PROCESO_ENVIO` | Enviada; se espera respuesta | Esperar o conciliar |
| `AUTORIZADA` | ARCA devolvió CAE | Ver PDF; no editar ni anular |
| `RECHAZADA` | ARCA rechazó el comprobante | Editar (vuelve a `PENDIENTE`), anular |
| `RESULTADO_DESCONOCIDO` | Falló la comunicación; no se sabe si ARCA autorizó | Conciliar |
| `ANULADA_LOCAL` | Anulada localmente, sin borrado físico | Ninguna |

Transiciones en `FacturaEstadoMachine.java` (17-30). La anulación de una factura autorizada no se permite desde la aplicación.

## API HTTP

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/facturas` | Listado paginado con filtros |
| `POST` | `/api/facturas` | Crear factura |
| `GET` | `/api/facturas/{id}` | Detalle con renglones |
| `PUT` | `/api/facturas/{id}` | Editar factura editable |
| `DELETE` | `/api/facturas/{id}` | Anular lógicamente |
| `GET` | `/api/facturas/pendientes` | Pendientes de envío |
| `GET` | `/api/facturas/autorizadas` | Autorizadas |
| `POST` | `/api/facturas/envios/arca` | Enviar lote a ARCA |
| `GET` | `/api/facturas/envios/{envioId}` | Resumen del envío |
| `GET` | `/api/facturas/{id}/envios` | Historial ARCA de la factura |
| `POST` | `/api/facturas/{id}/conciliar` | Conciliar resultado incierto |
| `GET` | `/api/facturas/pdf` | PDF de facturas autorizadas |
| `GET` | `/api/facturas/{id}/pdf` | PDF de una factura autorizada |
| `GET` | `/api/puntos-venta` | Catálogo de puntos de venta |
| `GET` | `/api/clientes` | Catálogo de clientes |
| `GET` | `/api/condiciones-iva` | Catálogo de condiciones de IVA |
| `GET` | `/api/tipos-moneda` | Catálogo de monedas |
| `GET` | `/api/listas-precio` | Catálogo de listas de precio |
| `GET` | `/api/listas-precio/{id}/articulos` | Artículos y precios de una lista |



## Pruebas y verificaciones

### Backend

```powershell
cd Back
.\mvnw.cmd test
```

Última ejecución local: **57 pruebas, 57 en verde**.

- El resto cubre alta/edición/anulación, validaciones, filtros, auditoría, concurrencia, datos iniciales y los clientes WSAA/WSFEv1 con HTTP simulado:
  - `FacturaIntegrationTests.java`
  - `FacturaCicloVidaArcaIntegrationTests.java`
  - `AfipWsaaClientTest.java`
  - `AfipWsfeClientTest.java`
  - `ArcaFacturaElectronicaServiceTest.java`
  - `DatosInicialesRunnerTests.java`
  - `ApiTpApplicationTests.java`

### Frontend

```powershell
cd Front
npm ci
npm run build
```

La compilación TypeScript pasa. **No hay pruebas automatizadas**: `npm test` está definido como un comando que falla (`Front/package.json` 9).

### Entorno de las pruebas

Las pruebas de backend corren con H2 en memoria como dependencia de test (`Back/pom.xml` 80), aunque la aplicación de ejecución usa MySQL.

