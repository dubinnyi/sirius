package de.unijena.bioinf.ms.frontend.subtools.lcms_align;

import com.google.common.base.Joiner;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.jobs.SiriusJobs;
import de.unijena.bioinf.ChemistryBase.ms.CompoundQuality;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.ChemistryBase.ms.MultipleSources;
import de.unijena.bioinf.ChemistryBase.ms.SpectrumFileSource;
import de.unijena.bioinf.ChemistryBase.ms.ft.model.AdductSettings;
import de.unijena.bioinf.babelms.ProjectSpaceManager;
import de.unijena.bioinf.babelms.ms.MsFileConfig;
import de.unijena.bioinf.io.lcms.LCMSParsing;
import de.unijena.bioinf.jjobs.BasicJJob;
import de.unijena.bioinf.lcms.LCMSProccessingInstance;
import de.unijena.bioinf.lcms.MemoryFileStorage;
import de.unijena.bioinf.lcms.ProcessedSample;
import de.unijena.bioinf.lcms.align.Cluster;
import de.unijena.bioinf.model.lcms.ConsensusFeature;
import de.unijena.bioinf.model.lcms.LCMSRun;
import de.unijena.bioinf.ms.frontend.subtools.PreprocessingJob;
import de.unijena.bioinf.ms.properties.ParameterConfig;
import de.unijena.bioinf.ms.properties.PropertyManager;
import de.unijena.bioinf.projectspace.sirius.CompoundContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class LcmsAlignSubToolJob extends PreprocessingJob {

    public LcmsAlignSubToolJob(@Nullable List<File> input, @Nullable ProjectSpaceManager space) {
        super(input, space);
    }

    @Override
    protected ProjectSpaceManager compute() throws Exception {
        final ArrayList<BasicJJob> jobs = new ArrayList<>();
        final LCMSProccessingInstance i = new LCMSProccessingInstance();
        i.setDetectableIonTypes(PropertyManager.DEFAULTS.createInstanceWithDefaults(AdductSettings.class).getDetectable());
        input = input.stream().sorted().collect(Collectors.toList());
        for (File f : input) {
            jobs.add(SiriusJobs.getGlobalJobManager().submitJob(new BasicJJob<>() {
                @Override
                protected Object compute() {
                    try {
                        MemoryFileStorage storage = new MemoryFileStorage();
                        final LCMSRun parse = LCMSParsing.parseRun(f, storage);
                        final ProcessedSample sample = i.addSample(parse, storage);
                        i.detectFeatures(sample);
                        storage.backOnDisc();
                        storage.dropBuffer();
                    } catch (Throwable e) {
                        e.printStackTrace();
                    }
                    return "";
                }
            }));
        }
        MultipleSources sourcelocation = MultipleSources.leastCommonAncestor(input.toArray(File[]::new));
        for (BasicJJob j : jobs) j.takeResult();
        i.getMs2Storage().backOnDisc();
        i.getMs2Storage().dropBuffer();
        Cluster alignment = i.alignAndGapFilling();
        i.detectAdductsWithGibbsSampling(alignment).writeToFile(i, File.createTempFile("network", ".js"));
        final ConsensusFeature[] consensusFeatures = i.makeConsensusFeatures(alignment);
        LOG().info("Gapfilling Done.");

        int totalFeatures=0, goodFeatures=0;
        //save to project space
        for (ConsensusFeature feature : consensusFeatures) {
            final Ms2Experiment experiment = feature.toMs2Experiment();
            ++totalFeatures;
            if (experiment.getAnnotation(CompoundQuality.class,CompoundQuality::new).isNotBadQuality()) {
                ++goodFeatures;
            }
            // set name to common prefix
            // kaidu: this is super slow, so we just ignore the filename
            experiment.setAnnotation(SpectrumFileSource.class, new SpectrumFileSource(sourcelocation.value));

            // if we found some adduct types in LCMS, set them into the config
            final Set<PrecursorIonType> ionTypes = feature.getPossibleAdductTypes();
            if (!ionTypes.isEmpty()) {
                ParameterConfig parameterConfig = PropertyManager.DEFAULTS.newIndependentInstance("LCMS-" + experiment.getName());
                parameterConfig.changeConfig("AdductSettings.enforced", Joiner.on(',').join(ionTypes));
                final MsFileConfig config = new MsFileConfig(parameterConfig);
                experiment.setAnnotation(MsFileConfig.class, config);
            }

            @NotNull final CompoundContainer compoundContainer = space.newCompoundWithUniqueId(experiment);
        }
        LOG().info("LCMS-Align done. " + goodFeatures + " of " + totalFeatures +  " are in qood quality.");
        return space;
    }
}
