/*
 *  This file is part of the SIRIUS Software for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2020 Kai Dührkop, Markus Fleischauer, Marcus Ludwig, Martin A. Hoffman, Fleming Kretschmer, Marvin Meusel and Sebastian Böcker,
 *  Chair of Bioinformatics, Friedrich-Schilller University.
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Affero General Public License
 *  as published by the Free Software Foundation; either
 *  version 3 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <https://www.gnu.org/licenses/agpl-3.0.txt>
 */

package de.unijena.bioinf.ms.gui.dialogs;
/**
 * Created by Markus Fleischauer (markus.fleischauer@gmail.com)
 * as part of the sirius_frontend
 * 13.10.16.
 */

import de.unijena.bioinf.ms.frontend.core.SiriusProperties;
import de.unijena.bioinf.ms.gui.configs.Icons;
import de.unijena.bioinf.ms.gui.errorReport.FingerIDWebErrorReporter;
import de.unijena.bioinf.ms.gui.errorReport.SiriusDefaultErrorReport;
import de.unijena.bioinf.ms.gui.settings.ErrorReportSettingsPanel;
import de.unijena.bioinf.ms.gui.utils.GuiUtils;
import de.unijena.bioinf.ms.properties.PropertyManager;
import de.unijena.bioinf.utils.errorReport.ErrorReport;
import de.unijena.bioinf.utils.errorReport.ErrorReporter;
import org.jdesktop.swingx.JXTitledSeparator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Properties;

/**
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */
public class BugReportDialog extends JDialog {
    private final JButton discard, send;
    private final ErrorReportSettingsPanel reportSettings;
    private final JTextField subjectField = new JTextField();
    private final JTextArea textarea;
    private final JComboBox<String> report;
    private static String[] reportTypes = {ErrorReport.TYPES[1], ErrorReport.TYPES[2]};

    private final Properties props = SiriusProperties.SIRIUS_PROPERTIES_FILE().asProperties();

    public BugReportDialog(Frame owner) {
        super(owner, true);
        setTitle("Bug Report");
        setLayout(new BorderLayout());

        //============= NORTH =================
        add(new DialogHaeder(Icons.BUG_64), BorderLayout.NORTH);

        //============= CENTER =================
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.PAGE_AXIS));
        center.setBorder(BorderFactory.createEmptyBorder(GuiUtils.LARGE_GAP, 0, 0, 0));

        report = new JComboBox<>(reportTypes);
        center.add(report);
        center.add(Box.createVerticalStrut(GuiUtils.MEDIUM_GAP));

        reportSettings = new ErrorReportSettingsPanel(props);
        reportSettings.add(new JXTitledSeparator("Report"), GuiUtils.MEDIUM_GAP, false);


        JPanel sub = new JPanel(new BorderLayout());
        sub.add(new JLabel("Subject:  "), BorderLayout.WEST);
        sub.add(subjectField, BorderLayout.CENTER);
        reportSettings.add(sub);

        textarea = new JTextArea();
        textarea.setEditable(true);
        final JScrollPane sc = new JScrollPane(textarea, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sc.setPreferredSize((new Dimension(sc.getPreferredSize().width, 200)));
        sc.setBorder(new TitledBorder(new EmptyBorder(GuiUtils.SMALL_GAP, GuiUtils.SMALL_GAP, GuiUtils.SMALL_GAP, GuiUtils.SMALL_GAP), "Message"));
        reportSettings.add(sc, GuiUtils.SMALL_GAP, true);

        center.add(reportSettings);

        add(center, BorderLayout.CENTER);


        //============= SOUTH =================
        discard = new JButton("Discard");
        discard.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
        send = new JButton("Send");
        send.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                SwingWorker<String, String> worker = new SwingWorker<String, String>() {
                    @Override
                    protected String doInBackground() throws Exception {
                        reportSettings.saveProperties();
                        SiriusProperties.SIRIUS_PROPERTIES_FILE().setAndStoreProperties(props);

                        boolean senMail = Boolean.valueOf(PropertyManager.getProperty("de.unijena.bioinf.sirius.core.errorReporting.sendUsermail"));
                        String mail = PropertyManager.getProperty("de.unijena.bioinf.sirius.core.mailService.usermail");
                        boolean systemInfo = Boolean.valueOf(PropertyManager.getProperty("de.unijena.bioinf.sirius.core.errorReporting.systemInfo"));
                        SiriusDefaultErrorReport r = new SiriusDefaultErrorReport(subjectField.getText(), textarea.getText(), mail, systemInfo);
                        ErrorReporter repoter = new FingerIDWebErrorReporter(r);
                        repoter.getReport().setSendReportToUser(senMail);
                        repoter.getReport().setType((String) report.getSelectedItem());
                        repoter.call();
                        return "SUCCESS";
                    }
                };

                worker.execute();
                dispose();
            }
        });


        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(discard);
        buttons.add(send);

        add(buttons, BorderLayout.SOUTH);

        setMinimumSize(new Dimension(350, getPreferredSize().height));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(getParent());
        setVisible(true);
    }


}
