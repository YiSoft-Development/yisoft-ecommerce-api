# HU-0.1: Proyecto base

**Historia:** como desarrollador de YiSoft, quiero un proyecto WebFlux limpio y bien estructurado para construir encima cada feature del e-commerce.

**Responsable:** Adrián

## Ramas

1. Crear `develop` desde `main` y subirla a GitHub.
2. Crear `feature/HU-0.1-project-base` desde `develop`.
3. Abrir el PR hacia `develop`. Incluye esta carpeta `docs/`.

## Proyecto Maven

Un solo `pom.xml`, sin multimódulo, con `spring-boot-starter-parent` como padre.

| Dato | Valor |
|---|---|
| `spring-boot-starter-parent` | `4.1.2` |
| `groupId` | `com.yisoft.development` |
| `artifactId` | `yisoft-ecommerce-api` |
| `version` | `0.1.0-SNAPSHOT` |
| `java.version` | `25` |
| Paquete raíz | `com.yisoft.development.ecommerce` |
| Clase principal | `EcommerceApiApplication` |

## Dependencias

Solo estas. Las demás entran con la historia que las necesite.

| Dependencia | Alcance |
|---|---|
| `spring-boot-starter-webflux` | compile |
| `org.projectlombok:lombok` | provided |
| `org.mapstruct:mapstruct` | compile |
| `spring-boot-starter-webflux-test` | test |
| `io.projectreactor:reactor-test` | test |

No debe entrar `spring-boot-starter-web`, porque el servidor debe ser Netty y no Tomcat.

## Procesadores de anotaciones

`maven-compiler-plugin` declara `annotationProcessorPaths` en este orden:

1. `lombok`
2. `lombok-mapstruct-binding`
3. `mapstruct-processor`

La versión de MapStruct va en una propiedad `mapstruct.version`, con la última estable. Boot administra la de Lombok: la dependencia no declara `<version>` y el procesador usa `${lombok.version}`.

## Empaquetado

`spring-boot-maven-plugin` excluye `org.projectlombok:lombok` en `<excludes>`, porque Lombok solo se usa al compilar y no debe llegar al jar. El alcance `provided` no lo evita.

## Estructura

```text
src/main/java/com/yisoft/development/ecommerce/
└── EcommerceApiApplication.java
src/main/resources/
└── application.properties
src/test/java/com/yisoft/development/ecommerce/
└── EcommerceApiApplicationTests.java
```

No se crean carpetas vacías por adelantado: Git no las versiona. Cada feature nace con su primera historia y sigue esta convención:

```text
<feature>/
├── domain/
│   ├── model/
│   └── exception/
├── application/
│   ├── port/in/
│   ├── port/out/
│   └── service/
└── infrastructure/
    └── adapter/
        ├── in/web/
        └── out/
```

El código compartido entre features va en `shared/`, y entra hasta que una historia lo necesite.

## Configuración

`application.properties` solo lleva esto, con su comentario de encabezado:

```properties
# Application
spring.application.name=yisoft-ecommerce-api
```

## `.gitignore`

Debe ignorar como mínimo:

```text
target/
.idea/
*.iml
.env
```

Confirma que `.idea/` no llegue al PR.

## README

Reemplazar el contenido actual por:

- Qué es el proyecto, en una o dos líneas.
- Requisitos: Java 25 y Maven.
- Cómo compilar y cómo arrancar.
- Enlace a `docs/PLAN.md` y `docs/backlog/README.md`.

## Pruebas esperadas

`EcommerceApiApplicationTests` usa `@SpringBootTest` y comprueba que el contexto carga. Lleva Javadoc a nivel de clase.

## Criterios de aceptación

- `mvn clean verify` termina en `BUILD SUCCESS`.
- La app arranca y el log muestra Netty en el puerto `8080`.
- `mvn dependency:tree` no muestra Tomcat ni `spring-webmvc`.
- El jar generado no contiene Lombok en `BOOT-INF/lib`.
- Lombok y MapStruct compilan juntos. Para confirmarlo, en local crea una prueba temporal con un DTO `@Data` y un mapper; bórrala antes del PR.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Docker Compose, conexión a base de datos, Flyway, perfiles, seguridad, manejo de errores, Actuator, OpenAPI, Karate y CI.

## Criterios de revisión

- Versiones, coordenadas y paquete raíz coinciden con esta historia.
- No sobran dependencias ni configuración.
- El orden de los procesadores es correcto.
- `.gitignore` cubre lo pedido y no hay archivos del IDE en el PR.
- No hay comentarios en Java; solo Javadoc a nivel de clase.
- El comentario del `.properties` y los comentarios descriptivos del `pom.xml` son los únicos permitidos.
