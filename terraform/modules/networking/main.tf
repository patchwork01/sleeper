data "aws_availability_zones" "available" {}

module "vpc" {
  source                       = "terraform-aws-modules/vpc/aws"
  name                         = "${var.namespace}-vpc"
  cidr                         = "10.150.0.0/16"
  azs                          = data.aws_availability_zones.available.names
  private_subnets              = []
  public_subnets               = ["10.150.1.0/24"]
  create_database_subnet_group = false
  enable_nat_gateway           = false
  single_nat_gateway           = false
}

// SG to allow SSH connections
resource "aws_security_group" "allow_ssh_pub" {
  name        = "${var.namespace}-allow_ssh"
  description = "Allow SSH inbound traffic"
  vpc_id      = module.vpc.vpc_id

  ingress {
    description = "SSH from the internet"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["${var.external_ssh_ip}/32"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.namespace}-allow_ssh_pub"
  }
}
