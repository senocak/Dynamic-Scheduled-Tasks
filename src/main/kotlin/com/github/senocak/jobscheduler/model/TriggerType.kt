package com.github.senocak.jobscheduler.model

enum class TriggerType(val displayName: String, val cronExpression: String, val description: String) {
    EVERY_MINUTE(displayName = "Every Minute", cronExpression = "0 * * * * *", description = "Runs every minute"),
    EVERY_5_MINUTES(displayName = "Every 5 Minutes", cronExpression = "0 */5 * * * *", description = "Runs every 5 minutes"),
    EVERY_10_MINUTES(displayName = "Every 10 Minutes", cronExpression = "0 */10 * * * *", description = "Runs every 10 minutes"),
    EVERY_15_MINUTES(displayName = "Every 15 Minutes", cronExpression = "0 */15 * * * *", description = "Runs every 15 minutes"),
    EVERY_30_MINUTES(displayName = "Every 30 Minutes", cronExpression = "0 */30 * * * *", description = "Runs every 30 minutes"),
    EVERY_HOUR(displayName = "Every Hour", cronExpression = "0 0 * * * *", description = "Runs every hour"),
    EVERY_2_HOURS(displayName = "Every 2 Hours", cronExpression = "0 0 */2 * * *", description = "Runs every 2 hours"),
    EVERY_6_HOURS(displayName = "Every 6 Hours", cronExpression = "0 0 */6 * * *", description = "Runs every 6 hours"),
    EVERY_12_HOURS(displayName = "Every 12 Hours", cronExpression = "0 0 */12 * * *", description = "Runs every 12 hours"),
    DAILY_AT_MIDNIGHT(displayName = "Daily at Midnight", cronExpression = "0 0 0 * * *", description = "Runs daily at 12:00 AM"),
    DAILY_AT_9AM(displayName = "Daily at 9 AM", cronExpression = "0 0 9 * * *", description = "Runs daily at 9:00 AM"),
    WEEKLY_ON_MONDAY(displayName = "Weekly on Monday", cronExpression = "0 0 9 * * MON", description = "Runs every Monday at 9:00 AM"),
    WEEKLY_ON_SUNDAY(displayName = "Weekly on Sunday", cronExpression = "0 0 9 * * SUN", description = "Runs every Sunday at 9:00 AM"),
    MONTHLY_ON_1ST(displayName = "Monthly on 1st", cronExpression = "0 0 9 1 * *", description = "Runs on the 1st of every month at 9:00 AM"),
    CUSTOM(displayName = "Custom", cronExpression = "", description = "Custom cron expression");

    companion object {
        fun fromDisplayName(displayName: String): TriggerType? =
            TriggerType.entries.find { it.displayName == displayName }
        
        fun getPredefinedTriggers(): List<TriggerType> =
            TriggerType.entries.filter { it != CUSTOM }
    }
}
