# ms-vehiculos — Pipeline CI/CD (EP2 DOY0101 · Ingeniería DevOps)

Microservicio **Spring Boot 4 (Java 21) + MySQL** con un pipeline CI/CD en **GitHub Actions**. El pipeline analiza la seguridad (Snyk, SonarCloud, Trivy y Dependabot), ejecuta pruebas automatizadas con JaCoCo, construye el JAR y la imagen Docker, y la despliega automáticamente en un **cloud simulado**: un clúster Kubernetes multi-nodo creado con `kind`.

```
                                                                                    ┌─► Issue de alerta 🚨
push / PR ─► snyk-scan ─► test ─► sonar-analysis ─► build ─► docker ─► deploy       │   (si algo falla)
              (SCA)      (JUnit    (SAST +          (JAR)   (imagen   (Kubernetes ──┘
                         +JaCoCo)  Quality Gate)            + Trivy)   kind + HPA)
     Si un job falla, todos los siguientes se OMITEN: nada inseguro llega a desplegarse.

Dependabot (semanal) ─► PRs con dependencias actualizadas ─► pasan por el mismo pipeline
```

## Estructura del repositorio

```
.github/
  workflows/ci-cd.yml      Pipeline CI/CD
  dependabot.yml           Escaneo semanal de dependencias
ms-vehiculos/
  src/main/...             Código del microservicio
  src/test/...             Pruebas unitarias e integración
  pom.xml                  Maven + JaCoCo (quality gate 70 %)
  Dockerfile               Imagen multi-etapa, no-root
  docker-compose.yml       Orquestación local: MySQL + 3 réplicas + Nginx
  k8s/                     Orquestación Kubernetes (namespace, MySQL, deployment, HPA, NetworkPolicy…)
  scripts/smoke-test.sh    Prueba post-despliegue
```

## Cumplimiento de la pauta

| Requisito del encargo | Dónde | IE |
|---|---|---|
| Dockerfile para contenerizar el microservicio | `ms-vehiculos/Dockerfile` | IE1 |
| Construcción de imagen Docker en el pipeline | job `docker` | IE1 |
| Pruebas unitarias (JUnit) | job `test` | IE2 |
| Escaneo de dependencias con Dependabot | `.github/dependabot.yml` | IE3 |
| Análisis con Snyk y SonarCloud | jobs `snyk-scan` y `sonar-analysis` | IE3 |
| Alertas o bloqueos si falla la seguridad | Cadena `needs:` + Quality Gate + job `alerta` (Issue) + `snyk monitor` (correo) | IE3 |
| Despliegue automático en entorno simulado | job `deploy` (Kubernetes kind) | IE4 |
| Orquestación de contenedores | `k8s/` (Kubernetes) y `docker-compose.yml` | IE5 |
| README con trazabilidad y calidad | este archivo | IE4 |

---

## Etapas del pipeline

| # | Job | Qué hace | Artefacto | Si falla |
|---|---|---|---|---|
| 1 | `snyk-scan` | **SCA**: busca vulnerabilidades HIGH/CRITICAL en las dependencias del `pom.xml` | `snyk-report` | Bloquea todo |
| 2 | `test` | `mvn verify`: pruebas unitarias, de integración y quality gate de cobertura JaCoCo ≥ 70 % | `jacoco-report`, `test-reports` | Bloquea |
| 3 | `sonar-analysis` | **SAST**: SonarCloud analiza bugs, vulnerabilidades, code smells, hotspots y cobertura. `qualitygate.wait=true` hace fallar el job si no pasa el Quality Gate | — | Bloquea |
| 4 | `build` | Empaqueta el JAR | `app-jar` | Bloquea |
| 5 | `docker` | Trivy (secretos, malas configuraciones, vulnerabilidades de la imagen), imagen Docker, SBOM, verificación de no-root y publicación en GHCR | `docker-image` | Bloquea |
| 6 | `deploy` | Despliegue en Kubernetes, smoke test, prueba de carga con HPA y rollback automático | resumen | Rollback |
| — | `alerta` | Abre un **Issue** asignado al autor si cualquier etapa falla | Issue | — |

**¿Por qué Snyk va primero?** Revisar dependencias es lo más barato (segundos). Si hay una librería vulnerable, no tiene sentido gastar minutos en pruebas, análisis y build: el pipeline falla rápido (*fail fast*).

## IE1 · Contenedores

- **Dockerfile multi-etapa:** compila con `maven:3.9-eclipse-temurin-21` y la imagen final solo lleva el JRE (`eclipse-temurin:21-jre-alpine`), así que es liviana y sin herramientas de compilación.
- La capa de dependencias se cachea y el build de GitHub Actions también usa caché.
- Usuario **no-root** (UID 1001), parches del SO, `HEALTHCHECK` contra Actuator y la JVM ajustada al contenedor (`MaxRAMPercentage=75`).
- La imagen se etiqueta con el SHA del commit y en `main` se publica en GitHub Container Registry.

