package com.travelapi.service;

import com.google.cloud.bigquery.*;
import com.travelapi.dto.Dtos.TripAnalyticsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Analytics queries against GCP BigQuery.
 *
 * Data pipeline (see terraform/bigquery.tf):
 *  - A Scheduled Query in BigQuery runs nightly to export trip aggregates
 *    from Cloud SQL into the travelapi_analytics dataset.
 *  - This service runs ad-hoc OLAP queries against that dataset.
 *
 * IAM: travelapi Service Account requires roles/bigquery.dataViewer
 *      and roles/bigquery.jobUser on the project.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final BigQuery bigQuery;

    @Value("${spring.cloud.gcp.project-id}")
    private String projectId;

    @Value("${gcp.bigquery.dataset}")
    private String dataset;

    /**
     * Returns top N destinations by trip count for a given user,
     * along with average trip duration in days.
     */
    public List<TripAnalyticsResponse> getTopDestinations(Long userId, int limit) {
        String query = String.format("""
                SELECT
                    destination,
                    COUNT(*) AS trip_count,
                    AVG(DATE_DIFF(end_date, start_date, DAY)) AS avg_duration_days
                FROM `%s.%s.trips`
                WHERE user_id = @userId
                GROUP BY destination
                ORDER BY trip_count DESC
                LIMIT %d
                """, projectId, dataset, limit);

        QueryJobConfiguration jobConfig = QueryJobConfiguration.newBuilder(query)
                .addNamedParameter("userId", QueryParameterValue.int64(userId))
                .setUseLegacySql(false)
                .build();

        return executeQuery(jobConfig);
    }

    /**
     * Returns monthly trip creation counts for the past 12 months.
     */
    public List<TripAnalyticsResponse> getMonthlyTripStats(Long userId) {
        String query = String.format("""
                SELECT
                    FORMAT_DATE('%%Y-%%m', created_at) AS destination,
                    COUNT(*) AS trip_count,
                    AVG(DATE_DIFF(end_date, start_date, DAY)) AS avg_duration_days
                FROM `%s.%s.trips`
                WHERE user_id = @userId
                  AND created_at >= DATE_SUB(CURRENT_DATE(), INTERVAL 12 MONTH)
                GROUP BY 1
                ORDER BY 1 DESC
                """, projectId, dataset);

        QueryJobConfiguration jobConfig = QueryJobConfiguration.newBuilder(query)
                .addNamedParameter("userId", QueryParameterValue.int64(userId))
                .setUseLegacySql(false)
                .build();

        return executeQuery(jobConfig);
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private List<TripAnalyticsResponse> executeQuery(QueryJobConfiguration jobConfig) {
        List<TripAnalyticsResponse> results = new ArrayList<>();
        try {
            JobId jobId = JobId.of(UUID.randomUUID().toString());
            Job job = bigQuery.create(JobInfo.newBuilder(jobConfig).setJobId(jobId).build());
            job = job.waitFor();

            if (job == null || !job.getStatus().getError() == null) {
                log.error("BigQuery job failed: {}", job != null ? job.getStatus().getError() : "null job");
                return results;
            }

            TableResult tableResult = job.getQueryResults();
            for (FieldValueList row : tableResult.iterateAll()) {
                results.add(TripAnalyticsResponse.builder()
                        .destination(row.get("destination").getStringValue())
                        .tripCount(row.get("trip_count").getLongValue())
                        .avgDurationDays(row.get("avg_duration_days").getDoubleValue())
                        .build());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("BigQuery query interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.error("BigQuery query error: {}", e.getMessage());
        }
        return results;
    }
}
