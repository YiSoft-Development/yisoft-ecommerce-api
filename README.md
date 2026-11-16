# yisoft-ecommerce-api

## Contexto
<b>yisoft-ecommerce-api</b> es un proyecto el cual está enfocado al desarrollo de un ecommerce que permitirá tener una 
gestión de la plataforma a detalle desde un panel de administrador, el cual también incluye una pasarela de pagos.

## Requisitos
<ol>
    <li>Java 25</li>
    <li>Maven</li>
</ol>

## Cómo compilar y ejecutar
1. Clonar el repositorio.
2. Copiar `.env.example` a `.env` y cambiar los valores.
3. Levantar los servicios: `docker compose up -d --wait`.
4. Compilar y probar: `mvn clean install`.
5. Ejecutar la aplicación: `mvn spring-boot:run`.

## Recursos
- [Plan del Proyecto](./docs/PLAN.md)
- [Backlog](./docs/backlog/README.md)   

## Servicios docker
- PostgreSQL
- Redis
- RabbitMQ
- Mailpit
- Observabilidad (Grafana LGTM)   

> Como ejecutar el archivo [docker-compose.yaml](compose.yaml)
1. Copiar ```.env.example``` a ```.env``` y cambiar los valores.
2. Levantar los servicios: ```docker compose up -d --wait```.
3. Ver su estado: ```docker compose ps```.
4. Detenerlos conservando datos: ```docker compose down```.
5. Detenerlos y borrar datos: ```docker compose down -v```.
6.
|Servicio|Puerto|Interfaz Web|
|--------|------|------------|
|PostgreSQL|5432|No aplica|
|Redis|6379|No aplica|
|RabbitMQ|5672 y 15672|http://localhost:15672|
|Mailpit|1025 y 8025|http://localhost:8025|
|Observabilidad (Grafana y OTLP)|3000, 4317 y 4318|http://localhost:3000 (usuario `admin`, contraseña `admin`)|

## Observabilidad
- Health checks: `/actuator/health`, `/actuator/health/liveness` y `/actuator/health/readiness`.
- Trazas, métricas y logs se envían por OTLP a Grafana. Para buscar una petición, en Grafana → Explore → Tempo usa `{span.x_trace_id="<X-Trace-Id>"}`.

## Perfiles

El ambiente se elige con un perfil de Maven. Maven toma los valores del archivo de ese perfil y los escribe en `application.properties` al compilar.

| Perfil | Cómo se activa | Archivo de valores | ¿Se versiona? |
|---|---|---|---|
| `local` | Automático, si no se indica otro perfil | `.env` | No |
| `qa` | `-Pqa` | `src/main/filters/qa.properties` | No |
| `prod` | `-Pprod` | `src/main/filters/prod.properties` | No |

### Preparar `qa` y `prod`

1. Copiar el archivo de ejemplo del ambiente:

   ```powershell
   Copy-Item src/main/filters/qa.example.properties src/main/filters/qa.properties
   Copy-Item src/main/filters/prod.example.properties src/main/filters/prod.properties
   ```

2. Llenar los valores de cada archivo.

Los archivos reales no se suben al repositorio. Si falta el archivo del perfil, el build falla.

### Arrancar la aplicación

| Ambiente | Comando |
|---|---|
| `local` | `mvn spring-boot:run` |
| `qa` | `mvn spring-boot:run -Pqa` |
| `prod` | `mvn spring-boot:run -Pprod` |

### Generar el jar

```bash
mvn clean package -Pprod
```

Cada jar lleva los valores de su ambiente, incluidas las credenciales. No debe compartirse fuera de ese ambiente.

## Cómo crear un endpoint

Los endpoints son funcionales: `RouterFunction` declara las rutas y un handler traduce HTTP al caso de uso. El ejemplo usa un feature ficticio `product`.

### Estructura

```text
product/
├── domain/
│   └── model/Product.java
├── application/
│   ├── port/in/CreateProductUseCase.java
│   ├── port/in/FindProductUseCase.java
│   └── service/...
└── infrastructure/
    └── adapter/in/web/
        ├── ProductRouter.java
        ├── ProductHandler.java
        ├── ProductWebMapper.java
        └── dto/
            ├── CreateProductRequest.java
            └── ProductResponse.java
```

