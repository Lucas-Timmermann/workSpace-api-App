# Plan de corrección — Error 409 al enviar facturas a ARCA

> Documento de ejecución. Se apoya en `context.md` (diagnóstico completo, evidencia y causas).
> Fecha: 2026-11-18.
> Estado: **propuesto, sin implementar**.
> Regla general: si una tarea no está listada en este plan, **no se hace**.

---

## 1. Objetivo

Eliminar el HTTP 409 de `POST /api/facturas/envios/arca`, evitar que un error de persistencia deje
facturas bloqueadas en `EN_PROCESO_ENVIO`, y corregir los datos fiscales que ARCA rechaza
(condición IVA del receptor y códigos de moneda), de modo que el envío de facturas pendientes
funcione en homologación sin perder trazabilidad ni historial.

### Resultados esperados

1. Un mensaje largo de ARCA (HTTP 500 / SOAP Fault / INCIERTO) se persiste sin 409.
2. Un fallo de persistencia de una factura no aborta el lote completo.
3. Ninguna factura queda en `EN_PROCESO_ENVIO` sin intento registrado ni vía de recuperación.
4. Las condiciones IVA y monedas enviadas a WSFEv1 son las que ARCA acepta.
5. Las 6 facturas atascadas quedan recuperadas y las 30 del seed siguen intactas.
6. La suite `mvn -f Back/pom.xml clean test` pasa.

---

## 2. Restricciones del plan (obligatorias)

Estas reglas existen para que la corrección no se convierta en un refactor abierto. Cualquier
necesidad que no encaje se registra en la sección 12 y se detiene la ejecución.

### 2.1 Alcance

- **R1.** Solo se implementan las tareas listadas en la sección 4. No se agregan mejoras,
  refactors, renombres, formateos ni “ya que estamos”.
- **R2.** No se modifica `Front/` ni el contrato de respuesta (`FacturaReporteDTO`, DTOs de salida,
  códigos HTTP existentes).
- **R3.** No se crean endpoints, DTOs, servicios ni repositorios nuevos, salvo las operaciones de
  catálogo ARCA explícitamente listadas en F2 (que siguen el patrón existente de
  `feParamGetTiposIva` / `feParamGetPtosVenta`).
- **R4.** No se agregan dependencias al `pom.xml`.
- **R5.** No se modifican reglas de negocio fiscales fuera de los mapeos S1 (condición IVA) y S2
  (moneda) descritos en `context.md §6`. El resto de las reglas (tipo de comprobante, concepto,
  documento, alícuotas, totales) queda como está.

### 2.2 Datos y base

- **R6.** Prohibido `DROP`, `TRUNCATE` y `DELETE` sobre tablas de facturación. Las correcciones de
  esquema son **solo ampliaciones** (`ALTER ... MODIFY ... VARCHAR(n)`).
- **R7.** Antes de cualquier script de reparación: backup de `factura_venta` y
  `factura_envio_arca`, y ejecución dentro de una transacción con verificación de filas afectadas.
- **R8.** La reparación de datos usa IDs explícitos y una guarda de estado (`AND estado =
  'EN_PROCESO_ENVIO'`). Nunca un `UPDATE` masivo sin `WHERE`.
- **R9.** No se altera información fiscal histórica de facturas `AUTORIZADA` (`cae`, `numero_fiscal`,
  `moneda_arca`, `condicion_iva_receptor_arca`, importes, fechas).
- **R10.** No se cambia la cantidad ni la idempotencia del seed de 30 facturas. Si se corrigen
  códigos de moneda del seed, se actualiza también su test de conteo para que siga verificando
  exactamente 4 monedas sin duplicados.

### 2.3 Pruebas y verificación

- **R11.** Prohibido emitir comprobantes reales contra ARCA en tests automatizados. Todo test usa
  mocks de `AfipWsfeClient` / `AfipWsaaClient`.
- **R12.** Toda tarea de código debe dejar al menos un test o una verificación automatizada del
  escenario que corrige (ver sección 8).
- **R13.** No se avanza de fase sin cumplir los criterios de aceptación de la fase anterior
  (sección 6).
- **R14.** La suite completa debe pasar antes de cualquier commit. Si un test ajeno falla, se
  documenta como hallazgo; no se “arregla” fuera de alcance.
- **R15.** Cada commit referencia el ID de tarea (`F1-T2: ...`) para mantener trazabilidad con este
  plan.

### 2.4 Prohibiciones explícitas

