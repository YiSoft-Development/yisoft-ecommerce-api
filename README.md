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
1. Clonar el repositorio
2. Ejecutar ```mvn clean install``` para poder bajar las dependencias al repositorio local.
3. Ejecutar la aplicación con ```mvn spring-boot:run```.

## Recursos
- [Plan del Proyecto](./docs/PLAN.md)
- [Backlog](./docs/backlog/README.md)   

## Servicios docker
- Docker
- Redis
- Mailpit   

> Como ejecutar el archivo [docker-compose.yaml](./docker-compose.yaml)
1. Copiar ```.env.example``` a ```.env``` y cambiar los valores.
2. Levantar los servicios: ```docker compose up -d --wait```.
3. Ver su estado: ```docker compose ps```.
4. Detenerlos conservando datos: ```docker compose down```.
5. Detenerlos y borrar datos: ```docker compose down -v```.
6. | Servicio | Imagen | Puertos | Volumen |
   |---|---|---|---|
   | `postgres` | `postgres:18-alpine` | `127.0.0.1:5432:5432` | `postgres-data:/var/lib/postgresql` |
   | `redis` | `redis:8-alpine` | `127.0.0.1:6379:6379` | `redis-data:/data` |
   | `rabbitmq` | `rabbitmq:4-management-alpine` | `127.0.0.1:5672:5672` y `127.0.0.1:15672:15672` | `rabbitmq-data:/var/lib/rabbitmq` |
   | `mailpit` | `axllent/mailpit`, con la última versión estable fijada | `127.0.0.1:1025:1025` y `127.0.0.1:8025:8025` | Ninguno |