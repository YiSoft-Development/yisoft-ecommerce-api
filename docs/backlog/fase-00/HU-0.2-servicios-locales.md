# HU-0.2: Servicios locales con Docker Compose

**Historia:** como desarrollador de YiSoft, quiero levantar con un comando los servicios que necesita el e-commerce para trabajar en local sin instalarlos uno por uno.

**Responsable:** Adrián

## Ramas

Crear `feature/HU-0.2-local-services` desde `develop` actualizada y abrir el PR hacia `develop`.

## Archivos

| Archivo | Acción |
|---|---|
| `../../../compose.yaml` | Crear en la raíz |
| `.env.example` | Crear en la raíz y versionar |
| `.env` | Crear solo en local; ya está ignorado |
| `README.md` | Agregar la sección de servicios locales |

Esta historia no toca Java, `pom.xml` ni `application.properties`.

## Proyecto de Compose

`../../../compose.yaml` declara `name: yisoft-ecommerce` y estos cuatro servicios. La app no corre como contenedor.

| Servicio | Imagen | Puertos | Volumen |
|---|---|---|---|
| `postgres` | `postgres:18-alpine` | `127.0.0.1:5432:5432` | `postgres-data:/var/lib/postgresql` |
| `redis` | `redis:8-alpine` | `127.0.0.1:6379:6379` | `redis-data:/data` |
| `rabbitmq` | `rabbitmq:4-management-alpine` | `127.0.0.1:5672:5672` y `127.0.0.1:15672:15672` | `rabbitmq-data:/var/lib/rabbitmq` |
| `mailpit` | `axllent/mailpit`, con la última versión estable fijada | `127.0.0.1:1025:1025` y `127.0.0.1:8025:8025` | Ninguno |

Reglas:

- No usar el tag `latest`.
- Todos los puertos se publican solo en `127.0.0.1`.
- PostgreSQL 18 monta el volumen en `/var/lib/postgresql`, no en `/var/lib/postgresql/data`.
- No crear `networks`; basta con la red que Compose genera por defecto.

## Variables de entorno

`.env.example` contiene estas claves, con valores de ejemplo y sin secretos reales:

```properties
# PostgreSQL
POSTGRES_DB=ecommerce
POSTGRES_USER=ecommerce
POSTGRES_PASSWORD=change-me

# Redis
REDIS_PASSWORD=change-me

# RabbitMQ
RABBITMQ_USER=ecommerce
RABBITMQ_PASSWORD=change-me
```

`../../../compose.yaml` no lleva contraseñas escritas. Las claves obligatorias se leen con `${VARIABLE:?mensaje}`, para que Compose falle con un mensaje claro si falta alguna.

## Configuración por servicio

### `postgres`

- Recibe `POSTGRES_DB`, `POSTGRES_USER` y `POSTGRES_PASSWORD`.
- Healthcheck: `pg_isready` con el usuario y la base configurados.

### `redis`

- Arranca con `redis-server --requirepass <contraseña> --appendonly yes`.
- Recibe `REDISCLI_AUTH` con la misma contraseña, para que `redis-cli` no la lleve en el comando.
- Healthcheck: `redis-cli ping`.

### `rabbitmq`

- Recibe `RABBITMQ_DEFAULT_USER` y `RABBITMQ_DEFAULT_PASS` desde `RABBITMQ_USER` y `RABBITMQ_PASSWORD`.
- No debe usar el usuario `guest`.
- Healthcheck: `rabbitmq-diagnostics -q ping`.

### `mailpit`

- SMTP en el puerto `1025` e interfaz web en el `8025`.
- Healthcheck: `/mailpit readyz`.

## README

Agregar una sección con:

1. Copiar `.env.example` a `.env` y cambiar los valores.
2. Levantar los servicios: `docker compose up -d --wait`.
3. Ver su estado: `docker compose ps`.
4. Detenerlos conservando datos: `docker compose down`.
5. Detenerlos y borrar datos: `docker compose down -v`.
6. Una tabla con el servicio, el puerto y la URL de las interfaces web.

## Pruebas esperadas

Esta historia no agrega pruebas automatizadas. La validación es manual y se documenta en el PR.

1. Con `.env` completo, `docker compose config --quiet` termina sin error.
2. Al quitar `POSTGRES_PASSWORD` de `.env`, `docker compose config --quiet` falla con un mensaje claro. Después se restaura el valor.
3. `docker compose up -d --wait` termina sin error.
4. `docker compose ps` muestra los cuatro servicios en `healthy`.
5. Tu gestor gráfico se conecta a PostgreSQL en `localhost:5432`.
6. `docker compose exec redis redis-cli ping` responde `PONG`.
7. La consola de RabbitMQ abre en `http://localhost:15672` con tu usuario. `guest` no entra.
8. Mailpit abre en `http://localhost:8025`.
9. Crea una tabla de prueba, corre `docker compose down` y luego `docker compose up -d --wait`. La tabla sigue ahí.
10. Después de `docker compose down -v`, la tabla ya no existe.

## Criterios de aceptación

- Los diez puntos anteriores se cumplen.
- `git ls-files` no incluye `.env`.
- No hay secretos reales en `../../../compose.yaml`, `.env.example` ni el README.
- El PR contiene solo lo que pide esta historia.

## Fuera de esta historia

Conexión desde Spring, Flyway, tablas, exchanges y colas de RabbitMQ, envío de correos, Stripe y contenedor de la app.

## Criterios de revisión

- Imágenes con versión fijada, sin `latest`.
- Puertos publicados solo en `127.0.0.1`.
- Volumen de PostgreSQL en la ruta correcta.
- Cada healthcheck comprueba su servicio.
- Las variables obligatorias fallan con mensaje claro.
- No hay contraseñas escritas en `../../../compose.yaml`.