- No cambiar `EstadoFactura` ni `FacturaEstadoMachine` para “permitir todo”. La recuperación se hace
  con la transición existente a `RESULTADO_DESCONOCIDO` y/o reparación SQL controlada (F0).
- No tocar la lógica de emisión, numeración fiscal ni conciliación más allá de lo listado.
- No silenciar excepciones con `catch` vacíos. Todo `catch` debe registrar y devolver un resultado
  por factura.
- No reemplazar `double` por `BigDecimal` ni cambiar tipos de entidades (deuda previa, fuera de
  alcance).
- No introducir valores fiscales por defecto silenciosos. Si falta un mapeo, se devuelve
  `NO_ENVIADA` con motivo explícito.

---

## 3. Alcance

### Dentro del alcance

| Área | Archivos previstos |
|---|---|
| Persistencia del resultado | `FacturaService.java`, `FacturaVenta.java`, `FacturaEnvioArca.java`, `GlobalExceptionHandler.java` |
| Catálogos ARCA | `AfipWsfeClient.java`, `ArcaFacturaElectronicaService.java`, `FacturaService.java` |
| Datos de moneda | `DatosInicialesRunner.java`, migración SQL de `tipo_moneda` |
| Esquema | `ALTER TABLE` de ampliación de columnas |
| Pruebas | `FacturaCicloVidaArcaIntegrationTests`, `FacturaIntegrationTests`, `DatosInicialesRunnerTests`, `AfipWsfeClientTest` |
| Documentación | `context.md`, `Plan.md` (actualización de estado al cerrar) |

### Fuera del alcance

- Frontend y experiencia de usuario.
- Notas de crédito/débito, Factura C, emisor no RI.
- QR fiscal, caché persistente de catálogos, reintentos con circuit breaker.
- Migración de `double` a `BigDecimal`, `estado` como enum persistido, locks distribuidos.
- Habilitación de puntos de venta en ARCA (configuración externa).
- Cambios de numeración distintos de evitar reutilizar un número ya consumido.

---

## 4. Fases y tareas

Convención: **P0** = bloqueante del 409; **P1** = necesario para que el envío funcione.

### Fase 0 — Contención y reparación de datos (P0)

| ID | Tarea | Detalle | Aceptación |
|---|---|---|---|
| F0-T1 | Congelar reintentos | No volver a llamar `POST /api/facturas/envios/arca` hasta cerrar F1 y F2. Cada reintento vuelve a atascar facturas | Sin nuevos `EN_PROCESO_ENVIO` |
| F0-T2 | Backup | Backup lógico de `factura_venta` y `factura_envio_arca` con fecha | Backup verificable y restaurable |
| F0-T3 | Verificar estado en ARCA | Consultar `FECompConsultar` para `(punto, tipo 6, número 1)` de las facturas 8, 19, 21, 26, 29 y 31. Es solo lectura | Confirmado que no están autorizadas |
| F0-T4 | Reparar las 6 facturas | Si F0-T3 confirma que no están autorizadas, devolverlas a `PENDIENTE` con el script de la sección 9 | 6 filas actualizadas; 0 `EN_PROCESO_ENVIO`; historial `AUTORIZADA` intacto |
| F0-T5 | Registrar evidencia | Guardar salida de F0-T2/F0-T3/F0-T4 en el registro de la corrección | Evidencia adjunta |

**Criterio de salida F0:** no quedan facturas en `EN_PROCESO_ENVIO` y existe backup.

> Si F0-T3 indica que alguna factura sí fue autorizada, **no** se resetea: se registra su CAE y
> número fiscal y se marca `AUTORIZADA`. Esa variante se documenta antes de ejecutar.

---

### Fase 1 — Persistencia del resultado y fin del 409 (P0)

