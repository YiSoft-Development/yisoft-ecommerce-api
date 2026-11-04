# Plan del e-commerce base de YiSoft

## Objetivo

Construir un MVP con lo común a cualquier tienda en línea. Cada cliente nace como un fork de esta base y solo agrega lo que necesite.

## Decisiones

| Tema | Decisión |
|---|---|
| Prioridad | El e-commerce va primero; InventoryAPI queda en pausa |
| Back | Java 25, Spring Boot 4.1, WebFlux, 100 % reactivo |
| Arquitectura | Monolito hexagonal por feature, con un solo POM |
| Paquete raíz | `com.yisoft.development.ecommerce` |
| Base de datos | PostgreSQL con R2DBC |
| Migraciones | Flyway, con SQL escrito a mano |
| Autenticación | JWT propio con Spring Security reactivo |
| Roles | `CUSTOMER` y `ADMIN` |
| Pagos | Stripe Checkout, confirmado por webhook, detrás de un puerto |
| Catálogo | Productos con variantes; cada variante tiene SKU, precio y stock |
| Checkout | Como invitado o con cuenta |
| Front | Next.js 16 con TypeScript, hecho por Claude con un skill propio |
| Backlog | Markdown en este repo |

## Alcance del MVP

- **Tienda:** catálogo, categorías, búsqueda, detalle con variantes, carrito y checkout.
- **Dashboard de cliente:** perfil, direcciones, historial de pedidos, seguimiento y solicitud de devolución.
- **Dashboard de admin:** productos, variantes, stock, pedidos, clientes, envíos, cupones, devoluciones y métricas básicas.
- **Pagos:** pago con Stripe, confirmación por webhook y reembolsos.
- **Entregas:** tarifa fija, recolección en tienda y paquetería por API.
- **Correos:** registro, confirmación de pedido, cambios de estado y devoluciones.

### Supuestos

- Cada fork tiene una sola tienda, con su despliegue y base de datos. No es multi-tenant.
- La operación usa pesos mexicanos, español y un solo almacén.
- Los invitados consultan su pedido con un enlace seguro enviado por correo.

### Fuera de la base

Reseñas, favoritos, facturación CFDI, multimoneda, multiidioma, varios almacenes, pasarelas adicionales, métricas avanzadas y funciones a la medida.

## Features

| Feature | Responsabilidad |
|---|---|
| `identity` | Registro, login, JWT, roles y perfil |
| `catalog` | Categorías, productos, variantes y búsqueda |
| `inventory` | Stock, reservas y ajustes |
| `cart` | Carrito de invitado y de cliente |
| `order` | Checkout, pedidos, estados y consulta de invitado |
| `payment` | Stripe, webhooks y reembolsos |
| `shipping` | Tarifas, recolección, guías y paquetería |
| `promotion` | Cupones y reglas de uso |
| `returns` | Solicitud, aprobación y reembolso |
| `notification` | Correos a partir de eventos |

Un feature no toca las tablas de otro: se comunican por puertos o eventos.

## Uso de cada herramienta

- **PostgreSQL:** fuente de verdad de pedidos, pagos, stock y reservas.
- **Redis:** carritos con expiración, refresh tokens, límites de intentos y caché del catálogo. No decide stock ni pagos.
- **RabbitMQ:** eventos entre features y correos, con reintentos y cola de errores.

## Flujo de compra

1. El cliente confirma el checkout y el back recalcula precios, cupón y envío.
2. Se crea el pedido en `PENDING_PAYMENT` y se reserva el stock con un `UPDATE` atómico.
3. Se crea una sesión de Stripe Checkout con expiración de 30 minutos.
4. El webhook valida la firma, ignora eventos repetidos y marca el pedido como `PAID`.
5. La misma transacción guarda el evento en una tabla outbox, y de ahí se publica a RabbitMQ.
6. Si el pago no llega, el pedido se cancela y se libera el stock.

El stock se reserva al iniciar el checkout, no al agregar al carrito. El carrito vive solo en Redis y puede expirar.

## Retos técnicos

- R2DBC no tiene relaciones ni carga lazy: los agregados se mapean a mano.
- Flyway usa JDBC solo al arrancar, no durante las peticiones.
- BlockHound detecta código bloqueante en pruebas.

## Estándares

- Cada historia define request, response, datos guardados, errores y pruebas.
- Sin comentarios en el código, salvo Javadoc a nivel de clase, encabezados en archivos de propiedades y comentarios descriptivos en el `pom.xml`.
- La lógica de negocio no va en la capa HTTP.
- Karate cubre la integración. Los contenedores se levantan con un perfil `ci` de Maven, sin Testcontainers.
- Cobertura con JaCoCo en el perfil `ci`: 90 % en dominio y 85 % en casos de uso.
- PIT corre solo sobre el dominio crítico.
- El build falla con vulnerabilidades altas o críticas.
- Ramas: `feature/*` nace de `develop` y regresa por PR. `develop` pasa a `release` por PR. `release` es QA y `main` es producción.
- El code review señala lo bloqueante, lo que pueda generar un bug y las palabras mal escritas.

## Roadmap

Estimación del back a 10 horas por semana. Cada feature se cierra primero en el back y luego se hace su front contra el OpenAPI.

| Fase | Entrega | Semanas |
|---|---|---|
| 0. Cimientos | Repos, Docker local, WebFlux, Flyway, CI, errores, OpenTelemetry y skill de front | 3 |
| 1. Identidad | Registro, login, JWT, roles y perfil | 3 |
| 2. Catálogo e inventario | Productos, variantes, búsqueda, stock y ajustes | 5 |
| 3. Carrito y cupones | Carrito invitado y cliente, fusión al iniciar sesión y descuentos | 3 |
| 4. Eventos | Outbox, RabbitMQ, reintentos y correos | 3 |
| 5. Checkout y pagos | Pedido, reserva, Stripe, webhook y tarifa fija o recolección | 6 |
| 6. Operación | Dashboards, gestión de pedidos, envío manual y métricas básicas | 3 |
| 7. Devoluciones | Solicitud, aprobación, reembolso y regreso de stock | 3 |
| 8. Paquetería | Cotización y guías por API | 3 |
| 9. Endurecimiento | Pruebas de carga, seguridad, observabilidad y checklist de fork | 3 |

Total: unas 35 semanas. El primer hito vendible es el cierre de la fase 6.

## Estrategia de forks

1. Cada mejora de la base sale como una versión con tag, por ejemplo `v1.2.0`.
2. El repo de cada cliente nace de un tag y conserva un remote `upstream` hacia la base.
3. El cliente actualiza haciendo merge de una versión nueva por PR. Las mejoras genéricas regresan primero a la base.
4. La personalización usa configuración, adaptadores nuevos y paquetes propios. Se evita editar el dominio base.
5. Las migraciones de la base usan `V1` a `V999` y las del cliente desde `V1000`, con `outOfOrder=true` y una prueba en CI que aplique ambas.
6. En el front, cada cliente tiene su archivo de tema, logos y textos; los componentes base se comparten.
7. Cada cliente tiene despliegue, base, Redis, RabbitMQ y credenciales propios. El costo se define con el primer cliente.
