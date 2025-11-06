Desafio: Proxy de APIs — Primera versión

Resumen

Esta es una versión del proxy de APIs implementado en Java 21 con Spring Boot (WebFlux) y Maven.

Estado y resumen de cambios recientes

- Las reglas de rate-limiting ahora modelan el tipo con `RateLimitType` y la lógica por tipo (validación, matching, computeKey, index/remove) está delegada en implementaciones separadas de `RateLimitStrategy` (clases en `src/main/java/org/example/ratelimit/strategy`), lo que facilita tests unitarios y mantenimiento.
- `RateLimitRuleManager` delega validación en `RateLimitType` y crea `RateLimitRule` con un id único.
- `RateLimitService` mantiene índices para búsqueda eficiente y delega operaciones de indexado/eliminación en `RateLimitType`.
- Hay un cliente mock (`MockProxyClient`) disponible bajo el profile `mock-client` y lee los archivos desde `src/main/resources/mocks/` para que los mocks estén disponibles también en el JAR final.

Objetivos cubiertos por ahora

- Proxy transparente hacia `api.mercadolibre.com`: cualquier ruta enviada al proxy se reenvía al host configurado en `proxy.target-base-url`.
- Rate limiting en memoria con Bucket4j: reglas por IP, por path, combinadas (IP+path) y una regla GLOBAL (única) que puede reemplazar a la anterio
- Estadísticas en memoria naive: totales, permitidos, denegados, contadores por path y por IP.
- Interfaz REST administrativa para ver estadísticas y reglas (`/admin/stats`, `/admin/rules`) y añadir reglas.
- Basado en WebFlux para facilitar alto throughput.

Stack:

- Spring Boot 3.2 + WebFlux (reactive stack)
- Bucket4j (in-memory rate-limiter)
- Reactor Netty (server/client)
- JUnit/Mockito para tests

Arquitectura / ficheros clave

- `src/main/java/org/example/config/ProxyConfig.java` — configuración del `WebClient` usado para el upstream.
- `src/main/java/org/example/client/MockProxyClient.java` — cliente para servir mocks cuando se activa el profile `mock-client`.
- `src/main/resources/mocks/` — ejemplos y respuestas mock utilizadas por el `MockProxyClient` y empaquetadas en el JAR.

Configuración (`src/main/resources/application.properties`)

- `server.port=8080`
- `proxy.target-base-url` — URL del upstream (por defecto `https://api.mercadolibre.com` en esta versión).
- `proxy.default-content-type` — contenido que el proxy añadirá por defecto cuando el upstream no devuelva `Content-Type`. Dejar vacío para no forzar header.
- SSL/HTTPS: esta configuración incluye una entrada de keystore (`keystore.p12`) y `server.ssl.enabled=true` para permitir pruebas locales sobre HTTPS; ajusta según tu entorno.

Autenticación para endpoints admin

- Los endpoints `/admin/**` están protegidos con HTTP Basic.
- Las credenciales se leen desde `application.properties` como `admin.username` y `admin.password` en texto plano para desarrollo.

Cómo ejecutar

1) Construir:

```bash
mvn -DskipTests package
```

2) Ejecutar:

```bash
java -jar target/desafio-proxy-1.0-SNAPSHOT.jar
```

3) Ejecutar tests (unit + integration):

```bash
mvn test
```

Usar el cliente mock (profile `mock-client`):

```bash
mvn -Dspring-boot.run.profiles=mock-client spring-boot:run
# o si arrancás el JAR
java -Dspring.profiles.active=mock-client -jar target/desafio-proxy-1.0-SNAPSHOT.jar
```

EndPoints útiles

- Proxy: http://localhost:8080/{ruta}
  Ejemplo: curl http://localhost:8080/sites -> reenvía a `${proxy.target.base.url}/sites`
- Admin:
  - GET /admin/stats
  - GET /admin/rules
  - POST /admin/rules (body JSON con `type`, `ip`, `path`, `rpm`)
  - DELETE /admin/rules/{id}

Formato del body para crear reglas (POST /admin/rules)

- `type`: required, one of `IP`, `PATH`, `IP_PATH`, `GLOBAL` (usa `RateLimitType` internamente).
- `ip`: opcional o requerido según `type`.
- `path`: opcional o requerido según `type` (prefijos admitidos con `/*`).
- `rpm`: required, entero positivo.

Ejemplos válidos:
- IP: `{ "type": "IP", "ip": "127.0.0.1", "rpm": 100 }`
- PATH: `{ "type": "PATH", "path": "/api/test", "rpm": 50 }`
- IP_PATH: `{ "type": "IP_PATH", "ip": "192.168.1.1", "path": "/api/user", "rpm": 10 }`
- GLOBAL: `{ "type": "GLOBAL", "rpm": 1000 }`

Notas y recomendaciones

- Mocks empaquetados: `MockProxyClient` lee desde `src/main/resources/mocks` para que los mismos archivos estén disponibles en el JAR; esto permite ejecutar la app sin depender del upstream.
- Escalabilidad: el rate-limiter es in-memory. Para escalar horizontalmente, migrar los estados de rate-limit a un backend compartido (Redis + Bucket4j o similar).
- Seguridad: no usar las credenciales de ejemplo en producción; en producción usar vault/secret manager y TLS en los endpoints.

Contribuciones y tests

- ejecutar `mvn test` para ver la cobertura.
