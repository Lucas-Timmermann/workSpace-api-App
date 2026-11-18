# Contexto — Diagnóstico del error 409 al enviar facturas a ARCA

> Documento de referencia para `Plan.md`.
> Fecha del diagnóstico: 2026-11-18.
> Alcance: backend Spring Boot (`Back/`), base MariaDB `facturacionlombock`, WSFEv1 en homologación.
> Este documento **no propone cambios**; describe qué pasó, con qué evidencia y qué queda abierto.

---

## 1. Resumen ejecutivo

Al enviar el lote de facturas precargadas a ARCA, el frontend recibe:

```
No se pudo completar el envío a ARCA: La solicitud de facturas falló (HTTP 409).
La operación viola una restricción de integridad de datos
```

El 409 **no proviene de ARCA**. Proviene de `GlobalExceptionHandler.handleDataIntegrity`
(`Back/src/main/java/com/example/api_tp/config/GlobalExceptionHandler.java:109-112`) al capturar una
`DataIntegrityViolationException` de MariaDB.

La causa inmediata es que la rama `INCIERTO` de `FacturaService.persistirResultado`
(`FacturaService.java:646`) escribe en `factura_venta.motivo_rechazo` un texto truncado a **1000**
caracteres, pero la columna real es **VARCHAR(255)** y MariaDB está en modo estricto. El `UPDATE`
falla, la transacción del resultado se revierte y la factura queda atascada en `EN_PROCESO_ENVIO`.

Detrás del 409 hay además dos problemas fiscales que impiden que el envío funcione aunque se corrija
la columna:

1. El `CondicionIVAReceptorId` se envía como `CondicionIva.codigoAfip` (1..6) sin mapear contra el
   catálogo real de ARCA. ARCA rechaza los valores 2 y 3 con `10242` y responde fault/HTTP 500 para
   4, 5 y 6.
2. Los códigos de moneda del catálogo (`USD`, `EUR`, `BRL`) no son los `Mon_Id` que espera WSFEv1.
   ARCA devuelve `12000: El código de moneda ingresado es invalido`.

Resultado actual: 21 facturas `PENDIENTE`, 4 `RECHAZADA` y 6 `EN_PROCESO_ENVIO` bloqueadas.

---

## 2. Síntoma y punto de entrada

- Endpoint: `POST /api/facturas/envios/arca` (`FacturaController.java:116`).
- El lote se procesa en forma secuencial en `FacturaService.enviarFacturasAArca` (línea 371).
- La primera excepción no controlada de persistencia corta el lote completo y sale como HTTP 409.
- El frontend muestra el mensaje del `GlobalExceptionHandler`; el detalle SQL no se registra.

---

## 3. Cadena del error

```
POST /api/facturas/envios/arca
  └─ FacturaService.enviarFacturasAArca
       └─ procesarEnvioFacturaIndividual
            ├─ prepararEnvio                     (transacción 1: valida, NO_ENVIADA si falla)
            ├─ resolverCotizacion                (PES → 1.0; extranjera → FEParamGetCotizacion)
            ├─ consultarUltimoComprobanteAutorizado
            ├─ marcarEnProcesoYConstruirSolicitud (transacción 2: COMMIT)
            │     estado = EN_PROCESO_ENVIO
            │     numeroFiscal = próximo (1)
            │     fechaEnvioArca = ahora
            ├─ ARCA FECAEASolicitar
            │     └─ fault / HTTP 500 → resultado INCIERTO
            └─ persistirResultado                 (transacción 3, SIN try/catch)
                  rama INCIERTO:
                    motivoRechazo = truncar(mensaje, 1000)
                    UPDATE factura_venta ...          ← VARCHAR(255) → error 1406
                  rollback
                  └─ excepción → HTTP 409
```

Puntos exactos:

| Elemento | Ubicación |
|---|---|
| Rama APROBADA | `FacturaService.java:613-627` |
| Rama RECHAZADA | `FacturaService.java:628-639` |
| Rama INCIERTO | `FacturaService.java:640-651` |
| Escritura de `motivoRechazo` (RECHAZADA) | `FacturaService.java:632` |
| Escritura de `motivoRechazo` (INCIERTO) | `FacturaService.java:646` |
| Escritura de `observaciones` (APROBADA) | `FacturaService.java:623` |
| `persistirResultado` invocado sin `try/catch` | `FacturaService.java:486` |
| Fallo de cotización invocado sin `try/catch` | `FacturaService.java:438-443` |
| Handler que convierte el error en 409 | `GlobalExceptionHandler.java:109-112` |

