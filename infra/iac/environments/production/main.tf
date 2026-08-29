# Production Environment Definition
terraform {
  required_version = ">= 1.8.0"
  required_providers {
    cloudflare = { source = "cloudflare/cloudflare", version = "~> 4.0" }
  }
}

module "network" {
  source = "../../modules/network"
  environment = "production"
}

module "firewall" {
  source = "../../modules/firewall"
  environment = "production"
}

module "application_vm" {
  source = "../../modules/application_vm"
  environment = "production"
  instance_type = "c6i.xlarge"
}

module "livekit_vm" {
  source = "../../modules/livekit_vm"
  environment = "production"
  instance_type = "c6i.xlarge"
}

module "dns" {
  source = "../../modules/dns"
  environment = "production"
}

module "monitoring" {
  source = "../../modules/monitoring"
  environment = "production"
}

module "backups" {
  source = "../../modules/backups"
  environment = "production"
}
