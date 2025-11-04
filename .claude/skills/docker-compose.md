# Docker Compose Skill

## Overview
You are an expert in managing Docker Compose multi-container applications. This skill provides best practices for working safely with Docker Compose environments, especially in systems with multiple independent Docker containers.

## Critical Rules

### 1. Always Use Docker Compose Commands
When working within a Docker Compose project directory:

✅ **CORRECT**:
```bash
docker compose ps              # List services in THIS compose project
docker compose logs service    # View logs for services in THIS project
docker compose restart service # Restart services in THIS project
docker compose down           # Stop THIS project's containers
docker compose up -d          # Start THIS project's containers
```

❌ **INCORRECT**:
```bash
docker ps                     # Lists ALL containers on the system (not just this project)
docker logs container-name    # Could affect containers from other projects
docker restart container-name # Might restart containers from other projects
```

### 2. Never Touch Other Containers
**CRITICAL**: On shared systems, multiple Docker Compose projects may be running simultaneously. Your operations should ONLY affect containers from your current project.

**Why This Matters**:
- Other users' containers may have the same name patterns
- System services may be running in Docker
- Multiple instances of similar services (e.g., multiple Kafka or Keycloak instances)

**Example of Safe Behavior**:
```bash
# You're in /project/testing with docker-compose.yml
cd /project/testing

# ✅ This only affects containers defined in testing/docker-compose.yml
docker compose ps
docker compose restart kafka

# ❌ This could affect ANY container named "kafka" on the system
docker ps | grep kafka
docker restart kafka
```

### 3. Proper Restart Workflow

When restarting services with configuration changes:

```bash
# ✅ CORRECT: Full cycle
docker compose down    # Stop and remove containers
docker compose up -d   # Create and start new containers

# ⚠️ ACCEPTABLE: For quick restarts without config changes
docker compose restart service-name

# ❌ AVOID: Just restart without down
docker compose restart  # May not pick up config changes
```

## Docker Compose Command Reference

### Project Management
```bash
# Start all services
docker compose up -d

# Stop services (keeps containers)
docker compose stop

# Stop and remove containers (keeps volumes and networks)
docker compose down

# Stop, remove containers, volumes, and networks
docker compose down -v

# Rebuild and start
docker compose up -d --build
```

### Service Operations
```bash
# Start specific service
docker compose up -d service-name

# Stop specific service
docker compose stop service-name

# Restart specific service
docker compose restart service-name

# View service status
docker compose ps

# View service logs
docker compose logs service-name
docker compose logs service-name --tail 50
docker compose logs service-name --follow
```

### Inspection
```bash
# List services and their status
docker compose ps

# Show service configuration
docker compose config

# View service logs
docker compose logs

# Execute command in running service
docker compose exec service-name command

# Run one-off command (starts temporary container)
docker compose run service-name command
```

## Working with Multiple Projects

### Project Isolation
Docker Compose uses **project names** to isolate containers. By default:
- Project name = directory name
- Containers named: `{project}_{service}_{replica}`

```bash
# Example: In directory /keycloak-kafka-sync-agent/testing
# Service "kafka" becomes container: "testing_kafka_1" or "testing-kafka-1"

# Override project name
docker compose -p my-project ps
```

### Checking What You're Affecting
```bash
# Before any operation, verify what containers are in THIS project
docker compose ps

# Example output:
# NAME       IMAGE          STATUS
# kms        cosmian/kms    Up 2 minutes
# kafka      apache/kafka   Up 2 minutes
# keycloak   keycloak:26    Up 2 minutes

# If you see unexpected containers, STOP - you might be in wrong directory
```

## Health Checks and Dependencies

### Health Check Patterns

**Without Health Check** (simpler):
```yaml
services:
  kms:
    image: ghcr.io/cosmian/kms:latest
    # No healthcheck needed

  service:
    depends_on:
      - kms  # Simple startup order
```

**With Health Check** (when needed):
```yaml
services:
  database:
    healthcheck:
      test: ["CMD", "pg_isready"]
      interval: 10s
      timeout: 5s
      retries: 5

  app:
    depends_on:
      database:
        condition: service_healthy  # Wait for health
```

