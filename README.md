# Docker Watchdog

Spring Boot service that periodically checks a local Docker daemon and sends SMTP email when a container becomes offline, unhealthy, starts health checking, disappears, or when Docker is unavailable. It sends one alert per state change and a recovery email when the problem clears, preventing repeated alerts for the same ongoing outage.

A weekly status report is sent independently of alerts.

## Configuration

Copy `.env.example` to `.env`, then set the SMTP settings and recipients. The application reads `.env` on startup; real environment variables take precedence.

Set `SMTP_FROM` to the sender address accepted by the SMTP relay. The relay can still rewrite or reject an unauthorized sender address; use an address or alias it permits.

`CONTAINERS=all` monitors every container. To monitor a dedicated subset, set exact Docker names, for example `CONTAINERS=api,worker,postgres`.

`CHECK_INTERVAL_MS` controls checks. `WEEKLY_REPORT_CRON` uses Spring's six-field cron format (second, minute, hour, day-of-month, month, day-of-week). `REPORT_TIMEZONE` is an IANA zone such as `Europe/Berlin`.

Containers without Docker health checks are considered healthy when their Docker state is `running`.

## Run

```bash
cp .env.example .env
mvn clean package
java -jar target/docker-watchdog-1.0.0.jar
```

The process user must be permitted to run `docker ps --all`. For a non-root user, add it to the `docker` group and start a new session.

## SMTP connectivity

For Gmail, use `SMTP_HOST=smtp.gmail.com`, `SMTP_PORT=587`, `SMTP_STARTTLS=true`, and an app password for `SMTP_PASSWORD`; normal Gmail account passwords are not supported.

The host must be allowed to make an outbound TCP connection to the configured SMTP port. An HTTPS proxy is for HTTP(S) traffic and does not proxy SMTP connections. If the network blocks port 587 or 465, use the organization's reachable SMTP relay or request an outbound firewall rule. If the network provides a SOCKS proxy, set `SMTP_SOCKS_HOST` and `SMTP_SOCKS_PORT`.

## Systemd

Copy the built JAR, `.env`, and `docker-watchdog.service` to `/opt/docker-watchdog`, then:

```bash
sudo cp docker-watchdog.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now docker-watchdog
```

The provided unit runs as root for Docker socket access. Change `User=` only after granting the chosen account Docker access.
