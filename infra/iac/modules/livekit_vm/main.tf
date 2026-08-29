# LiveKit Media Plane VM (WebRTC SFU + TURN + Redis)
variable "environment" { type = string }
variable "instance_type" { type = string; default = "c5.large" }

output "livekit_vm_id" { value = "vm-${var.environment}-livekit" }
output "public_ip" { value = "198.51.100.20" }