### External Health Verification
Sometimes it's better to check health externally (from Makefile or scripts):

```bash
# In Makefile
certs:
	@curl -sf $(KMS_URL)/version >/dev/null || (echo "KMS not running"; exit 1)
	# Generate certificates...
```

## Configuration Changes

### When to Use `down` vs `restart`

**Use `docker compose down && docker compose up -d`** when:
- ✅ docker-compose.yml changed
- ✅ Environment variables changed
- ✅ Volume mounts changed
- ✅ Network configuration changed
- ✅ Port mappings changed

**Use `docker compose restart`** when:
- ✅ Quick restart needed
- ✅ No configuration changes
- ✅ Service had temporary issue

**Example**:
```bash
# Changed database from SQLite to dev-file in docker-compose.yml
vim docker-compose.yml
# ✅ MUST use down/up cycle
docker compose down
docker compose up -d

# Service just needs a restart (no config change)
# ✅ Can use restart
docker compose restart keycloak
```

## Troubleshooting

### Check Current Project
```bash
# Where am I?
pwd

# What's my docker-compose.yml?
cat docker-compose.yml

# What containers are in THIS project?
docker compose ps

# What's running on the whole system? (be careful!)
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### Common Issues

| Issue | Diagnosis | Solution |
|-------|-----------|----------|
| "Container not found" | Wrong directory or project | `cd` to correct directory, verify with `docker compose ps` |
| Changes not applied | Used `restart` instead of `down/up` | `docker compose down && docker compose up -d` |
| Affecting wrong containers | Used `docker` instead of `docker compose` | Always use `docker compose` commands |
| Port already in use | Another service using the port | Check with `docker ps -a`, use different ports |

### Debugging Steps
```bash
# 1. Verify location
pwd
ls docker-compose.yml

# 2. Check current project containers
docker compose ps

# 3. View logs
docker compose logs service-name --tail 50

# 4. Check configuration
docker compose config

# 5. Full restart
docker compose down
docker compose up -d

# 6. Monitor startup
docker compose logs --follow
```

## Best Practices Summary

### ✅ DO
- Always use `docker compose` commands when in a project directory
- Use `down` then `up` when configuration changes
- Check `docker compose ps` before operations
- Use service names (e.g., `kafka`), not container names
- Keep project-specific operations isolated

### ❌ DON'T
- Don't use `docker ps` to find containers in your project
- Don't use `docker restart` on individual containers
- Don't assume container names are unique on the system
- Don't touch containers outside your project
- Don't forget to check your working directory

## Integration with Makefiles

When creating Makefiles for Docker Compose projects:

```makefile
.PHONY: start stop restart logs ps

# Start services
start:
	docker compose up -d

# Stop services
stop:
	docker compose stop

# Full restart (for config changes)
restart:
	docker compose down
	docker compose up -d

# View logs
logs:
	docker compose logs --follow

# Show status
ps:
	docker compose ps

# Health check (external verification)
health:
	@curl -sf http://localhost:57001/version && echo "✓ KMS OK" || echo "✗ KMS FAILED"
	@curl -sf http://localhost:57002/ && echo "✓ Keycloak OK" || echo "✗ Keycloak FAILED"
```

## Quick Reference Card

```bash
# Essential Commands
docker compose up -d           # Start all services
docker compose down            # Stop and remove containers
docker compose ps              # List services in this project
docker compose logs service    # View logs
docker compose restart service # Restart service

# Configuration Changes
docker compose down && docker compose up -d

# Checking Before Operations
docker compose ps              # What's in MY project?
pwd                           # Am I in the right directory?

# Debugging
docker compose logs service --tail 50 --follow
docker compose exec service command
docker compose config

# NEVER for project work
docker ps                     # Shows ALL containers (dangerous!)
docker restart container      # Affects specific container (bypass compose)
```

## Remember
**When in doubt**: Use `docker compose` commands, not `docker` commands. Your operations should only affect containers defined in the current directory's `docker-compose.yml` file.
