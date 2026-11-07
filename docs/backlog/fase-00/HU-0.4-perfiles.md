# HU-0.4: Perfiles de Maven por ambiente

**Historia:** como desarrollador de YiSoft, quiero separar la configuración de `local`, `qa` y `prod` con perfiles y filtros de Maven, para generar cada ambiente con un comando y sin subir sus valores al repositorio.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.4-environment-profiles` desde `develop` actualizada y abrir el PR hacia `develop`.

## Cómo debe funcionar

| Perfil de Maven | Cómo se activa | Archivo de valores | ¿Se versiona? |
|---|---|---|---|
| `local` | Automático, si no se indica otro perfil | `.env` | No |
| `qa` | `-Pqa` | `src/main/filters/qa.properties` | No |
| `prod` | `-Pprod` | `src/main/filters/prod.properties` | No |

- Maven lee el archivo del perfil y reemplaza cada `@valor@` de `application.properties` al compilar.
- Se genera un jar por ambiente.
- No hay perfiles de Spring ni archivos `application-qa.properties` o `application-prod.properties`.
- Spring ya no lee `.env`, así que `spring.config.import` desaparece.
- Si falta el archivo del perfil, el build falla.

## Archivos

| Archivo | Acción |
|---|---|
| `src/main/filters/qa.example.properties` | Crear y versionar, sin valores reales |
| `src/main/filters/prod.example.properties` | Crear y versionar, sin valores reales |
| `src/main/filters/qa.properties` | Crear solo donde se compile `qa` |
| `src/main/filters/prod.properties` | Crear solo donde se compile `prod` |
| `.env.example` y tu `.env` | Agregar el bloque de logging |
| `.gitignore` | Ignorar los filtros reales |
| `pom.xml` | Agregar filtros y perfiles |
| `application.properties` | Cambiar a `@valor@` |
| `README.md` | Agregar la sección de perfiles |

`src/main/filters` no es carpeta de recursos, así que esos archivos no se copian tal cual al jar.

## Valores por ambiente

Los dos `*.example.properties` usan estas claves, sin valores reales:

```properties
# PostgreSQL
POSTGRES_HOST=
POSTGRES_PORT=
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=

