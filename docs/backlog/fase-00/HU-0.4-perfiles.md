# HU-0.4: Perfiles por ambiente

**Historia:** como desarrollador de YiSoft, quiero separar la configuración de `local`, `qa` y `prod`, para que cada ambiente use sus propios valores y ningún secreto viva en el repositorio.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.4-environment-profiles` desde `develop` actualizada y abrir el PR hacia `develop`.

## Cómo debe funcionar

| Perfil | Cómo se activa | De dónde salen los valores |
|---|---|---|
| `local` | Automático, si no se indica otro perfil | Archivo `.env` |
| `qa` | `SPRING_PROFILES_ACTIVE=qa` | Variables de entorno del ambiente |
| `prod` | `SPRING_PROFILES_ACTIVE=prod` | Variables de entorno del ambiente |

Solo el perfil `local` lee `.env`. Si en `qa` o `prod` falta una variable, la aplicación no debe arrancar.

## Archivos

Todos van en `src/main/resources` y se versionan, porque ninguno lleva secretos.

### `application.properties`

Conserva todo lo común. Se quita `spring.config.import` y se agrega `spring.profiles.default`:

```properties
# Application
spring.application.name=yisoft-ecommerce-api
spring.profiles.default=local

# R2DBC
spring.r2dbc.url=r2dbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
spring.r2dbc.username=${POSTGRES_USER}
spring.r2dbc.password=${POSTGRES_PASSWORD}
spring.r2dbc.pool.initial-size=2
spring.r2dbc.pool.max-size=10
spring.r2dbc.pool.max-idle-time=30m
spring.r2dbc.pool.validation-query=SELECT 1

# Flyway
spring.flyway.url=jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DB}
spring.flyway.user=${POSTGRES_USER}
spring.flyway.password=${POSTGRES_PASSWORD}
spring.flyway.out-of-order=true
```

### `application-local.properties`

```properties
# Environment
spring.config.import=optional:file:.env[.properties]

# Logging
logging.level.com.yisoft.development.ecommerce=DEBUG
```

### `application-qa.properties`

```properties
# Logging
logging.level.root=INFO
logging.level.com.yisoft.development.ecommerce=DEBUG
```

### `application-prod.properties`

```properties
# Logging
logging.level.root=WARN
logging.level.com.yisoft.development.ecommerce=INFO
```

## README

Agregar una sección de perfiles con:

- La tabla de "Cómo debe funcionar".
- Las variables obligatorias en `qa` y `prod`: `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD`.

## Pruebas esperadas

Crear `ProfileConfigurationTests` en el paquete `com.yisoft.development.ecommerce.shared.config`, dentro de `src/test/java`. Usa `@SpringBootTest` y lleva Javadoc a nivel de clase.

| Prueba | Qué comprueba |
|---|---|
| `defaultProfileIsLocal` | `environment.getDefaultProfiles()` contiene `local` |

## Validación manual

Documenta el resultado en el PR. Los comandos son para PowerShell y se ejecutan con PostgreSQL arriba.

1. Sin indicar perfil, `mvn spring-boot:run` arranca y el log muestra que usa `local` como perfil por defecto.
2. Con `mvn spring-boot:run -Dspring-boot.run.profiles=qa`, la aplicación no arranca, porque en `qa` no se lee `.env`.
3. Con las variables cargadas en la terminal, la aplicación arranca en `qa`:

   ```powershell
   $env:POSTGRES_HOST="localhost"
   $env:POSTGRES_PORT="5432"
   $env:POSTGRES_DB="<tu valor de .env>"
   $env:POSTGRES_USER="<tu valor de .env>"
   $env:POSTGRES_PASSWORD="<tu valor de .env>"
   mvn spring-boot:run -Dspring-boot.run.profiles=qa
   ```

4. En esa misma terminal, la aplicación arranca en `prod`:

   ```powershell
   mvn spring-boot:run -Dspring-boot.run.profiles=prod
   ```

5. Cierra esa terminal para descartar las variables. En una nueva, `mvn clean verify` termina en `BUILD SUCCESS`.

## Criterios de aceptación

- Las cinco validaciones manuales se cumplen.
- `spring.config.import` solo existe en `application-local.properties`.
- `.env` no está versionado.
- Ningún archivo de propiedades contiene secretos ni valores por defecto para credenciales.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Perfil `ci`, ajustes de pool por ambiente, TLS hacia la base, Redis, RabbitMQ, correo e infraestructura de `qa` y `prod`.

## Criterios de revisión

- `spring.profiles.default` está en `application.properties`, no en un archivo de perfil.
- `qa` y `prod` no pueden leer `.env`.
- Cada archivo contiene solo lo que cambia en su ambiente.
- No hay secretos en el PR.