## IE2 · Pruebas automatizadas y política de calidad

| Tipo | Archivo | Herramienta |
|---|---|---|
| Unitarias del servicio (10) | `VehiculoServiceTest.java` | JUnit 5 + Mockito |
| Unitarias de la capa web (7) | `VehiculoControllerTest.java` | JUnit 5 + MockMvc |
| Integración (app completa + BD H2) | `VehiculoApiIT.java` | Spring Boot Test + Failsafe |
| Smoke test post-despliegue | `scripts/smoke-test.sh` | curl contra Kubernetes |

**Políticas de calidad aplicadas:**
1. Si una prueba falla, el pipeline se detiene.
2. La cobertura de líneas debe ser al menos 70 % (JaCoCo `check`).
3. Se debe pasar el **Quality Gate de SonarCloud**.

## IE3 · Escalabilidad y seguridad

**Seguridad en el pipeline:**
- Snyk (SCA), SonarCloud (SAST), Trivy (secretos, IaC e imagen) y Dependabot.
- Los tokens (`SNYK_TOKEN`, `SONAR_TOKEN`) se guardan como **GitHub Secrets** y nunca en el código.
- `permissions: contents: read` por defecto (mínimo privilegio).
- Bloqueo automático por la cadena `needs:` y alerta con un Issue en el repositorio y un correo de Snyk.

**Seguridad en los contenedores:**
- Pod Security `restricted`, `readOnlyRootFilesystem`, `capabilities: drop ALL` y seccomp.
- `NetworkPolicy` zero-trust: solo el microservicio puede hablar con MySQL.
- Credenciales de la BD aleatorias en un Secret de Kubernetes. `application.yml` ya no tiene la IP ni la contraseña escritas.

**Escalabilidad:**
- `HorizontalPodAutoscaler` de 2 a 8 réplicas según CPU y memoria.
- `requests`/`limits` en cada contenedor, más `ResourceQuota` y `LimitRange` en el namespace.
- `RollingUpdate` sin caída (`maxUnavailable: 0`) y `PodDisruptionBudget`.
- Probes de arranque, vida y disponibilidad; la disponibilidad incluye la BD.

## IE4 · Despliegue automático y trazabilidad

El job `deploy` crea un clúster Kubernetes con **kind** (1 control-plane + 2 workers) que simula un proveedor cloud. Luego despliega MySQL y el microservicio, ejecuta el smoke test y una prueba de carga para el HPA, y si algo falla hace **rollback automático**.

**Trazabilidad del commit a producción:**
- **Commit → imagen:** la imagen se etiqueta con el SHA del commit (`ms-vehiculos:a1b2c3d`).
- **Imagen → despliegue:** el Deployment se anota con el commit, visible en `kubectl rollout history` dentro del resumen del run.
- **Evidencia por ejecución:** `snyk-report`, `jacoco-report`, `test-reports`, `app-jar`, `docker-image` y SBOM.
- **Calidad histórica:** el panel de SonarCloud guarda cada análisis.
- **Historial de despliegues:** el environment `cloud-sim` aparece en la pestaña *Deployments* de GitHub.
- **Cada bloqueo** deja un Issue con el commit, el autor y el enlace a la ejecución.

## IE5 · Orquestación de contenedores

- **Kubernetes** (`ms-vehiculos/k8s/`), usado por el pipeline:
  - Namespace seguro.
  - ConfigMap + Secret.
  - MySQL como StatefulSet con volumen persistente.
  - Deployment con réplicas repartidas entre nodos.
  - Service balanceado.
  - HPA + PDB.
  - NetworkPolicies.
- **Docker Compose** (`ms-vehiculos/docker-compose.yml`), para uso local: MySQL con healthcheck, 3 réplicas con límites de CPU y memoria, y un balanceador Nginx.

---

## Configuración inicial

1. **Snyk:** crea una cuenta en snyk.io, copia el *Auth Token* y créalo como secret `SNYK_TOKEN`.
2. **SonarCloud:**
   - Importa la organización y el repositorio.
   - **Apaga Automatic Analysis** (*Administration → Analysis Method*).
   - Genera un token y créalo como secret `SONAR_TOKEN`.
   - Pon tu *Organization Key* y tu *Project Key* en `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY` al inicio de `ci-cd.yml`.
3. **Dependabot:** en *Settings → Code security*, activa *Dependabot alerts* y *security updates*.
4. **(Recomendado) Protección de rama:** exige que los checks pasen antes de fusionar a `main`.

## Ejecutar localmente

```bash
cd ms-vehiculos
./mvnw verify
cp .env.example .env
docker compose up -d --build
```

Swagger UI: http://localhost:8080/swagger-ui/index.html