| ID | Tarea | Detalle | Aceptación |
|---|---|---|---|
| F1-T1 | Ampliar columnas | `FacturaVenta.motivoRechazo` y `FacturaVenta.observaciones` con `@Column(length = 1000)`. DDL explícito de la sección 9. Mantener `truncar(..., 1000)` | Columnas reales `VARCHAR(1000)` |
| F1-T2 | Truncado en el límite de persistencia | Un único helper `truncarA(valor, max)` aplicado a todo texto que va a columna: `motivoRechazo`, `observaciones`, `resultadoGeneral`, `resultadoDetalle`. Sin truncados dispersos | Ningún texto supera la columna |
| F1-T3 | Proteger el lote | Envolver `persistirResultado` (línea 486) y el fallo de cotización (línea 438) en `try/catch` que devuelva un ítem de resultado y continúe el `for` | Un fallo no corta el lote; la respuesta sigue siendo 200 con detalle por factura |
| F1-T4 | Estado recuperable ante fallo de persistencia | Si falla la transacción del resultado, ejecutar una transacción de compensación que deje la factura en `RESULTADO_DESCONOCIDO` y registre un intento `INCIERTO` con el error de persistencia truncado | Nunca queda `EN_PROCESO_ENVIO` sin intento; la factura se puede conciliar |
| F1-T5 | Diagnóstico del 409 | En `GlobalExceptionHandler`, registrar `ex.getMostSpecificCause().getMessage()` antes de responder | El log identifica tabla/columna si vuelve a ocurrir |
| F1-T6 | Pruebas F1 | INCIERTO con mensaje > 255; APROBADA con observaciones > 255; lote con una factura que falla y otra OK | Ver sección 8, casos T1–T3 |

**Criterio de salida F1:** un INCIERTO con mensaje de 500+ caracteres se persiste, la factura queda
`RESULTADO_DESCONOCIDO`, el lote responde 200 y no aparece ningún 409.

---

### Fase 2 — Catálogos fiscales de ARCA (P1)

| ID | Tarea | Detalle | Aceptación |
|---|---|---|---|
| F2-T1 | Catálogo de condición IVA receptor | Implementar `feParamGetCondicionIvaReceptor` en `AfipWsfeClient` y `obtenerCondicionesIvaReceptor()` en `ArcaFacturaElectronicaService`, con el mismo patrón de parseo que `feParamGetTiposIva` | El servicio devuelve el catálogo real |
| F2-T2 | Resolver la condición IVA | Mapear `CondicionIva` local → código de `CondicionIVAReceptorId` usando el catálogo (código/descripción), no `codigoAfip` a ciegas. Persistir el código resuelto en `FacturaVenta.condicionIvaReceptorArca`. Si no hay equivalencia: `NO_ENVIADA` con motivo explícito y sin llamar a ARCA | Facturas con condición 2/3/4/5/6 dejan de recibir `10242` |
| F2-T3 | Catálogo de monedas | Implementar `feParamGetTiposMonedas` y resolver el `Mon_Id` real para cada `TipoMoneda`. Usar el mismo código en `FEParamGetCotizacion` y en `FeCaeDetalle.monId` | Facturas USD/EUR/BRL dejan de recibir `12000` |
| F2-T4 | Corregir datos de moneda | Actualizar `tipo_moneda.codigo_afip` con el mapeo que devuelva el catálogo (migración no destructiva, sección 9) y corregir `MONEDAS` del seed. Cuidar la clave natural: `obtenerOCrearTipoMoneda` busca por `codigoAfip`; migrar primero y luego cambiar el seed para no crear filas duplicadas. Actualizar el test de conteo a 4 monedas | 4 monedas, sin duplicados, con códigos válidos |
| F2-T5 | Validación previa al envío | En `prepararEnvio`/`validarDatosFiscalesParaEnvio`, validar condición y moneda contra los catálogos, igual que se hace con puntos de venta y alícuotas | Código inválido → `NO_ENVIADA`, sin llamada a `FECAEASolicitar` |
| F2-T6 | Pruebas F2 | Catálogos mockeados: mapeo correcto, código sin equivalencia → `NO_ENVIADA`, moneda válida → cotización | Ver sección 8, casos T4–T6 |

**Criterio de salida F2:** una factura PES con condición IVA válida llega a ARCA y recibe una
respuesta de negocio (A/R/observación), no `10242` ni fault por condición. Una factura USD obtiene
cotización y `Mon_Id` válido, no `12000`.

---

### Fase 3 — Numeración, estados y recuperación (P1)

| ID | Tarea | Detalle | Aceptación |
|---|---|---|---|
| F3-T1 | INCIERTO siempre conciliable | Asegurar que todo INCIERTO (técnico o de negocio) termina en `RESULTADO_DESCONOCIDO` con intento persistido | El endpoint `POST /api/facturas/{id}/conciliar` funciona sobre esas facturas |
| F3-T2 | No reutilizar número consumido | Tras un INCIERTO, no reenviar el mismo `numeroFiscal` sin conciliar. Tras un RECHAZADA, volver a consultar `FECompUltimoAutorizado` antes de reenviar | No se repite el mismo número para el mismo punto/tipo |
| F3-T3 | Recuperación de `EN_PROCESO_ENVIO` | La compensación de F1-T4 cubre fallos futuros. Para el histórico, F0-T4. No se agregan endpoints nuevos | No quedan facturas sin salida |
| F3-T4 | Pruebas F3 | INCIERTO → conciliación → AUTORIZADA/RECHAZADA; reintento tras RECHAZADA con numeración nueva | Ver sección 8, casos T7–T8 |

