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