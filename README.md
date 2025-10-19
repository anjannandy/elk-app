# ELK Stack Test Application

A simple Spring Boot application designed to test log ingestion, metrics collection, and monitoring with the ELK (Elasticsearch, Logstash, Kibana) stack running on Kubernetes.

## Overview

This application provides:
- **REST API endpoints** for generating various types of logs
- **JSON-formatted logs** optimized for Logstash ingestion
- **Prometheus metrics** via Spring Boot Actuator
- **Health checks** for Kubernetes probes
- **Multiple log levels** (TRACE, DEBUG, INFO, WARN, ERROR)

## Architecture

```
Spring Boot App (JSON Logs)
    ↓
Kubernetes Pods (stdout/stderr)
    ↓
Logstash (port 5044)
    ↓
Elasticsearch
    ↓
Kibana (Visualization)
```

## Prerequisites

- Java 17 or higher
- Maven 3.6+
- Docker
- Kubernetes cluster with kubectl configured
- ELK stack deployed (see `../04-elk-cluster-k8s`)
- DNS entry for `elk-test.homelab.com` (or use port-forward)

## Quick Start

### 1. Build and Deploy

```bash
# Deploy the application
./deploy.sh
```

This script will:
1. Build the Spring Boot application with Maven
2. Create a Docker image
3. Deploy to Kubernetes namespace `elk-test-app`
4. Wait for pods to be ready

### 2. Verify Deployment

```bash
# Check pod status
kubectl get pods -n elk-test-app

# View application logs
kubectl logs -n elk-test-app -l app=elk-test-app -f
```

### 3. Access the Application

**Via Ingress** (requires DNS setup):
```bash
curl https://elk-test.homelab.com/api/health
```

**Via Port Forward**:
```bash
kubectl port-forward -n elk-test-app svc/elk-test-app 8080:8080

# In another terminal
curl http://localhost:8080/api/health
```

## API Endpoints

### Health & Monitoring

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/actuator/health` | GET | Application health status |
| `/actuator/metrics` | GET | Available metrics |
| `/actuator/prometheus` | GET | Prometheus-formatted metrics |
| `/api/health` | GET | Custom health endpoint with request count |

### Test Endpoints

| Endpoint | Method | Parameters | Description |
|----------|--------|------------|-------------|
| `/api/hello` | GET | `name` (optional) | Simple greeting with INFO log |
| `/api/process` | POST | JSON body | Simulates data processing with variable delay |
| `/api/simulate-error` | GET | `throwException` (boolean) | Generates WARN or ERROR logs |
| `/api/generate-logs` | GET | `count` (default: 10) | Generates multiple logs of various levels |

## Step-by-Step Testing Guide

### Step 1: Configure Kibana for Log Viewing

1. **Access Kibana**:
   ```bash
   # Get Kibana credentials
   kubectl get secret elasticsearch-credentials -n elk-stack -o jsonpath='{.data.elastic-password}' | base64 -d && echo
   ```

2. **Login to Kibana**: `https://kibana.homelab.com`
   - Username: `elastic`
   - Password: (from above command)

3. **Create Index Pattern**:
   - Navigate to: **Management → Stack Management → Index Patterns**
   - Click **Create index pattern**
   - Index pattern name: `logstash-*`
   - Time field: `@timestamp`
   - Click **Create index pattern**

### Step 2: Generate Test Logs

#### A. Simple Hello Request
```bash
curl https://elk-test.homelab.com/api/hello?name=TestUser
```

**Expected logs**:
- INFO level log with "Received hello request from: TestUser"

#### B. Generate Multiple Logs
```bash
# Generate 50 logs of various levels
curl https://elk-test.homelab.com/api/generate-logs?count=50
```

**Expected logs**:
- Mix of TRACE, DEBUG, INFO, and WARN logs
- Log entry for each generated log with sequence numbers

#### C. Simulate Processing
```bash
curl -X POST https://elk-test.homelab.com/api/process \
  -H "Content-Type: application/json" \
  -d '{"user": "test", "action": "process_data", "items": 100}'
```

