# Controllers API Reference

Este documento lista y describe los controladores REST públicos de la aplicación.

## ProxyController

- Path: `/**`
- Auth: pública (no requiere autenticación)
- Método: cualquier (GET, POST, PUT, DELETE, ...)
- Descripción: actúa como proxy hacia el host configurado en `proxy.target-base-url`. Reenvía la request recibida (método, path, query params, headers y body) al upstream y devuelve la respuesta tal cual (status, headers, body), salvo transformaciones mínimas configuradas (p. ej. `proxy.default-content-type`).

Behavior summary:
- Todas las rutas que no empiecen por `/admin` son proxied.
- Se aplica rate-limiting configurado por `RateLimitService`.
- Se registran estadísticas en `StatsService`.

Examples:
- Forward GET `/sites` -> upstream `GET ${proxy.target-base-url}/sites`
- Forward GET `/categories/MLA5725` -> upstream `GET ${proxy.target-base-url}/categories/MLA5725`

## AdminController

- Base path: `/admin`
- Auth: HTTP Basic (se requiere rol `ADMIN`) — las credenciales se leen desde `application.properties` (`admin.username`, `admin.password`) y el `SecurityConfig` aplica un `PasswordEncoder` (BCrypt) al iniciar la aplicación.

Endpoints:

- GET `/admin/stats`
  - Descripción: devuelve snapshot de estadísticas en memoria (permitidos, denegados, upstream statuses, etc.).
  - Auth: sí (Basic)
  - Respuesta: JSON object con métricas.

- GET `/admin/rules`
  - Descripción: lista las reglas actuales del rate-limiter (almacenadas en memoria).
  - Auth: sí (Basic)
  - Respuesta: JSON array de objetos `RateLimitRule` con campos: `id`, `type` (`IP|PATH|IP_PATH|GLOBAL`), `ip`, `path`, `requestsPerMinute`.

- POST `/admin/rules`
  - Descripción: crea una nueva regla en memoria. Para `GLOBAL` si ya existe una regla GLOBAL, la nueva sustituirá la anterior (reemplazo).
  - Auth: sí (Basic)
  - Body (application/json): espera un DTO con los campos:
    - `type` (string) — required: `IP`, `PATH`, `IP_PATH`, o `GLOBAL`.
    - `ip` (string) — requerido según `type` (ver validaciones abajo). Puede ser `"*"` para wildcard en reglas `IP`/`IP_PATH`.
    - `path` (string) — requerido según `type` (ver validaciones abajo). Puede terminar en `/*` para prefix matching.
    - `rpm` (number) — required, entero positivo: requests per minute.

  - Validaciones por `type` (realizadas en `RateLimitType`/`RateLimitRuleManager`):
    - `IP`: `ip` es requerido, `path` es ignorado.
    - `PATH`: `path` es requerido (debe empezar con `/`), `ip` es ignorado.
    - `IP_PATH`: tanto `ip` y `path`son requeridos.
    - `GLOBAL`: no `ip` ni `path` necesarios; aplica a todas las requests.

  - Respuesta: `200 OK` con `{ "ok": true, "rule": <rule-object> }` (la regla incluye `id` generado) si la creación fue exitosa. Respuestas de error:
    - `400` con `{ "ok": false, "error": "<mensaje>" }` para peticiones inválidas.

  - Ejemplos de body válidos:
    - IP: `{ "type": "IP", "ip": "192.168.0.254", "rpm": 100 }`
    - PATH: `{ "type": "PATH", "path": "/api/test", "rpm": 50 }`
    - IP_PATH: `{ "type": "IP_PATH", "ip": "192.168.0.254", "path": "/api/user", "rpm": 10 }`
    - GLOBAL: `{ "type": "GLOBAL", "rpm": 1000 }`

- DELETE `/admin/rules/{id}`
  - Descripción: elimina una regla por su `id`.
  - Auth: sí (Basic)
  - Respuesta: `200 OK` con `{ "ok": true, "id": "<id>" }` si se elimina, o `404` con `{ "ok": false, "error": "rule not found", "id": "<id>" }` si no existe.

Notes
- Las reglas son almacenadas en memoria (no persisten ni se replican entre instancias). Para entornos distribuidos se recomienda un backend compartido (Redis + Bucket4j o similar).
- Las validaciones por `type` se hacen en `RateLimitType` mediante la estrategia asociada a cada tipo, y `RateLimitRuleManager` usa esa validación para crear reglas seguras.
- `MockProxyClient` (profile `mock-client`) sirve mocks leyendo los recursos desde `src/main/resources/mocks/`, de modo que los mismos ficheros estén empaquetados en el JAR y disponibles en runtime fuera del entorno de tests.
- Seguridad: la configuración actual usa HTTP Basic para `/admin/**` y `SecurityConfig` aplica BCrypt encoding al password configurado en `application.properties` al iniciar; para producción no usar credenciales en texto plano.

Ejemplo de uso de curl para endpoints admin (dev):

```bash
# listar reglas (prompt para user/pass o -u user:pass)
curl -u admin:proxymeli123 http://localhost:8080/admin/rules

# crear regla global
curl -u admin:proxymeli123 -X POST -H "Content-Type: application/json" -d '{"type":"GLOBAL","rpm":1000}' http://localhost:8080/admin/rules

# eliminar regla
curl -u admin:proxymeli123 -X DELETE http://localhost:8080/admin/rules/<id>
```


---
