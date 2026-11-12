# HU-0.6: Manejo global de errores y validación

**Historia:** como consumidor de la API, quiero que todos los errores lleguen con el mismo formato y un código estable, para mostrar el mensaje correcto sin depender del texto de cada excepción.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.6-error-handling` desde `develop` actualizada y abrir el PR hacia `develop`.

## Dependencias

| Dependencia | Alcance |
|---|---|
| `spring-boot-starter-validation` | compile |

## Formato de error

Todas las respuestas de error usan `ProblemDetail` con `Content-Type: application/problem+json`:

```json
{
  "type": "about:blank",
  "title": "Bad Request",
  "status": 400,
  "detail": "The request contains invalid fields.",
  "instance": "/api/v1/example",
  "code": "VALIDATION_ERROR",
  "timestamp": "2026-11-07T08:30:00Z",
  "traceId": "3f2b8c1e-7a4d-4e9b-9c1a-2d5e6f708192",
  "errors": [
    { "field": "email", "code": "Email", "message": "must be a well-formed email address" }
  ]
}
```

| Campo | Regla |
|---|---|
| `title` | Frase estándar del estado HTTP |
| `detail` | Texto en inglés para el desarrollador; el front no lo muestra |
| `instance` | Ruta de la petición, sin query string |
| `code` | Código estable en `UPPER_SNAKE_CASE`; el front traduce por este valor |
| `timestamp` | Instante en UTC, formato ISO-8601 |
| `traceId` | El mismo valor del header `X-Trace-Id`, leído con `TraceIdContext` (HU-0.5) |
| `errors` | Solo en errores de validación; nombre del campo, anotación que falló y mensaje |

Nunca se exponen stack traces, nombres de clases, SQL ni mensajes internos.

## Excepciones de dominio

En `com.yisoft.development.ecommerce.shared.domain.exception`:

| Clase | Uso | Estado HTTP |
|---|---|---|
| `DomainException` | Clase base abstracta; recibe `code` y `detail` | No se usa directo |
| `NotFoundException` | El recurso no existe | `404` |
| `ConflictException` | Choca con el estado actual, por ejemplo un duplicado | `409` |
| `BusinessRuleException` | Viola una regla de negocio | `422` |

- `DomainException` extiende `RuntimeException` y expone `getCode()`.
- El dominio no conoce HTTP. El mapeo a estados vive solo en el manejador.
- Cada feature crea sus excepciones heredando de estas tres. Por ejemplo, `EmailAlreadyRegisteredException extends ConflictException`.

## Manejador global

`GlobalExceptionHandler` va en `com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web`. Es un `@RestControllerAdvice` que extiende el `ResponseEntityExceptionHandler` **reactivo**, del paquete `org.springframework.web.reactive.result.method.annotation`.

| Situación | Excepción | Estado | `code` |
|---|---|---|---|
| Cuerpo con campos inválidos (`@Valid`) | `WebExchangeBindException` | `400` | `VALIDATION_ERROR` |
| Parámetro de ruta o query inválido | `HandlerMethodValidationException` | `400` | `VALIDATION_ERROR` |
| JSON mal formado o tipo incorrecto | `ServerWebInputException` | `400` | `MALFORMED_REQUEST` |
| Ruta inexistente | La que Spring lance para 404 | `404` | `NOT_FOUND` |
| Método no soportado | `MethodNotAllowedException` | `405` | `METHOD_NOT_ALLOWED` |
| Content-Type no soportado | `UnsupportedMediaTypeStatusException` | `415` | `UNSUPPORTED_MEDIA_TYPE` |
| Recurso de dominio no encontrado | `NotFoundException` | `404` | El de la excepción |
| Conflicto de dominio | `ConflictException` | `409` | El de la excepción |
| Regla de negocio | `BusinessRuleException` | `422` | El de la excepción |
| `X-Trace-Id` inválido, rechazado en `TraceIdFilter` | `ResponseStatusException` desde un filtro | `400` | `INVALID_TRACE_ID` |
| Cualquier otra excepción | `Exception` | `500` | `INTERNAL_ERROR` |

Reglas:

- Las excepciones de Spring se resuelven sobrescribiendo los métodos de `ResponseEntityExceptionHandler`, no con un `@ExceptionHandler` duplicado.
- En `500`, `detail` es siempre `An unexpected error occurred.`.
- Los `4xx` no se registran como `ERROR`. Los `500` se registran como `ERROR` con la excepción completa.
- La construcción del `ProblemDetail` con `code` y `timestamp` vive en un solo método privado o clase de apoyo; no se repite por handler.
- Los errores lanzados en filtros no llegan al `@RestControllerAdvice`. Para que también respondan con `ProblemDetail`, se agrega un `WebExceptionHandler` con prioridad sobre el de Spring Boot, que reutiliza la misma construcción del `ProblemDetail`.
- `timestamp` se obtiene de un `Clock` inyectado, para poder fijarlo en las pruebas. Agrega un bean `Clock.systemUTC()` en una clase de configuración en `shared.infrastructure.config`.

## Pruebas esperadas

Crear `GlobalExceptionHandlerTests` en `com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web`, dentro de `src/test/java`, con `@WebFluxTest` y `WebTestClient`. No levanta base de datos.

Para provocar cada error, crear en el mismo paquete de prueba un controlador que **solo existe en `src/test`**, `ErrorHandlingTestController`, con:

- `POST /test/validation` que recibe un `record` con `@NotBlank name` y `@Email email`, y responde `204`.
- `GET /test/not-found`, `GET /test/conflict`, `GET /test/business-rule` y `GET /test/unexpected`, que regresan `Mono.error(...)` con la excepción correspondiente.

| Prueba | Qué comprueba |
|---|---|
| `validBodyReturnsNoContent` | Un cuerpo válido responde `204` |
| `invalidBodyReturnsValidationError` | `400`, `VALIDATION_ERROR` y un elemento en `errors` por cada campo inválido |
| `malformedJsonReturnsMalformedRequest` | `400` y `MALFORMED_REQUEST` |
| `unknownRouteReturnsNotFound` | `404` y `NOT_FOUND` |
| `wrongMethodReturnsMethodNotAllowed` | `405` y `METHOD_NOT_ALLOWED` |
| `wrongContentTypeReturnsUnsupportedMediaType` | `415` y `UNSUPPORTED_MEDIA_TYPE` |
| `notFoundExceptionReturns404` | `404` y el `code` de la excepción |
| `conflictExceptionReturns409` | `409` y el `code` de la excepción |
| `businessRuleExceptionReturns422` | `422` y el `code` de la excepción |
| `unexpectedExceptionReturns500` | `500`, `INTERNAL_ERROR` y el `detail` genérico, sin el mensaje original |
| `problemResponseHasCommonFields` | `Content-Type` es `application/problem+json` y vienen `instance`, `code`, `timestamp` y `traceId` |
| `invalidTraceIdReturnsProblemDetail` | `X-Trace-Id: abc` responde `400`, `INVALID_TRACE_ID` y formato `ProblemDetail` |
| `errorTraceIdMatchesHeader` | `traceId` del cuerpo es igual al header `X-Trace-Id` |

`@WebFluxTest` solo carga la capa web: si el manejador, `TraceIdFilter` o el bean `Clock` no se detectan, impórtalos con `@Import`.

## Criterios de aceptación

- Las trece pruebas pasan y `mvn clean verify` termina en `BUILD SUCCESS`.
- Ninguna respuesta expone detalles internos.
- El controlador de prueba no existe en `src/main`.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Errores de seguridad (`401` y `403`), traducción de mensajes y códigos de cada feature.

## Criterios de revisión

- El dominio no importa clases de Spring Web.
- Se usa el `ResponseEntityExceptionHandler` reactivo, no el de MVC.
- El mapeo de estados está en un solo lugar.
- Los `500` no filtran el mensaje original.
- No hay `.block()`.
