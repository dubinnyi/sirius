/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.unijena.bioinf.sirius.gui.fingerid;

import de.unijena.bioinf.sirius.gui.mainframe.MainFrame;
import de.unijena.bioinf.sirius.gui.mainframe.results.ActiveResultChangedListener;
import de.unijena.bioinf.sirius.gui.settings.TwoCloumnPanel;
import de.unijena.bioinf.sirius.gui.structure.ExperimentContainer;
import de.unijena.bioinf.sirius.gui.structure.SiriusResultElement;
import de.unijena.bioinf.sirius.gui.utils.Icons;
import de.unijena.bioinf.sirius.gui.utils.ToolbarButton;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

public class CompoundCandidateView extends JPanel implements ActiveResultChangedListener {

    private ExperimentContainer experimentContainer;
    private SiriusResultElement resultElement;
    protected CSIFingerIdComputation storage;
    protected CandidateJList list;
    protected JButton searchCSIButton;

    protected CardLayout layout;
    private MainFrame frame;
    protected Runnable enableCsiFingerId;

    public CompoundCandidateView(MainFrame owner){
        this.frame = owner;
        this.storage = owner.getCsiFingerId();
        this.enableCsiFingerId = new Runnable() {
            @Override
            public void run() {
                searchCSIButton.setEnabled(storage.isEnabled());//todo is that in conflict with the action??
            }
        };
        storage.getEnabledListeners().add(enableCsiFingerId);
        refresh();
    }

    public void dispose() {
        list.dispose();
        storage.getEnabledListeners().remove(enableCsiFingerId);
    }

    private void refresh() {
        this.layout = new CardLayout();
        setLayout(layout);
        add(new JPanel(), "null");
        add(new ComputeElement(), "computeButton");
        add(new JLabel(Icons.FP_LOADER), "loader");


        TwoCloumnPanel nothing = new TwoCloumnPanel();
        nothing.add(new JLabel(Icons.NO_MATCH_128));
        nothing.add(new JLabel("<html><B>No candidates found for to this Molecular Formula.</B></html>"), 5, false);
        add(nothing, "empty");

        list = new CandidateJList(frame, storage, experimentContainer, resultElement == null ? null : resultElement.getFingerIdData());
        add(list, "list");
        setVisible(true);
        resultsChanged(null, null);
    }


    public boolean computationEnabled() {
        return searchCSIButton.isEnabled(); // this is ugly but good enough ;-)
    }

    public void addActionListener(ActionListener l) {
        searchCSIButton.addActionListener(l);
    }

    @Override
    public void resultsChanged(ExperimentContainer container, SiriusResultElement element) {
        System.out.println("CHANGE_DATA");
        this.experimentContainer = container;
        this.resultElement = element;
        list.refresh(container, resultElement == null ? null : resultElement.getFingerIdData());

        if (resultElement == null)
            layout.show(this, "null");
        else {
            switch (resultElement.fingerIdComputeState) {
                case COMPUTING:
                    System.out.println("loader");
                    layout.show(this, "loader");
                    break;
                case COMPUTED:
                    System.out.println("list");
                    if (list == null || list.candidateList.getModel().getSize() <= 0) {
                        layout.show(this, "empty");
                    } else {
                        layout.show(this, "list");
                    }
                    break;
                default:
                    System.out.println("button");
                    layout.show(this, "computeButton");//todo other types
                    break;
            }
        }

        if (resultElement == null || !storage.isEnabled()) {

            searchCSIButton.setEnabled(false);
            searchCSIButton.setToolTipText("");
        } else if (resultElement.getResult().getResolvedTree().numberOfVertices() < 3) {
            searchCSIButton.setEnabled(false);
            searchCSIButton.setToolTipText("Fragmentation tree must explain at least 3 peaks");
        } else {
            if (resultElement.getCharge() > 0) {
                searchCSIButton.setEnabled(true);
                searchCSIButton.setToolTipText("Start CSI:FingerId online search to identify the molecular structure of the measured compound");
            } else {
                searchCSIButton.setEnabled(false);
                searchCSIButton.setToolTipText("With this version, negative ion mode is not supported for CSI:FingerId");
            }
        }

    }

    public class ComputeElement extends TwoCloumnPanel {
        public ComputeElement() {
            searchCSIButton = new ToolbarButton(Icons.FINGER_64);
            searchCSIButton.setText("Search with CSI:FingerId");
            add(searchCSIButton);
            searchCSIButton.setEnabled((resultElement != null && storage.isEnabled()));
            setVisible(true);
        }
    }

}