---

## 4. Evidencia recolectada

### 4.1 Esquema real (MariaDB 10.4.32)

```
sql_mode = STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,NO_ENGINE_SUBSTITUTION
```

| Tabla | Columna | Tipo real |
|---|---|---|
| `factura_venta` | `motivo_rechazo` | `varchar(255)` |
| `factura_venta` | `observaciones` | `varchar(255)` |
| `factura_venta` | `cae` | `varchar(255)` |
| `factura_venta` | `resultado_afip` | `varchar(255)` |
| `factura_envio_arca` | `resultado_general` | `varchar(255)` |
| `factura_envio_arca` | `resultado_detalle` | `varchar(255)` |
| `factura_envio_arca` | `observaciones` / `errores` / `eventos` | `varchar(2000)` |
| `factura_envio_arca` | `mensaje_tecnico` | `varchar(1000)` |

`factura_venta` solo tiene `PRIMARY KEY (id)` y claves foráneas; **no** hay índice único sobre
`numero_fiscal` ni sobre `numero` más allá de la restricción declarada en la entidad.

### 4.2 Estado de las facturas

| Estado | Cantidad |
|---|---|
| `PENDIENTE` | 21 |
| `RECHAZADA` | 4 |
| `EN_PROCESO_ENVIO` | 6 |

Facturas atascadas (todas en pesos, `numero_fiscal = 1`):

| id | número | punto de venta | condición IVA | moneda | `fecha_envio_arca` |
|---|---|---|---|---|---|
| 8 | 8 | 2 | 5 Consumidor Final | PES | 2026-11-18 12:22:12 |
| 19 | 19 | 2 | 4 IVA Sujeto Exento | PES | 2026-11-18 12:23:06 |
| 21 | 21 | 2 | 5 Consumidor Final | PES | 2026-11-18 12:24:36 |
| 26 | 26 | 3 | 5 Consumidor Final | PES | 2026-11-18 12:37:00 |
| 29 | 29 | 2 | 6 Responsable Monotributo | PES | 2026-11-18 12:41:22 |
| 31 | 31 | 1 | 6 Responsable Monotributo | PES | 2026-11-18 12:29:18 |

Facturas rechazadas limpiamente (`motivo_rechazo` de 225 caracteres, por debajo de 255):

| id | condición IVA | mensaje ARCA |
|---|---|---|
| 3 | 2 IVA Responsable No Inscripto | `10242: El campo Condicion IVA receptor no es un valor permitido` |
| 24 | 3 IVA No Responsable | idem |
| 27 | 2 IVA Responsable No Inscripto | idem |
| 28 | 2 IVA Responsable No Inscripto | idem |

### 4.3 Huellas de los rollbacks

`factura_envio_arca` tiene `AUTO_INCREMENT = 140`, pero faltan los ids:

```
8, 25, 43, 70, 116, 139
```

Son exactamente **6 inserts consumidos y revertidos**, uno por cada factura atascada. No existe
ninguna fila de intento `INCIERTO` en la tabla: todas las ramas INCIERTO fallaron al persistir.

### 4.4 Correlación temporal

- Factura 29 marcada `EN_PROCESO_ENVIO` a las `12:41:22`.
- Error 409 reportado a las `12:41:23.832068800`.
- El siguiente id de `factura_envio_arca` (139) quedó consumido y no visible.

### 4.5 Errores reales de ARCA

| Código | Mensaje | Facturas afectadas |
|---|---|---|
| `10242` | El campo Condicion IVA receptor no es un valor permitido. Consultar `FEParamGetCondicionIvaReceptor` | 3, 24, 27, 28 |
| `12000` | El código de moneda ingresado es invalido. Verificar con `FEParamGetTiposMonedas` | 20 facturas en USD/EUR/BRL |

Mensajes `NO_ENVIADA` de cotización (moneda extranjera):

```
No se pudo determinar la cotización de la moneda USD/EUR/BRL:
WSFEv1 FEParamGetCotizacion devolvió errores:
[12000: El código de moneda ingresado es invalido...]
```

---

## 5. Causa raíz

### 5.1 Causa inmediata del 409

