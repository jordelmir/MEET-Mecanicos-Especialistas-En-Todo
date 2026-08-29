# Network & VPC Module for Elysium Vanguard Infrastructure
variable "environment" { type = string }
variable "cidr_block" { type = string; default = "10.0.0.0/16" }

output "vpc_id" { value = "vpc-${var.environment}" }
output "subnet_id" { value = "subnet-${var.environment}-app" }
