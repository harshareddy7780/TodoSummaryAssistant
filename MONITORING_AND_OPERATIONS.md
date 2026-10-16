# Monitoring and Operations Guide

## 1. Monitoring Components

- Prometheus collects application and host metrics.
- Grafana displays monitoring dashboards.
- Node Exporter exposes host CPU, memory, and disk metrics.
- Spring Boot Actuator exposes health and application metrics.

Configuration files:
- monitoring/prometheus.yml
- monitoring/alert-rules.yml
- monitoring/grafana-dashboard.json

## 2. Health Checks

Backend health endpoint:
http://localhost:8081/actuator/health

Prometheus metrics endpoint:
http://localhost:8081/actuator/prometheus

These addresses assume the backend is reachable on port 8081.

## 3. Important Metrics

Monitor:
- Backend availability
- HTTP request rate
- HTTP 5xx error rate
- Request latency
- Host CPU usage
- Host memory usage
- Host disk usage
- Container restarts and uptime

## 4. Alert Rules

The repository defines:
- BackendTargetDown: backend scraping fails for two minutes.
- HighHostCPU: CPU usage exceeds 85% for five minutes.

Configure and test a notification destination before relying on alerts in production.

## 5. Troubleshooting

### Backend failure
Run:
docker compose logs --tail=200 backend

Check the health endpoint, application logs, environment variables, and database connectivity.

### Database connection failure
Verify the RDS endpoint, credentials, database availability, routing, and security groups. Allow port 3306 only from the EC2 application security group.

### Prometheus target is DOWN
Check the target hostname, port, metrics path, Docker network, and service logs.

### Grafana shows no data
Check the Prometheus data source URL, target status, metric names, and dashboard time range.

### Frontend cannot reach the backend
Verify the frontend API URL, backend reachability, and CORS configuration. Do not use localhost as the API address for users accessing the application remotely.

## 6. Deployment and Rollback

1. Confirm that CI tests and image builds pass.
2. Deploy images tagged with the Git commit SHA.
3. Check backend health and frontend availability.
4. Run a basic application smoke test.
5. Inspect logs and dashboards.
6. If deployment fails, redeploy the previous known-good image tags.
7. Verify health again and document the incident.

Database schema changes may not be reversible by rolling back application images. Back up the database before risky changes.

## 7. Security

- Never commit populated .env files or credentials.
- Store secrets in a protected secret store or CI credential manager.
- Use a least-privilege EC2 IAM role.
- Keep RDS private.
- Restrict SSH and monitoring access.
- Patch dependencies and review AWS costs.
- Remove assessment resources when they are no longer needed.

## Container Restart and Uptime Checks

Use Docker to inspect container restart counts and start times:

```bash
docker inspect --format "{{.Name}} {{.RestartCount}} {{.State.StartedAt}}" $(docker compose ps -q)
```

- **RestartCount:** Number of times Docker has restarted the container.
- **StartedAt:** Timestamp when the current container process started.
- **Expected state:** All application and monitoring containers should be running, with zero unexpected restarts.

Investigate containers with increasing restart counts or unexpected start times. Check logs using:

```bash
docker compose logs --tail=100
docker compose logs backend
docker compose logs frontend
```

Note: These checks report Docker Engine state directly. They are not Prometheus metrics unless a separate exporter is configured.