`FacturaVenta.motivoRechazo` no declara longitud (`FacturaVenta.java:58`), por lo que Hibernate creó
`VARCHAR(255)`. El código, en cambio, trunca a 1000 (`FacturaService.java:632,646`).

En la rama `INCIERTO`, el mensaje se arma así:

```
resultadoInciertoTecnico(ex)
  = truncar("Error de comunicación con ARCA (resultado incierto): " + mensajeSeguro(ex), 1000)
```

Longitudes:

| Origen del error | Mensaje aproximado | Total |
|---|---|---|
| `ArcaCommunicationException` HTTP 500 | prefijo 53 + `"WSFEv1 respondió con HTTP 500: "` 31 + body resumido ≤ 203 | **≤ 287** |
| SOAP Fault (`ArcaWsfeException`) | prefijo 53 + `"WSFEv1 Fault [code]: faultstring - detail"` con `detail` sin límite | **> 255 con facilidad** |
| Timeout / IOException | prefijo 53 + mensaje corto | ~90 (no desborda) |

`AfipWsfeClient` resume el body HTTP a 200 caracteres (`AfipWsfeClient.java:550-554`) y extrae el
`detail` completo del SOAP Fault (`AfipWsfeClient.java:382-386,493-498`). Por eso el camino de fault
supera los 255 caracteres.

Con `STRICT_TRANS_TABLES`, MariaDB devuelve error 1406 (`Data too long for column 'motivo_rechazo'`),
Spring lo envuelve en `DataIntegrityViolationException` y el handler responde 409.

### 5.2 Por qué la rama fue INCIERTO y no APROBADA/RECHAZADA

Las facturas atascadas tienen condiciones IVA 4, 5 y 6. Las rechazadas tienen 2 y 3. El patrón indica
que ARCA valida el `CondicionIVAReceptorId` y responde de forma distinta según el valor:

- 2 y 3 → rechazo limpio `10242`, que sí persiste (225 caracteres).
- 4, 5 y 6 → fault/HTTP 500, que cae en `INCIERTO` y desborda `motivo_rechazo`.

Además, en el punto de venta 2 las facturas 8, 19, 21 y 29 obtuvieron todas `numero_fiscal = 1`.
Si alguna hubiera sido autorizada, `FECompUltimoAutorizado` habría devuelto 1 y la siguiente habría
recibido 2. Por lo tanto, **ninguna de las 6 fue autorizada en ARCA**; no hay comprobante fiscal
huérfano. Conviene confirmarlo con `FECompConsultar` antes de reparar.

### 5.3 Por qué el lote entero falla

`persistirResultado` se invoca sin `try/catch` (`FacturaService.java:486`), a diferencia de
`prepararEnvio`. Una excepción de BD escapa de `procesarEnvioFacturaIndividual`, corta el `for` de
`enviarFacturasAArca` y llega al controller. El usuario recibe un único 409 y las facturas
restantes del lote ni siquiera se intentan.

### 5.4 Por qué las facturas quedan bloqueadas

`EN_PROCESO_ENVIO` no es editable ni enviable (`EstadoFactura.java:11-17`) y la máquina de estados
solo permite `EN_PROCESO_ENVIO → AUTORIZADA | RECHAZADA | RESULTADO_DESCONOCIDO`
(`FacturaEstadoMachine.java:22-23`). No hay transición de vuelta a `PENDIENTE` ni endpoint de
reparación. Las 6 facturas no se pueden editar, anular ni reenviar.

---

## 6. Problemas secundarios confirmados

| # | Problema | Evidencia | Impacto |
|---|---|---|---|
| S1 | `CondicionIVAReceptorId` se envía como `codigoAfip` local 1..6 | `FacturaService.java:686,819`; ARCA `10242` | 4 rechazadas y 6 atascadas |
| S2 | Códigos de moneda ISO en lugar de `Mon_Id` de ARCA | `DatosInicialesRunner` (`PES/USD/EUR/BRL`); ARCA `12000` | 20 facturas `NO_ENVIADA`, sin cotización |
| S3 | `numero_fiscal = 1` repetido al reintentar | 6 facturas atascadas con 1 | Riesgo de duplicados cuando ARCA autorice |
| S4 | `EN_PROCESO_ENVIO` sin retorno | `EstadoFactura`, `FacturaEstadoMachine` | 6 facturas bloqueadas |
| S5 | `observaciones` (APROBADA) se escribe sin truncar | `FacturaService.java:623`; columna 255 | Mismo 409 latente en autorizaciones con observaciones largas |
| S6 | `joinNoVacio(mensaje, errores, observaciones)` duplica el texto | `FacturaService.java:632`; 225 chars actuales | Un error más supera 255 y rompe también RECHAZADA |
| S7 | `resultado_general` / `resultado_detalle` sin truncar | `FacturaEnvioArca`; columnas 255 | Bajo riesgo (hoy son `A`/`R`), pero sin invariante |
| S8 | El handler 409 no registra la causa SQL | `GlobalExceptionHandler.java:109-112` | Diagnóstico lento |

