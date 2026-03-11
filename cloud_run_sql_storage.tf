################################################################################
# Cloud SQL – PostgreSQL
################################################################################

resource "google_sql_database_instance" "postgres" {
  name             = "travelapi-db"
  database_version = "POSTGRES_15"
  region           = var.region
  depends_on       = [google_project_service.apis]

  settings {
    tier              = "db-f1-micro"   # Upgrade to db-n1-standard-2 for production
    availability_type = "ZONAL"

    backup_configuration {
      enabled            = true
      start_time         = "02:00"
      binary_log_enabled = false
      backup_retention_settings {
        retained_backups = 7
      }
    }

    ip_configuration {
      ipv4_enabled    = false          # Private IP only — Cloud Run connects via socket
      private_network = "projects/${var.project_id}/global/networks/default"
    }

    database_flags {
      name  = "max_connections"
      value = "100"
    }
  }

  deletion_protection = true
}

resource "google_sql_database" "travelapi" {
  name     = "travelapi_db"
  instance = google_sql_database_instance.postgres.name
}

resource "google_sql_user" "travelapi" {
  name     = "travelapi_user"
  instance = google_sql_database_instance.postgres.name
  password = var.db_password
}

################################################################################
# Cloud Storage – Trip Attachments
################################################################################

resource "google_storage_bucket" "attachments" {
  name          = "${var.project_id}-travelapi-attachments"
  location      = var.region
  force_destroy = false
  depends_on    = [google_project_service.apis]

  uniform_bucket_level_access = true   # Disable ACLs; use IAM only

  versioning {
    enabled = false
  }

  lifecycle_rule {
    action { type = "Delete" }
    condition { age = 365 }
  }

  cors {
    origin          = ["*"]
    method          = ["GET", "HEAD"]
    response_header = ["Content-Type"]
    max_age_seconds = 3600
  }
}

################################################################################
# Cloud Run – TravelAPI Service
################################################################################

resource "google_cloud_run_v2_service" "travelapi" {
  name     = "travelapi"
  location = var.region
  depends_on = [
    google_project_service.apis,
    google_sql_database_instance.postgres
  ]

  template {
    service_account = google_service_account.travelapi.email

    scaling {
      min_instance_count = 1    # Keep at least 1 warm to avoid cold starts on reminders
      max_instance_count = 10
    }

    containers {
      image = var.cloud_run_image

      resources {
        limits = {
          cpu    = "1"
          memory = "512Mi"
        }
        cpu_idle = true
      }

      # Cloud SQL connection
      env {
        name  = "CLOUD_SQL_INSTANCE"
        value = google_sql_database_instance.postgres.connection_name
      }
      env {
        name  = "DB_NAME"
        value = google_sql_database.travelapi.name
      }
      env {
        name  = "DB_USER"
        value = google_sql_user.travelapi.name
      }
      env {
        name = "DB_PASSWORD"
        value_source {
          secret_key_ref {
            secret  = google_secret_manager_secret.db_password.secret_id
            version = "latest"
          }
        }
      }

      # GCP config
      env {
        name  = "GCP_PROJECT_ID"
        value = var.project_id
      }
      env {
        name  = "GCS_BUCKET_NAME"
        value = google_storage_bucket.attachments.name
      }
      env {
        name  = "BQ_DATASET"
        value = google_bigquery_dataset.analytics.dataset_id
      }
    }

    volumes {
      name = "cloudsql"
      cloud_sql_instance {
        instances = [google_sql_database_instance.postgres.connection_name]
      }
    }
  }
}

# Allow unauthenticated access (JWT handles auth at app level)
resource "google_cloud_run_service_iam_member" "public_access" {
  service  = google_cloud_run_v2_service.travelapi.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "allUsers"
}

################################################################################
# Secret Manager – store sensitive values
################################################################################

resource "google_secret_manager_secret" "db_password" {
  secret_id  = "travelapi-db-password"
  depends_on = [google_project_service.apis]
  replication {
    auto {}
  }
}

resource "google_secret_manager_secret_version" "db_password" {
  secret      = google_secret_manager_secret.db_password.id
  secret_data = var.db_password
}
