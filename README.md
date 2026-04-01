# query-service

## Run with Docker Compose

This starts ClickHouse, Redis, and the Spring Boot service together.

```powershell
cd D:\Projects\Tanalytics\services\query-service
docker compose up --build -d
```

Check status and logs:

```powershell
docker compose ps
docker compose logs -f query-service
```

Basic checks:

```powershell
curl "http://localhost:8123/?query=SELECT%201"
curl http://localhost:8080/actuator/health
docker exec tanalytics-redis redis-cli ping
```

Stop/reset:

```powershell
docker compose down
docker compose down -v
```

