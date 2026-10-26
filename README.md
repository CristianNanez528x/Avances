# Sistema de Restaurante El Paisa

Aplicación para operación de salón, cocina y administración. La interfaz web y la API Java están separadas en carpetas para facilitar su ubicación.

## Estructura

```text
Avances/
  frontend/   HTML, CSS, JavaScript y servidor web local
  backend/    API REST Spring Boot, PostgreSQL y pruebas
```

## Requisitos

- Node.js para servir la interfaz.
- Java 17 o superior para la API.
- PostgreSQL y la base `restaurante_db`.

Configura la conexión a PostgreSQL en `backend/src/main/resources/application.properties`.

## Ejecutar la aplicación

Abre dos terminales desde la carpeta `Avances`.

Terminal 1, backend:

```powershell
Set-Location .\backend
$env:JAVA_HOME = "<ruta-a-tu-JDK-17-o-superior>"
.\mvnw.cmd spring-boot:run
```

Terminal 2, frontend:

```powershell
Set-Location .\frontend
node .\serve.mjs
```

Abre `http://localhost:8001`. La API escucha en `http://localhost:8080`.

## Roles de demostración

- Mozo: PIN `1234`
- Cocinero: PIN `5678`
- Administrador: PIN `9999`

Los PIN se guardan con BCrypt. El seed crea una cuenta con el PIN configurado si no existe una cuenta activa de ese rol que ya lo utilice; no modifica ni elimina usuarios existentes.

Los pedidos se guardan en PostgreSQL. Las pestañas abiertas sincronizan los cambios y las ventas de administración incluyen pedidos entregados. Consulta [backend/README.md](backend/README.md) para los endpoints, autenticación y configuración detallada de la API.

## Pruebas backend

```powershell
Set-Location .\backend
$env:JAVA_HOME = "<ruta-a-tu-JDK-17-o-superior>"
.\mvnw.cmd test
```
