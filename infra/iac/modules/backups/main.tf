# Automated Logical & Snapshot Backups
variable "environment" { type = string }

output "backup_bucket_arn" { value = "arn:aws:s3:::elysium-backups-${var.environment}" }