---

## 7. Hipótesis descartadas

| Hipótesis | Por qué se descarta |
|---|---|
| ARCA devuelve 409 | ARCA devuelve rechazos/faults SOAP; el 409 es del `GlobalExceptionHandler` |
| Violación de unicidad de `numero` o `numero_fiscal` | El envío no cambia `numero`; el esquema no tiene índice único en `numero_fiscal`; no hay error de duplicate key |
| Violación de NOT NULL / FK | Todos los campos obligatorios están seteados; no hay deletes en el envío |
| Falla de `observaciones` en APROBADA | Ninguna factura fue autorizada (se repite `numero_fiscal=1` por punto) |
| El seed de 30 facturas causó el error | El bug es previo; el seed solo lo hizo visible. Las 4 rechazadas persistieron con 225 chars |
| Timeout de ARCA | Un timeout produce un mensaje de ~90 chars, que no desborda 255 |

---

## 8. Consultas de verificación (solo lectura)

```sql
SELECT @@version, @@sql_mode;

SHOW CREATE TABLE factura_venta;
SHOW CREATE TABLE factura_envio_arca;

SELECT id, numero, estado, numero_fiscal, fecha_envio_arca
FROM factura_venta
WHERE estado = 'EN_PROCESO_ENVIO'
ORDER BY id;

SELECT id, factura_id, numero_intento, estado_intento,
       LENGTH(mensaje_tecnico), mensaje_tecnico
FROM factura_envio_arca
ORDER BY id;

SELECT AUTO_INCREMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'factura_envio_arca';
```

Verificación fiscal previa a la reparación (read-only, vía API/servicio de conciliación o
`FECompConsultar`): confirmar que las combinaciones `(punto de venta, tipo 6, número 1)` de las
facturas 8, 19, 21, 26, 29 y 31 no existen como autorizadas en ARCA.

---

## 9. Datos y configuración relevantes

| Dato | Valor |
|---|---|
| CUIT emisor | `20465471752` |
| Ambiente | `HOMOLOGACION` |
| Puntos de venta locales | 1, 2, 3, 4 (`tipoEmision = CAE`) |
| Facturas en base | 31 (30 del seed + 1 creada manualmente) |
| Monedas del seed | `PES`, `DOL`, `060`, `012` (códigos AFIP; antes `USD`, `EUR`, `BRL`) |
| Condiciones IVA del seed | 1..6 |
| `ddl-auto` | `update` |
| Dialecto | `MariaDBDialect` |

---

## 10. Preguntas abiertas

1. ¿Se prefiere **ampliar** las columnas a 1000/2000 o **truncar** en código a 255? (Recomendado:
   ampliar y truncar en el límite de persistencia.)
2. ¿La recuperación de las 6 facturas se hace con reparación SQL controlada o con una transición
   administrativa nueva en la máquina de estados? (Recomendado: SQL controlado ahora; transición
   reutilizable después.)
3. ¿Se implementan `FEParamGetCondicionIvaReceptor` y `FEParamGetTiposMonedas` como operaciones
   nuevas del cliente, o se resuelve con tablas estáticas validadas una vez? (Recomendado:
   catálogos, igual que `FEParamGetTiposIva` y `FEParamGetPtosVenta`.)
4. ¿Se corrige el catálogo `TipoMoneda` en datos existentes o se agrega una columna de mapeo?
   (Recomendado: corregir datos con migración no destructiva y mantener el código viejo en
   `FacturaVenta.monedaArca` de las facturas ya emitidas.)

---

## 11. Conclusión

