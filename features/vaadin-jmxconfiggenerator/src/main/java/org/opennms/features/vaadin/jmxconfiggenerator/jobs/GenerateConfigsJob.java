/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2015 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2015 The OpenNMS Group, Inc.
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
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.features.vaadin.jmxconfiggenerator.jobs;

import java.util.Collection;
import java.util.Objects;

import org.opennms.features.jmxconfiggenerator.graphs.GraphConfigGenerator;
import org.opennms.features.jmxconfiggenerator.graphs.JmxConfigReader;
import org.opennms.features.jmxconfiggenerator.graphs.Report;
import org.opennms.features.jmxconfiggenerator.log.Slf4jLogAdapter;
import org.opennms.features.vaadin.jmxconfiggenerator.JmxConfigGeneratorUI;
import org.opennms.features.vaadin.jmxconfiggenerator.data.UiModel;
import org.opennms.features.vaadin.jmxconfiggenerator.ui.UiState;

/**
 * Job to generate the configs needed.
 */
public class GenerateConfigsJob implements Task {

    private final UiModel model;
    private final JmxConfigGeneratorUI ui;

    public GenerateConfigsJob(JmxConfigGeneratorUI ui, UiModel model) {
        this.ui = Objects.requireNonNull(ui);
        this.model = Objects.requireNonNull(model);
    }

    @Override
    public Void execute() throws TaskRunException {
        // create snmp-graph.properties
        GraphConfigGenerator graphConfigGenerator = new GraphConfigGenerator(new Slf4jLogAdapter(GraphConfigGenerator.class));
        Collection<Report> reports = new JmxConfigReader(new Slf4jLogAdapter(JmxConfigReader.class)).generateReportsByJmxDatacollectionConfig(model.getOutputConfig());
        model.setSnmpGraphProperties(graphConfigGenerator.generateSnmpGraph(reports));
        model.updateOutput();
        return null;
    }

    @Override
    public void onSuccess(Object result) {
        ui.updateView(UiState.ResultView);
    }

    @Override
    public void onError() {

    }

    @Override
    public JmxConfigGeneratorUI getUI() {
        if (!ui.isAttached()) {
            throw new IllegalStateException("UI " + ui.getUIId() + " is detached.");
        }
        return ui;
    }
}
