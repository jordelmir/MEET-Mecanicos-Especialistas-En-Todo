# Firewall & Security Rules: Strict Default-Deny
variable "environment" { type = string }

output "app_security_group_id" { value = "sg-${var.environment}-app" }
output "livekit_security_group_id" { value = "sg-${var.environment}-livekit" }
