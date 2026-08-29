# Application VM (Elysium API + Outbox Worker + OTel Collector)
variable "environment" { type = string }
variable "instance_type" { type = string; default = "t3.medium" }
variable "image_digest" { type = string; default = "latest" }

output "application_vm_id" { value = "vm-${var.environment}-app" }
output "private_ip" { value = "10.0.1.10" }
