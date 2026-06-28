package com.olivia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Olivia — a conversational, best-in-class recruiting platform (POC).
 *
 * <p>Builds the full recruiting funnel as a composite of the category leaders studied in the
 * feature matrix: conversational apply &amp; automation (Paradox), personalized career sites
 * (Phenom), talent intelligence / skills graph (Eightfold), AI interviewing (Humanly), video
 * &amp; assessments (HireVue), and engagement / copilot / voice (Sense).
 */
@SpringBootApplication
public class OliviaApplication {

    public static void main(String[] args) {
        SpringApplication.run(OliviaApplication.class, args);
    }
}
