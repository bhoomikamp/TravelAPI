package com.travelapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * TravelAPI - Spring Boot Microservices backend deployed on GCP Cloud Run.
 *
 * Architecture:
 *  - REST API layer  : Spring Boot + Spring MVC controllers
 *  - Service layer   : Business logic + GCP integrations
 *  - Data layer      : JPA/Hibernate + PostgreSQL (Cloud SQL)
 *  - Messaging       : GCP Cloud Pub/Sub for async inter-service events
 *  - Storage         : GCP Cloud Storage for media/attachments
 *  - Analytics       : GCP BigQuery for trip analytics queries
 *  - Scheduling      : GCP Cloud Scheduler + Spring @Scheduled for reminders
 *  - IaC             : Terraform (see /terraform)
 *  - CI/CD           : GitHub Actions (see /.github/workflows)
 */
@SpringBootApplication
@EnableScheduling
public class TravelApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TravelApiApplication.class, args);
    }
}
