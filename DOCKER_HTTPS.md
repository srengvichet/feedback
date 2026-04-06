# Docker HTTPS Deployment

This project supports HTTPS in Docker by running Spring Boot behind an Nginx reverse proxy.

## Services

* `db`: MySQL 8.4
* `app`: Spring Boot application on internal port `8080`
* `proxy`: Nginx on `80` and `443`

## First Start

1. Create or update `.env` in the project root.
2. Set at least these values:

```env
DB_ROOT_PASSWORD=change-me
PUBLIC_HOST=localhost
APP_BASE_URL=https://localhost
HTTP_PORT=80
HTTPS_PORT=443
```

3. Start everything:

```powershell
docker compose up --build -d
```

4. Open the system in the browser:

```text
https://localhost
```

## Certificates

If `docker/certs/server.crt` and `docker/certs/server.key` do not exist, the proxy generates a self-signed certificate automatically.

That is useful for testing, but the browser will show a warning until the certificate is trusted.

For production, replace those files with a real TLS certificate and private key:

* `docker/certs/server.crt`
* `docker/certs/server.key`

## Notes

* HTTP requests are redirected to HTTPS.
* Spring uses forwarded headers, so generated links and redirects can stay on `https`.
* QR code URLs should use `APP_BASE_URL=https://your-hostname`.