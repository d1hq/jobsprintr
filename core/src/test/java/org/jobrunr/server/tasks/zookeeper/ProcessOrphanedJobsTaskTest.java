package org.jobrunr.server.tasks.zookeeper;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.filters.JobDefaultFilters;
import org.jobrunr.server.LogAllStateChangesFilter;
import org.jobrunr.server.tasks.AbstractTaskTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static java.util.Collections.singletonList;
import static org.jobrunr.JobRunrAssertions.assertThat;
import static org.jobrunr.jobs.JobTestBuilder.aJobInProgress;
import static org.jobrunr.jobs.JobTestBuilder.emptyJobList;
import static org.jobrunr.jobs.states.StateName.ENQUEUED;
import static org.jobrunr.jobs.states.StateName.FAILED;
import static org.jobrunr.jobs.states.StateName.PROCESSING;
import static org.jobrunr.jobs.states.StateName.SCHEDULED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessOrphanedJobsTaskTest extends AbstractTaskTest {

    LogAllStateChangesFilter logAllStateChangesFilter;
    ProcessOrphanedJobsTask task;

    @BeforeEach
    void setUpTask() {
        logAllStateChangesFilter = new LogAllStateChangesFilter();
        when(backgroundJobServer.getJobFilters()).thenReturn(new JobDefaultFilters(logAllStateChangesFilter));
        task = new ProcessOrphanedJobsTask(backgroundJobServer);
    }

    @Test
    void testTaskAndStateChangeFilters() {
        final Job orphanedJob = aJobInProgress().build();
        when(storageProvider.getJobList(eq(PROCESSING), any(Instant.class), any()))
                .thenReturn(
                        singletonList(orphanedJob),
                        emptyJobList()
                );

        runTask(task);

        verify(storageProvider).save(jobsToSaveArgumentCaptor.capture());
        assertThat(jobsToSaveArgumentCaptor.getValue().get(0)).hasStates(ENQUEUED, PROCESSING, FAILED, SCHEDULED);
        assertThat(logAllStateChangesFilter.getStateChanges(orphanedJob)).containsExactly("PROCESSING->FAILED", "FAILED->SCHEDULED");
        assertThat(logAllStateChangesFilter.onProcessingFailedIsCalled(orphanedJob)).isTrue();
    }

}