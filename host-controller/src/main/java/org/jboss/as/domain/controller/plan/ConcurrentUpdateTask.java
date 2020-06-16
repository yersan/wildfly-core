/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.as.domain.controller.plan;

import static java.security.AccessController.doPrivileged;
import static org.jboss.as.domain.controller.logging.DomainControllerLogger.HOST_CONTROLLER_LOGGER;

import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A task that uses an executor service to concurrently execute other tasks
 */
class ConcurrentUpdateTask implements Runnable {

    private final List<Runnable> concurrentTasks;
    private final ExecutorService executorService;

    ConcurrentUpdateTask(final List<Runnable> concurrentTasks, final ExecutorService executorService) {
        this.concurrentTasks = concurrentTasks;
        this.executorService = executorService;
    }

    @Override
    public void run() {

        // Submit each task to the executor
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (Runnable r : concurrentTasks) {
            final Runnable task = r;
            futures.add(doPrivileged(new PrivilegedAction<Future<?>>() {

                @Override
                public Future<?> run() {
                    return executorService.submit(task);
                }
            }));
        }

        // Wait until all complete before returning
        boolean patient = true;
        for (int i = 0; i < futures.size(); i++) {
            Future<?> future = futures.get(i);
            try {
                if (patient) {
                    future.get();
                } else {
                    // We've been interrupted; see if this task is already done
                    future.get(0, TimeUnit.MILLISECONDS);
                }
            } catch (InterruptedException e) {
                HOST_CONTROLLER_LOGGER.caughtExceptionWaitingForTask(ConcurrentUpdateTask.class.getSimpleName(),
                        e.getClass().getSimpleName(), concurrentTasks.get(i).toString());
                patient = false;
                future.cancel(true);
                HOST_CONTROLLER_LOGGER.info(" ....... InterruptedException ......"+patient);
            } catch (ExecutionException e) {
                HOST_CONTROLLER_LOGGER.caughtExceptionWaitingForTask(ConcurrentUpdateTask.class.getSimpleName(),
                        e.getClass().getSimpleName(), concurrentTasks.get(i).toString());
                future.cancel(true);
                HOST_CONTROLLER_LOGGER.info(" ....... ExecutionException ......"+patient);
            } catch (TimeoutException e) {
                // Task wasn't already done; cancel it
                HOST_CONTROLLER_LOGGER.caughtExceptionWaitingForTask(ConcurrentUpdateTask.class.getSimpleName(),
                        e.getClass().getSimpleName(), concurrentTasks.get(i).toString());
                patient = false; // it should already be false if we got here, but just in case someone changes something
                future.cancel(true);
                HOST_CONTROLLER_LOGGER.info(" ....... TimeoutException ......"+patient);
            }
        }

        if (!patient) {
            HOST_CONTROLLER_LOGGER.info(" ....... Initial ......"+Thread.currentThread().isInterrupted());
            Thread.currentThread().interrupt();
            HOST_CONTROLLER_LOGGER.info(" ....... Interrupted ......"+Thread.currentThread().isInterrupted());
//            Thread.currentThread().interrupt();
//            while (!Thread.currentThread().isInterrupted()) {
//                Thread.currentThread().interrupt();
//            }
            HOST_CONTROLLER_LOGGER.info(" ....... After While ......"+Thread.currentThread().isInterrupted());
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ConcurrentUpdateTask{tasks={");
        for (int i = 0; i < concurrentTasks.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(concurrentTasks.get(i).toString());
        }
        sb.append("}}");
        return sb.toString();
    }

}