**Criterio de salida F3:** una factura INCIERTO se puede conciliar y una RECHAZADA se puede
reenviar sin repetir número.

---

### Fase 4 — Verificación integral y cierre (P1)

| ID | Tarea | Detalle | Aceptación |
|---|---|---|---|
| F4-T1 | Suite completa | `mvn -f Back/pom.xml clean test` | Todo verde (salvo fallo ajeno documentado) |
| F4-T2 | Verificación de seed | Reiniciar contexto dos veces: 30 facturas, sin duplicados, 4 monedas sin duplicados | `DatosInicialesRunnerTests` verde |
| F4-T3 | Emisión controlada en homologación | Enviar **una** factura PES con condición/punto válidos, verificar CAE y estado en base. Luego un lote pequeño | 1 autorizada con CAE; sin 409; sin atascadas |
| F4-T4 | Actualizar documentos | Marcar tareas cerradas en `Plan.md` y agregar resultado final en `context.md` | Documentos coherentes |
| F4-T5 | Informe final | Cambios, evidencia, pruebas, riesgos y pendientes | Entregado |

**Criterio de salida F4:** envío real controlado exitoso en homologación (o bloqueo externo de ARCA
documentado con evidencia) y suite verde.

---

## 5. Cambios por archivo (resumen)

| Archivo | Fase | Cambio |
|---|---|---|
| `Back/src/main/java/com/example/api_tp/model/FacturaVenta.java` | F1 | `@Column(length = 1000)` en `motivoRechazo` y `observaciones` |
| `Back/src/main/java/com/example/api_tp/service/FacturaService.java` | F1/F2/F3 | Truncado central, try/catch de resultado y cotización, compensación a `RESULTADO_DESCONOCIDO`, resolución de condición/moneda, no reutilizar número |
| `Back/src/main/java/com/example/api_tp/config/GlobalExceptionHandler.java` | F1 | Log de la causa SQL |
| `Back/src/main/java/com/example/api_tp/client/AfipWsfeClient.java` | F2 | `feParamGetCondicionIvaReceptor`, `feParamGetTiposMonedas` |
| `Back/src/main/java/com/example/api_tp/service/arca/ArcaFacturaElectronicaService.java` | F2 | `obtenerCondicionesIvaReceptor`, `obtenerTiposMonedas`, resolución de códigos |
| `Back/src/main/java/com/example/api_tp/config/DatosInicialesRunner.java` | F2 | Códigos de moneda del seed corregidos |
| `Back/src/main/resources/application.properties` | — | Sin cambios |
| Migración SQL (script de despliegue) | F0/F1/F2 | Ampliación de columnas, reparación de las 6 facturas, actualización de `tipo_moneda` |
| Tests citados en la sección 8 | F1–F4 | Nuevos casos y ajustes de expectativas |

---

## 6. Orden de ejecución y puertas

```
F0 (datos) ──▶ F1 (persistencia) ──▶ F2 (catálogos) ──▶ F3 (estados) ──▶ F4 (verificación)
```

- No se solapan fases.
- Una fase no se cierra sin su criterio de salida y sus tests.
- Si una tarea revela un problema nuevo, se registra en la sección 12 y se **detiene** la fase hasta
  decidir si entra o no en el plan.

---

## 7. Criterios de aceptación globales

1. `POST /api/facturas/envios/arca` nunca responde 409 por longitud de texto ni por un fallo de
   persistencia de una factura.
2. Un mensaje de ARCA de hasta 1000 caracteres se guarda completo o truncado explícitamente, sin
   error de base.
3. Un lote con una factura problemática procesa las demás y devuelve el detalle por factura.
4. Ninguna factura queda `EN_PROCESO_ENVIO` sin intento `INCIERTO`/`NO_ENVIADA` y sin posibilidad de
   conciliación.
5. Condición IVA y moneda se validan contra los catálogos de ARCA antes de emitir.
6. No hay duplicados de monedas ni de facturas del seed al reiniciar.
7. Las facturas `AUTORIZADA` conservan intactos sus datos fiscales.
8. El contrato de respuesta del frontend no cambia.
9. La suite completa pasa.

