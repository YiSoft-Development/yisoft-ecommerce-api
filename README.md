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
3. Ejecutar el archivo ```compose.yaml``` con ``docker compose up -d --wait``
3. Ejecutar la aplicación con ```mvn spring-boot:run```.

## Recursos
- [Plan del Proyecto](./docs/PLAN.md)
- [Backlog](./docs/backlog/README.md)   

## Servicios docker
- Postgres
- Redis
- RabbiMQ
- Mailpit   

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