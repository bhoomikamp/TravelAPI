output "cloud_run_url" {
  description = "TravelAPI Cloud Run service URL"
  value       = google_cloud_run_v2_service.travelapi.uri
}

output "cloud_sql_connection_name" {
  description = "Cloud SQL connection name (for local dev with Cloud SQL Proxy)"
  value       = google_sql_database_instance.postgres.connection_name
}

output "gcs_bucket_name" {
  description = "Cloud Storage bucket name for attachments"
  value       = google_storage_bucket.attachments.name
}

output "service_account_email" {
  description = "TravelAPI Service Account email"
  value       = google_service_account.travelapi.email
}

output "bigquery_dataset" {
  description = "BigQuery analytics dataset ID"
  value       = google_bigquery_dataset.analytics.dataset_id
}
