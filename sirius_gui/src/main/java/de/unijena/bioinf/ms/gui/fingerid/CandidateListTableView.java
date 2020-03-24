package de.unijena.bioinf.ms.gui.fingerid;

import ca.odell.glazedlists.EventList;
import ca.odell.glazedlists.FilterList;
import ca.odell.glazedlists.SortedList;
import de.unijena.bioinf.chemdb.PubmedLinks;
import de.unijena.bioinf.projectspace.InstanceBean;
import de.unijena.bioinf.projectspace.FormulaResultBean;
import de.unijena.bioinf.ms.gui.table.*;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Created by fleisch on 15.05.17.
 */
public class CandidateListTableView extends CandidateListView implements ActiveElementChangedListener<FormulaResultBean, InstanceBean> {

    private final ActionTable<FingerprintCandidateBean> table;
    private SortedList<FingerprintCandidateBean> sortedSource;

    public CandidateListTableView(final CandidateList list) {
        super(list);

        final CandidateTableFormat tf = new CandidateTableFormat(source.scoreStats);
        this.table = new ActionTable<>(filteredSource, sortedSource, tf);

        table.setSelectionModel(filteredSelectionModel);
        final SiriusResultTableCellRenderer defaultRenderer = new SiriusResultTableCellRenderer(tf.highlightColumnIndex());
        table.setDefaultRenderer(Object.class, defaultRenderer);

        table.getColumnModel().getColumn(5).setCellRenderer(new ListStatBarTableCellRenderer(tf.highlightColumnIndex(), source.scoreStats, false, false, null));
        table.getColumnModel().getColumn(6).setCellRenderer(new BarTableCellRenderer(tf.highlightColumnIndex(), 0f, 1f, true));
        LinkedSiriusTableCellRenderer linkRenderer = new LinkedSiriusTableCellRenderer(defaultRenderer, (LinkedSiriusTableCellRenderer.LinkCreator<PubmedLinks>) s -> s == null ? null : s.getPubmedLink());
        linkRenderer.registerToTable(table, 7);
        this.add(
                new JScrollPane(table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED),
                BorderLayout.CENTER
        );


    }

    @Override
    protected FilterList<FingerprintCandidateBean> configureFiltering(EventList<FingerprintCandidateBean> source) {
        sortedSource = new SortedList<>(source);
        return super.configureFiltering(sortedSource);
    }

    @Override
    public void resultsChanged(InstanceBean experiment, FormulaResultBean sre, List<FormulaResultBean> resultElements, ListSelectionModel selections) {
        System.out.println("CandidateListTableView Changed!");
        //not used
    }
}
