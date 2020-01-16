/*
 * Copyright 2020 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.controller;

/**
 * Allows callers to check the availability of the web console.
 * <p>
 * By default, the web console is not available until the process controller transitions to RUNNING.
 * The callers can try to make the console available even before by using the {@link #isAvailable()} method.
 *
 * @author <a href="mailto:yborgessf@redhat.com">Yeray Borges</a>
 */
public interface ConsoleAvailability {
    /**
     * Gets the availability of the web console.
     *
     * @return Whether the console is available at this moment.
     */
    boolean isAvailable();

    /**
     * Tries to make the console available. The console will be available only if the process controller is not
     * stopping.
     */
    void setAvailable();
}
