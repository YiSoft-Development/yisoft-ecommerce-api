# HU-0.3: Conexión R2DBC y primera migración

**Historia:** como desarrollador de YiSoft, quiero que la aplicación se conecte a PostgreSQL de forma reactiva y aplique sus migraciones al arrancar, para que cada feature tenga su persistencia lista.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.3-reactive-persistence` desde `develop` actualizada y abrir el PR hacia `develop`.

## Cómo funciona

- Las peticiones de la aplicación usan R2DBC.
- Flyway usa JDBC solo al arrancar.
- Por eso el proyecto lleva dos drivers y dos URL hacia la misma base.

## Dependencias

| Dependencia | Alcance |
|---|---|
| `spring-boot-starter-data-r2dbc` | compile |
| `spring-boot-starter-flyway` | compile |
| `org.flywaydb:flyway-database-postgresql` | compile |
| `org.postgresql:r2dbc-postgresql` | runtime |
| `org.postgresql:postgresql` | runtime |

Las versiones las administra Spring Boot, así que no llevan `<version>`.

Comprueba en `mvn dependency:tree` que `io.r2dbc:r2dbc-pool` esté presente. Si no lo está, agrégalo como dependencia de compilación.

## Variables de entorno

Agregar a `.env.example`, y a tu `.env`, dentro del bloque de PostgreSQL:

```properties
POSTGRES_HOST=localhost
POSTGRES_PORT=5432
```

## Configuración

`application.properties` queda así:

```properties
# Application
spring.application.name=yisoft-ecommerce-api

# Environment
spring.config.import=optional:file:.env[.properties]

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

- `spring.config.import` lee tu `.env` en local. Si el archivo no existe, se usan las variables de entorno del sistema.
- `spring.flyway.out-of-order=true` permite aplicar migraciones de la base después de las de un fork.
- No deben existir valores por defecto para usuario ni contraseña.
- No se declara `spring.datasource.*`.

## Primera migración

Crear `src/main/resources/db/migration/V1__create_set_updated_at_function.sql`:

```sql
CREATE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;
```

Esta función la usarán los triggers de las tablas que tengan `updated_at`. Esta historia no crea tablas de negocio.

## Reglas de migraciones

- Las migraciones de la base usan `V1` a `V999`.
- Un archivo aplicado no se edita. Cualquier cambio va en una migración nueva.
- El SQL se escribe a mano.

## Qué se guarda

| Objeto | Resultado |
|---|---|
| `flyway_schema_history` | Flyway la crea y registra la versión `1` con `success = true` |
| `set_updated_at()` | Función disponible en el esquema `public` |

## Pruebas esperadas

Crear `DatabaseInfrastructureTests` en el paquete `com.yisoft.development.ecommerce.shared.persistence`, dentro de `src/test/java`. Usa `@SpringBootTest` y lleva Javadoc a nivel de clase.

| Prueba | Qué comprueba |
|---|---|
| `connectionFactoryUsesPool` | El bean `ConnectionFactory` es una instancia de `ConnectionPool` |
| `databaseClientExecutesQuery` | `SELECT 1` por `DatabaseClient` regresa `1`, verificado con `StepVerifier` |
| `firstMigrationIsApplied` | `flyway_schema_history` tiene la versión `1` con `success = true` |
| `setUpdatedAtFunctionExists` | `pg_proc` contiene la función `set_updated_at` |

Las pruebas no deben usar `.block()`.

Desde esta historia, `mvn clean verify` necesita PostgreSQL arriba. El perfil `ci` de la HU-0.8 lo automatiza. Documenta en el README que primero se corre `docker compose up -d --wait`.

## Validación manual

Documenta el resultado en el PR.

1. Con una base limpia, `mvn spring-boot:run` arranca y el log indica que aplicó 1 migración.
2. Tu gestor gráfico muestra `flyway_schema_history` con la versión `1` y la función `set_updated_at`.
3. Al arrancar otra vez, Flyway indica que el esquema está actualizado y no aplica nada.
4. Con PostgreSQL detenido, la aplicación falla al arrancar con un error de conexión claro.
5. Sin `POSTGRES_PASSWORD` en `.env`, la aplicación falla al arrancar por la propiedad faltante.

## Criterios de aceptación

- `mvn clean verify` termina en `BUILD SUCCESS` con PostgreSQL arriba.
- Las cinco validaciones manuales se cumplen.
- `.env` no está versionado.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Tablas de negocio, repositorios, entidades, perfiles `qa` y `prod`, Redis, RabbitMQ, health checks y perfil `ci`.

## Criterios de revisión

- El código de la aplicación no usa JDBC. JDBC solo lo usa Flyway.
- No hay `.block()` en las pruebas.
- No hay secretos ni valores por defecto para credenciales.
- La migración sigue la convención de nombres.
