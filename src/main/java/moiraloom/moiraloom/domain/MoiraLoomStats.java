package taskforge.taskforge.domain;

public record TaskForgeStats(
        String taskForgeName,

        int currentRunningJobs,

        int maxConcurrentJobs,

        boolean isVirtualThread,

        int totalCompletedJobs,

        int totalFailedJobs
) {
}
