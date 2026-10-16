# AWS Deployment and Setup Guide

## 1. Architecture

The production deployment uses the following components:

- **Amazon EC2:** Hosts the Dockerized React frontend, Spring Boot backend, Prometheus, Grafana, and Node Exporter.
- **Amazon RDS for MySQL:** Stores application data in a managed database. MySQL is not run as a production container.
- **Amazon VPC:** Provides network isolation for the application and database.
- **Security Groups:** Restrict access to the application, monitoring services, and database.
- **IAM Instance Role:** Grants the EC2 instance only the AWS permissions it needs.
- **AWS Systems Manager (recommended):** Supports secure instance administration without exposing SSH publicly.
- **Jenkins and Docker Hub:** Build, tag, and publish container images, then initiate deployment.

This document describes the intended configuration. Complete the setup and record actual resource identifiers before treating the deployment as operational.

## 2. Create or select an AWS Region and VPC

1. Select an AWS Region close to the expected users.
2. Use an existing suitable VPC or create a dedicated VPC.
3. Create public subnets for internet-facing resources and private subnets for the database.
4. Configure routing, an internet gateway, and NAT access if required.
5. Use multiple Availability Zones for the RDS subnet group where practical.

Keep the database in private subnets. Do not assign it a public IP or enable public accessibility.

## 3. Create the RDS MySQL database

1. Open Amazon RDS in the AWS Console.
2. Create an Amazon RDS for MySQL database.
3. Select the VPC and a DB subnet group containing private subnets.
4. Disable public accessibility.
5. Configure a strong database username and password.
6. Store credentials in AWS Secrets Manager or another approved secret store.
7. Enable encryption at rest and automated backups.
8. Configure the database name and port. MySQL normally uses port 3306.

Record the endpoint privately for configuration. Do not commit database passwords or sensitive connection details to Git.

## 4. Configure security groups

### EC2 security group

Allow only the traffic required by the deployment:

- HTTP (80): allow public access if the frontend is intended to be public.
- HTTPS (443): allow public access when TLS is configured.
- SSH (22): restrict to the administrator's trusted IP address, or avoid public SSH and use Systems Manager.
- Backend port (8081): do not expose publicly when the backend is accessed through a reverse proxy or internal network.
- Grafana (3000) and Prometheus (9090): restrict to an administrator IP, VPN, or private network. Do not expose these ports publicly without appropriate access controls.

### RDS security group

- Allow inbound TCP port 3306 only from the EC2 application security group.
- Do not allow MySQL traffic from `0.0.0.0/0`.
- Do not expose the database directly to the public internet.

Verify connectivity from EC2 to RDS before deploying the application.

## 5. Configure the EC2 instance

1. Launch a supported Linux EC2 instance in the selected VPC.
2. Attach the EC2 security group.
3. Attach an IAM instance role with least-privilege permissions.
4. Install Docker and the Docker Compose plugin.
5. Configure access to Docker Hub images.
6. Create a deployment directory, such as `/opt/todo-summary-assistant`.
7. Configure application environment variables using a protected environment file or secret-management mechanism.
8. Restrict file permissions on deployment configuration and secret files.

Do not place AWS access keys or application passwords in Dockerfiles, source code, or Git.

## 6. Configure application environment variables

Configure the backend with environment variables appropriate to the deployment:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `COHERE_API_KEY`
- `SLACK_WEBHOOK_URL`

The database URL should reference the RDS endpoint, for example:

`jdbc:mysql://<RDS-ENDPOINT>:3306/todo_db`

Replace the example endpoint and database name with the actual values. Keep all credentials outside the repository.

Configure the frontend API URL for the real public application endpoint. Do not use `localhost` as the API address for users accessing the deployed frontend.

## 7. Configure CI/CD deployment

Configure Jenkins to:

1. Check out the approved Git repository.
2. Run backend tests and build the frontend.
3. Build Docker images tagged with the Git commit SHA.
4. Push images to Docker Hub using Jenkins credentials.
5. Deploy the new image versions to EC2 using SSH restricted to a trusted network or AWS Systems Manager.
6. Check the backend health endpoint after deployment.
7. Fail the pipeline if the deployment or health check fails.
8. Retain the previous image tags for rollback.

Configure a GitHub webhook or the CI system's supported automatic trigger. Keep Docker Hub tokens in Jenkins Credentials, never in the Jenkinsfile.

## 8. Verification checklist

Before marking the AWS deployment complete, verify:

- [ ] EC2 instance is running and reachable through the intended access method.
- [ ] Backend and frontend containers are healthy.
- [ ] RDS is private and accepts connections only from the EC2 security group.
- [ ] Application CRUD operations work against RDS.
- [ ] Backend health endpoint returns HTTP 200.
- [ ] Prometheus successfully scrapes backend and host metrics.
- [ ] Grafana dashboards display live metrics.
- [ ] At least two monitoring alerts are configured and tested.
- [ ] CI/CD builds, publishes, deploys, and checks application health.
- [ ] No credentials are committed to Git.

## 9. Cost and cleanup

Use appropriately sized resources for the assessment. Review AWS pricing before creation.

When the assessment and review are complete, stop or delete resources that are no longer required. Confirm whether database snapshots or backups must be retained before deleting RDS resources. Removing an EC2 instance alone may not remove separately billed EBS volumes, snapshots, load balancers, NAT gateways, or RDS databases.
