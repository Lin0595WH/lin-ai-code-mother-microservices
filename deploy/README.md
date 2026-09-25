# Backend deployment bundle

Build from the repository root, then copy the executable JARs into this bundle:

```bash
mvn clean verify
mkdir -p deploy/releases deploy/data/code_output deploy/data/code_deploy
cp lin-ai-code-user/target/lin-ai-code-user-1.0-SNAPSHOT.jar deploy/releases/lin-ai-code-user.jar
cp lin-ai-code-app/target/lin-ai-code-app-1.0-SNAPSHOT.jar deploy/releases/lin-ai-code-app.jar
cp lin-ai-code-screenshot/target/lin-ai-code-screenshot-1.0-SNAPSHOT.jar deploy/releases/lin-ai-code-screenshot.jar
```

On the server, create each `application-prod.yml` from its `.example` file and replace every placeholder. The runtime layout is `/www/wwwroot/aiapp/backend`; the App service persists generated source and deployments under `data/code_output` and `data/code_deploy`. Containers run as UID/GID `10001`, so make production configuration group-readable and data directories writable by that identity. The Vue builder receives only one project's source archive per request; it has no production configuration, shared source volume, or Docker socket:

```bash
chown root:10001 config/*/application-prod.yml
chmod 640 config/*/application-prod.yml
chown -R 10001:10001 data/code_output data/code_deploy
```

```bash
docker compose -p ai-code-mother config
docker compose -p ai-code-mother build
docker compose -p ai-code-mother up -d user screenshot
docker compose -p ai-code-mother up -d vue-builder app
```

## Production routing

Build the frontend with `npm run pure-build` in the frontend repository. The resulting Vue bundle is served by the existing Baota **PHP website** at `aiapp.linwh.top`, with document root `/www/wwwroot/aiapp/front-end`. Keep its History-mode fallback to `index.html` and serve generated deployments from `/dist/`. `deploy/nginx/aiapp-rewrite.conf` contains the `/api/`, `/dist/`, and SPA rules; merge them into the PHP site's Nginx configuration. If the main server block already has `location /api`, edit that rule instead of adding a duplicate.

The PHP site's `/api/` rule must use `proxy_pass http://127.0.0.1:8080;` **without a URI suffix** so the backend receives the `/api` prefix. Send `Host $host`, forward the original scheme, set `proxy_read_timeout` and `proxy_send_timeout` to `900s`, and keep `proxy_buffering off` for streamed chat responses.

Higress uses host port `8001` for its console and `127.0.0.1:8080` for the gateway. Keep the existing gateway mapping on `8081` for other consumers. User and app share `baota_net` with Higress; their Compose network aliases are `ai-code-mother-user.svc` and `ai-code-mother-app.svc`. Add these to the existing McpBridge DNS registries without replacing unrelated registries:

| Registry name | DNS domain | Port | Route path | `higress.io/destination` |
| --- | --- | ---: | --- | --- |
| `ai-code-mother-user` | `ai-code-mother-user.svc` | 8124 | `/api/user` | `ai-code-mother-user.dns:8124` |
| `ai-code-mother-app` | `ai-code-mother-app.svc` | 8125 | `/api` | `ai-code-mother-app.dns:8125` |

Both Ingress routes use host `aiapp.linwh.top`, `pathType: Prefix`, and `higress.io/timeout: "900"`. Do not rewrite their paths. Higress 2.2.x reads `higress.io/timeout` as the route timeout; `nginx.ingress.kubernetes.io/proxy-read-timeout` does not configure it.

For Nacos in each external `application-prod.yml`, keep `address`, `username`, and `password` as separate fields, and replace their placeholders with the server's actual values. Compose does not pass those environment variables into the containers. Do not put credentials in the Nacos URL query. Keep these files owned by `root:10001` with mode `640`; never package them with the JARs or deployment bundle.

The app's `ChatMemoryStore` uses ordinary Redis string operations and does not require RedisJSON. The isolated Vue builder's `/tmp` tmpfs needs `exec` so npm can run `esbuild`. The screenshot container uses `/usr/bin/chromedriver` from its image; when `webdriver.chrome.driver` is set, the screenshot service leaves that path intact instead of letting WebDriverManager replace it.

Public HTTPS checks completed on 2026-09-25: register, login, app creation, Vue chat SSE through the `done` event (104 events), preview resources, deployed `/dist/` resources, a Vue counter interaction, source ZIP download, and screenshot generation with COS upload and cover update. Frontend JS/CSS and static image hashes match the local `pure-build` output. The backend passed `mvn clean verify` (61 tests, no failures or errors).
