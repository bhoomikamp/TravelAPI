################################################################################
# Cloud Pub/Sub – Topics & Subscriptions
################################################################################

resource "google_pubsub_topic" "reminder_created" {
  name       = "reminder-created"
  depends_on = [google_project_service.apis]

  message_retention_duration = "86400s"  # 24h
}

resource "google_pubsub_topic" "trip_shared" {
  name       = "trip-shared"
  depends_on = [google_project_service.apis]

  message_retention_duration = "86400s"
}

# Subscription: processes reminder-created events (reminder notifications)
resource "google_pubsub_subscription" "reminder_processor" {
  name  = "reminder-processor-sub"
  topic = google_pubsub_topic.reminder_created.name

  ack_deadline_seconds       = 30
  message_retention_duration = "86400s"
  retain_acked_messages      = false

  expiration_policy {
    ttl = ""   # Never expire
  }

  retry_policy {
    minimum_backoff = "10s"
    maximum_backoff = "600s"
  }
}

# Subscription: processes trip-shared events
resource "google_pubsub_subscription" "trip_shared_processor" {
  name  = "trip-shared-processor-sub"
  topic = google_pubsub_topic.trip_shared.name

  ack_deadline_seconds       = 30
  message_retention_duration = "86400s"
  retain_acked_messages      = false

  expiration_policy {
    ttl = ""
  }
}

################################################################################
# BigQuery – Analytics Dataset
################################################################################

resource "google_bigquery_dataset" "analytics" {
  dataset_id                  = "travelapi_analytics"
  friendly_name               = "TravelAPI Analytics"
  description                 = "Trip analytics data — populated nightly by a Scheduled Query from Cloud SQL"
  location                    = var.region
  default_table_expiration_ms = null
  depends_on                  = [google_project_service.apis]
}

# trips table in BigQuery (mirrors Cloud SQL schema for analytics)
resource "google_bigquery_table" "trips" {
  dataset_id = google_bigquery_dataset.analytics.dataset_id
  table_id   = "trips"

  schema = jsonencode([
    { name = "id",          type = "INT64",   mode = "REQUIRED" },
    { name = "user_id",     type = "INT64",   mode = "REQUIRED" },
    { name = "title",       type = "STRING",  mode = "REQUIRED" },
    { name = "destination", type = "STRING",  mode = "REQUIRED" },
    { name = "start_date",  type = "DATE",    mode = "REQUIRED" },
    { name = "end_date",    type = "DATE",    mode = "REQUIRED" },
    { name = "status",      type = "STRING",  mode = "NULLABLE" },
    { name = "created_at",  type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "updated_at",  type = "TIMESTAMP", mode = "NULLABLE" },
  ])

  deletion_protection = false
}

################################################################################
# Cloud Scheduler – Trigger reminder processing every minute
################################################################################

resource "google_cloud_scheduler_job" "reminder_processor" {
  name        = "travelapi-reminder-processor"
  description = "Triggers TravelAPI to process and publish due reminders"
  schedule    = "* * * * *"   # Every minute
  time_zone   = "Asia/Kolkata"
  region      = var.region
  depends_on  = [google_project_service.apis]

  http_target {
    uri         = "${google_cloud_run_v2_service.travelapi.uri}/api/internal/reminders/process"
    http_method = "POST"

    oidc_token {
      service_account_email = google_service_account.scheduler.email
      audience              = google_cloud_run_v2_service.travelapi.uri
    }
  }

  retry_config {
    retry_count = 3
  }
}