**Expected logs**:
- INFO log with request details
- DEBUG log with payload
- INFO log with processing time

#### D. Generate Error Logs
```bash
# Generate WARNING log
curl https://elk-test.homelab.com/api/simulate-error

# Generate ERROR log with exception
curl https://elk-test.homelab.com/api/simulate-error?throwException=true
```

**Expected logs**:
- WARN level logs for first request
- ERROR level logs with stack trace for second request

### Step 3: View Logs in Kibana

1. **Open Discover**:
   - Navigate to: **Analytics → Discover**
   - Select time range: **Last 15 minutes**

2. **Filter by Application**:
   - Add filter: `app : "elk-test-app"`
   - Or search: `app:"elk-test-app"`

3. **View Log Details**:
   - Click on any log entry to expand
   - Observe JSON fields:
     - `@timestamp`: Log timestamp
     - `message`: Log message
     - `level`: Log level (INFO, WARN, ERROR, etc.)
     - `logger_name`: Java class name
     - `app`: Application name
     - `environment`: kubernetes

4. **Filter by Log Level**:
   ```
   app:"elk-test-app" AND level:"ERROR"
   app:"elk-test-app" AND level:"WARN"
   app:"elk-test-app" AND level:"INFO"
   ```

5. **Search for Specific Messages**:
   ```
   app:"elk-test-app" AND message:"hello request"
   app:"elk-test-app" AND message:"Processing completed"
   app:"elk-test-app" AND message:"error"
   ```

### Step 4: Create Visualizations

#### A. Log Level Distribution (Pie Chart)

1. Navigate to: **Analytics → Visualize Library**
2. Click **Create visualization**
3. Select **Pie**
4. Configure:
   - Index pattern: `logstash-*`
   - Metric: Count
   - Buckets → Split slices:
     - Aggregation: Terms
     - Field: `level.keyword`
     - Size: 10
5. Click **Update**
6. Save: "ELK Test App - Log Levels"

#### B. Logs Over Time (Line Chart)

1. Create visualization → Select **Line**
2. Configure:
   - Index pattern: `logstash-*`
   - Metric: Count
   - X-axis → Date Histogram:
     - Field: `@timestamp`
     - Interval: Auto
   - Add filter: `app:"elk-test-app"`
3. Click **Update**
4. Save: "ELK Test App - Logs Over Time"

#### C. Error Log Timeline

1. Create visualization → Select **Area**
2. Configure:
   - Index pattern: `logstash-*`
   - Metric: Count
   - X-axis → Date Histogram:
     - Field: `@timestamp`
     - Interval: Auto
   - Add filter: `app:"elk-test-app" AND (level:"ERROR" OR level:"WARN")`
3. Click **Update**
4. Save: "ELK Test App - Errors and Warnings"

### Step 5: Create Dashboard

1. Navigate to: **Analytics → Dashboard**
2. Click **Create dashboard**
3. Click **Add from library**
4. Select your visualizations:
   - Log Levels (Pie Chart)
   - Logs Over Time (Line Chart)
   - Errors and Warnings (Area Chart)
5. Arrange the panels
6. Save: "ELK Test Application Dashboard"

### Step 6: Verify Metrics (Prometheus)

```bash
# View Prometheus metrics
curl https://elk-test.homelab.com/actuator/prometheus

# Look for metrics like:
# - http_server_requests_seconds_count
# - jvm_memory_used_bytes
# - process_cpu_usage
```

### Step 7: Load Testing (Optional)

Use the provided test script to generate continuous load:

```bash
./test-logs.sh
```

This will:
- Make requests to all endpoints
- Generate logs of various levels
- Create realistic traffic patterns
- Show real-time log ingestion in Kibana

## Monitoring Application Logs

### View Logs in Kubernetes

```bash
# Follow logs from all pods
kubectl logs -n elk-test-app -l app=elk-test-app -f

# View logs from specific pod
kubectl logs -n elk-test-app elk-test-app-xxxxxxxxx-xxxxx -f

# View previous pod logs (if crashed)
kubectl logs -n elk-test-app elk-test-app-xxxxxxxxx-xxxxx --previous
```

