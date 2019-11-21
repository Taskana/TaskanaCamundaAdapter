package pro.taskana.adapter.impl;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pro.taskana.adapter.manager.AdapterManager;
import pro.taskana.adapter.systemconnector.api.ReferencedTask;
import pro.taskana.adapter.systemconnector.api.SystemConnector;
import pro.taskana.adapter.taskanaconnector.api.TaskanaConnector;
import pro.taskana.exceptions.SystemException;

/**
 * Completes ReferencedTasks in the external system after completion of corresponding taskana tasks.
 *
 * @author bbr
 */
@Component
public class ReferencedTaskCompleter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferencedTaskCompleter.class);

    @Value("${taskanaAdapter.total.transaction.lifetime.in.seconds:120}")
    private int maximumTotalTransactionLifetime;

    @Autowired
    AdapterManager adapterManager;

    @Scheduled(
        fixedRateString = "${taskana.adapter.scheduler.run.interval.for.complete.referenced.tasks.in.milliseconds}")
    public void retrieveFinishedTaskanaTasksAndCompleteCorrespondingReferencedTasks() {

        synchronized (ReferencedTaskCompleter.class) {
            if (!adapterManager.isInitialized()) {
                return;
            }

            LOGGER.debug(
                "----------retrieveFinishedTaskanaTasksAndCompleteCorrespondingReferencedTasks started----------------------------");
            try {
                retrieveFinishedTaskanaTasksAndCompleteCorrespondingReferencedTask();
            } catch (Exception ex) {
                LOGGER.debug("Caught {} while trying to complete referenced tasks", ex);
            }
        }
    }

    public void retrieveFinishedTaskanaTasksAndCompleteCorrespondingReferencedTask() {
        LOGGER
            .trace("ReferencedTaskCompleter.retrieveFinishedTaskanaTasksAndCompleteCorrespondingReferencedTask ENTRY");
        try {
            TaskanaConnector taskanaSystemConnector = adapterManager.getTaskanaConnector();

            List<ReferencedTask> tasksCompletedByTaskana = taskanaSystemConnector.retrieveCompletedTaskanaTasks();
            List<ReferencedTask> tasksCompletedInExternalSystem = completeReferencedTasksInExternalSystem(
                tasksCompletedByTaskana);

            taskanaSystemConnector.referencedTasksHaveBeenCompleted(tasksCompletedInExternalSystem);
        } finally {
            LOGGER.trace(
                "ReferencedTaskCompleter.retrieveFinishedTaskanaTasksAndCompleteCorrespondingReferencedTask EXIT ");
        }
    }

    private List<ReferencedTask> completeReferencedTasksInExternalSystem(List<ReferencedTask> tasksCompletedByTaskana) {
        List<ReferencedTask> tasksCompletedInExternalSystem = new ArrayList<>();
        for (ReferencedTask referencedTask : tasksCompletedByTaskana) {
            if (completeReferencedTask(referencedTask)) {
                tasksCompletedInExternalSystem.add(referencedTask);
            }
        }
        return tasksCompletedInExternalSystem;
    }

    public boolean completeReferencedTask(ReferencedTask referencedTask) {
        LOGGER.trace("ENTRY to ReferencedTaskCompleter.completeReferencedTask, TaskId = {} ", referencedTask.getId());
        boolean success = false;
        try {
            SystemConnector connector = adapterManager.getSystemConnectors().get(referencedTask.getSystemURL());
            if (connector != null) {
                connector.completeReferencedTask(referencedTask);
                success = true;
            } else {
                throw new SystemException("couldnt find a connector for systemUrl " + referencedTask.getSystemURL());
            }
        } catch (Exception ex) {
            LOGGER.error("Caught {} when attempting to complete referenced task {}", ex, referencedTask);
        }
        LOGGER.trace("Exit from ReferencedTaskCompleter.completeReferencedTask, Success = {} ", success);
        return success;
    }

}
