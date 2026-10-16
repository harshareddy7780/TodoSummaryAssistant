# Failure and Rollback Procedure

## 1. Docker Image Optimization

The backend runtime image was optimized by switching from `eclipse-temurin:21-jre` to `eclipse-temurin:21-jre-alpine`.

| Metric | Original | Optimized |
|---|---:|---:|
| Docker disk usage | 578 MB | 398 MB |
| Image content size | 171 MB | 127 MB |
| Disk usage reduction | — | 180 MB (31.1%) |

The optimized image was tested before being deployed through Docker Compose.

## 2. Validation Performed

- Spring Boot application started successfully.
- MySQL database connection succeeded.
- Docker health check reported `healthy`.
- `/actuator/health` returned HTTP 200.
- `/api/todos` returned HTTP 200.
- Frontend loaded, displayed existing Todo items, and successfully created new items.

## 3. Rollback Plan

The original backend image is retained locally as:

`todosummaryassistant-backend:latest`

Its original image ID was `9fffb080cb46`.

If the Alpine image causes runtime problems:

1. Update `docker-compose.yml` under the `backend` service to use `image: todosummaryassistant-backend:latest`.
2. Remove the `build:` section for that service if it is present.
3. Validate the Compose configuration using `docker compose config --quiet`.
4. Apply the rollback using `docker compose up -d --no-build backend`.
5. Check the container health with `docker inspect --format='{{.State.Health.Status}}' todo-backend`.
6. Verify the API using `curl -i http://localhost:8081/api/todos`.
7. Verify the frontend at `http://localhost:3001`.

Do not delete the MySQL volume or database when rolling back the backend image.

## 4. Troubleshooting

### Backend is unhealthy

Inspect logs:

`docker logs --tail 100 todo-backend`

Check health:

`docker inspect --format='{{.State.Health.Status}}' todo-backend`

### Database connection failure

Verify that the MySQL service is healthy and that the backend uses:

`jdbc:mysql://mysql:3306/todo_db`

Confirm the database username and password are supplied through environment variables.

### API failure

Test the health endpoint:

`curl -i http://localhost:8081/actuator/health`

Test the Todo API:

`curl -i http://localhost:8081/api/todos`

## 5. Security Notes

- Do not commit `.env` or database credentials.
- Keep the backend running as a non-root user.
- Keep database access restricted to the required application services.
- Validate health and API functionality after every deployment or rollback.