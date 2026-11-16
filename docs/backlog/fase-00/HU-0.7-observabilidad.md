# HU-0.7: Health checks y OpenTelemetry

**Historia:** como responsable de operar el e-commerce, quiero saber si la aplicación está viva y lista, y ver trazas, métricas y logs de cada petición en Grafana, para encontrar fallas a partir del `X-Trace-Id` que reporta un cliente.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.7-observability` desde `develop` actualizada y abrir el PR hacia `develop`.

## Variantes

| Variante | Cómo funciona | Beneficios | Costo |
|---|---|---|---|
| A. Agente Java de OpenTelemetry | Un `-javaagent` instrumenta todo sin código | Cubre muchas librerías sin configurar | Magia fuera de Spring; difícil de probar y de versionar con el proyecto |
| B. `spring-boot-starter-opentelemetry` | Micrometer Observation con puente a OpenTelemetry y exportación OTLP | Integrado con Spring Boot 4, configurable por propiedades y probable en pruebas | Los logs necesitan un appender aparte |
| C. Solo Micrometer + Prometheus | Métricas sin trazas | Lo más simple | No cumple el rastreo distribuido |

**Elegida: B.** Continúa la variante C de la HU-0.5: `X-Trace-Id` sigue siendo el identificador del cliente y se guarda en cada span para buscarlo en Grafana.

## Dependencias

| Dependencia | Alcance | Para qué |
|---|---|---|
| `spring-boot-starter-actuator` | compile | Health checks |
| `spring-boot-starter-opentelemetry` | compile | Trazas y métricas por OTLP |
| `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` | compile | Enviar los logs por OTLP; la versión se fija en una propiedad del `pom.xml` |

## Servicios locales

Agregar a `compose.yaml` el servicio `observability`:

| Dato | Valor |
|---|---|
| Imagen | `grafana/otel-lgtm`, con la última versión estable fijada |
| Puertos | `127.0.0.1:3000:3000` (Grafana), `127.0.0.1:4317:4317` (OTLP gRPC) y `127.0.0.1:4318:4318` (OTLP HTTP) |
| Healthcheck | Que confirme que Grafana responde |

Documentar en el README la URL de Grafana y su usuario por defecto.

## Configuración por ambiente

Valores nuevos en `.env.example`, `.env` y los dos `*.example.properties` de `src/main/filters`:

| Variable | `local` | `qa` | `prod` |
|---|---|---|---|
| `OTLP_ENDPOINT` | `http://localhost:4318` | Por definir | Por definir |
| `TRACING_SAMPLING_PROBABILITY` | `1.0` | `1.0` | `0.1` |
| `HEALTH_SHOW_DETAILS` | `always` | `never` | `never` |

En `application.properties`, con `@valor@`:

1. Exponer por HTTP **solo** el endpoint `health`.
2. Activar los grupos `liveness` y `readiness`.
3. `readiness` incluye la base de datos; `liveness` no depende de servicios externos.
4. Detalles del health según `HEALTH_SHOW_DETAILS`.
5. Probabilidad de muestreo según `TRACING_SAMPLING_PROBABILITY`.
6. Endpoints OTLP de trazas, métricas y logs a partir de `OTLP_ENDPOINT` (`/v1/traces`, `/v1/metrics` y `/v1/logs`).
7. El patrón de logs muestra los dos identificadores: `[xTraceId] [traceId,spanId]`. `traceId` y `spanId` son las llaves que Micrometer Tracing escribe en el MDC.

## Health checks

| Endpoint | Responde `200` cuando |
|---|---|
| `GET /actuator/health` | Todo está `UP` |
| `GET /actuator/health/liveness` | La aplicación está viva |
| `GET /actuator/health/readiness` | La aplicación puede atender peticiones, incluida la base |

- Responden con el formato de Actuator, no con `ApiResponse`. Es la excepción documentada al formato de respuesta.
- Llevan el header `X-Trace-Id`, porque pasan por `TraceIdFilter`.
- Cualquier otro endpoint de Actuator responde `404`.

## Logs por OTLP

1. Crear `src/main/resources/logback-spring.xml` que conserve la salida a consola de Spring Boot y agregue un `OpenTelemetryAppender` al logger raíz.
2. Instalar el appender con la instancia de `OpenTelemetry` de Spring, desde un bean en `shared.infrastructure.config` (por ejemplo `OpenTelemetryLoggingConfiguration`).

## Relación entre `X-Trace-Id` y la traza

Cada span del servidor HTTP lleva el atributo `x_trace_id` con el valor de `X-Trace-Id`.

- Crear `TraceIdObservationFilter` en `shared.infrastructure.adapter.in.web`, que implementa `ObservationFilter`.
- Si el contexto es un `ServerRequestObservationContext`, lee el atributo `TraceIdContext.KEY` de sus atributos y lo agrega como `highCardinalityKeyValue` `x_trace_id`.
- Es de alta cardinalidad: va en la traza, nunca como etiqueta de métrica.
- Se registra como bean.

## Pruebas esperadas

### `ActuatorHealthTests`

`@SpringBootTest` con puerto aleatorio y `WebTestClient`. Necesita PostgreSQL arriba.

| Prueba | Qué comprueba |
|---|---|
| `healthIsUp` | `/actuator/health` responde `200` y `status` es `UP` |
| `livenessIsUp` | `/actuator/health/liveness` responde `200` |
| `readinessIsUp` | `/actuator/health/readiness` responde `200` |
| `healthHasTraceIdHeader` | La respuesta trae `X-Trace-Id` |
| `otherActuatorEndpointsAreNotExposed` | `/actuator/env` y `/actuator/beans` responden `404` |

### `TraceIdObservationFilterTests`

Prueba unitaria, sin Spring.

| Prueba | Qué comprueba |
|---|---|
| `addsTraceIdToServerObservation` | Con el atributo presente, el contexto queda con `x_trace_id` |
| `ignoresOtherContexts` | Un contexto que no es de servidor HTTP no cambia |
| `ignoresMissingTraceId` | Sin el atributo, no agrega nada |

Las pruebas no exportan a OTLP: Spring Boot desactiva la exportación en pruebas salvo que se pida explícitamente.

## Validación manual

Documenta el resultado en el PR.

1. `docker compose up -d --wait` deja `observability` en `healthy` y Grafana abre en `http://localhost:3000`.
2. Con la app arriba, una petición con un `X-Trace-Id` conocido aparece en Grafana → Explore → Tempo, buscando con TraceQL `{span.x_trace_id="<uuid>"}`.
3. Desde esa traza se ven los logs de la petición en Loki.
4. La métrica `http.server.requests` aparece en Grafana → Explore → Prometheus.
5. Con PostgreSQL detenido, `/actuator/health/readiness` responde `503` y `/actuator/health/liveness` sigue en `200`.

## Criterios de aceptación

- Las pruebas pasan y `mvn clean verify` termina en `BUILD SUCCESS`.
- Las cinco validaciones manuales se cumplen.
- Solo `health` está expuesto.
- No hay endpoints ni credenciales escritos en el código; todo sale de los filtros de Maven.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Alertas por correo, dashboards de Grafana, trazas de RabbitMQ y Redis (llegan con sus features), e infraestructura de observabilidad en `qa` y `prod`.

## Criterios de revisión

- `x_trace_id` no aparece como etiqueta de ninguna métrica.
- `liveness` no depende de la base.
- `prod` muestrea al 10 % y no muestra detalles del health.
- La exportación de logs no duplica ni elimina la salida a consola.
