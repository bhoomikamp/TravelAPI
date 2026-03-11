package com.travelapi.config;

import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

/**
 * GCP service beans.
 *
 * On Cloud Run, credentials are automatically provided via the attached
 * Service Account (Workload Identity). Locally, set GOOGLE_APPLICATION_CREDENTIALS
 * to a service account key JSON file.
 *
 * IAM roles required by the travelapi Service Account:
 *   - roles/cloudsql.client          (Cloud SQL access)
 *   - roles/storage.objectAdmin      (Cloud Storage bucket read/write)
 *   - roles/bigquery.dataEditor      (BigQuery dataset read/write)
 *   - roles/pubsub.publisher         (Pub/Sub publish)
 *   - roles/pubsub.subscriber        (Pub/Sub subscribe)
 */
@Slf4j
@Configuration
public class GcpConfig {

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    /**
     * GCP Cloud Storage client.
     * Used for uploading/downloading trip attachments.
     */
    @Bean
    public Storage googleCloudStorage() {
        log.info("Initialising GCP Cloud Storage client for project: {}", projectId);
        return StorageOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }

    /**
     * GCP BigQuery client.
     * Used for trip analytics queries against the travelapi_analytics dataset.
     */
    @Bean
    public BigQuery bigQuery() {
        log.info("Initialising GCP BigQuery client for project: {}", projectId);
        return BigQueryOptions.newBuilder()
                .setProjectId(projectId)
                .build()
                .getService();
    }
}
