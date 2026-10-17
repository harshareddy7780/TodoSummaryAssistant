# Todo Summary Assistant — AWS Setup and Deployment Documentation

## 1. Project Overview

Todo Summary Assistant is a containerized web application deployed on AWS. It uses Docker for application packaging, Jenkins for CI/CD, Amazon EC2 for hosting, Amazon RDS for MySQL, and Prometheus with Grafana for monitoring.

### Technology Stack

| Component | Technology |
|---|---|
| Cloud platform | AWS |
| Application hosting | Amazon EC2 |
| Database | Amazon RDS for MySQL |
| Containerization | Docker |
| CI/CD | Jenkins |
| Source control | Git and GitHub |
| Metrics collection | Prometheus |
| Infrastructure metrics | Node Exporter |
| Visualization | Grafana |
| Frontend / web server | Nginx |
| Application metrics | Spring Boot Actuator and Micrometer Prometheus |

## 2. AWS Environment

- **AWS Region:** Asia Pacific (Sydney), `ap-southeast-2`
- **Compute:** Amazon EC2 running Amazon Linux 2023
- **Database:** Amazon RDS for MySQL
- **Frontend:** Nginx on port 80
- **Backend:** Port 8081
- **Prometheus:** Port 9090, bound to host localhost
- **Grafana:** Port 3000, bound to host localhost
- **Node Exporter:** Port 9100, internal access only

Obtain the current public IP and RDS endpoint from the AWS console. Do not publish database passwords, private keys, API tokens, or other secrets.

## 3. AWS Infrastructure Configuration

### 3.1 EC2 Instance

1. Launch an EC2 instance using Amazon Linux 2023.
2. Restrict SSH access to a trusted IP address.
3. Allow HTTP on port 80 for the frontend.
4. Allow other application ports only where required.
5. Attach an IAM role with the minimum permissions needed.
6. Install and start Docker.

Verify Docker:

```bash
sudo systemctl enable --now docker
sudo docker --version
sudo docker ps
```

Docker group membership grants highly privileged access to the host, so grant it only when required.

### 3.2 RDS MySQL Database

1. Create an Amazon RDS MySQL database in the same VPC as EC2.
2. Use a dedicated application database.
3. Disable public accessibility.
4. Allow TCP port 3306 only from the EC2 security group.
5. Store database credentials in a protected environment file or a secret-management service.

Connection URL pattern:

```text
jdbc:mysql://<RDS-ENDPOINT>:3306/<DATABASE-NAME>
```

Do not expose port 3306 publicly or commit database credentials to GitHub.

## 4. Application Environment Configuration

The backend uses environment variables for database settings and optional external integrations. Example names (use the exact names expected by the application):

```text
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
SPRING_JPA_HIBERNATE_DDL_AUTO
SERVER_PORT
COHERE_API_KEY
SLACK_WEBHOOK_URL
JAVA_TOOL_OPTIONS
```

On EC2, the environment file is stored at:

```text
/home/ec2-user/app.env
```

Restrict file permissions:

```bash
sudo chmod 600 /home/ec2-user/app.env
sudo chown ec2-user:ec2-user /home/ec2-user/app.env
```

Start the backend with the protected environment file:

```bash
sudo docker run -d \
  --name todo-backend \
  --restart unless-stopped \
  --env-file /home/ec2-user/app.env \
  -p 8081:8081 \
  --network todo-network \
  harshareddy7780/todo-summary-backend:<IMAGE-TAG>
```

Replace `<IMAGE-TAG>` with the image version produced by CI/CD.

## 5. Docker Deployment

The frontend and backend are packaged as Docker images and connected through a Docker network.

Create the network if it does not exist:

```bash
sudo docker network create todo-network
```

Example image naming:

```text
harshareddy7780/todo-summary-frontend:<IMAGE-TAG>
harshareddy7780/todo-summary-backend:<IMAGE-TAG>
```

Use immutable Git commit SHA tags to identify deployed versions. Avoid relying exclusively on `latest`.

