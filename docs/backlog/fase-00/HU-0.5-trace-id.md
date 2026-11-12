# HU-0.5: Trace ID por petición

**Historia:** como consumidor de la API, quiero que cada petición tenga un identificador único que regrese en el header y en el cuerpo de la respuesta, para reportar cualquier problema y encontrarlo en los logs.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.5-trace-id` desde `develop` actualizada y abrir el PR hacia `develop`.

## Variantes

| Variante | Cómo funciona | Beneficios | Costo |
|---|---|---|---|
| A. Filtro propio | `WebFilter` + contexto de Reactor + MDC | Se aprende cómo viaja una petición en WebFlux; el ID es un UUID | Solo vive dentro de esta app |
| B. Micrometer Tracing + OpenTelemetry | Spring genera y propaga el ID automáticamente | Propagación a RabbitMQ, HTTP saliente y Grafana sin código | El ID son 32 caracteres hexadecimales; la mecánica queda oculta |
| C. Híbrida en dos pasos | A en esta historia y B en la HU-0.7, ligadas | `X-Trace-Id` UUID para el cliente y rastreo distribuido; en Grafana se busca por el UUID | Dos piezas que mantener |

**Elegida: C.** Esta historia implementa la parte A. La HU-0.7 agrega OpenTelemetry y guarda el UUID como atributo del span, para relacionar ambos.

## Conceptos nuevos

| Concepto | Qué es | Por qué importa aquí |
|---|---|---|
| `WebFilter` | Pieza que intercepta cada petición antes y después del controlador | Es el único punto por donde pasan todas las peticiones, incluidas las que terminan en error o en `404` |
| `ServerWebExchange` | La petición y la respuesta juntas, más atributos | Desde aquí se lee el header de entrada y se escribe el de salida |
| `WebFilterChain` | La cadena de filtros que sigue | `chain.filter(exchange)` regresa un `Mono<Void>`: nada corre hasta que alguien se suscribe |
| Contexto de Reactor | Mapa inmutable que viaja con la suscripción, no con el hilo | En WebFlux una petición cambia de hilo; `ThreadLocal` y `ScopedValue` no la siguen |
| `contextWrite` | Agrega datos al contexto | Se aplica de abajo hacia arriba: lo que escribe el filtro lo ven el controlador y el manejador de errores |
| `Mono.deferContextual` | Lee el contexto en el momento de la suscripción | Así se obtiene el trace ID sin pasarlo como parámetro por todas las capas |
| Context propagation de Micrometer | Copia valores del contexto de Reactor a `ThreadLocal` mientras corre el código | El MDC de los logs es `ThreadLocal`; sin esto, el log no ve el trace ID |
| `record` genérico | Clase inmutable con constructor, getters, `equals` y `hashCode` | `ApiResponse<T>` no necesita Lombok |

## Pasos

Se resuelven uno por uno en el chat. Cada paso se revisa antes del siguiente.

1. Esqueleto de `TraceIdFilter`, que solo deja pasar la petición.
2. Resolver el trace ID: leer el header y validar el UUID.
3. Escribir el header de respuesta.
4. Guardar el trace ID en el contexto de Reactor y crear `TraceIdContext`.
5. Crear `ApiResponse<T>`.
6. Llevar el trace ID a los logs.
7. Pruebas y validación manual.

## Reglas del trace ID

| Caso | Resultado |
|---|---|
| La petición trae `X-Trace-Id` con un UUID válido | Se reutiliza ese valor |
| La petición no trae `X-Trace-Id` | Se genera un UUID v4 |
| La petición trae `X-Trace-Id` vacío | Se genera un UUID v4 |
| La petición trae `X-Trace-Id` que no es un UUID con formato canónico | Se rechaza con `400`; la respuesta lleva un UUID nuevo en `X-Trace-Id` |

- El valor se normaliza a minúsculas y en formato canónico, con guiones.
- Nunca se escribe en logs ni en la respuesta un valor recibido sin validar.
- El rechazo usa `ResponseStatusException` con `400`, regresado con `Mono.error`, no con `throw`. El formato `ProblemDetail` y el código `INVALID_TRACE_ID` se agregan en la HU-0.6.
- Toda respuesta, exitosa o de error, lleva el header `X-Trace-Id` con el valor final.

## Componentes

Todos van en `com.yisoft.development.ecommerce.shared.infrastructure`.

| Clase | Paquete | Responsabilidad |
|---|---|---|
| `TraceIdFilter` | `adapter.in.web` | `WebFilter` que resuelve el trace ID, lo agrega al header de respuesta y al contexto de Reactor |
| `TraceIdContext` | `adapter.in.web` | Constantes del header y de la clave del contexto, y un método que lee el trace ID desde el contexto |
| `ApiResponse<T>` | `adapter.in.web` | `record` de respuesta exitosa con `traceId` y `data` |
| `TraceIdLoggingConfiguration` | `config` | Registra el trace ID para que aparezca en los logs |

### `TraceIdFilter`

- Es el primer filtro de la cadena: `@Order(Ordered.HIGHEST_PRECEDENCE)`.
- Agrega el header a la respuesta antes de continuar la cadena, para que también salga cuando la petición termina en error.
- Guarda el valor en el contexto de Reactor con `contextWrite`, bajo la clave definida en `TraceIdContext`.
- Guarda también el valor como atributo del `ServerWebExchange`, con la misma clave. Lo usa el manejador de errores de filtros de la HU-0.6, que corre fuera de la cadena y no ve el contexto de Reactor.
- No usa `ThreadLocal` ni `.block()`.

### `TraceIdContext`

Expone un método estático que regresa `Mono<String>` con el trace ID, leyéndolo con `Mono.deferContextual`. Lo usarán los controladores y el manejador de errores.

## Respuesta exitosa

Toda respuesta con cuerpo usa este formato:

```json
{
  "traceId": "3f2b8c1e-7a4d-4e9b-9c1a-2d5e6f708192",
  "data": {
    "id": "0d8f6b3a-7c21-4f10-9a33-5c1e2b7d9f10",
    "firstName": "Ana"
  }
}
```

- `ApiResponse<T>` es un `record` con `traceId` y `data`.
- Los controladores arman la respuesta con un método de apoyo, por ejemplo `ApiResponse.from(Mono<T>)`, que combina el resultado con `TraceIdContext`. No se repite esa lógica en cada endpoint.
- Las respuestas `204 No Content` no llevan cuerpo; el trace ID va solo en el header.
- Las respuestas de error no usan `ApiResponse`; llevan `traceId` dentro del `ProblemDetail` (HU-0.6).

## Logs

Cada línea de log que se escriba dentro de una petición debe incluir el trace ID.

1. Agregar la dependencia `io.micrometer:context-propagation`, sin versión.
2. En `TraceIdLoggingConfiguration`, activar la propagación automática de Reactor con `Hooks.enableAutomaticContextPropagation()`. No se usa la propiedad `spring.reactor.context-propagation`, porque depende de una autoconfiguración que `@WebFluxTest` no carga.
3. Agregar a `TraceIdContext` la constante `MDC_KEY = "xTraceId"`.
4. En la misma clase, registrar en `ContextRegistry` un `ThreadLocalAccessor` con la llave `TraceIdContext.KEY`, que copie el valor al MDC bajo `TraceIdContext.MDC_KEY` y lo limpie al terminar.
5. Agregar el patrón:

```properties
# Logging
logging.pattern.correlation=[%X{xTraceId:-}] 
```

- Fuera de una petición, el campo queda vacío.
- La llave del MDC es `xTraceId` y no `traceId`, porque en la HU-0.7 Micrometer Tracing escribirá su propio `traceId` de OpenTelemetry en el MDC y lo sobrescribiría.
- Un log escrito en el cuerpo de `TraceIdFilter`, antes del `contextWrite`, no tiene trace ID: el contexto todavía no existe en ese punto.

## Pruebas esperadas

Crear `TraceIdFilterTests` en `com.yisoft.development.ecommerce.shared.infrastructure.adapter.in.web`, dentro de `src/test/java`, con `@WebFluxTest` y `WebTestClient`. No levanta base de datos.

Crear un controlador que **solo existe en `src/test`**, `TraceIdTestController`, con:

- `GET /test/trace` que escribe un log y responde `ApiResponse` con un dato fijo.
- `GET /test/trace/empty` que responde `204`.
- `GET /test/trace/delayed` que espera con `Mono.delay` y responde como `data` el trace ID leído después de la espera.

| Prueba | Qué comprueba |
|---|---|
| `generatesTraceIdWhenHeaderIsMissing` | El header de respuesta trae un UUID válido |
| `reusesValidTraceIdFromRequest` | El header de respuesta es igual al UUID enviado |
| `rejectsInvalidTraceId` | Con `X-Trace-Id: abc` y con `1-1-1-1-1`, responde `400` y el header de respuesta es un UUID válido distinto al enviado |
| `bodyTraceIdMatchesHeader` | `traceId` del cuerpo es igual al header de respuesta |
| `noContentResponseHasTraceIdHeader` | `204` sin cuerpo y con el header presente |
| `logIncludesTraceId` | Con `OutputCaptureExtension`, el log de `/test/trace` contiene el mismo trace ID del header |
| `concurrentRequestsKeepTheirOwnTraceId` | 50 peticiones en paralelo con hilos virtuales, cada una con su UUID, reciben su propio valor en header, `traceId` y `data`, aunque la petición cambie de hilo |

`@WebFluxTest` no siempre detecta filtros ni configuraciones: si `TraceIdFilter` no se aplica, impórtalo con `@Import`.

## Validación manual

Documenta el resultado en el PR.

1. Como `/test/trace` solo existe en las pruebas, valida con una ruta inexistente: sin header, responde `404` y aun así trae `X-Trace-Id`.
2. Al arrancar la app, una línea de log escrita fuera de una petición muestra `[]` vacío, sin errores.

## Criterios de aceptación

- Las siete pruebas pasan y `mvn clean verify` termina en `BUILD SUCCESS`.
- Las dos validaciones manuales se cumplen.
- El controlador de prueba no existe en `src/main`.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Formato de error y `traceId` en el `ProblemDetail` (HU-0.6), OpenTelemetry y `traceparent` (HU-0.7), y propagar el trace ID a RabbitMQ o servicios externos.

## Criterios de revisión

- No hay `ThreadLocal` manual, `.block()` ni estado compartido entre peticiones.
- Un valor recibido sin validar nunca llega a logs ni a la respuesta.
- Un header inválido responde `400`, nunca `500`.
- El header se agrega aunque la petición termine en error.
- La lectura del trace ID está en un solo lugar.

## Explicación y repaso

Resumen de lo que se resolvió en cada paso, para repasar después de implementar.

### Cómo viaja una petición

```text
Netty → TraceIdFilter → otros filtros → Controlador
```

- **Netty** es el servidor: recibe la conexión y entrega la petición a WebFlux.
- **WebFlux** es el framework: corre la fila de filtros y después llama al controlador.
- Es la misma relación que Tomcat y Spring MVC.

### `TraceIdFilter`

**Qué es un `WebFilter`.** Una interfaz de WebFlux para crear un punto de la fila por donde pasan todas las peticiones, incluidas las que terminan en `404` o en error. Al implementarla y sobrescribir su método `filter`, se crea un filtro propio; no se reemplaza ninguno existente.

**Quién arma la fila.** Spring, al arrancar. Busca los beans que implementan `WebFilter` (por eso `@Component`) y los ordena por `@Order`. `HIGHEST_PRECEDENCE` pone este filtro primero, antes de cualquier otro filtro y del controlador.

**Las piezas del método `filter`:**

| Pieza | Qué es |
|---|---|
| `ServerWebExchange` | La petición y la respuesta juntas; de aquí se leen y escriben headers y atributos |
| `WebFilterChain` | El resto de la fila que viene después de este filtro |
| `chain.filter(exchange)` | "Terminé mi parte, continúa la cadena con esta petición" |
| `Mono<Void>` | Aviso de que la petición terminó o falló; no emite un valor |

**`Mono` es una descripción.** `chain.filter(exchange)` no ejecuta nada en ese momento: describe lo que va a pasar, y corre cuando Netty se suscribe. Un `Mono` que se crea y se ignora nunca se ejecuta.

**Reactivo solo para esperar.** Validar el header es un cálculo inmediato en memoria, así que el método de validación regresa `String`, no `Mono<String>`. Lo reactivo se usa para lo que espera algo externo: base de datos, Redis, RabbitMQ o HTTP.

**Errores en código reactivo.** Dentro de un método que regresa `Mono` o `Flux`, los errores se regresan con `Mono.error(...)`, no con `throw`. Se usa `ResponseStatusException` porque el filtro es capa web y ya conoce HTTP; un error genérico como `IllegalArgumentException` llegaría como `500`.

**Validar el UUID.** `UUID.fromString("1-1-1-1-1")` no falla, así que se valida el formato canónico con un `Pattern` constante. Un valor recibido sin validar nunca se escribe en logs ni en la respuesta: evita que alguien inyecte líneas falsas en los logs.

**Dos variables, dos preguntas.** `traceIdHeader` responde "¿qué mandó el cliente?" y decide el rechazo. `traceId` responde "¿qué ID lleva esta respuesta?" y va en el header de salida.

**`beforeCommit`.** El *commit* es el momento en que Netty envía los headers; después ya no se pueden cambiar. `beforeCommit` registra una función que corre justo antes, después de cualquier manejador de errores. Así el header sale en `200`, `400`, `404` y `500`, sin que el filtro dependa de lo que hagan los componentes posteriores.

### `TraceIdContext`

**Por qué no `ThreadLocal`.** En WebFlux una petición cambia de hilo mientras espera, y un `ThreadLocal` se queda en el hilo anterior.

**Qué es el contexto de Reactor.** Un mapa de solo lectura que viaja con la petición, no con el hilo:

```text
"traceId" → "3f2b8c1e-7a4d-4e9b-9c1a-2d5e6f708192"
```

**Los dos nombres:**

| Nombre | Dónde vive | Para qué |
|---|---|---|
| `X-Trace-Id` | HTTP | Header que entra y sale de la API |
| `traceId` | Dentro de la app | Llave del mapa en el contexto y en el atributo del `exchange` |

`KEY` es solo el nombre de la constante en Java; `"traceId"` es la llave y el UUID es el valor.

**`contextWrite`.** Pega el mapa a la petición. Se escribe después de `chain.filter(...)`, pero lo ve todo lo que corre dentro de ella: otros filtros, controlador, casos de uso y repositorios.

**`current()`.** Lee la llave del mapa de esta petición con `Mono.deferContextual`. Regresa `Mono<String>` porque el contexto solo existe mientras la petición corre.

**Atributo del `exchange`.** Copia del trace ID para el código que corre fuera de la cadena, como el manejador de errores de filtros de la HU-0.6, que no ve el contexto de Reactor.

### `ApiResponse`

**El `record`.** Solo define la forma del JSON: `{ traceId, data }`. `T` es el tipo de `data` y cambia según el endpoint.

**`from(Mono<T> data)`.** Arma la respuesta. Es `static` y declara su propio `<T>`, porque no depende de una instancia.

**`zipWith`.** Espera a dos `Mono` y une sus valores con una función:

```text
current()  →  "3f2b..."        ┐
                               ├─ zipWith → ApiResponse("3f2b...", Customer)
