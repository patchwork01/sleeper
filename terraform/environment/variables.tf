variable "namespace" {
  description = "The project namespace to use for unique resource naming"
  default     = "Sleeper-Environment"
  type        = string
}

variable "region" {
  description = "AWS region"
  default     = "eu-west-2"
  type        = string
}
