# Monitoring & OpenTelemetry Collector Setup
variable "environment" { type = string }

output "otel_collector_endpoint" { value = "http://10.0.1.10:4317" }
