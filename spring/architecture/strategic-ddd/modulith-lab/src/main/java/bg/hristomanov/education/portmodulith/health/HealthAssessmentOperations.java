package bg.hristomanov.education.portmodulith.health;

import bg.hristomanov.education.portmodulith.health.events.HealthAssessed;

/** API for an external caller such as a REST adapter (not a clearance-module dependency). */
public interface HealthAssessmentOperations {

    HealthAssessed assess(String vesselId, boolean infectiousConditionReported);
}