### Check Elasticsearch Indices

```bash
# Get elastic password
ELASTIC_PW=$(kubectl get secret elasticsearch-credentials -n elk-stack -o jsonpath='{.data.elastic-password}' | base64 -d)

# List all indices
kubectl exec -n elk-stack elasticsearch-0 -- \
  curl -sk -u "elastic:$ELASTIC_PW" \
  https://localhost:9200/_cat/indices?v \
  --cacert /usr/share/elasticsearch/config/certs/ca/ca-bundle.pem

# Check logstash indices
kubectl exec -n elk-stack elasticsearch-0 -- \
  curl -sk -u "elastic:$ELASTIC_PW" \
  https://localhost:9200/_cat/indices/logstash-*?v \
  --cacert /usr/share/elasticsearch/config/certs/ca/ca-bundle.pem

# Search for app logs
kubectl exec -n elk-stack elasticsearch-0 -- \
  curl -sk -u "elastic:$ELASTIC_PW" \
  https://localhost:9200/logstash-*/_search?q=app:elk-test-app \
  --cacert /usr/share/elasticsearch/config/certs/ca/ca-bundle.pem | jq .
```

## Troubleshooting

### Pods Not Starting

```bash
# Check pod status
kubectl get pods -n elk-test-app

# Describe pod for events
kubectl describe pod -n elk-test-app <pod-name>

# Check logs
kubectl logs -n elk-test-app <pod-name>
```

### Logs Not Appearing in Kibana

1. **Check Logstash is receiving logs**:
   ```bash
   kubectl logs -n elk-stack -l app=logstash -f
   ```

2. **Verify Elasticsearch has indices**:
   ```bash
   ELASTIC_PW=$(kubectl get secret elasticsearch-credentials -n elk-stack -o jsonpath='{.data.elastic-password}' | base64 -d)
   kubectl exec -n elk-stack elasticsearch-0 -- \
     curl -sk -u "elastic:$ELASTIC_PW" \
     https://localhost:9200/_cat/indices/logstash-*?v \
     --cacert /usr/share/elasticsearch/config/certs/ca/ca-bundle.pem
   ```

3. **Check index pattern in Kibana**:
   - Navigate to **Stack Management → Index Patterns**
   - Verify `logstash-*` pattern exists
   - Refresh field list if needed

4. **Check time range in Kibana**:
   - Set time range to "Last 1 hour" or "Today"

### Application Not Accessible

```bash
# Check service
kubectl get svc -n elk-test-app

# Check ingress
kubectl get ingressroute -n elk-test-app

# Use port-forward as workaround
kubectl port-forward -n elk-test-app svc/elk-test-app 8080:8080
```

## Clean Up

```bash
# Delete the application
kubectl delete namespace elk-test-app

# Remove Docker image
docker rmi elk-test-app:1.0.0
```

## Application Configuration

### Logging Configuration

Logs are formatted as JSON using Logstash Logback Encoder. Configuration in `src/main/resources/logback-spring.xml`:

- **Console output**: JSON format for Kubernetes log collection
- **Custom fields**: `app`, `environment` tags
- **MDC support**: Trace and span IDs (for distributed tracing)

### Resource Limits

Default resources (defined in `k8s/02-deployment.yaml`):
- **Memory**: 256Mi request, 512Mi limit
- **CPU**: 250m request, 500m limit
- **Replicas**: 2

Adjust based on your cluster capacity.

## Next Steps

1. **Add Filebeat** for file-based log collection
2. **Configure APM** for distributed tracing
3. **Add Metricbeat** for system metrics
4. **Create alerts** in Kibana for error conditions
5. **Implement log retention policies** in Elasticsearch

## References

- [Spring Boot Logging](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.logging)
- [Logstash Logback Encoder](https://github.com/logfellow/logstash-logback-encoder)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Kibana Documentation](https://www.elastic.co/guide/en/kibana/current/index.html)
