# HU-0.6: Manejo global de errores y validación

**Historia:** como consumidor de la API, quiero que todos los errores lleguen con el formato del RFC 9457, un tipo de problema estable y el `traceId`, para mostrar el mensaje correcto y poder rastrear cualquier falla.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.6-error-handling` desde `develop` actualizada y abrir el PR hacia `develop`.

## Decisión previa: endpoints funcionales

El proyecto usa endpoints funcionales: `RouterFunction` para las rutas y handlers para la lógica HTTP. Esto define cómo se manejan los errores:

| Componente | Anotados (`@RestController`) | Funcionales (`RouterFunction`) | Filtros |
|---|---|---|---|
| `@RestControllerAdvice` | Sí | No | No |
| `WebExceptionHandler` | Sí | Sí | Sí |

## Variantes

| Variante | Cómo funciona | Beneficios | Costo |
|---|---|---|---|
| A. Advice + manejador de filtros | `@RestControllerAdvice` y un `WebExceptionHandler` aparte | Spring resuelve validación y errores HTTP | No cubre endpoints funcionales |
| B. Un solo `WebExceptionHandler` | Todo error pasa por una clase | Un único punto para controladores, rutas funcionales y filtros | Cada excepción de Spring se traduce a mano |
| C. A con Java moderno | A con excepciones `sealed` y `switch` de patrones | El compilador avisa si falta mapear un error | No cubre endpoints funcionales |

**Elegida: B, con excepciones `sealed` y `switch` de patrones.** Cubre el estilo funcional y conserva la ayuda del compilador.

## Conceptos nuevos

| Concepto | Qué es | Por qué importa aquí |
|---|---|---|
| `WebExceptionHandler` | Componente que recibe cualquier error que no se resolvió antes | Es el único punto por donde pasan los errores de filtros, rutas funcionales y controladores |
| `@Order` en manejadores de errores | Spring Boot registra su propio manejador con orden `-1` | El nuestro usa `-2` para ejecutarse antes |
| Respuesta comprometida | Si los headers ya se enviaron, no se puede escribir otra respuesta | El manejador revisa `isCommitted()` y, en ese caso, deja pasar el error |
| `ProblemDetail` | Clase de Spring que implementa el formato de error del RFC 9457 | Evita crear un formato propio; sus extensiones se escriben como campos de primer nivel |
| `application/problem+json` | Media type del RFC 9457; el sufijo `+json` indica que el cuerpo es JSON | Le dice al cliente que el cuerpo es un error con este formato, sin revisar el estado |
| JSON Pointer | Ruta a un valor dentro de un JSON, del RFC 6901, como `#/items/0/quantity` | Indica en `errors` qué campo del cuerpo falló |
| Clases `sealed` | Una clase que declara exactamente qué clases pueden heredarla | El compilador conoce todos los tipos de error del dominio |
| `non-sealed` | Una subclase que vuelve a permitir herencia libre | Cada feature crea sus excepciones heredando de las tres categorías |
| `switch` de patrones | `case NotFoundException e -> ...` compara por tipo y declara la variable | Reemplaza cadenas de `instanceof` |
| Exhaustividad | Un `switch` sobre un tipo `sealed` sin `default` | Si se agrega una categoría y no se mapea, el código no compila |
| Dominancia | Un `case` de una clase padre antes que el de su hija es un error de compilación | Obliga a ordenar los casos de lo específico a lo general |
| Guardas `when` | `case ResponseStatusException e when ...` agrega una condición al patrón | Distingue el `404` de ruta inexistente de otros estados |
| `Validator` de Jakarta | El motor de `@NotBlank`, `@Email` y demás | En el estilo funcional no existe `@Valid`; se valida a mano |
| `Clock` inyectado | Fuente de la hora que se puede reemplazar | Las pruebas fijan la hora y comparan `timestamp` exacto |

## Pasos

Se resuelven uno por uno en el chat. Cada paso se revisa antes del siguiente.

1. Excepciones de dominio `sealed`.
2. Excepciones de la capa web y ajuste de `TraceIdFilter`.
3. `ValidationConfiguration` y `RequestValidator` para validar cuerpos en handlers funcionales.
4. `ProblemDetailMapper`: la traducción de cada error, con `switch` de patrones.
5. `GlobalErrorHandler`: escribir la respuesta, registrar en logs y respetar la respuesta comprometida.
6. Bean de `Clock`.
7. Pruebas.

## Dependencias

| Dependencia | Alcance |
|---|---|
| `spring-boot-starter-validation` | compile |

## Formato de error

