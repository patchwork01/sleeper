output "public_connection_string" {
  description = "Command to connect to EC2"
  value       = "ssh -i ${module.ssh-key.key_name}.pem ubuntu@${module.ec2.public_ip}"
}
