# Deploying Ramses Bazaar (Railway + the Filotimo database server)

Same shape as Filotimo:

| Piece | Code | Image (GHCR) | Runs on |
| --- | --- | --- | --- |
| API | this repo — Spring Boot 4, Java 21, Flyway | `ghcr.io/elchosaryiosif/ramses-api` | Railway |
| Shop + admin | [ramses-ui](https://github.com/ElchosaryIosif/ramses-ui) — React/Vite, served by nginx | `ghcr.io/elchosaryiosif/ramses-ui` | Railway |
| Database | Postgres, schema in `src/main/resources/db/migration` (see [schema.md](schema.md)) | — | the Postgres server Filotimo uses |

Each repo builds its own image with GitHub Actions on every push to its
**`release`** branch (`ramses-api`: `.github/workflows/deploy.yml`, `ramses-ui`:
`.github/workflows/release.yml`). Pull requests and pushes to `main` only run
the checks.

The live site keeps running on Netlify until step 6, so nothing goes down while
you set this up.

---

## 1. Create the database

On the Postgres server Filotimo uses, create a separate database and user for
Ramses, so neither app can touch the other's data:

```sql
CREATE USER ramses WITH PASSWORD '<a long random password>';
CREATE DATABASE ramses OWNER ramses;
```

The API creates its own tables on first start (Flyway), so nothing else is needed.

## 2. Publish the images

In **both** repos, create a `release` branch from `main` (GitHub → branch
menu → type `release` → *Create branch: release from main*), or:

```bash
git push origin main:release
```

Later releases: merge `main` into `release`.

Then check the repo's **Actions** tab: both workflows should finish green, and
**Packages** on your GitHub profile should list `ramses-api` and `ramses-ui`.

Railway has to be able to pull them. Either do what Filotimo's `api-server`
does (registry credentials in Railway), or open each package → **Package
settings → Change visibility → Public**. The images hold no secrets, so making
them public is safe.

## 3. API service on Railway

In the Railway project (Filotimo's project is fine, or a new one):
**New → Docker Image →** `ghcr.io/elchosaryiosif/ramses-api:latest`.

Variables:

| Variable | Value |
| --- | --- |
| `DB_HOST` | host of the database server, e.g. `xyz.eu-central-1.rds.amazonaws.com:5432` (same as Filotimo's) |
| `DB_NAME` | `ramses` |
| `DB_USER` | `ramses` |
| `DB_PASSWORD` | the password from step 1 |
| `SESSION_SECRET` | output of `openssl rand -hex 32`. Changing it logs every admin out. |
| `ADMIN_PASSWORD` | *(optional)* always accepted as the admin password. Use it to get back in if the password is lost; delete it afterwards. |

Under **Settings → Networking**, add the custom domain **`api.ramsesbazaar.com`**.
Under **Settings → Deploy**, set the healthcheck path to `/api/settings`.

Check it: `https://<the railway url>/api/products` should answer
`{"rev":0,"products":[]}`. API docs are at `/swagger-ui.html`.

## 4. Web service on Railway

**New → Docker Image →** `ghcr.io/elchosaryiosif/ramses-ui:latest`.

| Variable | Value |
| --- | --- |
| `API_BASE` | `https://api.ramsesbazaar.com` (until DNS is switched, use the API's `*.up.railway.app` URL) |

Add the custom domains **`ramsesbazaar.com`** and **`www.ramsesbazaar.com`**.

The API already accepts calls from `ramsesbazaar.com`, `*.ramsesbazaar.com` and
`*.up.railway.app`. If the shop is ever served from another origin, add that
origin to the API's `CORS_EXTRA_ORIGINS` variable (comma-separated).

## 5. Try it before switching

Open the web service's `*.up.railway.app` URL:

1. The shop loads (empty catalogue).
2. `/#admin`: sign in with `ramses1996`, or with `ADMIN_PASSWORD` if you set it.
   **Change the password straight away** under ⚙️ Ρυθμίσεις.
3. Re-enter the shop details (WhatsApp, phone, IBAN, bank, account name, email)
   under ⚙️ Ρυθμίσεις. The old Netlify settings storage was broken and there is
   nothing to carry over; the live Netlify catalogue is empty too.
4. Add a product with a photo, place a test order from the shop, and check it
   under 🧾 Παραγγελίες.

## 6. Switch the domain

The `ramsesbazaar.com` DNS zone is hosted by Netlify DNS (Netlify → DNS →
ramsesbazaar.com). Railway shows the exact records to add when you add a custom
domain (step 3 and 4). Add them there:

- `api` → **CNAME** to the target Railway gives for the API.
- `www` → **CNAME** to the target Railway gives for the web service.
- `ramsesbazaar.com` (apex) → **ALIAS** record to the web service's target.
  A plain CNAME is not allowed at the apex.
- Any `_railway-verify` **TXT** records Railway asks for.

Then delete the two `NETLIFY`-type records for `ramsesbazaar.com` and
`www.ramsesbazaar.com`, and remove the domain from the Netlify project. Railway
issues the HTTPS certificates once DNS points at it, usually within minutes.

When the site works on the real domain, delete the two Netlify projects (`ramsesbazaar.com` and `ramsesbazaarkalamata`). The old `ramsesbazaar` repo
can be archived. Keep the **DNS zone** on Netlify: it's free, and moving it means changing the
nameservers at the registrar, which needs the account that registered the domain.

## Costs

- Railway bills by usage. Two small services (a JVM API and an nginx container)
  typically cost a few dollars a month on top of the plan.
- The database shares the existing server, so it adds no new instance.

## WhatsApp message for every new order

The API can send the shop's phone a WhatsApp message for each order, through the
free [CallMeBot](https://www.callmebot.com/blog/free-api-whatsapp-messages/) service.
Orders are saved before the message is sent, so if CallMeBot is ever down nothing
is lost: the order is still in 🧾 Παραγγελίες.

1. On the phone that should get the alerts, save CallMeBot's WhatsApp number as a
   contact. The current number is on the CallMeBot page above; it changes now and then.
2. Send that contact this WhatsApp message: `I allow callmebot to send me messages`
3. It replies with an **API key** (a few digits).
4. On the API service in Railway, add:

   | Variable | Value |
   | --- | --- |
   | `WHATSAPP_PHONE` | the phone, with country code: `+306912345678` |
   | `WHATSAPP_APIKEY` | the key from step 3 |

5. Place a test order from the shop. The message arrives within a minute.

Leave both empty to turn the alerts off.

## Not done yet

- **No email when an order comes in.** Orders appear in the admin panel's
  🧾 Παραγγελίες tab and, if set up, as a WhatsApp message. Adding email needs
  an SMTP or email-API account (for example AWS SES on the same AWS account).
