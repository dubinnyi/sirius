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

package de.unijena.bioinf.ms.gui.molecular_formular;
/**
 * Created by Markus Fleischauer (markus.fleischauer@gmail.com)
 * as part of the sirius_frontend
 * 31.01.17.
 */

import ca.odell.glazedlists.swing.DefaultEventListModel;
import de.unijena.bioinf.GibbsSampling.ZodiacScore;
import de.unijena.bioinf.fingerid.blast.TopCSIScore;
import de.unijena.bioinf.ms.gui.actions.SiriusActions;
import de.unijena.bioinf.ms.gui.table.ActionListView;
import de.unijena.bioinf.projectspace.FormulaResultBean;
import de.unijena.bioinf.sirius.scores.SiriusScore;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

/**
 * @author Markus Fleischauer (markus.fleischauer@gmail.com)
 */
public class FormulaListCompactView extends ActionListView<FormulaList> {
    public FormulaListCompactView(FormulaList source) {
        super(source);

        final JList<FormulaResultBean> resultListView;
        resultListView = new JList<>(new DefaultEventListModel<>(source.getElementList()));
        resultListView.setCellRenderer(new FormulaListTextCellRenderer(source.getRenderScoreFunc(),source.getBestFunc()));
        resultListView.setSelectionModel(source.getResultListSelectionModel());
        resultListView.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        resultListView.setVisibleRowCount(1);
        resultListView.setPrototypeCellValue(FormulaListTextCellRenderer.PROTOTYPE);
        resultListView.setMinimumSize(new Dimension(0, 45));

        setLayout(new BorderLayout());


        JScrollPane listJSP = new JScrollPane(resultListView, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS);
        add(listJSP, BorderLayout.NORTH);






        resultListView.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    // Double-click detected
                    int index = resultListView.locationToIndex(e.getPoint());
                    resultListView.setSelectedIndex(index);
                    SiriusActions.COMPUTE_CSI_LOCAL.getInstance().actionPerformed(new ActionEvent(resultListView, 112, SiriusActions.COMPUTE_CSI_LOCAL.name()));
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
            }

            @Override
            public void mouseReleased(MouseEvent e) {
            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        });

        //decorate this guy
        KeyStroke enterKey = KeyStroke.getKeyStroke("ENTER");
        resultListView.getInputMap().put(enterKey, SiriusActions.COMPUTE_CSI_LOCAL.name());
        resultListView.getActionMap().put(SiriusActions.COMPUTE_CSI_LOCAL.name(),SiriusActions.COMPUTE_CSI_LOCAL.getInstance());
    }
}