Las respuestas de error siguen el [RFC 9457](https://datatracker.ietf.org/doc/html/rfc9457) con `ProblemDetail` y `Content-Type: application/problem+json`:

```json
{
  "type": "tag:yisoft-development.com,2026:problems/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "The request contains invalid fields.",
  "instance": "urn:uuid:3f2b8c1e-7a4d-4e9b-9c1a-2d5e6f708192",
  "timestamp": "2026-11-16T06:30:00Z",
  "traceId": "3f2b8c1e-7a4d-4e9b-9c1a-2d5e6f708192",
  "errors": [
    { "pointer": "#/email", "detail": "must be a well-formed email address" }
  ]
}
```

| Campo | Regla |
|---|---|
| `type` | URI que identifica el tipo de problema; es el identificador que usa el front para traducir. `about:blank` cuando el problema no agrega nada al estado HTTP |
| `title` | Resumen fijo del tipo de problema, en inglés. Con `about:blank`, es la frase estándar del estado, como `Not Found` |
| `status` | Igual al estado de la respuesta HTTP |
| `detail` | Explica esta ocurrencia para ayudar a corregirla; el front no lee datos de aquí |
| `instance` | `urn:uuid:` seguido del trace ID; identifica esta ocurrencia |
| `timestamp` | Extensión: instante en UTC, formato ISO-8601, tomado del `Clock` |
| `traceId` | Extensión: el atributo `TraceIdContext.KEY` del `exchange`, igual al header `X-Trace-Id` |
| `errors` | Extensión, solo en validación: `pointer` (JSON Pointer del RFC 6901 hacia el campo del cuerpo) y `detail` |

### Tipos de problema

Las URI son de tipo `tag`: no tienen que abrir una página y no dependen del dominio de cada fork. La base es `tag:yisoft-development.com,2026:problems/`, seguida del identificador en `kebab-case`.

| Tipo | Cuándo |
|---|---|
| `validation-error` | Cuerpo con campos inválidos |
| `malformed-request` | JSON mal formado o tipo de dato incorrecto |
| `invalid-trace-id` | `X-Trace-Id` no es un UUID válido |
| Uno por cada excepción de dominio | Lo define la excepción, por ejemplo `email-already-registered` |
| `about:blank` | Ruta inexistente, `415`, `500` y cualquier otro estado sin semántica propia |

Nunca se exponen stack traces, nombres de clases, SQL ni mensajes internos.

## Paso 1: excepciones de dominio

En `com.yisoft.development.ecommerce.shared.domain.exception`:

| Clase | Declaración | Estado HTTP |
|---|---|---|
| `DomainException` | `public abstract sealed class ... extends RuntimeException permits NotFoundException, ConflictException, BusinessRuleException` | No se usa directo |
| `NotFoundException` | `public non-sealed class ... extends DomainException` | `404` |
| `ConflictException` | `public non-sealed class ... extends DomainException` | `409` |
| `BusinessRuleException` | `public non-sealed class ... extends DomainException` | `422` |

- `DomainException` recibe `problemType`, `title` y `detail`. `problemType` es el identificador en `kebab-case`, sin la base de la URI; el dominio no sabe nada de URI ni de HTTP.
- `detail` se pasa al constructor padre como mensaje.
- El dominio no importa nada de Spring.
- Cada feature hereda de estas tres y fija su `problemType` y su `title`. Por ejemplo, `EmailAlreadyRegisteredException extends ConflictException` con `email-already-registered` y `Email already registered`.

## Paso 2: excepciones de la capa web

En `com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web`:

| Clase | Qué representa |
|---|---|
| `RequestValidationException` | El cuerpo no cumple sus anotaciones; contiene una lista de `FieldViolation` |
| `FieldViolation` | `record` con `pointer` y `detail` |
| `InvalidTraceIdException` | El header `X-Trace-Id` no es un UUID válido |

`TraceIdFilter` cambia `ResponseStatusException` por `InvalidTraceIdException`, para que el error tenga su propio tipo de problema.

## Paso 3: validación de cuerpos

### 3.1 `ValidationConfiguration`

En `com.yisoft.development.ecommerce.shared.infrastructure.config`.

**Propósito:** crear el `Validator` de toda la app con los mensajes fijos en inglés, para que `detail` no dependa del idioma del sistema operativo.

Requerimientos:

1. Clase `@Configuration`.
2. Un bean `ValidatorFactory` creado con la configuración específica de Hibernate Validator (`HibernateValidator`), con idioma por defecto `Locale.ENGLISH`.
3. Un bean `jakarta.validation.Validator` obtenido de esa fábrica, recibida como parámetro del método.
4. La fábrica es un bean para que Spring la cierre al apagar la app.
5. Este bean reemplaza al `Validator` que crea Spring Boot.

### 3.2 `RequestValidator`

En `com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web`.

**Propósito:** validar cualquier DTO recibido en un handler funcional, donde no existe `@Valid`. Se usa así: `request.bodyToMono(Dto.class).flatMap(requestValidator::validate)`.

Requerimientos:

1. Bean `@Component` que recibe el `jakarta.validation.Validator` por constructor, en un campo `final`.
2. Método público genérico `<T> Mono<T> validate(T body)`.
3. Sin violaciones: regresa `Mono.just(body)`.
4. Con violaciones: regresa `Mono.error(new RequestValidationException(...))`.
5. Cada `ConstraintViolation` se convierte en un `FieldViolation`: `pointer` desde `getPropertyPath()` y `detail` desde `getMessage()`.
6. La conversión usa un *stream*: transformar, ordenar por `pointer` y juntar en una lista. El orden estable es obligatorio, porque `validate` regresa un `Set`.
7. La ruta se convierte en JSON Pointer en un método privado `toPointer(Path path)`:
   - Empieza con `#`.
   - Recorre los nodos de `Path`; es un `Iterable<Path.Node>`.
   - Por cada nodo: si `getIndex()` no es `null`, agrega `/` y el índice; después, si `getName()` no es `null`, agrega `/` y el nombre.
   - El índice de una lista viene en el nodo siguiente al de la lista.

| Ruta del `Validator` | `pointer` |
|---|---|
| `email` | `#/email` |
| `address.zipCode` | `#/address/zipCode` |
| `items[0].quantity` | `#/items/0/quantity` |

8. Sin `throw` ni `.block()`.
9. En los DTOs, la validación en cascada de listas usa la anotación sobre el tipo: `List<@Valid Item>`. `@Valid` sobre la lista está obsoleto en Hibernate Validator 9.

## Paso 4: `ProblemDetailMapper`

`@Component` en el mismo paquete. Solo traduce; no escribe la respuesta ni conoce el `exchange`.

- Firma: `ProblemDetail toProblemDetail(Throwable error, String traceId)`.
- Recibe el `Clock` por constructor.
- Agrega `instance`, `timestamp` y `traceId` en un solo lugar.
- La base `tag:yisoft-development.com,2026:problems/` vive en una sola constante.

| Error | Estado | `type` | `title` |
|---|---|---|---|
| `NotFoundException` | `404` | El de la excepción | El de la excepción |
| `ConflictException` | `409` | El de la excepción | El de la excepción |
| `BusinessRuleException` | `422` | El de la excepción | El de la excepción |
| `RequestValidationException` | `400` | `validation-error`, con `errors` | `Validation failed` |
| `InvalidTraceIdException` | `400` | `invalid-trace-id` | `Invalid trace ID` |
| `ServerWebInputException`, JSON mal formado o tipo incorrecto | `400` | `malformed-request` | `Malformed request` |
| `UnsupportedMediaTypeStatusException` | `415` | `about:blank` | `Unsupported Media Type` |
| Otro `ResponseStatusException`, incluida la ruta inexistente | Su estado | `about:blank` | La frase del estado |
| Cualquier otro error | `500` | `about:blank` | `Internal Server Error` |

Reglas:

- El mapeo usa un `switch` de patrones sobre el error.
- Las tres categorías de dominio se resuelven en un `switch` **sin `default`** sobre `DomainException`, para que sea exhaustivo.
- `ServerWebInputException` y `UnsupportedMediaTypeStatusException` heredan de `ResponseStatusException`, así que sus `case` van antes.
- En `500`, `detail` es siempre `An unexpected error occurred.`.
- En las rutas funcionales, un método o un `Content-Type` que no coincide con ninguna ruta termina en `404`, no en `405`.

## Paso 5: `GlobalErrorHandler`

Implementa `WebExceptionHandler`, con `@Component` y `@Order(-2)`, en el mismo paquete.

1. Si `exchange.getResponse().isCommitted()`, regresa `Mono.error(error)`.
2. Lee el trace ID del atributo `TraceIdContext.KEY` del `exchange`.
3. Pide el `ProblemDetail` a `ProblemDetailMapper`, con el error y el trace ID.
4. Registra el log: los `4xx` como `WARN` sin stack trace y los `500` como `ERROR` con la excepción completa.
5. Escribe la respuesta con el estado, `Content-Type: application/problem+json` y el `ProblemDetail` como JSON, usando los codecs de Spring a través de `ServerResponse` y `ServerCodecConfigurer`.

## Paso 6: `Clock`

Bean `Clock.systemUTC()` en `com.yisoft.development.ecommerce.shared.infrastructure.config`.

## Paso 7: pruebas esperadas

### `ProblemDetailMapperTests`

Prueba unitaria, sin Spring. Usa `Clock.fixed(...)`.

| Prueba | Qué comprueba |
|---|---|
| `mapsNotFound` | `404`, y `type` y `title` de la excepción |
| `mapsConflict` | `409`, y `type` y `title` de la excepción |
| `mapsBusinessRule` | `422`, y `type` y `title` de la excepción |
| `mapsValidation` | `400`, `validation-error` y `errors` con `pointer` y `detail` de cada campo |
| `mapsInvalidTraceId` | `400` e `invalid-trace-id` |
| `mapsMalformedRequest` | `400` y `malformed-request` |
| `mapsUnsupportedMediaType` | `415` y `about:blank` |
| `mapsRouteNotFound` | `404`, `about:blank` y `title` `Not Found` |
| `mapsUnexpectedErrorWithoutLeakingMessage` | `500`, `about:blank` y `detail` genérico, sin el mensaje original |
| `addsCommonFields` | `instance` es `urn:uuid:` más el trace ID, `traceId` y `timestamp` igual al `Clock` fijo |

Las excepciones de dominio de prueba heredan de las categorías, por ejemplo `TestNotFoundException extends NotFoundException`, y viven solo en `src/test`.

### `GlobalErrorHandlerTests`

Con `@WebFluxTest` y `WebTestClient`. Las rutas de prueba viven solo en `src/test`, en una clase `@TestConfiguration` que declara un `RouterFunction`:

- `POST /test/errors/validation`: valida un `record` con `@NotBlank name` y `@Email email`, y responde `204`.
- `GET /test/errors/not-found`, `GET /test/errors/conflict` y `GET /test/errors/unexpected`: regresan `Mono.error(...)`.

| Prueba | Qué comprueba |
|---|---|
| `validBodyReturnsNoContent` | Un cuerpo válido responde `204` |
| `invalidBodyReturnsValidationError` | `400`, `validation-error` y un elemento en `errors` por cada campo inválido, con su `pointer` |
| `malformedJsonReturnsMalformedRequest` | `400` y `malformed-request` |
| `unknownRouteReturnsNotFound` | `404` y `about:blank` |
| `domainErrorReturnsProblemDetail` | `404` y el `type` de la excepción |
| `unexpectedErrorReturns500` | `500` y el `detail` genérico |
| `invalidTraceIdReturnsProblemDetail` | `X-Trace-Id: abc` responde `400` e `invalid-trace-id` |
| `problemResponseHasCommonFields` | `Content-Type` es `application/problem+json` y vienen `type`, `title`, `status`, `instance`, `timestamp` y `traceId` como campos de primer nivel |
| `errorTraceIdMatchesHeader` | `traceId` del cuerpo es igual al header `X-Trace-Id`, e `instance` es `urn:uuid:` más ese valor |

Si `@WebFluxTest` no detecta el manejador, el mapper, el validador, `ValidationConfiguration`, el `Clock` o las rutas, se importan con `@Import`.

### `RequestValidatorTests`

Prueba unitaria, sin Spring, con un `Validator` en inglés.

| Prueba | Qué comprueba |
|---|---|
| `validBodyPasses` | Regresa el mismo objeto con `StepVerifier` |
| `invalidBodyFails` | Termina con `RequestValidationException` y una violación por campo |
| `violationsAreSortedByPointer` | La lista sale ordenada por `pointer` |
| `nestedListPathBecomesJsonPointer` | `items[0].quantity` se convierte en `#/items/0/quantity` |
| `messagesAreInEnglish` | `detail` sale en inglés aunque el idioma del sistema sea otro |

## Criterios de aceptación

- Todas las pruebas pasan y `mvn clean verify` termina en `BUILD SUCCESS`.
- Ninguna respuesta de error expone detalles internos.
- Las rutas y excepciones de prueba no existen en `src/main`.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Errores de seguridad (`401` y `403`), traducción de mensajes y códigos de cada feature.

## Criterios de revisión

- El dominio no importa clases de Spring.
- El `switch` de dominio no tiene `default`.
- El mapeo de estados vive solo en `ProblemDetailMapper`.
- Los `500` no filtran el mensaje original.
- Toda URI de tipo de problema sale de la misma constante base.
- El manejador respeta la respuesta comprometida.
- No hay `.block()`.
