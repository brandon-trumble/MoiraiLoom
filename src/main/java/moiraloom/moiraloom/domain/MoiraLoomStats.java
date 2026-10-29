package moiraloom.moiraloom.domain;

public record MoiraLoomStats(
        String moiraLoomName,

        int currentRunningJobs,

        int maxConcurrentJobs,

        boolean isVirtualThread,

        int totalCompletedJobs,

        int totalFailedJobs
) {
}
