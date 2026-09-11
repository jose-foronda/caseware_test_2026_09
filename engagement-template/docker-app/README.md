# Docker Setup for Offer Application

This folder contains a complete, independent Docker setup for the Spring Boot Offer application.

## Prerequisites
- Docker Desktop installed and running
- Docker Compose installed

## Files
- `Dockerfile` - Multi-stage build for the Spring Boot application
- `docker-compose.yml` - Orchestrates PostgreSQL and the Spring Boot app
- `init.sql` - Database initialization script
- `logs/` - Application logs will be written here (created automatically)

## Quick Start

### Start the application
```bash
cd docker-app
docker-compose up --build
```

### Start in detached mode (background)
```bash
docker-compose up -d --build
```

### Stop the application
```bash
docker-compose down
```

### Stop and remove volumes (clean slate)
```bash
docker-compose down -v
```

## Access

- **Application**: http://localhost:8080
- **PostgreSQL**: localhost:5432
  - Database: `offer_db`
  - Username: `local`
  - Password: `local`

## View Logs

### Option 1: Docker logs (real-time)
```bash
docker logs -f offer-app
```

### Option 2: Local file
Logs are automatically written to `./logs/application.log` in this folder.

### Option 3: All services
```bash
docker-compose logs -f
```

### Option 4: Specific service
```bash
docker-compose logs -f offer-app
docker-compose logs -f postgres
```

## Troubleshooting

### Rebuild from scratch
```bash
docker-compose down -v
docker-compose build --no-cache
docker-compose up
```

### Check service status
```bash
docker-compose ps
```

### Access container shell
```bash
docker exec -it offer-app sh
docker exec -it offer-postgres sh
```
