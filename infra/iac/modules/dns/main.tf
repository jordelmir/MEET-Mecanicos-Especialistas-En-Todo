# DNS Routing & Cloudflare Edge Proxies
variable "domain" { type = string; default = "elysium369.com" }
variable "environment" { type = string }

output "api_endpoint" { value = "api.${var.domain}" }
output "realtime_endpoint" { value = "realtime.${var.domain}" }
output "rtc_endpoint" { value = "rtc.${var.domain}" }