Check containers and logs:

```bash
sudo docker ps
sudo docker logs --tail 100 todo-backend
sudo docker logs --tail 100 todo-frontend
```

Confirm the actual frontend container name using `sudo docker ps`.

## 6. Jenkins CI/CD Pipeline

The Jenkins job is configured for the GitHub repository and branch `clean-main`.

Job name:

```text
TodoSummaryAssistant-CI-CD
```

Pipeline workflow:

1. Check out source code.
2. Build and test the application.
3. Build frontend and backend Docker images.
4. Tag images with the Git commit SHA.
5. Push images to Docker Hub using Jenkins credentials.
6. Connect to EC2 using a protected SSH credential.
7. Deploy application containers.
8. Run application health checks.

### CI/CD Security and Rollback

- Store Docker Hub credentials and the EC2 SSH key in Jenkins Credentials.
- Do not hardcode credentials in the Jenkinsfile.
- Keep private keys outside the repository.
- Restrict deployment permissions to the required host.
- Ensure failed health checks fail the deployment.
- Preserve the previous known-good image tag for rollback.

If the deployment removes old containers before checking the new deployment, it is not an automatic zero-downtime rollback. Preserve the previous image tag and document the recovery procedure.

## 7. Prometheus Monitoring

Prometheus collects backend and host metrics. The EC2 configuration uses a 30-second scrape interval and evaluates rules every 30 seconds.

Configured targets:

| Target | Purpose |
|---|---|
| `localhost:9090` | Prometheus self-monitoring |
| `host.docker.internal:8081` | Backend Actuator metrics |
| `172.17.0.1:9100` | Node Exporter host metrics |

EC2 configuration file paths:

```text
/home/ec2-user/prometheus-ec2.yml
/home/ec2-user/alert-rules.yml
```

Prometheus is bound to `127.0.0.1:9090` on the EC2 host. Do not expose port 9090 publicly.

Check Prometheus health:

```bash
sudo docker exec prometheus wget -qO- http://localhost:9090/-/healthy
```

Validate alert rules:

```bash
sudo docker exec prometheus promtool check rules /etc/prometheus/alert-rules.yml
```

Expected output includes:

```text
SUCCESS: 2 rules found
```

## 8. Node Exporter

Node Exporter collects host CPU, memory, filesystem, and system metrics. It runs in a Docker container with resource limits and read-only host filesystem access. The metrics endpoint is intended for internal access only.

Verify metrics:

```bash
sudo docker exec node-exporter wget -qO- http://172.17.0.1:9100/metrics
```

Do not add a public security-group rule for port 9100.

## 9. Grafana Configuration

Grafana visualizes Prometheus metrics. The configured Prometheus data-source URL is:

```text
http://prometheus:9090
```

Grafana and Prometheus communicate over the private Docker network `monitoring-network`.

Create and connect the network if necessary:

```bash
sudo docker network create monitoring-network
sudo docker network connect monitoring-network prometheus
sudo docker network connect monitoring-network grafana
```

If the network already exists or a container is already connected, verify its current state before repeating these commands.

### Dashboard Import

Dashboard JSON file:

```text
monitoring/grafana-dashboard.json
```

To import it:

1. Sign in to Grafana.
2. Open **Dashboards**.
3. Choose **New → Import**.
4. Upload `monitoring/grafana-dashboard.json`.
5. Select the Prometheus data source.
6. Click **Import**.

The dashboard includes panels for backend availability, HTTP request rate, HTTP 5xx error rate, p95 latency, host CPU, memory, and disk usage.

### Secure Access

Grafana is bound to the EC2 host loopback interface. Access it from a trusted Windows computer through an SSH tunnel:

```bash
ssh -i "/path/to/todo-assessment-key.pem" \
  -L 3000:127.0.0.1:3000 \
  ec2-user@<EC2-PUBLIC-IP>
```

Then open:

```text
http://localhost:3000
```