| Pieza | Responsabilidad | No hace |
|---|---|---|
| Router | Une método y ruta con un método del handler | Lógica |
| Handler | Lee, valida, mapea, llama al caso de uso y arma la respuesta | Reglas de negocio |
| Web mapper | Convierte DTO ↔ dominio | Validaciones |
| Caso de uso | Lógica de negocio | Nada de HTTP |

### DTOs

```java
public record CreateProductRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull @Positive BigDecimal price,
        List<@Valid CreateVariantRequest> variants) {
}

public record ProductResponse(UUID id, String name, BigDecimal price) {
}
```

### Puerto de entrada

```java
public interface CreateProductUseCase {

    Mono<Product> create(CreateProductCommand command);
}
```

### Web mapper

```java
@Mapper(componentModel = "spring")
public interface ProductWebMapper {

    CreateProductCommand toCommand(CreateProductRequest request);

    ProductResponse toResponse(Product product);
}
```

### Handler

```java
@Component
@RequiredArgsConstructor
public class ProductHandler {

    private final CreateProductUseCase createProductUseCase;
    private final FindProductUseCase findProductUseCase;
    private final RequestValidator requestValidator;
    private final ProductWebMapper mapper;

    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(CreateProductRequest.class)
                .flatMap(requestValidator::validate)
                .map(mapper::toCommand)
                .flatMap(createProductUseCase::create)
                .map(mapper::toResponse)
                .flatMap(product -> ApiResponse.from(Mono.just(product))
                        .flatMap(body -> ServerResponse
                                .created(URI.create("/api/v1/products/" + product.id()))
                                .bodyValue(body)));
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        return pathUuid(request, "id")
                .flatMap(findProductUseCase::findById)
                .map(mapper::toResponse)
                .as(ApiResponse::from)
                .flatMap(body -> ServerResponse.ok().bodyValue(body));
    }

    private static Mono<UUID> pathUuid(ServerRequest request, String name) {
        return Mono.fromCallable(() -> UUID.fromString(request.pathVariable(name)))
                .onErrorMap(IllegalArgumentException.class,
                        error -> new ServerWebInputException("Invalid path variable: " + name));
    }
}
```

### Router

```java
@Configuration
public class ProductRouter {

    @Bean
    RouterFunction<ServerResponse> productRoutes(ProductHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/products", builder -> builder
                        .POST("", RequestPredicates.contentType(MediaType.APPLICATION_JSON), handler::create)
                        .GET("/{id}", handler::findById))
                .build();
    }
}
```

### Prueba de la capa web

```java
@WebFluxTest
@Import({
        ProductRouter.class,
        ProductHandler.class,
        ProductWebMapperImpl.class,
        RequestValidator.class,
        ValidationConfiguration.class,
        GlobalErrorHandler.class,
        ProblemDetailMapper.class,
        ClockConfiguration.class
})
class ProductRouterTests {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateProductUseCase createProductUseCase;

    @MockitoBean
    private FindProductUseCase findProductUseCase;

    @Test
    void createsProduct() {
        given(createProductUseCase.create(any())).willReturn(Mono.just(product));

        webTestClient.post()
                .uri("/api/v1/products")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(validJson)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().exists("Location")
                .expectBody()
                .jsonPath("$.traceId").exists()
                .jsonPath("$.data.id").isEqualTo(product.id().toString());
    }
}
```

### Reglas

1. Todas las rutas van bajo `/api/v1`, agrupadas con `.path(...)`.
2. Todo cuerpo se valida con `RequestValidator` antes de mapearlo.
3. Toda respuesta con cuerpo usa `ApiResponse.from(...)`.
4. `201` lleva `Location`; `204` no lleva cuerpo.
5. Los errores viajan con `Mono.error`, nunca con `throw`. El `GlobalErrorHandler` los convierte en `ProblemDetail`.
6. Un parámetro de ruta inválido termina en `ServerWebInputException` (`400`), nunca en `500`.
7. El handler no contiene reglas de negocio ni accede a repositorios.
8. Cada router tiene su prueba con `@WebFluxTest` y los casos de uso simulados con `@MockitoBean`.

## Convenciones
- Sin comentarios en el código, salvo encabezados en archivos de propiedades y comentarios descriptivos en el `pom.xml`.
- El Javadoc no se escribe durante el desarrollo; la documentación del código se hará en una etapa posterior.
- Más estándares del proyecto en el [plan](./docs/PLAN.md#estándares).