---

## 8. Pruebas y matriz de verificación

| ID | Escenario | Preparación | Resultado esperado |
|---|---|---|---|
| T1 | INCIERTO con mensaje largo | Mock `feCaeSolicitar` lanza `ArcaCommunicationException` con body de 500 chars | HTTP 200 en el lote; factura `RESULTADO_DESCONOCIDO`; intento `INCIERTO` persistido; sin 409 |
| T2 | APROBADA con observaciones largas | Mock devuelve CAE y observaciones de 600 chars | Factura `AUTORIZADA`; `observaciones` ≤ 1000; sin 409 |
| T3 | Lote con una factura que falla al persistir | Forzar error en una factura y dejar otra válida | Ambas se procesan; la respuesta detalla el error de la primera |
| T4 | Condición IVA sin equivalencia | Catálogo mock sin el código local | `NO_ENVIADA`; no se llama `feCaeSolicitar` |
| T5 | Condición IVA válida | Catálogo mock con equivalencia | Se envía el código ARCA correcto |
| T6 | Moneda válida / inválida | Catálogo mock con `Mon_Id` y cotización | Válida: envía y cotiza; inválida: `NO_ENVIADA` |
| T7 | Conciliación de INCIERTO | Factura `RESULTADO_DESCONOCIDO` | `POST /{id}/conciliar` la pasa a `AUTORIZADA` o `RECHAZADA` |
| T8 | Reintento tras RECHAZADA | Factura `RECHAZADA` | Reenvía con numeración recalculada, sin repetir número |
| T9 | Seed idempotente | `runner.run()` dos veces | 30 facturas, 4 monedas, 0 duplicados |
| T10 | Historial protegido | Factura `AUTORIZADA` | Edición/anulación sigue rechazada; datos fiscales intactos |

Comando único de verificación:

```bash
mvn -f Back/pom.xml clean test
```

---

## 9. Scripts de referencia

> Ejecutar solo con backup previo, en ventana de mantenimiento y con la aplicación detenida.
> Los valores de moneda son un ejemplo: deben salir de `FEParamGetTiposMonedas`.

### 9.1 Ampliación de columnas (F1-T1)

```sql
ALTER TABLE factura_venta
  MODIFY motivo_rechazo VARCHAR(1000) NULL,
  MODIFY observaciones VARCHAR(1000) NULL;
```

Verificación:

```sql
SELECT column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'factura_venta'
  AND column_name IN ('motivo_rechazo', 'observaciones');
```

### 9.2 Reparación de las 6 facturas atascadas (F0-T4)

Requisito: F0-T3 confirmó que no están autorizadas en ARCA.

```sql
START TRANSACTION;

UPDATE factura_venta
SET estado = 'PENDIENTE',
    resultado_afip = 'PENDIENTE',
    numero_fiscal = NULL,
    fecha_envio_arca = NULL,
    motivo_rechazo = NULL,
    fecha_modificacion = NOW(6),
    usuario_modificacion_id = 1
WHERE id IN (8, 19, 21, 26, 29, 31)
  AND estado = 'EN_PROCESO_ENVIO';

-- Debe devolver 6. Si devuelve otro número, ROLLBACK y revisar.
SELECT ROW_COUNT();

COMMIT;
```

No hay filas de `factura_envio_arca` que borrar: los intentos de esas facturas se revirtieron.

### 9.3 Actualización de monedas (F2-T4)

1. Obtener el catálogo real con `FEParamGetTiposMonedas`.
2. Construir el mapeo `código actual → Mon_Id`.
3. Actualizar por denominación, no por posición:

```sql
START TRANSACTION;

UPDATE tipo_moneda SET codigo_afip = '<MON_ID_DOLAR>'
WHERE denominacion = 'Dólar Estadounidense' AND codigo_afip = 'USD';

UPDATE tipo_moneda SET codigo_afip = '<MON_ID_EURO>'
WHERE denominacion = 'Euro' AND codigo_afip = 'EUR';

UPDATE tipo_moneda SET codigo_afip = '<MON_ID_REAL>'
WHERE denominacion = 'Real Brasileño' AND codigo_afip = 'BRL';

COMMIT;
```

4. Recién después, actualizar `MONEDAS` en `DatosInicialesRunner` con los mismos códigos.
5. Ejecutar el seed dos veces y verificar que `tipo_moneda` sigue teniendo 4 filas.

