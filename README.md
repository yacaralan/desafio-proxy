Desafio: Proxy de APIs — Primera versión

Resumen

Esta es una primera versión de un "proxy de apis" implementado en Java 21 con Spring Boot (WebFlux) y Maven.

Objetivos cubiertos por ahora

- Proxy transparente hacia api.mercadolibre.com: cualquier ruta enviada al proxy se reenvía al host configurado en `proxy.target-base-url`.
- Rate limiting naive configurable (in-memory) con reglas por IP, por path y combinadas (IP+path) usando Bucket4j.
- Estadísticas en memoria naive: totales, permitidos, denegados, contadores por path y por IP.
- Interfaz REST administrativa para ver estadísticas y reglas (/admin/stats, /admin/rules) y añadir reglas.
- Basado en WebFlux para facilitar alto throughput.

Stack:

- `pom.xml` — convertido a proyecto Spring Boot 3.2 + dependencias clave:
  - `spring-boot-starter-webflux`: pila reactiva para alto rendimiento.
  - `reactor-netty-http`: cliente HTTP reactivo.
  - `bucket4j-core`: rate limiting in-memory.
  - `spring-boot-starter-actuator`: salud/monitorización.
  Motivo: ofrecer una base escalable y dependencias necesarias para el proxy.

- `src/main/java/org/example/Main.java` — application entry point (Spring Boot).
  Motivo: arrancar la aplicación Spring Boot.

- `src/main/java/org/example/config/ProxyConfig.java` — define `WebClient` apuntando a `proxy.target-base-url` y desactivar redirecciones.
  Motivo: centralizar la configuración del cliente usado para proxy.

- `src/main/java/org/example/ratelimit/RateLimitService.java` — servicio en memoria con reglas (IP, PATH, IP_PATH, GLOBAL) usando Bucket4j.
  Motivo: implementar control de cantidad máxima de llamados por distintos criterios.

- `src/main/java/org/example/stats/StatsService.java` — servicio en memoria para capturar estadísticas simples.
  Motivo: almacenar métricas de uso para inspección rápida.

- `src/main/java/org/example/controller/ProxyController.java` — controlador reactivo que intercepta todas las rutas `/**` y reenvía al `WebClient`.
  - Excluye rutas `/admin` y `/actuator`.
  - Aplica rate-limit y registra estadísticas.
  Motivo: comportamiento core del proxy.

- `src/main/java/org/example/controller/AdminController.java` — endpoints REST para consultar estadísticas y reglas y añadir nuevas reglas.
  Motivo: control y visibilidad.

- `src/main/resources/application.properties` — configuración mínima (puerto 8080, SSL y URL objetivo).

Cómo ejecutar

1) Construir:

```bash
mvn -DskipTests package
```

2) Ejecutar:

```bash
java -jar target/desafio-proxy-1.0-SNAPSHOT.jar
```

3) Endpoints útiles:
- Proxy: http://localhost:8080/{ruta}
  Ejemplo: curl localhost:8080/categories/MLA97994 -> reenvía a https://api.mercadolibre.com/categories/MLA97994
- Admin:
  - GET /admin/stats
  - GET /admin/rules
  - POST /admin/rules  (body JSON: {"type":"IP|PATH|IP_PATH|GLOBAL","pattern":"...","rpm":1000})

Notas sobre diseño y escalabilidad

- WebFlux (Netty) y WebClient: pila no bloqueante con baja latencia y alto throughput, adecuada para objetivo de 50k req/s en escenarios con la infraestructura correcta.
- Rate limiting in-memory (Bucket4j): sencillo y rápido; en producción para escalar horizontalmente se recomienda usar una solución distribuida (Redis-backed buckets o una capa de API-GW con rate limiting centralizado).
- Estadísticas en memoria: suficiente para POC; para producción usar una base de series temporales o datastore (InfluxDB, Prometheus, Elastic) y exponer métricas a través de /actuator/metrics.
- Balanceo y escalado: desplegar múltiples instancias detrás de un load-balancer, mantener estado de rate-limit en un store compartido para políticas globales o por IP.

Limitaciones conocidas y próximos pasos

- El rate-limiter actual es in-memory y no comparte estado entre instancias.
- No hay caching (requisito: sin cache). Si se necesitara caching, agregar una estrategia configurable.
- No hay UI; es REST básico.
- Seguridad: no se han agregado autenticación/autorización para los endpoints admin.

Diagrama simple (top-level):

Client(s) --> Load Balancer --> Proxy Instances (this app) --> api.mercadolibre.com
                                           |
                                           +-> Stats (in-memory / to be externalized)

Posibles proximas mejoras:
- persistencia de métricas (Prometheus/InfluxDB),
- rate-limit distribuido (quizas Redis + Bucket4j extension?),
- pruebas de carga y ajuste de configuración Reactor Netty,
- visualización estadísticas,
- diagramas más detallados (arquitectura),
