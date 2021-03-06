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

package de.unijena.bioinf.projectspace.summaries;

import de.unijena.bioinf.ChemistryBase.algorithm.scoring.FormulaScore;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.SScored;
import de.unijena.bioinf.ChemistryBase.algorithm.scoring.Score;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.chem.RetentionTime;
import de.unijena.bioinf.ChemistryBase.ms.ft.FTree;
import de.unijena.bioinf.ChemistryBase.ms.ft.TreeStatistics;
import de.unijena.bioinf.GibbsSampling.ZodiacScore;
import de.unijena.bioinf.fingerid.ConfidenceScore;
import de.unijena.bioinf.fingerid.blast.TopCSIScore;
import de.unijena.bioinf.ms.annotations.DataAnnotation;
import de.unijena.bioinf.projectspace.FormulaScoring;
import de.unijena.bioinf.projectspace.ProjectSpaceManager;
import de.unijena.bioinf.projectspace.ProjectWriter;
import de.unijena.bioinf.projectspace.Summarizer;
import de.unijena.bioinf.projectspace.sirius.CompoundContainer;
import de.unijena.bioinf.projectspace.sirius.FormulaResult;
import de.unijena.bioinf.sirius.FTreeMetricsHelper;
import de.unijena.bioinf.sirius.scores.IsotopeScore;
import de.unijena.bioinf.sirius.scores.SiriusScore;
import de.unijena.bioinf.sirius.scores.TreeScore;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class FormulaSummaryWriter implements Summarizer {

    final static List<Class<? extends FormulaScore>> RANKING_SCORES = List.of(ZodiacScore.class, SiriusScore.class, TreeScore.class, IsotopeScore.class, TopCSIScore.class);
    final LinkedHashMap<Class<? extends FormulaScore>, String> globalTypes = new LinkedHashMap<>();
    final Map<FormulaResult, Class<? extends FormulaScore>> globalResults = new HashMap<>();
    final Map<FormulaResult, String> prefix = new HashMap<>();

    @Override
    public List<Class<? extends DataAnnotation>> requiredFormulaResultAnnotations() {
        return Arrays.asList(
                FormulaScoring.class,
                FTree.class
        );
    }


    @Override
    public void addWriteCompoundSummary(ProjectWriter writer, @NotNull CompoundContainer exp, List<? extends SScored<FormulaResult, ? extends FormulaScore>> formulaResults) throws IOException {
        if (!writer.exists(exp.getId().getDirectoryName()))
            return;
        if (formulaResults == null || formulaResults.isEmpty())
            return;

        //todo add adducts
        List<SScored<FormulaResult, ? extends FormulaScore>> results = FormulaScoring.reRankBy(formulaResults, RANKING_SCORES, true);

        writer.inDirectory(exp.getId().getDirectoryName(), () -> {
            writer.textFile(SummaryLocations.FORMULA_CANDIDATES, w -> {
                LinkedHashMap<Class<? extends FormulaScore>, String> types = new LinkedHashMap<>();

                final AtomicBoolean first = new AtomicBoolean(true);
                results.forEach(r -> r.getCandidate().getAnnotation(FormulaScoring.class)
                        .ifPresent(s -> {
                            if (first.getAndSet(false)) {
                                this.globalResults.put(r.getCandidate(), r.getScoreObject().getClass());
                                this.prefix.put(r.getCandidate(), exp.getId().getIonMass().orElse(Double.NaN) + "\t" + exp.getId().getRt().orElse(RetentionTime.NA()).getRetentionTimeInSeconds() + "\t" + exp.getId().getDirectoryName());
                            }
                            s.annotations().forEach((key, value) -> {
                                if (value != null && !value.isNa()) {
                                    types.putIfAbsent(value.getClass(), value.name());
                                    this.globalTypes.putIfAbsent(value.getClass(), value.name());
                                }
                            });
                        }));

                //writing stuff
                types.remove(TopCSIScore.class);
                types.remove(ConfidenceScore.class);
                writeCSV(w, types, results, null);
            });

            return true;
        });
    }

    @Override
    public void writeProjectSpaceSummary(ProjectWriter writer) throws IOException {
        final List<SScored<FormulaResult, ? extends FormulaScore>> r = FormulaScoring.rankBy(globalResults.keySet(), RANKING_SCORES, true);
        globalTypes.remove(ConfidenceScore.class);
        globalTypes.remove(TopCSIScore.class);
        writer.textFile(SummaryLocations.FORMULA_SUMMARY, w -> {
            writeCSV(w, globalTypes, r, prefix);
        });
    }

    private String makeHeader(String scorings) {
        final StringBuilder headerBuilder = new StringBuilder("molecularFormula\tadduct\tprecursorFormula");/*	rankingScore*/
        if (scorings != null && !scorings.isEmpty())
            headerBuilder.append("\t").append(scorings);
        headerBuilder.append("\tnumExplainedPeaks\texplainedIntensity\tmedianMassErrorFragmentPeaks(ppm)\tmedianAbsoluteMassErrorFragmentPeaks(ppm)\tmassErrorPrecursor(ppm)");
        return headerBuilder.toString();
    }

    private void writeCSV(Writer w, LinkedHashMap<Class<? extends FormulaScore>, String> types, List<? extends SScored<? extends FormulaResult, ? extends Score<?>>> results, Map<FormulaResult, String> prefix) throws IOException {
        final List<Class<? extends FormulaScore>> scoreOrder = ProjectSpaceManager.scorePriorities().stream().filter(types::containsKey).collect(Collectors.toList());
        results = results.stream()
                .sorted((i1, i2) -> FormulaScoring.comparingMultiScore(scoreOrder).compare(
                        i1.getCandidate().getAnnotationOrThrow(FormulaScoring.class),
                        i2.getCandidate().getAnnotationOrThrow(FormulaScoring.class)))
                .collect(Collectors.toList());


        String header = makeHeader(scoreOrder.stream().map(types::get).collect(Collectors.joining("\t")));
        if (prefix != null)
            header = header + "\tionMass" + "\tretentionTimeInSeconds" + "\tid";

        w.write("rank\t" + header + "\n");

        int rank = 0;
        MolecularFormula preFormula = null;
        for (SScored<? extends FormulaResult, ? extends Score<?>> s : results) {
            FormulaResult r = s.getCandidate();
            PrecursorIonType ion = r.getId().getIonType();
            FormulaScoring scores = r.getAnnotationOrThrow(FormulaScoring.class);
            FTree tree = r.getAnnotationOrNull(FTree.class);
            if (preFormula == null || !r.getId().getPrecursorFormula().equals(preFormula))
                rank++;
            preFormula = r.getId().getPrecursorFormula();


            w.write(String.valueOf(rank));
            w.write('\t');
            w.write(r.getId().getMolecularFormula().toString());
            w.write('\t');
            w.write(ion != null ? ion.toString() : "?");
            w.write('\t');

            w.write(preFormula.toString());
            w.write('\t');


//            w.write(s.getScoreObject().toString());
//            w.write('\t');
            //writing different Scores to file e.g. sirius and zodiac
            for (Class<? extends FormulaScore> k : scoreOrder) {
                w.write(scores.getAnnotationOr(k, FormulaScore::NA).toString());
                w.write('\t');
            }
            w.write(tree != null ? String.valueOf(tree.numberOfVertices()) : "");
            w.write('\t');
            w.write(tree != null ? String.valueOf(tree.getAnnotationOrThrow(TreeStatistics.class).getExplainedIntensity()) : "");
            w.write('\t');
            w.write(tree != null ? String.valueOf(new FTreeMetricsHelper(tree).getMedianMassDeviation().getPpm()) : "");
            w.write('\t');
            w.write(tree != null ? String.valueOf(new FTreeMetricsHelper(tree).getMedianAbsoluteMassDeviation().getPpm()) : "");
            w.write('\t');
            w.write(tree != null ? r.getId().getParentId().getIonMass().map(e -> tree.getMassErrorTo(tree.getRoot(), e).getPpm()).map(String::valueOf).orElse("N/A") : "");
            if (prefix != null) {
                w.write('\t');
                w.write(prefix.get(r));
            }

            w.write('\n');
        }
    }
}