---

## 10. Rollback

| Cambio | Rollback |
|---|---|
| Ampliación de columnas | No requiere rollback funcional; si hiciera falta, volver a `VARCHAR(255)` solo con datos que entren |
| Reparación de las 6 facturas | Restaurar desde el backup de F0-T2 |
| Códigos de moneda | Restaurar `tipo_moneda` desde backup |
| Cambios de código | Revertir el commit de la tarea; la suite debe seguir pasando |
| Emisión controlada en homologación | No se puede deshacer un CAE; por eso F4-T3 se hace con **una** factura y después del resto de las fases |

---

## 11. Riesgos

| Riesgo | Probabilidad | Mitigación |
|---|---|---|
| El 409 viene de otra columna además de `motivo_rechazo` | Baja | F1-T5 registra la causa SQL; T1/T2 cubren las dos columnas de 255 |
| ARCA sigue rechazando por otra regla no diagnosticada | Media | F2 valida catálogos; F4-T3 prueba una factura antes del lote |
| Cambiar códigos de moneda duplica filas del seed | Media | Migrar datos antes de cambiar el seed; T9 verifica 4 monedas |
| Puntos de venta no habilitados en homologación | Media | Es configuración externa; se documenta como bloqueo, no se parchea con datos falsos |
| La reparación SQL toca una factura autorizada | Baja | F0-T3 + guarda `estado = 'EN_PROCESO_ENVIO'` + backup |
| Un fallo de compensación vuelve a dejar `EN_PROCESO_ENVIO` | Baja | F1-T4 usa textos truncados y una transacción separada; T1 lo verifica |

---

## 12. Hallazgos fuera de alcance (registro)

> Completar solo si aparece algo nuevo. **No implementar** sin actualizar este plan.

| Fecha | Hallazgo | Archivo/área | Decisión |
|---|---|---|---|
| 2026-11-18 | El cliente WSAA no persiste el TA (Ticket de Acceso). Un reinicio de la app durante la ventana del TA (~12 h) deja toda llamada a ARCA con `coe.alreadyAuthenticated` / HTTP 500 | `Back/src/main/java/com/example/api_tp/client/AfipWsaaClient.java` | No se implementa en este plan (R3/R4). Queda documentado como riesgo operativo para F4-T3 |
| 2026-11-18 | `FEParamGetCondicionIvaReceptor` exige `ClaseCmp` para devolver el subconjunto por clase de comprobante; el catálogo es por clase (A/B/C/M) | `AfipWsfeClient`, `ArcaFacturaElectronicaService` | Implementado en F2 con catálogos A y B separados |
| 2026-11-18 | Fallo preexistente de baseline: `FacturaIntegrationTests.exportaTodasLasFacturasEnPdfDescargable` espera 2+ páginas y obtiene 1; no toca ARCA ni persistencia | `FacturaIntegrationTests` | Ajeno a este plan; se documenta y no se corrige (F4-T1 lo admite) |

---

## 13. Definition of Done

- [x] F0: backup, verificación ARCA y 6 facturas reparadas; 0 `EN_PROCESO_ENVIO`.
- [x] F1: columnas ampliadas, truncado central, lote resiliente, compensación, log de causa.
- [x] F2: catálogos de condición IVA y moneda implementados y validados; datos de moneda corregidos.
- [ ] F3: INCIERTO conciliable y numeración no reutilizada.
- [ ] T1–T10 implementados y verdes.
- [ ] `mvn -f Back/pom.xml clean test` verde.
- [ ] Una emisión controlada en homologación con CAE (o bloqueo externo documentado).
- [ ] `context.md` y `Plan.md` actualizados con el resultado.
- [ ] Sin cambios en `Front/`, sin endpoints/DTOs nuevos, sin dependencias nuevas.
- [ ] Informe final entregado con evidencia y pendientes.

---

## 14. Trazabilidad con `context.md`

| Tarea | Sección de `context.md` |
|---|---|
| F0-T3, F0-T4 | §4.2, §4.4, §5.2, §9 |
| F1-T1, F1-T2 | §4.1, §5.1, §6 (S5, S6, S7) |
| F1-T3, F1-T4 | §5.3, §5.4 |
| F1-T5 | §5.1, §6 (S8) |
| F2-T1…F2-T5 | §4.5, §5.2, §6 (S1, S2) |
| F3-T1…F3-T3 | §5.2, §5.4, §6 (S3, S4) |
| F4 | §4, §8, §11 |