# Logging
LOG_LEVEL_ROOT=
LOG_LEVEL_APP=
```

Agregar a `.env.example`, y a tu `.env`:

```properties
# Logging
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_APP=DEBUG
```

Niveles esperados:

| Ambiente | `LOG_LEVEL_ROOT` | `LOG_LEVEL_APP` |
|---|---|---|
| `local` | `INFO` | `DEBUG` |
| `qa` | `INFO` | `DEBUG` |
| `prod` | `WARN` | `INFO` |

## `.gitignore`

Agregar:

```text
src/main/filters/*.properties
!src/main/filters/*.example.properties
```

## `pom.xml`

Dentro de `<build>`, antes de `<plugins>`:

```xml
<filters>
    <filter>${filter.file}</filter>
</filters>
```

Después de `<build>`:

```xml
<profiles>
    <profile>
        <id>local</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <filter.file>${project.basedir}/.env</filter.file>
        </properties>
    </profile>

    <profile>
        <id>qa</id>
        <properties>
            <filter.file>${project.basedir}/src/main/filters/qa.properties</filter.file>
        </properties>
    </profile>

    <profile>
        <id>prod</id>
        <properties>
            <filter.file>${project.basedir}/src/main/filters/prod.properties</filter.file>
        </properties>
    </profile>
</profiles>
```

`spring-boot-starter-parent` ya activa el reemplazo de `@valor@` en `application*.properties`, así que no hay que configurar `<resources>`.

`activeByDefault` deja de aplicar cuando se activa cualquier otro perfil. La HU-0.8 debe tomarlo en cuenta al crear el perfil `ci`.

## `application.properties`

Es el único archivo de propiedades de Spring y queda así:

```properties
# Application
spring.application.name=yisoft-ecommerce-api

# Logging
logging.level.root=@LOG_LEVEL_ROOT@
logging.level.com.yisoft.development.ecommerce=@LOG_LEVEL_APP@

# R2DBC
spring.r2dbc.url=r2dbc:postgresql://@POSTGRES_HOST@:@POSTGRES_PORT@/@POSTGRES_DB@
spring.r2dbc.username=@POSTGRES_USER@
spring.r2dbc.password=@POSTGRES_PASSWORD@
spring.r2dbc.pool.initial-size=2
spring.r2dbc.pool.max-size=10
spring.r2dbc.pool.max-idle-time=30m
spring.r2dbc.pool.validation-query=SELECT 1

# Flyway
spring.flyway.url=jdbc:postgresql://@POSTGRES_HOST@:@POSTGRES_PORT@/@POSTGRES_DB@
spring.flyway.user=@POSTGRES_USER@
spring.flyway.password=@POSTGRES_PASSWORD@
spring.flyway.out-of-order=true
```

## Secretos

- Los archivos reales de `qa` y `prod` nunca se versionan.
- El jar generado contiene los valores de su ambiente, incluida la contraseña. No se publica ni se comparte fuera de ese ambiente.
- `target/` ya está ignorado.

## README

Agregar una sección de perfiles con:

- La tabla de "Cómo debe funcionar".
- Cómo preparar `qa` y `prod`: copiar el `*.example.properties` de su ambiente y llenar los valores.
- Cómo arrancar: `mvn spring-boot:run`, `mvn spring-boot:run -Pqa` o `mvn spring-boot:run -Pprod`.
- Cómo generar el jar: `mvn clean package -Pprod`.

## Pruebas esperadas

Crear `BuildProfileConfigurationTests` en el paquete `com.yisoft.development.ecommerce.shared.config`, dentro de `src/test/java`. Usa `@SpringBootTest` y lleva Javadoc a nivel de clase.

| Prueba | Qué comprueba |
|---|---|
| `mavenPlaceholdersAreResolved` | `logging.level.root`, `logging.level.com.yisoft.development.ecommerce` y `spring.r2dbc.url` tienen valor y no contienen `@` |
| `springProfilesAreNotUsed` | `environment.getActiveProfiles()` está vacío |

## Validación manual

Documenta el resultado en el PR, sin pegar valores reales. Se ejecuta con PostgreSQL arriba.

1. `mvn clean spring-boot:run` arranca con los valores de `.env`.
2. Sin `src/main/filters/qa.properties`, `mvn clean spring-boot:run -Pqa` falla porque no encuentra el archivo.
3. Copia `qa.example.properties` a `qa.properties`, llénalo con tus valores locales y `mvn clean spring-boot:run -Pqa` arranca.
4. Repite lo mismo con `prod`, usando los niveles de log de `prod`, y `mvn clean spring-boot:run -Pprod` arranca.
5. `mvn clean package -Pprod -DskipTests` genera el jar, y `target/classes/application.properties` no conserva ningún `@`.
6. `git status` no muestra `.env`, `qa.properties` ni `prod.properties`.
7. `mvn clean verify` termina en `BUILD SUCCESS` con `local`.

## Criterios de aceptación

- Las siete validaciones manuales se cumplen.
- No quedan perfiles de Spring ni `application-<ambiente>.properties`.
- No quedan `spring.config.import` ni valores `${...}` en `application.properties`.
- Ningún archivo versionado contiene secretos.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Perfil `ci`, ajustes de pool por ambiente, TLS hacia la base, Redis, RabbitMQ, correo e infraestructura de `qa` y `prod`.

## Criterios de revisión

- Cada `@valor@` existe en `.env.example` y en los dos archivos de ejemplo.
- Los filtros reales están ignorados y los ejemplos sí se versionan.
- No hay `spring.profiles.*`.
- No hay secretos en el PR.
