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

variable "repository" {
  description = "Name of GitHub repository to check out"
  default     = "sleeper"
  type        = string
}

variable "branch" {
  description = "Branch in GitHub repository to check out"
  default     = "main"
  type        = string
}

variable "fork" {
  description = "GitHub fork to check out"
  default     = "gchq"
  type        = string
}
