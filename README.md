# ramses-api

Spring Boot API for Ramses Bazaar: catalogue, product photos, shop settings,
admin sign-in and orders. The shop and admin panel are in
[ramses-ui](https://github.com/ElchosaryIosif/ramses-ui).

## Stack

- Spring Boot 4, Java 21
- Spring Security (signed bearer tokens for the single admin)
- Spring Data JPA + Postgres, Flyway migrations
- springdoc OpenAPI (`/swagger-ui.html`)
- Jib → `ghcr.io/elchosaryiosif/ramses-api`, run on Railway

## Run

```sh
cp .env.example .env    # fill in DB_* and SESSION_SECRET
set -a; . ./.env; set +a
./gradlew bootRun       # http://localhost:8080
./gradlew test          # in-memory H2, no database needed
```

## Endpoints

| | | |
| --- | --- | --- |
| `GET /api/products` | public | `{rev, products}` |
| `PUT /api/products` | admin | `{baseRev, products}`; 409 with the current catalogue if `baseRev` is stale |
| `GET /api/settings` / `PUT` | public / admin | contact and bank details |
| `POST /api/login` | public | `{password, remember}` → `{token, exp, status}` |
| `GET /api/session` | admin | `{status}` |
| `POST /api/password` | admin | `{current, next}`; signs out other devices |
| `GET /api/images/{key}` | public | product photo, cached for a year |
| `POST /api/orders` | public | priced from the catalogue, not the request |
| `GET /api/orders`, `PATCH /api/orders/{id}` | admin | list orders, set status |

## Structure

```
src/main/java/gr/ramsesbazaar/core/
  controller/   REST endpoints
  service/      catalogue, images, settings, auth, orders
  security/     token filter, @RequireAdmin
  entity/ repository/ dto/ config/
src/main/resources/db/migration/   Flyway migrations
docs/schema.md                     database schema
docs/deploy.md                     Railway + database set-up and domain switch
```
