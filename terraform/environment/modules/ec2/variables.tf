variable "namespace" {
  type = string
}

variable "vpc" {
  type = any
}

variable "key_name" {
  type = string
}

variable "sg_pub_id" {
  type = any
}

variable "repository" {
  type = string
}

variable "branch" {
  type = string
}

variable "fork" {
  type = string
}
