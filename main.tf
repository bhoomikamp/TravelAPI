################################################################################
# TravelAPI – Terraform Infrastructure
# Provisions all GCP resources required by the application.
#
# Resources:
#   - Cloud SQL (PostgreSQL)
#   - Cloud Run service
#   - Cloud Storage bucket
#   - Cloud Pub/Sub topics & subscriptions
#   - BigQuery dataset + scheduled query
#   - Cloud Scheduler job
#   - IAM Service Account + role bindings
################################################################################

terraform {
  required_version = ">= 1.5"
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 5.0"
    }
  }

  # Store state in GCS (recommended for team/CI use)
  backend "gcs" {
    bucket = "travelapi-tf-state"
    prefix = "terraform/state"
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

# ─── Enable required GCP APIs ──────────────────────────────────────────────────

resource "google_project_service" "apis" {
  for_each = toset([
    "run.googleapis.com",
    "sqladmin.googleapis.com",
    "storage.googleapis.com",
    "pubsub.googleapis.com",
    "bigquery.googleapis.com",
    "cloudscheduler.googleapis.com",
    "bigquerydatatransfer.googleapis.com",
    "iam.googleapis.com",
  ])
  service            = each.value
  disable_on_destroy = false
}