data       →  Customer(Ana)    ┘
```

**`ApiResponse::new`.** Referencia al constructor del `record`. Equivale a `(traceId, datos) -> new ApiResponse<>(traceId, datos)`. Funciona porque el orden del `record` coincide con el orden en que `zipWith` entrega los valores.

**Si `data` está vacío.** El resultado también queda vacío. Un recurso inexistente se resuelve con `NotFoundException` en el caso de uso (HU-0.6).

### Logs: MDC y context-propagation

**Qué es el MDC.** Un mapa de SLF4J y Logback cuyos valores se agregan solos a cada línea de log. El patrón `%X{xTraceId:-}` imprime el valor de `xTraceId`, o nada si no existe.

**El problema.** El MDC es un `ThreadLocal`, y el trace ID vive en el contexto de Reactor.

**La solución.** La librería context-propagation de Micrometer copia valores del contexto al `ThreadLocal` cada vez que Reactor ejecuta código de la petición, y los limpia al terminar. `Hooks.enableAutomaticContextPropagation()` activa esa copia automática. Se llama desde `TraceIdLoggingConfiguration` y no con la propiedad `spring.reactor.context-propagation=auto`: la propiedad la aplica una autoconfiguración que `@WebFluxTest` no carga, y la prueba de logs falló por eso. Así toda la configuración de logs vive en una sola clase.

**El `ThreadLocalAccessor`.** Le indica a la librería qué llave copiar y cómo:

| Pieza | Pregunta |
|---|---|
| `TraceIdContext.KEY` | ¿Qué llave del contexto copio? |
| `() -> MDC.get(...)` | ¿Cómo leo el valor actual? |
| `value -> MDC.put(...)` | ¿Cómo lo escribo? |
| `() -> MDC.remove(...)` | ¿Cómo lo limpio? |

**Por qué `xTraceId` en el MDC.** En la HU-0.7, Micrometer Tracing escribirá en el MDC su propio `traceId` de OpenTelemetry. Con nombres distintos, los dos conviven.

**La trampa.** Un log escrito al armar el `Mono`, antes de la suscripción, no tiene trace ID. Solo lo tienen los logs que corren dentro del flujo, por ejemplo en `doOnNext`.

### Pruebas

**`@WebFluxTest` contra `@SpringBootTest`.** `@WebFluxTest` levanta solo la capa web: controladores, filtros y manejadores, sin base de datos. No carga clases `@Configuration`, por eso se importa `TraceIdLoggingConfiguration`.

**`WebTestClient` en tres fases.** Lo que va antes de `exchange()` es la petición (`get`, `uri`, `header`); `exchange()` la envía; lo que va después valida la respuesta (`expectStatus`, `expectHeader`, `expectBody`).

**Controlador de prueba.** Vive solo en `src/test` y da endpoints controlados para probar el filtro sin depender de features reales.

**Pruebas parametrizadas.** `@ParameterizedTest` con `@ValueSource` ejecuta la misma prueba una vez por cada valor.

**`ParameterizedTypeReference`.** Java borra los genéricos al compilar: en ejecución, `ApiResponse<String>` es solo `ApiResponse`. La clase anónima `new ParameterizedTypeReference<>() {}` conserva el tipo completo para que Jackson convierta `data` correctamente.

**`returnResult()`.** Entrega la respuesta completa para comparar header y cuerpo en la misma prueba.

**Concurrencia con hilos virtuales.** `Executors.newVirtualThreadPerTaskExecutor()` crea un hilo virtual barato por tarea. Dentro de un `try-with-resources`, el `close()` espera a que terminen todas. `Future.get()` relanza cualquier fallo de una tarea, para que no quede escondido.

**Por qué `Mono.delay` en el endpoint concurrente.** La espera obliga a Reactor a continuar en otro hilo. Leer el trace ID después de la espera demuestra que el contexto sigue a la petición y no al hilo.

**`OutputCaptureExtension`.** Captura lo que se imprime en consola durante la prueba y lo entrega como `CapturedOutput`, para validar el contenido de los logs.

### Recorrido completo

1. `TraceIdFilter` resuelve el trace ID, registra el header con `beforeCommit`, guarda el atributo y continúa la cadena con `contextWrite`.
2. El controlador llama a `ApiResponse.from(useCase.find(...))`.
3. `from` lee el trace ID con `current()` y lo junta con los datos usando `zipWith`.
4. Cualquier log dentro del flujo imprime el trace ID, porque context-propagation lo copió al MDC.
5. Spring convierte el `ApiResponse` a JSON.
6. Justo antes de enviar, `beforeCommit` escribe el mismo valor en el header.

## Recursos

### WebFlux y filtros
- [Reactive Core (Spring Framework)](https://docs.spring.io/spring-framework/reference/web/webflux/reactive-spring.html): cómo se arma la cadena de filtros y el `ServerWebExchange`.
- [Reactive Web Applications (Spring Boot)](https://docs.spring.io/spring-boot/reference/web/reactive.html): cómo Boot configura WebFlux y Netty.
- [Javadoc de `WebFilter`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/server/WebFilter.html)
- [Javadoc de `ServerHttpResponse`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/server/reactive/ServerHttpResponse.html): incluye `beforeCommit`.
- [Spring WebFlux Filters (Baeldung)](https://www.baeldung.com/spring-webflux-filters)
- [How to Intercept a Request and Add Headers in WebFlux (Baeldung)](https://www.baeldung.com/spring-webflux-intercept-request-add-headers)

### Reactor
- [Adding a Context to a Reactive Sequence (Reactor)](https://projectreactor.io/docs/core/release/reference/advancedFeatures/context.html): `contextWrite`, `deferContextual` y por qué se lee de abajo hacia arriba.
- [Context-Propagation Support (Reactor)](https://projectreactor.io/docs/core/release/reference/advanced-contextPropagation.html): base del paso 6, para llevar el contexto a los logs.
- [Javadoc de `Mono`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html): `zipWith`, `error` y `deferContextual`.
- [Combining Publishers in Project Reactor (Baeldung)](https://www.baeldung.com/reactor-combine-streams): `zip` y `zipWith` con ejemplos.

### Java
- [Using Records to Model Immutable Data (Dev.java)](https://dev.java/learn/language/oop/records/)
- [JEP 395: Records](https://openjdk.org/jeps/395)
- [Lambda Expressions (Dev.java)](https://dev.java/learn/lambdas/): incluye referencias a métodos y constructores, como `ApiResponse::new`.

### Logs y context-propagation
- [Context Propagation support (Micrometer)](https://docs.micrometer.io/context-propagation/reference/): qué resuelve la librería y cómo registrar un `ThreadLocalAccessor`.
- [Examples (Micrometer Context Propagation)](https://docs.micrometer.io/context-propagation/reference/usage.html)
- [Chapter 8: Mapped Diagnostic Context (Logback)](https://logback.qos.ch/manual/mdc.html): qué es el MDC y cómo se usa en los patrones.

### Pruebas
- [WebTestClient (Spring Framework)](https://docs.spring.io/spring-framework/reference/testing/webtestclient.html): las tres fases y todas las validaciones disponibles.
- [Testing Spring Boot Applications (Spring Boot)](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html): `@WebFluxTest`, pruebas por capas y `OutputCaptureExtension`.
- [Parameterized Classes and Tests (JUnit)](https://docs.junit.org/5.14.3/writing-tests/parameterized-classes-and-tests.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444): qué son los hilos virtuales y cuándo usarlos.
