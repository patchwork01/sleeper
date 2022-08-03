data "external" "current_ip" {
  program = ["bash", "-c", "curl -s 'https://api.ipify.org?format=json'"]
}

module "networking" {
  source          = "../modules/networking"
  namespace       = var.namespace
  external_ssh_ip = data.external.current_ip.result.ip
}

module "ssh-key" {
  source    = "../modules/ssh-key"
  namespace = var.namespace
}

module "ec2" {
  source    = "../modules/ec2"
  namespace = var.namespace
  vpc       = module.networking.vpc
  sg_pub_id = module.networking.sg_pub_id
  key_name  = module.ssh-key.key_name
  user_data = base64encode(templatefile("${path.module}/user_data", {
    var1 = "this is a variable value"
  }))
}

