# Restaurante Backend

API REST independiente para el sistema de restaurante, basada en Java 17 y Spring Boot 3.

## Requisitos

- Java 17 o superior.
- PostgreSQL en `localhost:5432`.
- Base de datos `restaurante_db`.

```sql
CREATE DATABASE restaurante_db;
```

## Configuración y ejecución

Las propiedades aceptan variables de entorno para no guardar credenciales reales en el repositorio:

| Variable | Valor local predeterminado |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/restaurante_db` |
| `DB_USERNAME` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `JWT_SECRET` | clave local de desarrollo en Base64 |
| `JWT_EXPIRATION_MS` | `28800000` |
| `PIN_MOZO` | `1234` |
| `PIN_COCINERO` | `5678` |
| `PIN_ADMIN` | `9999` |
| `SEED_DATA_ENABLED` | `true` |

Desde PowerShell, establece las credenciales que usa tu instalación local de PostgreSQL y ejecuta el wrapper:

```powershell
$env:JAVA_HOME = "<ruta-a-tu-JDK-17-o-superior>"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "<tu-password-local>"
Set-Location .\backend
.\mvnw.cmd spring-boot:run
```

El servidor escucha en `http://localhost:8080`. En el primer inicio se crean, si las tablas están vacías, los usuarios, seis mesas y el menú. Los PIN se guardan con BCrypt; define un `JWT_SECRET` propio fuera de desarrollo.

## Autenticación

```http
POST /api/v1/auth/login
Content-Type: application/json

{"pin":"1234"}
```

Envía el token recibido en el resto de endpoints:

```http
Authorization: Bearer <token>
```

Roles: `ROLE_MOZO`, `ROLE_COCINERO` y `ROLE_ADMIN`.

## Endpoints

| Método | Ruta | Rol |
| --- | --- | --- |
| `POST` | `/api/v1/auth/login` | Público |
| `GET` | `/api/v1/mozo/mesas` | Mozo |
| `GET` | `/api/v1/mozo/productos` | Mozo |
| `POST` | `/api/v1/mozo/pedidos` | Mozo |
| `GET` | `/api/v1/mozo/pedidos/estado` | Mozo |
| `PUT` | `/api/v1/mozo/pedidos/{id}/estado` | Mozo, para entrega |
| `GET` | `/api/v1/cocina/pedidos/pendientes` | Cocinero |
| `PUT` | `/api/v1/cocina/pedidos/{id}/estado` | Cocinero |
| `GET` | `/api/v1/cocina/resumen` | Cocinero |
| `GET` | `/api/v1/admin/ventas` | Administrador; admite `fechaInicio` y `fechaFin` opcionales (`yyyy-MM-dd`), sin fechas lista todas las ventas entregadas |
| `GET` | `/api/v1/admin/reportes/excel` | Administrador |

Los pedidos se guardan inicialmente como `PENDIENTE` (valor permitido por la restricción PostgreSQL existente); cocina puede moverlos a `EN_PROCESO` o `LISTO`, y el mozo puede marcarlos `ENTREGADO`. El total se calcula desde el precio vigente del producto en el servidor.

## CORS y registros

CORS permite `http://localhost:5500` y `http://127.0.0.1:5500` (además del frontend local en puerto 8001). Los logs se escriben en `logs/restaurante-backend.log` con rotación diaria y por tamaño.