Keep the SSH session open while using Grafana. Replace the example key path and IP with the actual values. Use a strong, private Grafana password.

## 10. Alert Rules

Two Prometheus alert rules are configured.

### BackendTargetDown

- **Condition:** `up{job="todo-backend"} == 0`
- **Duration:** 2 minutes
- **Severity:** Critical
- **Purpose:** Detects a backend target that remains unreachable.

### HighHostCPU

- **Condition:** Host CPU utilization exceeds 85%.
- **Duration:** 5 minutes
- **Severity:** Warning
- **Purpose:** Detects sustained high CPU usage.

These rules define alert conditions. Notification delivery requires a configured receiver and notification policy in Alertmanager or the applicable Grafana alerting setup. Defining Prometheus rules alone does not guarantee email or Slack notifications.

Alerts showing **Inactive** are normal when their conditions are not met.

## 11. Health Checks and Troubleshooting

### Backend health

```bash
curl -s http://localhost:8081/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

### Frontend health

```bash
curl -s http://localhost/health
```

Expected response:

```text
healthy
```

### Container status and resource usage

```bash
sudo docker ps
sudo docker stats --no-stream
```

### Prometheus targets

Open the Prometheus Targets page through a secure SSH tunnel and verify that all configured targets show `UP`.

### Common issues

**Backend unhealthy**
- Check backend logs.
- Verify environment variables and database connectivity.
- Check the RDS security-group rule and database availability.

**Prometheus target down**
- Verify the Actuator endpoint.
- Check the target address and Docker network.
- Inspect Prometheus logs and configuration.

**Grafana cannot connect to Prometheus**
- Confirm both containers share `monitoring-network`.
- Confirm the data-source URL is `http://prometheus:9090`.
- Test the Prometheus health endpoint from inside Grafana.

**Dashboard graphs are empty**
- Verify Prometheus targets are `UP`.
- Check the selected Prometheus data source.
- Confirm the application exposes the relevant metrics.

## 12. Security Checklist

- [ ] RDS is not publicly accessible.
- [ ] RDS port 3306 accepts traffic only from the EC2 security group.
- [ ] SSH is restricted to trusted IP addresses.
- [ ] Grafana and Prometheus are not publicly exposed.
- [ ] Node Exporter is not publicly exposed.
- [ ] Database credentials and API keys are excluded from Git.
- [ ] Environment files have restrictive permissions.
- [ ] Jenkins secrets are stored in Jenkins Credentials.
- [ ] Docker images use identifiable version tags.
- [ ] Application privileges are limited where practical.
- [ ] Unused AWS resources are removed after the assessment.

## 13. Repository Deliverables

Expected project files:

```text
TodoSummaryAssistant/
├── aws/
│   ├── aws-setup.md
│   └── architecture-diagram.png
├── monitoring/
│   ├── prometheus.yml
│   ├── alert-rules.yml
│   └── grafana-dashboard.json
├── Jenkinsfile
├── docker-compose.yml
├── README.md
├── FAILURE_AND_ROLLBACK.md
└── MONITORING_AND_OPERATIONS.md
```

The local Compose configuration is intended for development and local monitoring. The AWS deployment uses EC2 and RDS; MySQL is managed by RDS rather than a MySQL container.

## 14. Final Verification

Before submitting:

1. Confirm the application is accessible through its intended URL.
2. Confirm the backend is healthy and connects to RDS.
3. Confirm Jenkins has a successful build and deployment.
4. Confirm Prometheus targets are `UP`.
5. Confirm both alert rules are loaded.
6. Confirm Grafana displays application and infrastructure metrics.
7. Review rollback and monitoring operations documentation.
8. Check that tracked files contain no credentials.
9. Stop or remove unused resources to control AWS costs.

**Verified during setup:** application deployment, RDS connectivity, Prometheus, Node Exporter, Grafana, dashboard import, and two alert rules. Notification delivery, automated rollback, and remaining repository checks should be validated separately before claiming them complete.