El 409 es un defecto de persistencia local, no un rechazo de ARCA: `motivo_rechazo` es
`VARCHAR(255)` y el código escribe hasta 1000 caracteres en la rama `INCIERTO`, bajo MariaDB
estricto. El rollback deja las facturas en `EN_PROCESO_ENVIO`, sin intento registrado y sin vía de
recuperación. Corregir solo la columna evita el 409, pero el envío seguirá fallando por el mapeo de
condición IVA y por los códigos de moneda. El plan debe atacar las tres capas en ese orden:
persistencia, catálogos fiscales y recuperación/numeración.

---

## 12. Resultado de la ejecución (2026-11-18)

### F0 — Datos

- Backup lógico verificado: `backup_facturacionlombock_20261118_131430.sql`,
  31 filas de `factura_venta` + 133 de `factura_envio_arca`, SHA-256 `abf9db31…eda3`.
- `FECompConsultar` (solo lectura) para `(punto, tipo 6, número 1)` de las facturas 8, 19, 21,
  26, 29 y 31: las seis devolvieron `602 - No existen datos en nuestros registros`, es decir
  ninguna estaba autorizada.
- Reparación con IDs explícitos y guarda `estado = 'EN_PROCESO_ENVIO'`: 6 filas a `PENDIENTE`.
  Estado final de la base: 27 `PENDIENTE`, 4 `RECHAZADA`, 0 `EN_PROCESO_ENVIO`.

### F1 — Persistencia

- `factura_venta.motivo_rechazo` y `observaciones` ampliadas a `VARCHAR(1000)` (DDL aplicado y
  verificado en `information_schema`).
- Truncado en el límite de persistencia (`motivoRechazo`, `observaciones`, `resultadoGeneral`,
  `resultadoDetalle`); errores de cotización y de persistencia ya no cortan el lote.
- Compensación `compensarFalloPersistencia`: si falla la transacción del resultado, la factura
  queda `RESULTADO_DESCONOCIDO` con intento `INCIERTO` y mensaje del fallo.
- `GlobalExceptionHandler` registra `getMostSpecificCause().getMessage()` para diagnosticar
  cualquier 409 futuro.

### F2 — Catálogos fiscales

- `AfipWsfeClient`: `FEParamGetCondicionIvaReceptor` con `ClaseCmp` y `FEParamGetTiposMonedas`.
- `ArcaFacturaElectronicaService`: `obtenerCondicionesIvaReceptor(clase)`, `obtenerTiposMonedas`,
  `resolverCondicionIvaReceptorId` y `resolverMonId` (Id exacto o descripción normalizada).
- `FacturaService`: los catálogos se piden una vez por lote; la condición y la moneda resueltas se
  persisten en la factura y se validan antes de llamar a `FECAEASolicitar`. Sin equivalencia o
  sin catálogo: `NO_ENVIADA` con motivo explícito.
- `tipo_moneda` migrada sin pérdida: `USD→DOL`, `EUR→060`, `BRL→012` (3 filas, sin duplicados);
  seed corregido. `PES` se mantiene.
- Catálogo real de condiciones por clase: A/M = {1,6,13,16}; B = {4,5,7,8,9,10,15}; C = todas.
  Por eso una condición 2/3/6 en Factura B queda `NO_ENVIADA` en lugar de recibir `10242`.

### F3 — Estados y numeración

- Todo INCIERTO termina en `RESULTADO_DESCONOCIDO` con intento persistido y se concilia con
  `POST /api/facturas/{id}/conciliar`.
- `RESULTADO_DESCONOCIDO` no es reenviable. Una `RECHAZADA` se edita (vuelve a `PENDIENTE`, limpia
  `numeroFiscal`) y el reenvío consulta de nuevo `FECompUltimoAutorizado` antes de numerar.

### F4 — Verificación

- `mvn -f Back/pom.xml clean test`: 64 tests, 1 fallo ajeno documentado
  (`FacturaIntegrationTests.exportaTodasLasFacturasEnPdfDescargable`, paginación del PDF, falla
  igual sin estos cambios).
- Seed idempotente: `DatosInicialesRunnerTests` ejecuta el runner dos veces y verifica 4 monedas
  con los códigos nuevos.
- **Emisión real en homologación pendiente por bloqueo externo.** WSAA responde
  `coe.alreadyAuthenticated` ("El CEE ya posee un TA valido para el acceso al WSN solicitado")
  porque el TA del envío anterior sigue vigente (~12 h desde 2026-11-18 13:14). El cliente no
  persiste el TA, así que un reinicio durante esa ventana no puede emitir. Reintentar F4-T3
  después de que expire el TA, con una factura PES y luego un lote pequeño.
