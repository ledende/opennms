/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2016 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2016 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 * OpenNMS(R) Licensing <license@opennms.org>
 *      http://www.opennms.org/
 *      http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.datachoices.shell.internal;

import org.apache.karaf.shell.api.action.Action;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Reference;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.opennms.features.datachoices.internal.UsageStatisticsReporter;

@Command(scope = "datachoices", name = "display-usage-report", description="Displays the usage statistics report.")
@Service
public class DisplayUsageReportCommand implements Action {

    @Reference
    public UsageStatisticsReporter m_usageStatisticsReporter;

    @Override
    public Object execute() throws Exception {
        long then = System.currentTimeMillis();
        String reportAsJson = m_usageStatisticsReporter.generateReport().toJson(true);
        long delta = System.currentTimeMillis() - then;

        System.out.printf("Generated usage statitics reports in %.2f seconds:\n%s\n", delta / 1000f, reportAsJson);
        return null;
    }
}
