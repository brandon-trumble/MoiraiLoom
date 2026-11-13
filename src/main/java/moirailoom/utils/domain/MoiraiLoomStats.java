package moirailoom.moirailoom.domain;

public record MoiraiLoomStats(
        String moiraiLoomName,

        int currentRunningJobs,

        int maxConcurrentJobs,

        boolean isVirtualThread,

        int totalCompletedJobs,

        int totalFailedJobs,

        int totalJobsPending,

        int totalJobsCanceled
) {
}
