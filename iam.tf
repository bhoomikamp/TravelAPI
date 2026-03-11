################################################################################
# IAM – Service Account for TravelAPI Cloud Run service
################################################################################

resource "google_service_account" "travelapi" {
  account_id   = "travelapi"
  display_name = "TravelAPI Service Account"
  description  = "Used by Cloud Run service and Terraform-managed resources"
  depends_on   = [google_project_service.apis]
}

# Cloud SQL: connect to instances
resource "google_project_iam_member" "cloudsql_client" {
  project = var.project_id
  role    = "roles/cloudsql.client"
  member  = "serviceAccount:${google_service_account.travelapi.email}"
}

# Cloud Storage: read and write objects in the attachments bucket
resource "google_storage_bucket_iam_member" "storage_object_admin" {
  bucket = google_storage_bucket.attachments.name
  role   = "roles/storage.objectAdmin"
  member = "serviceAccount:${google_service_account.travelapi.email}"
}

# Pub/Sub: publish to topics
resource "google_project_iam_member" "pubsub_publisher" {
  project = var.project_id
  role    = "roles/pubsub.publisher"
  member  = "serviceAccount:${google_service_account.travelapi.email}"
}

# Pub/Sub: pull from subscriptions
resource "google_project_iam_member" "pubsub_subscriber" {
  project = var.project_id
  role    = "roles/pubsub.subscriber"
  member  = "serviceAccount:${google_service_account.travelapi.email}"
}

# BigQuery: read data from analytics dataset
resource "google_project_iam_member" "bigquery_data_viewer" {
  project = var.project_id
  role    = "roles/bigquery.dataViewer"
  member  = "serviceAccount:${google_service_account.travelapi.email}"
}

# BigQuery: run jobs (required to execute queries)
resource "google_project_iam_member" "bigquery_job_user" {
  project = var.project_id
  role    = "roles/bigquery.jobUser"
  member  = "serviceAccount:${google_service_account.travelapi.email}"
}

# Cloud Run invoker: allow Cloud Scheduler to call the service
resource "google_service_account" "scheduler" {
  account_id   = "travelapi-scheduler"
  display_name = "TravelAPI Cloud Scheduler SA"
}

resource "google_cloud_run_service_iam_member" "scheduler_invoker" {
  service  = google_cloud_run_v2_service.travelapi.name
  location = var.region
  role     = "roles/run.invoker"
  member   = "serviceAccount:${google_service_account.scheduler.email}"
}
